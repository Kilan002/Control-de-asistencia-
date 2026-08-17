const express = require('express');
const Acceso = require('../models/Acceso');
const { requiereSesion, requiereRol } = require('../middleware/auth');

const router = express.Router();

// Solo admin / admin_lectura pueden ver el historial de accesos
router.get('/', requiereSesion, requiereRol('admin', 'admin_lectura'), async (req, res) => {
  const accesos = await Acceso.find({}).sort({ timestamp: -1 }).limit(100);
  res.json(accesos);
});

module.exports = router;
