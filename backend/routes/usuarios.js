const express = require('express');
const bcrypt = require('bcryptjs');
const Usuario = require('../models/Usuario');
const { requiereSesion, requiereRol } = require('../middleware/auth');

const router = express.Router();

// Solo letras y números para la matrícula: evita ids raros en Mongo
// (nada de "/", comillas, etc. que rompían cosas o permitían inyección de HTML).
const MATRICULA_VALIDA = /^[a-z0-9]{3,20}$/;
const GRUPO_VALIDO = /^(RBM|IMTM)[1-5][1-9]$/;

router.use(requiereSesion);

// GET /usuarios  — cualquier sesión válida puede listar (igual que antes)
router.get('/', async (req, res) => {
  const usuarios = await Usuario.find({}, { passwordHash: 0 }).sort({ creadoEn: -1 });
  res.json(usuarios);
});

// POST /usuarios  — solo admin (admin_lectura NO puede dar de alta)
router.post('/', requiereRol('admin'), async (req, res) => {
  const matricula = (req.body.matricula || '').trim().toLowerCase();
  const nombre = (req.body.nombre || '').trim();
  const grupo = (req.body.grupo || '').trim().toUpperCase();
  const rol = req.body.rol;
  const passwordTemp = req.body.passwordTemp || '';

  if (!matricula || !nombre || !passwordTemp) {
    return res.status(400).json({ error: 'Ingresa matrícula, nombre y contraseña temporal.' });
  }
  if (!MATRICULA_VALIDA.test(matricula)) {
    return res.status(400).json({ error: 'La matrícula solo puede tener letras y números (3-20 caracteres).' });
  }
  if (!['alumno', 'admin', 'admin_lectura'].includes(rol)) {
    return res.status(400).json({ error: 'Rol inválido.' });
  }
  if (rol === 'alumno' && !GRUPO_VALIDO.test(grupo)) {
    return res.status(400).json({ error: 'El grupo debe tener un código válido, como RBM11 o IMTM21.' });
  }
  if (passwordTemp.length < 6) {
    return res.status(400).json({ error: 'La contraseña temporal debe tener al menos 6 caracteres.' });
  }

  const yaExiste = await Usuario.findById(matricula);
  if (yaExiste) {
    return res.status(409).json({ error: 'Esa matrícula ya tiene cuenta.' });
  }

  const passwordHash = await bcrypt.hash(passwordTemp, 10);
  await Usuario.create({
    _id: matricula,
    nombre, grupo, rol, passwordHash,
    debeCambiarPassword: true
  });

  res.status(201).json({ ok: true });
});

// DELETE /usuarios/:matricula — solo admin
router.delete('/:matricula', requiereRol('admin'), async (req, res) => {
  const matricula = req.params.matricula.toLowerCase();
  if (matricula === req.usuario.matricula) {
    return res.status(400).json({ error: 'No puedes eliminar tu propia cuenta desde aquí.' });
  }
  await Usuario.findByIdAndDelete(matricula);
  res.json({ ok: true });
});

module.exports = router;
