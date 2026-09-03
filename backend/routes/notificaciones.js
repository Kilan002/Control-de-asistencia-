const express = require('express');
const Dispositivo = require('../models/Dispositivo');
const { requiereSesion, requiereRol } = require('../middleware/auth');

const router = express.Router();
router.use(requiereSesion);

router.post('/dispositivos', requiereRol('admin', 'admin_lectura'), async (req, res) => {
  const token = String(req.body.token || '').trim();
  if (!token || token.length > 4096) {
    return res.status(400).json({ error: 'Token de notificaciones inválido.' });
  }

  await Dispositivo.findOneAndUpdate(
    { token },
    {
      token,
      matricula: req.usuario.matricula,
      plataforma: 'android',
      actualizadoEn: new Date()
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );
  res.json({ ok: true });
});

router.delete('/dispositivos', requiereRol('admin', 'admin_lectura'), async (req, res) => {
  const token = String(req.body.token || '').trim();
  if (token) {
    await Dispositivo.deleteOne({ token, matricula: req.usuario.matricula });
  }
  res.json({ ok: true });
});

module.exports = router;
