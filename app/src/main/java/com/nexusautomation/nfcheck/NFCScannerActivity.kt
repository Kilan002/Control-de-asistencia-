package com.nexusautomation.nfcheck

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.nexusautomation.nfcheck.viewmodel.NfcViewModel

/**
 * NFCScannerActivity — refactorizada a observador puro.
 *
 * Responsabilidades que quedan en la Activity (ligadas al lifecycle de pantalla):
 *   - Habilitar/deshabilitar NfcAdapter.enableReaderMode / disableReaderMode
 *   - Observar NfcViewModel.estadoUI y actualizar vistas
 *   - Leer extras del Intent (materia, grupo de override)
 *
 * Toda la lógica de negocio (validación, verificación cruzada, cooldowns,
 * registro en Firebase/SQLite) vive en NfcViewModel.
 *
 * Seguridad implementada en NfcViewModel:
 *   1. Índice /nfc_index → lectura O(1) sin descargar colección completa.
 *   2. Verificación cruzada: compara nfcId físico con /alumnos/{g}/{m}/nfcId.
 *   3. Anti-replay por matrícula (cooldown 30 s).
 *   4. Timeout de 5 s en cada llamada Firebase.
 */
class NFCScannerActivity : AppCompatActivity() {

    private lateinit var textEstadoNFC: TextView
    private lateinit var textInfoClase: TextView
    private lateinit var imgAlumnoScanner: ImageView
    private lateinit var txtNombreAlumnoScanner: TextView
    private lateinit var layoutEsperando: View
    private lateinit var layoutFotoAlumno: View

    private var nfcAdapter: NfcAdapter? = null

    private var materiaIntent: String = ""
    private var grupoIntent: String   = ""

    private val viewModel: NfcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfcscanner)

        textEstadoNFC          = findViewById(R.id.textEstadoNFC)
        textInfoClase          = findViewById(R.id.txtContextoNFC)
        imgAlumnoScanner       = findViewById(R.id.imgAlumnoScanner)
        txtNombreAlumnoScanner = findViewById(R.id.txtNombreAlumnoScanner)
        layoutEsperando        = findViewById(R.id.layoutEsperando)
        layoutFotoAlumno       = findViewById(R.id.layoutFotoAlumno)

        materiaIntent = intent.getStringExtra("materia") ?: ""
        grupoIntent   = intent.getStringExtra("grupo")   ?: ""

        val clase = HorarioManager.obtenerClaseActual()

        when {
            materiaIntent.isNotEmpty() && grupoIntent.isNotEmpty() ->
                textInfoClase.text = "Materia: $materiaIntent  |  Grupo: $grupoIntent"
            clase != null ->
                textInfoClase.text = "Materia: ${clase.materia}  |  Grupo: ${clase.grupo}"
            else ->
                textInfoClase.text = "Sin clase activa — se registrará al escanear"
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "Este dispositivo no tiene NFC", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        observarViewModel()
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this, this::onTagDiscovered,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    // ----- NFC -----

    private fun onTagDiscovered(tag: Tag?) {
        val nfcId = tag?.id?.joinToString("") { "%02X".format(it) } ?: return
        val clase  = HorarioManager.obtenerClaseActual()

        // Delegar toda la lógica al ViewModel; pasar contexto de aplicación (no Activity)
        viewModel.procesarTag(
            nfcId          = nfcId,
            appContext      = applicationContext,
            grupoOverride   = grupoIntent,
            materiaOverride = materiaIntent,
            grupoClase      = clase?.grupo,
            materiaClase    = clase?.materia
        )
    }

    // ----- Observación de estado -----

    private fun observarViewModel() {
        viewModel.estadoUI.observe(this) { estado ->
            when (estado) {
                is NfcViewModel.EstadoNFC.Esperando -> mostrarEsperando()

                is NfcViewModel.EstadoNFC.Verificando -> {
                    textEstadoNFC.text = estado.mensaje
                }

                is NfcViewModel.EstadoNFC.AlumnoEncontrado -> {
                    textInfoClase.text = "Materia: ${estado.materia}  |  Grupo: ${estado.grupo}"
                    mostrarAlumno(estado.nombre, estado.fotoUrl)
                }

                is NfcViewModel.EstadoNFC.Exito -> {
                    val icono = if (estado.estado == "retardo") "[tardanza]" else "[ok]"
                    textEstadoNFC.text = "$icono ${estado.nombre} — ${estado.estado}"
                }

                is NfcViewModel.EstadoNFC.ExitoOffline -> {
                    textEstadoNFC.text = "[offline] ${estado.nombre} — guardado offline"
                }

                is NfcViewModel.EstadoNFC.Error -> {
                    textEstadoNFC.text = estado.mensaje
                    mostrarEsperando()
                }
            }
        }
    }

    // ----- Helpers de UI -----

    private fun mostrarAlumno(nombre: String, fotoUrl: String) {
        layoutEsperando.visibility        = View.GONE
        layoutFotoAlumno.visibility       = View.VISIBLE
        txtNombreAlumnoScanner.visibility = View.VISIBLE
        txtNombreAlumnoScanner.text       = nombre

        if (fotoUrl.isNotEmpty()) {
            Glide.with(this).load(fotoUrl)
                .placeholder(R.drawable.ic_person_placeholder)
                .transform(CircleCrop())
                .into(imgAlumnoScanner)
        } else {
            imgAlumnoScanner.setImageResource(R.drawable.ic_person_placeholder)
        }
    }

    private fun mostrarEsperando() {
        layoutFotoAlumno.visibility       = View.GONE
        layoutEsperando.visibility        = View.VISIBLE
        txtNombreAlumnoScanner.visibility = View.GONE
        txtNombreAlumnoScanner.text       = ""
        textEstadoNFC.text                = "Acerca la tarjeta NFC del alumno"
    }
}
