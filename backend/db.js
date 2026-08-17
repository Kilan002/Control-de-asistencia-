const mongoose = require('mongoose');

async function conectarDB() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    throw new Error('Falta MONGODB_URI en las variables de entorno (.env)');
  }
  await mongoose.connect(uri);
  console.log('✓ Conectado a MongoDB');
}

module.exports = conectarDB;
