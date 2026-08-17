const mongoose = require('mongoose');

// La matrícula es el _id: así se comporta igual que el doc.id de Firestore
// y evitamos tener un id de mongo (ObjectId) Y una matrícula por separado.
const usuarioSchema = new mongoose.Schema({
  _id: { type: String, required: true }, // matrícula, ej. "al252561"
  nombre: { type: String, required: true, trim: true },
  grupo: { type: String, default: '' },
  rol: {
    type: String,
    enum: ['alumno', 'admin', 'admin_lectura'],
    required: true
  },
  passwordHash: { type: String, required: true },
  debeCambiarPassword: { type: Boolean, default: true },
  creadoEn: { type: Date, default: Date.now }
}, { _id: false });

module.exports = mongoose.model('Usuario', usuarioSchema);
