const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const { logger } = require("firebase-functions");

admin.initializeApp();

// ─── Helper: verifica admin ───────────────────────────────────────────────────
async function verificarAdmin(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Se requiere autenticación");
  }
  const snap = await admin.database()
    .ref(`usuarios/${request.auth.uid}`)
    .once("value");
  if (snap.val()?.rol !== "admin") {
    throw new HttpsError("permission-denied", "Solo administradores");
  }
}

// ─── crearProfesor ────────────────────────────────────────────────────────────
exports.crearProfesor = onCall(
  { enforceAppCheck: false },
  async (request) => {
    await verificarAdmin(request);

    const { nombre, correo, password, grupos, materias } = request.data;

    if (!nombre || !correo || !password || !grupos?.length || !materias?.length) {
      throw new HttpsError("invalid-argument", "Faltan campos obligatorios");
    }
    if (password.length < 8) {
      throw new HttpsError("invalid-argument", "Contraseña mínimo 8 caracteres");
    }

    let userRecord;
    try {
      userRecord = await admin.auth().createUser({
        email: correo,
        password: password,
        displayName: nombre,
      });
    } catch (e) {
      if (e.code === "auth/email-already-exists") {
        throw new HttpsError("already-exists", "El correo ya está registrado");
      }
      throw new HttpsError("internal", "Error creando usuario");
    }

    const uid = userRecord.uid;
    const gruposMap = Object.fromEntries(grupos.map(g => [g, true]));
    const materiasMap = Object.fromEntries(materias.map(m => [m, true]));

    try {
      await admin.database().ref(`usuarios/${uid}`).set({
        nombre,
        correo,
        rol: "profesor",
        gruposAsignados: gruposMap,
        materiasAsignadas: materiasMap,
      });
    } catch (e) {
      await admin.auth().deleteUser(uid).catch(() => { });
      throw new HttpsError("internal", "Error guardando datos");
    }

    logger.info(`Profesor creado: ${uid}`);
    return { userId: uid, message: "Profesor creado correctamente" };
  }
);

// ─── crearAlumno ──────────────────────────────────────────────────────────────
exports.crearAlumno = onCall(
  { enforceAppCheck: false },
  async (request) => {
    await verificarAdmin(request);

    const { nombre, matricula, nfcId, correo, password, grupo } = request.data;

    if (!nombre || !matricula || !nfcId || !correo || !password || !grupo) {
      throw new HttpsError("invalid-argument", "Faltan campos obligatorios");
    }
    if (password.length < 8) {
      throw new HttpsError("invalid-argument", "Contraseña mínimo 8 caracteres");
    }

    const nfcSnap = await admin.database().ref(`nfc_index/${nfcId}`).once("value");
    if (nfcSnap.exists()) {
      throw new HttpsError("already-exists", "Tarjeta NFC ya registrada");
    }

    let userRecord;
    try {
      userRecord = await admin.auth().createUser({
        email: correo,
        password: password,
        displayName: nombre,
      });
    } catch (e) {
      if (e.code === "auth/email-already-exists") {
        throw new HttpsError("already-exists", "El correo ya está registrado");
      }
      throw new HttpsError("internal", "Error creando usuario");
    }

    const userId = userRecord.uid;
    const updates = {};
    updates[`alumnos/${grupo}/${matricula}`] = { nombre, nfcId, uid: userId };
    updates[`usuarios/${userId}`] = { nombre, rol: "alumno", matricula, grupo };
    updates[`nfc_index/${nfcId}`] = { matricula, grupo };

    try {
      await admin.database().ref().update(updates);
    } catch (e) {
      await admin.auth().deleteUser(userId).catch(() => { });
      throw new HttpsError("internal", "Error guardando datos");
    }

    logger.info(`Alumno creado: ${userId}`);
    return { userId, message: "Alumno creado correctamente" };
  }
);

// ─── eliminarUsuario ──────────────────────────────────────────────────────────
exports.eliminarUsuario = onCall(
  { enforceAppCheck: false },
  async (request) => {
    await verificarAdmin(request);

    const { userId, tipo, grupo, matricula, nfcId } = request.data;
    if (!userId) {
      throw new HttpsError("invalid-argument", "userId requerido");
    }

    const updates = {};
    updates[`usuarios/${userId}`] = null;
    if (tipo === "alumno" && grupo && matricula) {
      updates[`alumnos/${grupo}/${matricula}`] = null;
      if (nfcId) updates[`nfc_index/${nfcId}`] = null;
    }

    await admin.database().ref().update(updates);
    await admin.auth().deleteUser(userId);

    return { success: true };
  }
);