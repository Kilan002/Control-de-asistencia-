const express = require('express');
const Dispositivo = require('../models/Dispositivo');
const { requiereSesion, requiereRol } = require('../middleware/auth');

const router = express.Router();
router.use(requiereSesion);

router.post('/dispositivos', requiereRol('admin', 'admin_lectura'), async (req, res) => {
  const token = String(req.body.token || '').trim();
  // Conserva compatibilidad con APK anteriores, que no enviaban plataforma.
  const plataforma = String(req.body.plataforma || 'android').trim().toLowerCase();
  if (!token || token.length > 4096) {
    return res.status(400).json({ error: 'Token de notificaciones inválido.' });
  }
  if (!['android', 'ios'].includes(plataforma)) {
    return res.status(400).json({ error: 'Plataforma de notificaciones inválida.' });
  }

  await Dispositivo.findOneAndUpdate(
    { token },
    {
      token,
      matricula: req.usuario.matricula,
      plataforma,
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
