require('dotenv').config();
const express = require('express');
const cors = require('cors');
const conectarDB = require('./db');

const authRoutes = require('./routes/auth');
const usuarioRoutes = require('./routes/usuarios');
const registroRoutes = require('./routes/registros');
const accesoRoutes = require('./routes/accesos');
const catalogoRoutes = require('./routes/catalogos');
const notificacionRoutes = require('./routes/notificaciones');

const app = express();

// Límite alto porque las fotos de evidencia viajan como base64 dentro del JSON.
app.use(express.json({ limit: '5mb' }));

// Permite la página publicada y la aplicación Android de Capacitor.
const origenesPermitidos = [
  process.env.FRONTEND_ORIGIN,
  'https://localhost'
].filter(Boolean);

app.use(cors({
  origin(origen, callback) {
    // Las llamadas sin Origin (pruebas del servidor) también son válidas.
    if (!origen || origenesPermitidos.includes(origen) || (!process.env.FRONTEND_ORIGIN && origenesPermitidos.length === 1)) {
      return callback(null, true);
    }
    return callback(new Error('Origen no permitido por CORS.'));
  }
}));

app.get('/', (req, res) => res.json({ ok: true, servicio: 'bitacora-backend' }));

app.use('/auth', authRoutes);
app.use('/usuarios', usuarioRoutes);
app.use('/registros', registroRoutes);
app.use('/accesos', accesoRoutes);
app.use('/catalogos', catalogoRoutes);
app.use('/notificaciones', notificacionRoutes);

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
