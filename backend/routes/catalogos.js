const express = require('express');
const Catalogo = require('../models/Catalogo');
const { requiereSesion, requiereRol } = require('../middleware/auth');

const router = express.Router();
router.use(requiereSesion);

function normalizar(nombre) {
  return nombre.trim().replace(/\s+/g, ' ').toLocaleLowerCase('es-MX');
}

// Cualquier usuario autenticado necesita consultar estas opciones al registrar.
router.get('/', async (req, res) => {
  const elementos = await Catalogo.find({}).sort({ tipo: 1, nombre: 1 });
  res.json({
    materias: elementos.filter(e => e.tipo === 'materia'),
    profesores: elementos.filter(e => e.tipo === 'profesor')
  });
});

// Solo el administrador con permisos de modificación puede agregar opciones.
router.post('/', requiereRol('admin'), async (req, res) => {
  const tipo = req.body.tipo;
  const nombre = (req.body.nombre || '').trim().replace(/\s+/g, ' ');

  if (!['materia', 'profesor'].includes(tipo)) {
    return res.status(400).json({ error: 'Tipo de catálogo inválido.' });
  }
  if (!nombre || nombre.length > 100) {
    return res.status(400).json({ error: 'El nombre debe tener entre 1 y 100 caracteres.' });
  }

  try {
    const elemento = await Catalogo.create({
      tipo,
      nombre,
      nombreNormalizado: normalizar(nombre)
    });
    res.status(201).json(elemento);
  } catch (err) {
    if (err.code === 11000) {
      return res.status(409).json({ error: 'Ese elemento ya está agregado.' });
    }
    throw err;
  }
});

// Eliminar una opción no borra ni altera los registros históricos que la usaron.
router.delete('/:id', requiereRol('admin'), async (req, res) => {
  const elemento = await Catalogo.findByIdAndDelete(req.params.id);
  if (!elemento) return res.status(404).json({ error: 'El elemento ya no existe.' });
  res.json({ ok: true });
});

module.exports = router;
