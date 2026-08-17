const mongoose = require('mongoose');

const accesoSchema = new mongoose.Schema({
  matricula: { type: String, required: true },
  resultado: { type: String, enum: ['ok', 'fallido'], required: true },
  timestamp: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Acceso', accesoSchema);
