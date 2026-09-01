const express = require('express');
const Registro = require('../models/Registro');
const Asignacion = require('../models/Asignacion');
const Usuario = require('../models/Usuario');
const { requiereSesion, requiereRol } = require('../middleware/auth');

const router = express.Router();
router.use(requiereSesion);

function calcularFueraDeHorario(version) {
  if (!version.capturadoEn || !version.horaFin) return false;
  const [hF, mF] = version.horaFin.split(':').map(Number);
  const limite = new Date(version.capturadoEn);
  limite.setHours(hF, mF, 0, 0);
  return new Date(version.capturadoEn) > limite;
}

// GET /registros/mios — los del alumno logueado
router.get('/mios', async (req, res) => {
  const registros = await Registro.find({ matricula: req.usuario.matricula }).sort({ creadoEn: -1 });
  res.json(registros);
});

// GET /registros — todos, solo admin / admin_lectura
router.get('/', requiereRol('admin', 'admin_lectura'), async (req, res) => {
  const registros = await Registro.find({}).sort({ creadoEn: -1 });
  res.json(registros);
});

// GET /registros/:id — administradores o el alumno dueño del registro
router.get('/:id', async (req, res) => {
  const registro = await Registro.findById(req.params.id);
  if (!registro) return res.status(404).json({ error: 'No existe ese registro.' });
  const esAdmin = ['admin', 'admin_lectura'].includes(req.usuario.rol);
  if (!esAdmin && registro.matricula !== req.usuario.matricula) {
    return res.status(403).json({ error: 'No tienes permiso para consultar este registro.' });
  }
  res.json(registro);
});

// POST /registros — el alumno crea su registro (primera versión)
router.post('/', async (req, res) => {
  const { materia, maestro, horaInicio, horaFin, asistencia, observaciones, evidencia } = req.body;

  const faltantes = [];
  if (!materia) faltantes.push('materia');
  if (!maestro) faltantes.push('maestro');
  if (!horaInicio) faltantes.push('hora de inicio');
  if (!horaFin) faltantes.push('hora de fin');
  if (!['normal', 'retardo', 'falta'].includes(asistencia)) faltantes.push('si se impartió la clase');
  const necesitaEvidencia = asistencia === 'falta' || asistencia === 'retardo';
  if (necesitaEvidencia && !evidencia) faltantes.push('foto de evidencia');
  if (faltantes.length) {
    return res.status(400).json({ error: 'Faltan datos: ' + faltantes.join(', ') });
  }

  const usuario = await Usuario.findById(req.usuario.matricula, { grupo: 1 });
  const grupoUsuario = (usuario?.grupo || '').trim().toUpperCase();
  const normalizar = valor => valor.trim().replace(/\s+/g, ' ').toLocaleLowerCase('es-MX');
  const asignacionPermitida = await Asignacion.findOne({
    grupo: grupoUsuario,
    materiaNormalizada: normalizar(materia),
    profesor: { $regex: new RegExp(`^${maestro.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`, 'i') }
  });
  if (!asignacionPermitida) {
    return res.status(403).json({ error: 'Esa combinación de materia y profesor no está asignada a tu grupo.' });
  }

  // capturadoEn lo pone el servidor (Date.now del propio backend), nunca
  // el valor que mande el navegador — así nadie puede declarar una hora falsa.
  const registro = await Registro.create({
    matricula: req.usuario.matricula,
    grupo: grupoUsuario,
    materia: asignacionPermitida.materia,
    maestro: asignacionPermitida.profesor,
    versiones: [{
      numeroVersion: 1,
      autor: req.usuario.matricula,
      horaInicio, horaFin, asistencia, observaciones: (observaciones || '').trim(),
      evidencia: evidencia || null,
      capturadoEn: new Date()
    }]
  });

  res.status(201).json(registro);
});

// PUT /registros/:id — solo admin agrega una versión nueva (nunca borra las viejas)
router.put('/:id', requiereRol('admin'), async (req, res) => {
  const registro = await Registro.findById(req.params.id);
  if (!registro) return res.status(404).json({ error: 'No existe ese registro.' });

  const { materia, maestro, horaInicio, horaFin, asistencia, observaciones } = req.body;
  if (!materia || !maestro || !horaInicio || !horaFin || !['normal', 'retardo', 'falta'].includes(asistencia)) {
    return res.status(400).json({ error: 'Ingresa todos los datos obligatorios.' });
  }

  const anterior = registro.versiones[registro.versiones.length - 1];
  registro.materia = materia;
  registro.maestro = maestro;
  registro.versiones.push({
    numeroVersion: registro.versiones.length + 1,
    autor: anterior.autor,
    editadoPorAdmin: req.usuario.matricula,
    horaInicio, horaFin, asistencia,
    observaciones: (observaciones || '').trim(),
    evidencia: anterior.evidencia || null,
    capturadoEn: new Date()
  });
  await registro.save();
  res.json(registro);
});

// DELETE /registros/:id — solo admin
router.delete('/:id', requiereRol('admin'), async (req, res) => {
  await Registro.findByIdAndDelete(req.params.id);
  res.json({ ok: true });
});

module.exports = router;
