/**
 * Script de migración NFC — NFCheck
 * URL corregida: https://nfcheck-39c84-default-rtdb.firebaseio.com/
 *
 * SETUP:
 *   1. Pon serviceAccountKey.json en esta misma carpeta
 *   2. npm install firebase-admin
 *   3. node migrate_nfc_index.js
 */

const admin = require("firebase-admin");
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "https://nfcheck-39c84-default-rtdb.firebaseio.com"
});

const db = admin.database();

async function migrarIndiceNFC() {
  console.log("═══════════════════════════════════════");
  console.log("  Migración /nfc_index — NFCheck");
  console.log("═══════════════════════════════════════\n");

  const alumnosSnap = await db.ref("alumnos").once("value");

  if (!alumnosSnap.exists()) {
    console.log("⚠️  No se encontraron datos en /alumnos.");
    process.exit(0);
  }

  const updates = {};
  let total = 0;
  let sinNFC = 0;
  let duplicados = [];
  const vistos = new Map();

  alumnosSnap.forEach((grupoSnap) => {
    const grupo = grupoSnap.key;
    grupoSnap.forEach((alumnoSnap) => {
      const matricula = alumnoSnap.key;
      const nfcId = alumnoSnap.child("nfcId").val();
      const nombre = alumnoSnap.child("nombre").val() ?? "(sin nombre)";
      total++;

      if (!nfcId || nfcId.trim() === "") {
        console.log(`  ⚠️  Sin NFC: ${nombre} (${matricula}) — ${grupo}`);
        sinNFC++; return;
      }

      if (vistos.has(nfcId)) {
        duplicados.push({ nfcId, actual: `${grupo}/${matricula}`, anterior: vistos.get(nfcId) });
        console.log(`  ❌ NFC duplicado: ${nfcId} → ${grupo}/${matricula}`);
        return;
      }

      vistos.set(nfcId, `${grupo}/${matricula}`);
      updates[`nfc_index/${nfcId}`] = { matricula, grupo };
      console.log(`  ✅  ${nombre} | ${matricula} | ${grupo} | ${nfcId}`);
    });
  });

  console.log("\n───────────────────────────────────────");
  console.log(`  Total alumnos   : ${total}`);
  console.log(`  A indexar       : ${Object.keys(updates).length}`);
  console.log(`  Sin NFC         : ${sinNFC}`);
  console.log(`  Duplicados      : ${duplicados.length}`);
  console.log("───────────────────────────────────────\n");

  if (Object.keys(updates).length === 0) {
    console.log("⚠️  Nada que escribir."); process.exit(0);
  }

  await db.ref().update(updates);
  console.log("✅  /nfc_index actualizado correctamente.\n");

  if (duplicados.length > 0) {
    console.log("════ ACCIÓN REQUERIDA — duplicados ════");
    duplicados.forEach(d => console.log(`  ${d.nfcId}\n    → ${d.anterior}\n    → ${d.actual}\n`));
  }

  process.exit(0);
}

migrarIndiceNFC().catch((err) => {
  console.error("❌  Error:", err.message);
  process.exit(1);
});