const mongoose = require('mongoose');

// A diferencia de Firestore (donde "versiones" era una subcolección aparte),
// en Mongo las guardamos embebidas dentro del mismo documento: es un
// arreglo que solo crece (nunca se edita ni se borra un elemento existente,
// para conservar el mismo historial inmutable que tenía la app original).
const versionSchema = new mongoose.Schema({
  numeroVersion: { type: Number, required: true },
  autor: { type: String, required: true },       // matrícula de quien la creó
  editadoPorAdmin: { type: String, default: null }, // matrícula del admin, si aplica
  horaInicio: { type: String, required: true },   // "HH:MM"
  horaFin: { type: String, required: true },
  asistencia: { type: String, enum: ['normal', 'retardo', 'falta'], required: true },
  observaciones: { type: String, default: '' },
  evidencia: { type: String, default: null },      // data URL base64 de la foto
  capturadoEn: { type: Date, default: Date.now }    // la pone el SERVIDOR, no el cliente
}, { _id: false });

const registroSchema = new mongoose.Schema({
  matricula: { type: String, required: true, index: true },
  grupo: { type: String, default: '' },
  materia: { type: String, required: true },
  maestro: { type: String, required: true },
  creadoEn: { type: Date, default: Date.now },
  versiones: { type: [versionSchema], default: [] }
});

module.exports = mongoose.model('Registro', registroSchema);
