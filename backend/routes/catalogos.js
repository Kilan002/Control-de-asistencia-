const express = require('express');
const Catalogo = require('../models/Catalogo');
const Usuario = require('../models/Usuario');
const { requiereSesion, requiereRol } = require('../middleware/auth');

const router = express.Router();
router.use(requiereSesion);

function normalizar(nombre) {
  return nombre.trim().replace(/\s+/g, ' ').toLocaleLowerCase('es-MX');
}

const GRUPO_VALIDO = /^(RBM|IMTM)[1-5][1-9]$/;

function normalizarGrupos(grupos) {
  const lista = Array.isArray(grupos) ? grupos : [];
  return [...new Set(lista.map(g => String(g).trim().toUpperCase()).filter(Boolean))];
}

function validarGrupos(grupos) {
  return grupos.every(grupo => GRUPO_VALIDO.test(grupo));
}

// Cualquier usuario autenticado necesita consultar estas opciones al registrar.
router.get('/', async (req, res) => {
  let filtro = {};
  if (req.usuario.rol === 'alumno') {
    const usuario = await Usuario.findById(req.usuario.matricula, { grupo: 1 });
    const grupo = (usuario?.grupo || '').trim().toUpperCase();
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
    profesores: elementos.filter(e => e.tipo === 'profesor')
  });
});

// Solo el administrador con permisos de modificación puede agregar opciones.
router.post('/', requiereRol('admin'), async (req, res) => {
  const tipo = req.body.tipo;
  const nombre = (req.body.nombre || '').trim().replace(/\s+/g, ' ');
  const grupos = normalizarGrupos(req.body.grupos);

  if (!['materia', 'profesor'].includes(tipo)) {
    return res.status(400).json({ error: 'Tipo de catálogo inválido.' });
  }
  if (!nombre || nombre.length > 100) {
    return res.status(400).json({ error: 'El nombre debe tener entre 1 y 100 caracteres.' });
  }
  if (tipo === 'materia' && grupos.length === 0) {
    return res.status(400).json({ error: 'Asigna la materia al menos a un grupo.' });
  }
  if (!validarGrupos(grupos)) {
    return res.status(400).json({ error: 'Usa códigos válidos como RBM11, RBM12 o IMTM21.' });
  }

  try {
    const elemento = await Catalogo.create({
      tipo,
      nombre,
      nombreNormalizado: normalizar(nombre),
      grupos: tipo === 'materia' ? grupos : []
    });
    res.status(201).json(elemento);
  } catch (err) {
    if (err.code === 11000) {
      return res.status(409).json({ error: 'Ese elemento ya está agregado.' });
    }
    throw err;
  }
});

// Permite asignar o reasignar una materia existente sin eliminarla.
router.put('/:id/grupos', requiereRol('admin'), async (req, res) => {
  const grupos = normalizarGrupos(req.body.grupos);
  if (grupos.length === 0 || !validarGrupos(grupos)) {
    return res.status(400).json({ error: 'Indica al menos un código válido, como RBM11 o IMTM21.' });
  }

  const elemento = await Catalogo.findOneAndUpdate(
    { _id: req.params.id, tipo: 'materia' },
    { grupos },
    { new: true }
  );
  if (!elemento) return res.status(404).json({ error: 'La materia ya no existe.' });
  res.json(elemento);
});

// Eliminar una opción no borra ni altera los registros históricos que la usaron.
router.delete('/:id', requiereRol('admin'), async (req, res) => {
  const elemento = await Catalogo.findByIdAndDelete(req.params.id);
  if (!elemento) return res.status(404).json({ error: 'El elemento ya no existe.' });
  res.json({ ok: true });
});

module.exports = router;
