const admin = require('firebase-admin');
const Dispositivo = require('../models/Dispositivo');
const Usuario = require('../models/Usuario');

let firebaseApp = null;

function obtenerFirebase() {
  if (firebaseApp) return firebaseApp;
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;

  try {
    const credenciales = JSON.parse(raw);
    firebaseApp = admin.apps.length
      ? admin.app()
      : admin.initializeApp({ credential: admin.credential.cert(credenciales) });
    return firebaseApp;
  } catch (error) {
    console.error('FIREBASE_SERVICE_ACCOUNT_JSON no es válido:', error.message);
    return null;
  }
}

async function notificarFalta({ registroId, profesor, materia, grupo }) {
  if (!obtenerFirebase()) {
    console.warn('Notificación omitida: Firebase no está configurado en Render.');
    return;
  }

  const administradores = await Usuario.find(
    { rol: { $in: ['admin', 'admin_lectura'] } },
    { _id: 1 }
  ).lean();
  const matriculas = administradores.map(usuario => usuario._id);
  const dispositivos = await Dispositivo.find(
    { matricula: { $in: matriculas } },
    { token: 1 }
  ).lean();
  const tokens = [...new Set(dispositivos.map(item => item.token).filter(Boolean))];
  if (!tokens.length) return;

  for (let inicio = 0; inicio < tokens.length; inicio += 500) {
    const lote = tokens.slice(inicio, inicio + 500);
    const respuesta = await admin.messaging().sendEachForMulticast({
      tokens: lote,
      notification: {
        title: `Falta registrada · ${grupo}`,
        body: `${profesor} · ${materia}`
      },
      data: {
        tipo: 'falta',
        registroId: String(registroId),
        grupo: String(grupo || ''),
        profesor: String(profesor || ''),
        materia: String(materia || '')
      },
      android: {
        priority: 'high',
        notification: { channelId: 'faltas', sound: 'default' }
      }
    });

    const invalidos = [];
    respuesta.responses.forEach((resultado, indice) => {
      const codigo = resultado.error?.code;
      if (codigo === 'messaging/registration-token-not-registered' || codigo === 'messaging/invalid-registration-token') {
        invalidos.push(lote[indice]);
      }
    });
    if (invalidos.length) await Dispositivo.deleteMany({ token: { $in: invalidos } });
  }
}

module.exports = { notificarFalta };
