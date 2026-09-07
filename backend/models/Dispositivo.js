const mongoose = require('mongoose');

const dispositivoSchema = new mongoose.Schema({
  token: { type: String, required: true, unique: true, index: true },
  matricula: { type: String, required: true, index: true },
  plataforma: { type: String, enum: ['android', 'ios'], required: true },
  actualizadoEn: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Dispositivo', dispositivoSchema);
