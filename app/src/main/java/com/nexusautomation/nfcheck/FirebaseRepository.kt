package com.nexusautomation.nfcheck

import com.nexusautomation.nfcheck.AlumnoResumen
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FirebaseRepository {

    private val db = FirebaseDatabase.getInstance().reference

    /**
     * Devuelve la lista de alumnos con sus contadores de asistencia/retardo/falta para un grupo.
     * Retorna lista vacía si hay timeout (5 s) o cualquier error de red.
     */
    suspend fun obtenerResumenGrupo(grupo: String): List<AlumnoResumen> {
        return withTimeoutOrNull(5_000L) {
            // Paso 1: nombres desde /alumnos/{grupo}
            val alumnosSnap = db.child("alumnos").child(grupo).get().await()

            val nombresMap = mutableMapOf<String, String>()
            for (alumnoSnap in alumnosSnap.children) {
                val matricula = alumnoSnap.key ?: continue
                val nombre    = alumnoSnap.child("nombre").value?.toString() ?: matricula
                nombresMap[matricula] = nombre
            }

            if (nombresMap.isEmpty()) return@withTimeoutOrNull emptyList()

            // Paso 2: asistencias acumuladas
            val mapa = nombresMap.entries.associate { (m, n) ->
                m to AlumnoResumen(nombre = n, matricula = m)
            }.toMutableMap()

            val asistenciasSnap = runCatching {
                db.child("asistencias").child(grupo).get().await()
            }.getOrNull()

            if (asistenciasSnap != null) {
                for (materiaSnap in asistenciasSnap.children) {
                    for (fechaSnap in materiaSnap.children) {
                        for (alumnoSnap in fechaSnap.children) {
                            val matricula = alumnoSnap.key ?: continue
                            val alumno    = mapa[matricula] ?: continue
                            val estado    = alumnoSnap.child("estado").value?.toString() ?: "falta"
                            when (estado) {
                                "asistencia" -> alumno.asistencias++
                                "retardo"    -> alumno.retardos++
                                "falta"      -> alumno.faltas++
                            }
                        }
                    }
                }
            }

            mapa.values.sortedBy { it.nombre }
        } ?: emptyList()
    }

    /**
     * Filtra alumnos que tienen al menos un registro en la materia indicada.
     * Retorna lista vacía si hay timeout (5 s) o cualquier error.
     */
    suspend fun obtenerResumenGrupoMateria(grupo: String, materia: String): List<AlumnoResumen> {
        return withTimeoutOrNull(5_000L) {
            val snap = db.child("asistencias").child(grupo).child(materia).get().await()

            val matriculas = mutableSetOf<String>()
            for (fechaSnap in snap.children) {
                for (alumnoSnap in fechaSnap.children) {
                    alumnoSnap.key?.let { matriculas.add(it) }
                }
            }

            val todos = obtenerResumenGrupo(grupo)
            todos.filter { it.matricula in matriculas }
        } ?: emptyList()
    }
}
