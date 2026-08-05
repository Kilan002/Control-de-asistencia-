package com.nexusautomation.nfcheck

// ✅ AlumnoResumen — sin cambios, está correcto
data class AlumnoResumen(
    val nombre: String = "",
    val matricula: String = "",
    var asistencias: Int = 0,
    var faltas: Int = 0,
    var retardos: Int = 0
)
// ✅ CORREGIDO: RegistroAsistenciaAlumno ahora en su propio archivo y sin import incorrecto
// El original hacía `import RegistroAsistenciaAlumno` sin package, lo que falla en Kotlin
data class RegistroAsistenciaAlumno(
    val fecha: String = "",
    val hora: String = "",
    val estado: String = ""
)