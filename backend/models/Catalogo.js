const mongoose = require('mongoose');

const catalogoSchema = new mongoose.Schema({
  tipo: {
    type: String,
    enum: ['materia', 'profesor'],
    required: true
  },
  nombre: {
    type: String,
    required: true,
    trim: true,
    maxlength: 100
  },
  nombreNormalizado: {
    type: String,
    required: true
  },
  creadoEn: { type: Date, default: Date.now }
});

// Evita duplicados aunque cambien mayúsculas, minúsculas o espacios.
catalogoSchema.index({ tipo: 1, nombreNormalizado: 1 }, { unique: true });

module.exports = mongoose.model('Catalogo', catalogoSchema);
