require('dotenv').config();
const express = require('express');
const cors = require('cors');
const conectarDB = require('./db');

const authRoutes = require('./routes/auth');
const usuarioRoutes = require('./routes/usuarios');
const registroRoutes = require('./routes/registros');
const accesoRoutes = require('./routes/accesos');

const app = express();

// Límite alto porque las fotos de evidencia viajan como base64 dentro del JSON.
app.use(express.json({ limit: '5mb' }));

// Solo el dominio de tu GitHub Pages puede llamar a esta API.
app.use(cors({
  origin: process.env.FRONTEND_ORIGIN || '*'
}));

app.get('/', (req, res) => res.json({ ok: true, servicio: 'bitacora-backend' }));

app.use('/auth', authRoutes);
app.use('/usuarios', usuarioRoutes);
app.use('/registros', registroRoutes);
app.use('/accesos', accesoRoutes);

// Cualquier error no atrapado en una ruta cae aquí en vez de tumbar el proceso.
app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ error: 'Error interno del servidor.' });
});

const PORT = process.env.PORT || 3000;

conectarDB()
  .then(() => {
    app.listen(PORT, () => console.log(`✓ API corriendo en puerto ${PORT}`));
  })
  .catch((err) => {
    console.error('No se pudo conectar a MongoDB:', err.message);
    process.exit(1);
  });
