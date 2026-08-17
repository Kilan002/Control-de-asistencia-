const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const Usuario = require('../models/Usuario');
const Acceso = require('../models/Acceso');
const { requiereSesion } = require('../middleware/auth');

const router = express.Router();

// POST /auth/login  { matricula, password }
router.post('/login', async (req, res) => {
  const matricula = (req.body.matricula || '').trim().toLowerCase();
  const password = req.body.password || '';

  if (!matricula || !password) {
    return res.status(400).json({ error: 'Ingresa tu matrícula y contraseña.' });
  }

  const usuario = await Usuario.findById(matricula);

  // Mismo mensaje tanto si la matrícula no existe como si la contraseña
  // está mal: así no le decimos a un atacante cuáles matrículas sí existen.
  if (!usuario) {
    return res.status(401).json({ error: 'Matrícula o contraseña incorrecta.' });
  }

  const coincide = await bcrypt.compare(password, usuario.passwordHash);
  if (!coincide) {
    return res.status(401).json({ error: 'Matrícula o contraseña incorrecta.' });
  }

  await Acceso.create({ matricula, resultado: 'ok' });

  const token = jwt.sign(
    { matricula, rol: usuario.rol },
    process.env.JWT_SECRET,
    { expiresIn: '12h' }
  );

  res.json({
    token,
    usuario: {
      matricula,
      nombre: usuario.nombre,
      rol: usuario.rol,
      grupo: usuario.grupo,
      debeCambiarPassword: usuario.debeCambiarPassword
    }
  });
});

// PUT /auth/password  { nueva }  (requiere sesión)
router.put('/password', requiereSesion, async (req, res) => {
  const nueva = req.body.nueva || '';
  if (nueva.length < 6) {
    return res.status(400).json({ error: 'La contraseña debe tener al menos 6 caracteres.' });
  }
  const hash = await bcrypt.hash(nueva, 10);
  await Usuario.findByIdAndUpdate(req.usuario.matricula, {
    passwordHash: hash,
    debeCambiarPassword: false
  });
  res.json({ ok: true });
});

module.exports = router;
