package com.nexusautomation.nfcheck.viewmodel

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.FirebaseDatabase
import com.nexusautomation.nfcheck.DatabaseHelper
import com.nexusautomation.nfcheck.HorarioManager
import com.nexusautomation.nfcheck.SyncWorker
import com.nexusautomation.nfcheck.NetworkUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * NfcViewModel — contiene toda la lógica de validación y registro NFC.
 *
 * La Activity solo:
 *   1. Convierte el Tag físico a nfcId (hexadecimal).
 *   2. Llama a procesarTag(nfcId, contexto, grupoOverride, materiaOverride).
 *   3. Observa estadoUI para actualizar vistas.
 *
 * El manejo de NfcAdapter (enableReaderMode/disableReaderMode) permanece en la Activity
 * porque está ligado al lifecycle de la pantalla.
 */
class NfcViewModel : ViewModel() {

    // --- Estado UI ---

    sealed class EstadoNFC {
        object Esperando : EstadoNFC()
        data class Verificando(val mensaje: String) : EstadoNFC()
        data class AlumnoEncontrado(val nombre: String, val fotoUrl: String, val materia: String, val grupo: String) : EstadoNFC()
        data class Error(val mensaje: String) : EstadoNFC()
        data class Exito(val nombre: String, val estado: String) : EstadoNFC()
        data class ExitoOffline(val nombre: String) : EstadoNFC()
    }

    val estadoUI = MutableLiveData<EstadoNFC>(EstadoNFC.Esperando)

    // --- Anti-replay ---
    // clave = "mat_{matricula}", valor = timestamp del último registro exitoso
    private val registrosRecientes = ConcurrentHashMap<String, Long>()
    private var ultimoNfcId: String? = null

    private val NFC_COOLDOWN_MS     = 5_000L
    private val REGISTRO_COOLDOWN_MS = 30_000L
    private val FIREBASE_TIMEOUT_MS  = 5_000L

    companion object {
        private const val TOLERANCIA_RETARDO_HORAS = 0.25
    }

    private val db = FirebaseDatabase.getInstance().reference

    /**
     * Punto de entrada principal — llamado desde la Activity al detectar un tag.
     *
     * @param nfcId          ID hexadecimal del tag físico.
     * @param appContext     ApplicationContext (para DatabaseHelper y SyncWorker).
     * @param grupoOverride  Si el admin lanzó la pantalla con grupo específico, no nulo.
     * @param materiaOverride Si el admin lanzó con materia específica, no nulo.
     * @param grupoClase     Grupo de la clase activa según HorarioManager (puede ser nulo).
     * @param materiaClase   Materia de la clase activa (puede ser nula).
     */
    fun procesarTag(
        nfcId: String,
        appContext: Context,
        grupoOverride: String,
        materiaOverride: String,
        grupoClase: String?,
        materiaClase: String?
    ) {
        val ahora = System.currentTimeMillis()

        // Anti-doble-tap por ID de tarjeta
        if (nfcId == ultimoNfcId) return
        ultimoNfcId = nfcId

        estadoUI.value = EstadoNFC.Verificando("Verificando tarjeta...")

        viewModelScope.launch {
            procesarTagInterno(nfcId, ahora, appContext, grupoOverride, materiaOverride, grupoClase, materiaClase)
        }
    }

    /** Permite a la Activity resetear el cooldown de tarjeta (tras mostrar el resultado). */
    fun resetearUltimoNfcId() {
        ultimoNfcId = null
    }

    // ---------------------------------------------------------------------------
    // Lógica interna — todo corre en coroutines dentro de viewModelScope
    // ---------------------------------------------------------------------------

    private suspend fun procesarTagInterno(
        nfcId: String,
        timestamp: Long,
        appContext: Context,
        grupoOverride: String,
        materiaOverride: String,
        grupoClase: String?,
        materiaClase: String?
    ) {
        // PASO 1 — Leer /nfc_index/{nfcId}
        val indexSnap = withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
            db.child("nfc_index").child(nfcId).get().await()
        }

        if (indexSnap == null) {
            estadoUI.postValue(EstadoNFC.Error("Error de conexión"))
            resetearConDelay(NFC_COOLDOWN_MS)
            return
        }

        if (!indexSnap.exists()) {
            estadoUI.postValue(EstadoNFC.Error("Tarjeta no registrada"))
            resetearConDelay(NFC_COOLDOWN_MS)
            return
        }

        val matricula = indexSnap.child("matricula").value?.toString()
        val grupoReal  = indexSnap.child("grupo").value?.toString()

        if (matricula == null || grupoReal == null) {
            estadoUI.postValue(EstadoNFC.Error("Datos de tarjeta corruptos"))
            resetearConDelay(NFC_COOLDOWN_MS)
            return
        }

        // Cooldown por alumno
        val clave = "mat_$matricula"
        val ultimoRegistro = registrosRecientes[clave]
        if (ultimoRegistro != null && timestamp - ultimoRegistro < REGISTRO_COOLDOWN_MS) {
            val restante = (REGISTRO_COOLDOWN_MS - (timestamp - ultimoRegistro)) / 1000
            estadoUI.postValue(EstadoNFC.Error("Ya registrado. Espera ${restante}s"))
            resetearConDelay(NFC_COOLDOWN_MS)
            return
        }

        // Validación de grupo
        val grupoFinal = if (grupoOverride.isNotEmpty()) {
            grupoOverride
        } else {
            if (grupoClase != null && grupoReal != grupoClase) {
                estadoUI.postValue(EstadoNFC.Error("Alumno no pertenece a este grupo"))
                resetearConDelay(NFC_COOLDOWN_MS)
                return
            }
            grupoReal
        }

        // PASO 2 — Verificación cruzada: /alumnos/{grupo}/{matricula}/nfcId
        val nfcIdSnap = withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
            db.child("alumnos").child(grupoFinal).child(matricula).child("nfcId").get().await()
        }

        val nfcIdAlmacenado = nfcIdSnap?.getValue(String::class.java)
        if (nfcIdAlmacenado == null || nfcIdAlmacenado != nfcId) {
            estadoUI.postValue(EstadoNFC.Error("Tarjeta no válida para este alumno"))
            resetearConDelay(NFC_COOLDOWN_MS)
            return
        }

        // PASO 3 — Leer datos del alumno para mostrar en UI
        val alumnoSnap = withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
            db.child("alumnos").child(grupoFinal).child(matricula).get().await()
        }

        if (alumnoSnap == null) {
            estadoUI.postValue(EstadoNFC.Error("Error de conexión"))
            resetearConDelay(NFC_COOLDOWN_MS)
            return
        }

        val nombre  = alumnoSnap.child("nombre").value?.toString() ?: "Alumno"
        val fotoUrl = alumnoSnap.child("fotoUrl").value?.toString() ?: ""

        val materiaFinal = when {
            materiaOverride.isNotEmpty() -> materiaOverride
            materiaClase != null         -> materiaClase
            else -> {
                estadoUI.postValue(EstadoNFC.Error("No hay clase activa en este horario"))
                resetearConDelay(NFC_COOLDOWN_MS)
                return
            }
        }

        // Mostrar alumno encontrado en UI antes de registrar
        estadoUI.postValue(EstadoNFC.AlumnoEncontrado(nombre, fotoUrl, materiaFinal, grupoFinal))

        val fecha  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hora   = SimpleDateFormat("HH:mm",      Locale.getDefault()).format(Date())
        val estado = determinarEstado()

        // PASO 4 — Registrar asistencia
        registrarAsistencia(
            matricula, nombre, grupoFinal, materiaFinal,
            fecha, hora, estado, timestamp, clave, appContext
        )
    }

    private suspend fun registrarAsistencia(
        matricula: String, nombre: String,
        grupo: String, materia: String,
        fecha: String, hora: String,
        estado: String, timestamp: Long,
        clave: String, appContext: Context
    ) {
        val datos = mapOf("estado" to estado, "hora" to hora)

        if (NetworkUtils.hayConexion(appContext)) {
            val exito = withTimeoutOrNull(FIREBASE_TIMEOUT_MS) {
                runCatching {
                    db.child("asistencias")
                        .child(grupo).child(materia).child(fecha).child(matricula)
                        .setValue(datos)
                        .await()
                    true
                }.getOrElse { false }
            } ?: false

            if (exito) {
                registrosRecientes[clave] = timestamp
                estadoUI.postValue(EstadoNFC.Exito(nombre, estado))
                SyncWorker.sincronizarAhora(appContext)
                resetearConDelay(3_000L)
                return
            }
        }

        // Sin conexión o fallo Firebase — guardar offline
        DatabaseHelper(appContext).guardarAsistencia(
            grupo = grupo, materia = materia,
            matricula = matricula, nombre = nombre,
            fecha = fecha, hora = hora, estado = estado
        )
        registrosRecientes[clave] = timestamp
        estadoUI.postValue(EstadoNFC.ExitoOffline(nombre))
        resetearConDelay(3_000L)
    }

    private fun resetearConDelay(delayMs: Long) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            ultimoNfcId = null
            estadoUI.postValue(EstadoNFC.Esperando)
        }
    }

    private fun determinarEstado(): String {
        val clase = HorarioManager.obtenerClaseActual() ?: return "asistencia"
        val cal   = Calendar.getInstance()
        val hd    = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE).toDouble() / 60.0
        return if (hd > clase.horaInicio + TOLERANCIA_RETARDO_HORAS) "retardo" else "asistencia"
    }
}
