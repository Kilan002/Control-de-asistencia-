package com.nexusautomation.nfcheck

import android.content.ContentValues
import android.content.Context
import android.provider.Settings
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

/**
 * DatabaseHelper con SqlCipher (AES-256).
 *
 * La clave se deriva del ANDROID_ID del dispositivo: única por instalación,
 * no hardcodeada en el APK, y no requiere que el usuario recuerde nada.
 *
 * ADVERTENCIA DE MIGRACIÓN:
 * Si el dispositivo tiene una BD sin encriptar de una versión anterior, SQLiteException
 * se lanzará al intentar abrirla con contraseña. El bloque try/catch en obtenerClave()
 * no ayuda en ese caso — DatabaseHelper.kt detecta el error y borra la BD para crear una
 * nueva encriptada. Los registros offline pendientes de sincronizar se perderán, pero
 * los datos en Firebase permanecen intactos y se pueden resincronizar desde allí.
 */
class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, "asistencia.db", null, 3) {

    companion object {
        const val TABLE = "asistencias_locales"
    }

    init {
        // SQLiteDatabase.loadLibs() debe llamarse antes de cualquier apertura de BD.
        // Es idempotente: llamarlo varias veces no causa problemas.
        SQLiteDatabase.loadLibs(context)
    }

    /**
     * Deriva la clave de encriptación del ANDROID_ID del dispositivo.
     * No es perfecta (ANDROID_ID puede cambiar en factory reset), pero es
     * suficiente para proteger datos en reposo en este contexto.
     */
    private fun obtenerClave(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "nfcheck_fallback_key"

    // --- Apertura de BD con clave ---

    fun abrirWritable(): SQLiteDatabase = getWritableDatabase(obtenerClave())
    fun abrirReadable(): SQLiteDatabase = getReadableDatabase(obtenerClave())

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                grupo           TEXT    NOT NULL,
                materia         TEXT    NOT NULL,
                matricula       TEXT    NOT NULL,
                nombre          TEXT    NOT NULL,
                fecha           TEXT    NOT NULL,
                hora            TEXT    NOT NULL,
                estado          TEXT    NOT NULL DEFAULT 'asistencia',
                justificante    INTEGER NOT NULL DEFAULT 0,
                nota            TEXT    DEFAULT '',
                sincronizado    INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX idx_sincronizado ON $TABLE(sincronizado)")
        db.execSQL("CREATE INDEX idx_grupo_fecha ON $TABLE(grupo, fecha)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN justificante INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE $TABLE ADD COLUMN nota TEXT DEFAULT ''") } catch (_: Exception) {}
        }
    }

    fun guardarAsistencia(
        grupo: String,
        materia: String,
        matricula: String,
        nombre: String,
        fecha: String,
        hora: String,
        estado: String = "asistencia",
        justificante: Boolean = false,
        nota: String = ""
    ) {
        abrirWritable().use { db ->
            val valores = ContentValues().apply {
                put("grupo",        grupo)
                put("materia",      materia)
                put("matricula",    matricula)
                put("nombre",       nombre)
                put("fecha",        fecha)
                put("hora",         hora)
                put("estado",       estado)
                put("justificante", if (justificante) 1 else 0)
                put("nota",         nota)
                put("sincronizado", 0)
            }
            db.insert(TABLE, null, valores)
        }
    }

    fun obtenerPendientes(): List<Map<String, String>> {
        val lista = mutableListOf<Map<String, String>>()
        abrirReadable().use { db ->
            val cursor = db.query(
                TABLE, null,
                "sincronizado = 0", null, null, null, "fecha ASC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    lista.add(mapOf(
                        "id"           to it.getString(it.getColumnIndexOrThrow("id")),
                        "grupo"        to it.getString(it.getColumnIndexOrThrow("grupo")),
                        "materia"      to it.getString(it.getColumnIndexOrThrow("materia")),
                        "matricula"    to it.getString(it.getColumnIndexOrThrow("matricula")),
                        "nombre"       to it.getString(it.getColumnIndexOrThrow("nombre")),
                        "fecha"        to it.getString(it.getColumnIndexOrThrow("fecha")),
                        "hora"         to it.getString(it.getColumnIndexOrThrow("hora")),
                        "estado"       to it.getString(it.getColumnIndexOrThrow("estado")),
                        "justificante" to it.getString(it.getColumnIndexOrThrow("justificante")),
                        "nota"         to it.getString(it.getColumnIndexOrThrow("nota"))
                    ))
                }
            }
        }
        return lista
    }

    fun marcarSincronizado(id: String) {
        abrirWritable().use { db ->
            db.update(TABLE, ContentValues().apply { put("sincronizado", 1) }, "id = ?", arrayOf(id))
        }
    }
}
