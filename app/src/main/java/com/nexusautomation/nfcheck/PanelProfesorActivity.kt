package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class PanelProfesorActivity : AppCompatActivity() {

    private lateinit var cardSpinnersAdmin: View
    private lateinit var txtTitulo: TextView
    private lateinit var txtClaseDetectada: TextView
    private lateinit var cardAutoDetect: View
    private lateinit var cardSinClase: View

    // Spinners legacy (ocultos, compatibilidad)
    private lateinit var spinnerMateria: Spinner
    private lateinit var spinnerGrupo: Spinner

    // Buscadores para admin
    private lateinit var etBuscarGrupoAdmin: EditText
    private lateinit var listViewGruposAdmin: ListView
    private lateinit var txtGrupoAdmin: TextView
    private lateinit var btnLimpiarGrupoAdmin: View
    private lateinit var etBuscarMateriaAdmin: EditText
    private lateinit var listViewMateriasAdmin: ListView
    private lateinit var txtMateriaAdmin: TextView
    private lateinit var btnLimpiarMateriaAdmin: View

    private var materiaActiva: String? = null
    private var grupoActivo: String?   = null
    private var esAdmin = false

    private lateinit var buscadorGrupo: BuscadorHelper
    private var buscadorMateria: BuscadorHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panel_profesor)

        esAdmin           = intent.getBooleanExtra("esAdmin", false)
        cardSpinnersAdmin = findViewById(R.id.cardSpinnersAdmin)
        txtTitulo         = findViewById(R.id.txtTituloPanel)
        txtClaseDetectada = findViewById(R.id.txtClaseDetectada)
        cardAutoDetect    = findViewById(R.id.cardAutoDetect)
        cardSinClase      = findViewById(R.id.cardSinClase)
        spinnerMateria    = findViewById(R.id.spinnerMateriaProfesor)
        spinnerGrupo      = findViewById(R.id.spinnerGrupoProfesor)

        etBuscarGrupoAdmin     = findViewById(R.id.etBuscarGrupoAdmin)
        listViewGruposAdmin    = findViewById(R.id.listViewGruposAdmin)
        txtGrupoAdmin          = findViewById(R.id.txtGrupoAdminSeleccionado)
        btnLimpiarGrupoAdmin   = findViewById(R.id.btnLimpiarGrupoAdmin)
        etBuscarMateriaAdmin   = findViewById(R.id.etBuscarMateriaAdmin)
        listViewMateriasAdmin  = findViewById(R.id.listViewMateriasAdmin)
        txtMateriaAdmin        = findViewById(R.id.txtMateriaAdminSeleccionada)
        btnLimpiarMateriaAdmin = findViewById(R.id.btnLimpiarMateriaAdmin)

        val btnEscanear   = findViewById<Button>(R.id.btnEscanearAutomatico)
        val btnManual     = findViewById<Button>(R.id.btnTomarAsistencia)
        val btnVer        = findViewById<Button>(R.id.btnVerAsistencias)
        val btnCerrar     = findViewById<Button>(R.id.btnLogout)
        val btnVerHorario = findViewById<Button>(R.id.btnVerHorario)

        if (esAdmin) {
            txtTitulo.text               = "Panel Admin — Asistencia"
            cardAutoDetect.visibility    = View.GONE
            cardSinClase.visibility      = View.GONE
            btnVerHorario.visibility     = View.GONE
            cardSpinnersAdmin.visibility = View.VISIBLE
            spinnerGrupo.visibility      = View.GONE
            spinnerMateria.visibility    = View.GONE

            // ✅ Buscador de grupo estilo CrearAlumno
            buscadorGrupo = BuscadorHelper(
                editText        = etBuscarGrupoAdmin,
                listView        = listViewGruposAdmin,
                txtSeleccionado = txtGrupoAdmin,
                btnLimpiar      = btnLimpiarGrupoAdmin,
                opciones        = DatosUniversidad.grupos,
                prefixBadge     = "📍",
                onSeleccion     = { grupo ->
                    grupoActivo = grupo
                    // Carga materias de ese grupo dinámicamente
                    cargarMateriasDeGrupo(grupo)
                },
                onLimpiado = {
                    grupoActivo   = null
                    materiaActiva = null
                    buscadorMateria?.limpiar()
                    buscadorMateria?.actualizarOpciones(DatosUniversidad.materias)
                }
            )

            // Buscador de materia (opciones se actualizan según grupo)
            buscadorMateria = BuscadorHelper(
                editText        = etBuscarMateriaAdmin,
                listView        = listViewMateriasAdmin,
                txtSeleccionado = txtMateriaAdmin,
                btnLimpiar      = btnLimpiarMateriaAdmin,
                opciones        = DatosUniversidad.materias,
                prefixBadge     = "📚",
                onSeleccion     = { materia -> materiaActiva = materia },
                onLimpiado      = { materiaActiva = null }
            )

        } else {
            txtTitulo.text               = "Panel Profesor"
            cardSpinnersAdmin.visibility = View.GONE
            detectarClaseActual()
        }

        btnVerHorario.setOnClickListener {
            startActivity(Intent(this, VerHorarioActivity::class.java))
        }

        btnEscanear.setOnClickListener {
            if (esAdmin) {
                val m = materiaActiva; val g = grupoActivo
                if (m == null || g == null) {
                    Toast.makeText(this, "Selecciona grupo y materia", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                startActivity(Intent(this, NFCScannerActivity::class.java).putExtra("materia", m).putExtra("grupo", g))
            } else {
                startActivity(Intent(this, NFCScannerActivity::class.java))
            }
        }

        btnManual.setOnClickListener {
            val intent = Intent(this, AsistenciaManualActivity::class.java)
            if (esAdmin) {
                intent.putExtra("materia", materiaActiva ?: "")
                intent.putExtra("grupo",   grupoActivo   ?: "")
                intent.putExtra("esAdmin", true)
            } else {
                val clase = HorarioManager.obtenerClaseActual()
                if (clase != null) { intent.putExtra("materia", clase.materia); intent.putExtra("grupo", clase.grupo) }
            }
            startActivity(intent)
        }

        btnVer.setOnClickListener {
            startActivity(Intent(this, VerAsistenciasActivity::class.java).putExtra("esAdmin", esAdmin))
        }

        btnCerrar.setOnClickListener {
            HorarioManager.limpiar()
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }

    private fun cargarMateriasDeGrupo(grupo: String) {
        FirebaseDatabase.getInstance().reference.child("asistencias").child(grupo).get()
            .addOnSuccessListener { snap ->
                val materiasFirebase = snap.children.mapNotNull { it.key }
                val lista = if (materiasFirebase.isNotEmpty()) materiasFirebase else DatosUniversidad.materias
                buscadorMateria?.actualizarOpciones(lista)
            }
    }

    private fun detectarClaseActual() {
        val clase = HorarioManager.obtenerClaseActual()
        if (clase != null) {
            cardAutoDetect.visibility = View.VISIBLE
            cardSinClase.visibility   = View.GONE
            txtClaseDetectada.text    = "📍 ${clase.materia}\n👥 Grupo: ${clase.grupo}"
            materiaActiva = clase.materia
            grupoActivo   = clase.grupo
        } else {
            cardAutoDetect.visibility = View.GONE
            cardSinClase.visibility   = View.VISIBLE
        }
    }
}