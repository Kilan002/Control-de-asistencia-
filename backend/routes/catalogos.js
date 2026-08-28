const express = require('express');
const Catalogo = require('../models/Catalogo');
const Usuario = require('../models/Usuario');
const Asignacion = require('../models/Asignacion');
const { requiereSesion, requiereRol } = require('../middleware/auth');
const multer = require('multer');
const pdfParse = require('pdf-parse');
const { GRUPO_VALIDO } = require('../utils/grupos');

const router = express.Router();
router.use(requiereSesion);

function normalizar(nombre) {
  return nombre.trim().replace(/\s+/g, ' ').toLocaleLowerCase('es-MX');
}

const uploadPdf = multer({
  storage: multer.memoryStorage(),
  limits: { files: 1, fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, cb) => cb(null, file.mimetype === 'application/pdf' || /\.pdf$/i.test(file.originalname))
});

const MATERIAS_COMPLETAS = {
  'Com Hab Dig': 'Comunicación y Habilidades Digitales',
  'Hab Soc Man Conf': 'Habilidades Socioemocionales y Manejo de Conflictos',
  'Elect Analog Pot': 'Electrónica Analógica y de Potencia',
  'Des Pens TD': 'Desarrollo del Pensamiento y Toma de Decisiones',
  'Sis Neum Hid': 'Sistemas Neumáticos e Hidráulicos',
  'Est Prop Mat': 'Estructura y Propiedades de los Materiales',
  'CLP': 'Controladores Lógicos Programables',
  'LEAD': 'Liderazgo de Equipos de Alto Desempeño',
  'Imp Sist Aut': 'Implementación de Sistemas Automatizados'
};

function esIngles(materia) {
  return /^ingl[eé]s\s+(?:[ivx]+|\d+)$/i.test(materia.trim());
}

async function extraerHorarioPdf(buffer) {
  const filas = new Map();
  let grupo = '';
  await pdfParse(buffer, {
    pagerender: async (page) => {
      const contenido = await page.getTextContent();
      const items = contenido.items.map(item => ({
        texto: item.str.trim(), x: item.transform[4], y: item.transform[5]
      })).filter(item => item.texto);

      if (!grupo) {
        grupo = items.map(i => i.texto.toUpperCase()).find(t => GRUPO_VALIDO.test(t)) || '';
      }
      const encabezado = items.find(i => i.texto === 'Profesores');
      if (!encabezado) return '';

      const candidatos = items.filter(i => i.y < encabezado.y - 2 && i.x > 90);
      for (const item of candidatos) {
        const clave = Math.round(item.y * 2) / 2;
        if (!filas.has(clave)) filas.set(clave, []);
        filas.get(clave).push(item);
      }
      return '';
    }
  });

  if (!grupo) throw new Error('No se encontró un código de grupo válido en el PDF.');
  const registros = [];
  let profesorAnterior = '';
  for (const items of [...filas.values()].sort((a, b) => b[0].y - a[0].y)) {
    const profesorItem = items.filter(i => i.x < 300).sort((a, b) => a.x - b.x)[0];
    const materiaItem = items.filter(i => i.x >= 300).sort((a, b) => a.x - b.x)[0];
    if (!profesorItem || !materiaItem) continue;

    let profesor = profesorItem.texto;
    if (/^['’]{2}$/.test(profesor.replace(/\s/g, ''))) profesor = profesorAnterior;
    if (!profesor) continue;
    profesor = profesor.replace(/\s*\([^)]+\)\s*$/, '').trim();
    profesorAnterior = profesor;

    const materiaOriginal = materiaItem.texto;
    const abreviatura = materiaOriginal.match(/\(([^)]+)\)\s*$/)?.[1];
    const materia = (abreviatura && MATERIAS_COMPLETAS[abreviatura]) ||
      materiaOriginal.replace(/\s*\([^)]+\)\s*$/, '').trim();
    if (!materia || esIngles(materia)) continue;
    registros.push({ grupo, profesor, materia });
  }
  if (!registros.length) throw new Error('No se encontraron profesores y materias en la tabla final del PDF.');
  return { grupo, registros };
}

function validarAsignacion(registro) {
  const grupo = String(registro.grupo || '').trim().toUpperCase();
  const profesor = String(registro.profesor || '').trim().replace(/\s+/g, ' ');
  const materia = String(registro.materia || '').trim().replace(/\s+/g, ' ');
  if (!GRUPO_VALIDO.test(grupo)) throw new Error(`Grupo inválido: ${grupo || '(vacío)'}.`);
  if (!profesor || profesor.length > 100) throw new Error('Revisa el nombre del profesor.');
  if (!materia || materia.length > 100 || esIngles(materia)) throw new Error('Revisa el nombre de la materia. Inglés no se puede importar.');
  return { grupo, profesor, materia, materiaNormalizada: normalizar(materia), actualizadoEn: new Date() };
}

async function asegurarCatalogos({ grupo, profesor, materia }) {
  await Catalogo.findOneAndUpdate(
    { tipo: 'materia', nombreNormalizado: normalizar(materia) },
    { $setOnInsert: { tipo: 'materia', nombre: materia, nombreNormalizado: normalizar(materia) }, $addToSet: { grupos: grupo } },
    { upsert: true }
  );
  await Catalogo.findOneAndUpdate(
    { tipo: 'profesor', nombreNormalizado: normalizar(profesor) },
    { $setOnInsert: { tipo: 'profesor', nombre: profesor, nombreNormalizado: normalizar(profesor), grupos: [] } },
    { upsert: true }
  );
}

router.post('/importar-pdf', requiereRol('admin'), uploadPdf.single('archivo'), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'Selecciona un solo archivo PDF.' });
  try {
    res.json(await extraerHorarioPdf(req.file.buffer));
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

router.get('/asignaciones', requiereRol('admin', 'admin_lectura'), async (req, res) => {
  res.json(await Asignacion.find({}).sort({ grupo: 1, materia: 1 }));
});

router.post('/asignaciones', requiereRol('admin'), async (req, res) => {
  const entrada = Array.isArray(req.body.registros) ? req.body.registros : [];
  if (!entrada.length || entrada.length > 40) return res.status(400).json({ error: 'La importación debe contener entre 1 y 40 registros.' });
  let registros;
  try { registros = entrada.map(validarAsignacion); }
  catch (err) { return res.status(400).json({ error: err.message }); }

  for (const registro of registros) {
    await asegurarCatalogos(registro);
    await Asignacion.findOneAndUpdate(
      { grupo: registro.grupo, materiaNormalizada: registro.materiaNormalizada },
      registro,
      { upsert: true, new: true, setDefaultsOnInsert: true }
    );
  }
  res.json({ ok: true, guardados: registros.length });
});

router.put('/asignaciones/:id', requiereRol('admin'), async (req, res) => {
  let registro;
  try { registro = validarAsignacion(req.body); }
  catch (err) { return res.status(400).json({ error: err.message }); }
  await asegurarCatalogos(registro);
  const actualizado = await Asignacion.findByIdAndUpdate(req.params.id, registro, { new: true, runValidators: true });
  if (!actualizado) return res.status(404).json({ error: 'La asignación ya no existe.' });
  res.json(actualizado);
});

// Cualquier usuario autenticado necesita consultar estas opciones al registrar.
router.get('/', async (req, res) => {
  let filtro = {};
  let asignaciones = [];
  if (req.usuario.rol === 'alumno') {
    const usuario = await Usuario.findById(req.usuario.matricula, { grupo: 1 });
    const grupo = (usuario?.grupo || '').trim().toUpperCase();
    asignaciones = await Asignacion.find({ grupo }, { grupo: 1, materia: 1, profesor: 1 }).sort({ materia: 1 });
    filtro = {
      $or: [
        { tipo: 'profesor' },
        { tipo: 'materia', $or: [{ grupos: grupo }, { grupos: { $size: 0 } }, { grupos: { $exists: false } }] }
      ]
    };
  }

  const elementos = await Catalogo.find(filtro).sort({ tipo: 1, nombre: 1 });
  res.json({
    materias: elementos.filter(e => e.tipo === 'materia'),
    profesores: elementos.filter(e => e.tipo === 'profesor'),
    asignaciones
  });
});

module.exports = router;
// Se expone para las pruebas automáticas del importador sin abrir el servidor.
module.exports.extraerHorarioPdf = extraerHorarioPdf;
