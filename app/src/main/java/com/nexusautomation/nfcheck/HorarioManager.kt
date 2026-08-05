package com.nexusautomation.nfcheck

import java.util.*

/**
 * HorarioManager — gestiona el horario del PROFESOR, no del alumno.
 *
 * El horario se guarda en Firebase bajo:
 *   usuarios/{uid}/horario/{dia}/{bloque} → { materia, grupo, horaInicio, horaFin }
 *
 * Ejemplo en Firebase:
 *   usuarios/ABC123/horario/LUNES/0 → { materia: "PROYECTO INTEGRADOR II", grupo: "RBM51", horaInicio: 7.0, horaFin: 9.0 }
 *
 * Al iniciar sesión, LoginActivity llama a HorarioManager.cargarDesdeFirebase(uid)
 * y el horario queda en memoria. Luego PanelProfesorActivity llama a
 * obtenerClaseActual() para saber qué materia y grupo toca ahora.
 */
object HorarioManager {

    // Estructura de un bloque de clase
    data class BloqueClase(
        val materia: String,
        val grupo: String,
        val horaInicio: Double,
        val horaFin: Double
    )

    // Horario en memoria: día → lista de bloques
    private val horario = mutableMapOf<Int, MutableList<BloqueClase>>()
    private var cargado = false

    /**
     * Carga el horario del profesor desde Firebase.
     * Llamar desde LoginActivity después de verificar el rol.
     *
     * Estructura Firebase esperada:
     * usuarios/{uid}/horario → {
     *   "LUNES":   [ {materia, grupo, horaInicio, horaFin}, ... ],
     *   "MARTES":  [ ... ],
     *   ...
     * }
     */
    fun cargarDesdeFirebase(uid: String, onListo: (() -> Unit)? = null) {
        com.google.firebase.database.FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uid).child("horario")
            .get()
            .addOnSuccessListener { snap ->
                horario.clear()

                for (diaSnap in snap.children) {
                    val diaCalendar = nombreDiaACalendar(diaSnap.key ?: continue)
                    val bloques = mutableListOf<BloqueClase>()

                    for (bloqueSnap in diaSnap.children) {
                        val materia    = bloqueSnap.child("materia").value?.toString()    ?: continue
                        val grupo      = bloqueSnap.child("grupo").value?.toString()      ?: continue
                        val horaInicio = bloqueSnap.child("horaInicio").value
                        val horaFin    = bloqueSnap.child("horaFin").value

                        val inicio = when (horaInicio) {
                            is Double -> horaInicio
                            is Long   -> horaInicio.toDouble()
                            else      -> continue
                        }
                        val fin = when (horaFin) {
                            is Double -> horaFin
                            is Long   -> horaFin.toDouble()
                            else      -> continue
                        }

                        bloques.add(BloqueClase(materia, grupo, inicio, fin))
                    }

                    if (bloques.isNotEmpty()) {
                        horario[diaCalendar] = bloques
                    }
                }

                cargado = true
                onListo?.invoke()
            }
    }

    /**
     * Retorna la clase que corresponde AHORA según el horario cargado.
     * Retorna null si no hay clase en este momento.
     */
    fun obtenerClaseActual(): BloqueClase? {
        val calendar    = Calendar.getInstance()
        val dia         = calendar.get(Calendar.DAY_OF_WEEK)
        val hora        = calendar.get(Calendar.HOUR_OF_DAY)
        val minuto      = calendar.get(Calendar.MINUTE)
        val horaDecimal = hora + (minuto.toDouble() / 60.0)

        return horario[dia]?.firstOrNull { bloque ->
            horaDecimal >= bloque.horaInicio && horaDecimal < bloque.horaFin
        }
    }

    /**
     * Compatibilidad con código anterior — retorna solo el nombre de la materia.
     */
    fun obtenerMateriaActual(): String? = obtenerClaseActual()?.materia

    fun obtenerTextoMateriaActual(): String =
        obtenerMateriaActual() ?: "Sin clase en este horario"

    fun estaHorarioCargado(): Boolean = cargado

    fun limpiar() {
        horario.clear()
        cargado = false
    }

    private fun nombreDiaACalendar(nombre: String): Int = when (nombre.uppercase()) {
        "LUNES"     -> Calendar.MONDAY
        "MARTES"    -> Calendar.TUESDAY
        "MIERCOLES", "MIÉRCOLES" -> Calendar.WEDNESDAY
        "JUEVES"    -> Calendar.THURSDAY
        "VIERNES"   -> Calendar.FRIDAY
        else        -> -1
    }

    /**
     * Genera el JSON que se debe subir a Firebase para configurar
     * el horario de un profesor. Útil para entender la estructura.
     *
     * Ejemplo de uso desde la consola Firebase o una activity de configuración:
     *
     * usuarios/{uid}/horario/LUNES/0:
     *   materia: "PROYECTO INTEGRADOR II"
     *   grupo: "RBM51"
     *   horaInicio: 7.0
     *   horaFin: 9.0
     */
    fun estructuraEjemplo(): Map<String, Any> {
        return mapOf(
            "LUNES" to listOf(
                mapOf("materia" to "PROYECTO INTEGRADOR II",    "grupo" to "RBM51", "horaInicio" to 7.0,  "horaFin" to 9.0),
                mapOf("materia" to "FUNDAMENTOS DE PROGRAMACION","grupo" to "RBM51", "horaInicio" to 9.0,  "horaFin" to 11.0),
                mapOf("materia" to "INGLES V",                   "grupo" to "RBM51", "horaInicio" to 11.0, "horaFin" to 13.0)
            ),
            "MARTES" to listOf(
                mapOf("materia" to "MANTENIMIENTO A SISTEMAS ROBOT", "grupo" to "RBM51", "horaInicio" to 7.0, "horaFin" to 9.0),
                mapOf("materia" to "LEAD",                            "grupo" to "RBM51", "horaInicio" to 9.0, "horaFin" to 10.0)
            )
        )
    }
}