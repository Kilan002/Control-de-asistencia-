// Uso (una sola vez, para crear tu primer administrador):
//   node scripts/crearAdmin.js al252561 "Luis Pérez" contraseñaTemporal123
//
// Después de esto, entra a la app y da de alta a los demás usuarios
// normalmente desde el panel de admin.

require('dotenv').config();
const bcrypt = require('bcryptjs');
const conectarDB = require('../db');
const Usuario = require('../models/Usuario');

async function main() {
  const [matricula, nombre, password] = process.argv.slice(2);
  if (!matricula || !nombre || !password) {
    console.error('Uso: node scripts/crearAdmin.js <matricula> <nombre> <passwordTemporal>');
    process.exit(1);
  }
  if (password.length < 6) {
    console.error('La contraseña debe tener al menos 6 caracteres.');
    process.exit(1);
  }

  await conectarDB();
  const passwordHash = await bcrypt.hash(password, 10);

  await Usuario.findByIdAndUpdate(
    matricula.toLowerCase(),
    {
      _id: matricula.toLowerCase(),
      nombre, rol: 'admin', grupo: '',
      passwordHash,
      debeCambiarPassword: true
    },
    { upsert: true }
  );

  console.log(`✓ Admin "${matricula}" creado/actualizado.`);
  process.exit(0);
}

main();
