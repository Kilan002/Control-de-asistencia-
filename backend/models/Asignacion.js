const mongoose = require('mongoose');

const asignacionSchema = new mongoose.Schema({
  grupo: { type: String, required: true, trim: true, uppercase: true },
  profesor: { type: String, required: true, trim: true, maxlength: 100 },
  materia: { type: String, required: true, trim: true, maxlength: 100 },
  materiaNormalizada: { type: String, required: true },
  actualizadoEn: { type: Date, default: Date.now }
});

asignacionSchema.index({ grupo: 1, materiaNormalizada: 1 }, { unique: true });

module.exports = mongoose.model('Asignacion', asignacionSchema);
