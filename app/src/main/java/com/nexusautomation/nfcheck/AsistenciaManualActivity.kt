package com.nexusautomation.nfcheck

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class AsistenciaManualActivity : AppCompatActivity() {

    private lateinit var buscadorGrupo:   BuscadorHelper
    private lateinit var buscadorMateria: BuscadorHelper
    private lateinit var buscadorAlumno:  BuscadorHelper

    private lateinit var radioEstado: RadioGroup
    private lateinit var btnRegistrar: Button
    private lateinit var txtContexto: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cardGrupo:   View
    private lateinit var cardMateria: View

    private val alumnosMap     = mutableMapOf<String, String>()
    private val alumnosNombres = mutableListOf<String>()
    private val materiasProfesor = mutableListOf<String>()

    private var grupoActual:   String = ""
    private var materiaActual: String = ""
    private var esAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asistencia_manual)

        radioEstado  = findViewById(R.id.radioEstado)
        btnRegistrar = findViewById(R.id.btnRegistrarManual)
        txtContexto  = findViewById(R.id.textContexto)
        progressBar  = findViewById(R.id.progressManual)
        cardGrupo    = findViewById(R.id.cardGrupoManual)
        cardMateria  = findViewById(R.id.cardMateriaManual)

        esAdmin = intent.getBooleanExtra("esAdmin", false)
        val grupoIntent   = intent.getStringExtra("grupo")   ?: ""
        val materiaIntent = intent.getStringExtra("materia") ?: ""

        buscadorGrupo = BuscadorHelper(
            editText        = findViewById(R.id.etBuscarGrupoManual),
            listView        = findViewById(R.id.listViewGruposManual),
            txtSeleccionado = findViewById(R.id.txtGrupoManualSeleccionado),
            btnLimpiar      = findViewById(R.id.btnLimpiarGrupoManual),
            opciones        = emptyList(),
            prefixBadge     = "📍",
            onSeleccion     = { grupo ->
                grupoActual = grupo
                cargarMateriasParaGrupo(grupo)
                cargarAlumnos(grupo)
            },
            onLimpiado = {
                grupoActual   = ""
                materiaActual = ""
                alumnosNombres.clear()
                buscadorAlumno.actualizarOpciones(emptyList())
                buscadorMateria.limpiar()
                buscadorMateria.actualizarOpciones(emptyList())
            }
        )

        buscadorMateria = BuscadorHelper(
            editText        = findViewById(R.id.etBuscarMateriaManual),
            listView        = findViewById(R.id.listViewMateriasManual),
            txtSeleccionado = findViewById(R.id.txtMateriaManualSeleccionada),
            btnLimpiar      = findViewById(R.id.btnLimpiarMateriaManual),
            opciones        = emptyList(),
            prefixBadge     = "📚",
            onSeleccion     = { materia -> materiaActual = materia },
            onLimpiado      = { materiaActual = "" }
        )

        buscadorAlumno = BuscadorHelper(
            editText        = findViewById(R.id.etBuscarAlumnoManual),
            listView        = findViewById(R.id.listViewAlumnosManual),
            txtSeleccionado = findViewById(R.id.txtAlumnoManualSeleccionado),
            btnLimpiar      = findViewById(R.id.btnLimpiarAlumnoManual),
            opciones        = emptyList(),
            prefixBadge     = "👤",
            onSeleccion     = { },
            onLimpiado      = { }
        )

        when {
            grupoIntent.isNotEmpty() && materiaIntent.isNotEmpty() -> {
                grupoActual   = grupoIntent
                materiaActual = materiaIntent
                txtContexto.text = "Grupo: $grupoIntent  ·  Materia: $materiaIntent"
                cardGrupo.visibility   = View.GONE
                cardMateria.visibility = View.GONE
                cargarAlumnos(grupoIntent)
            }
            esAdmin -> cargarGrupos(todosLosGrupos = true)
            else    -> cargarGruposYMaterias()
        }

        btnRegistrar.setOnClickListener { registrarAsistencia() }
    }

    // ✅ Cierra cualquier lista abierta con el botón Atrás antes de salir
    override fun onBackPressed() {
        if (buscadorAlumno.cerrarSiAbierto())  return
        if (buscadorMateria.cerrarSiAbierto()) return
        if (buscadorGrupo.cerrarSiAbierto())   return
        super.onBackPressed()
    }

    private fun cargarGrupos(todosLosGrupos: Boolean) {
        if (todosLosGrupos) {
            buscadorGrupo.actualizarOpciones(DatosUniversidad.grupos)
            materiasProfesor.addAll(DatosUniversidad.materias)
        }
    }

    private fun cargarGruposYMaterias() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        FirebaseDatabase.getInstance().reference.child("usuarios").child(uid).get()
            .addOnSuccessListener { snap ->
                progressBar.visibility = View.GONE
                val grupos = snap.child("gruposAsignados").children.mapNotNull { it.key }.sorted()
                buscadorGrupo.actualizarOpciones(grupos)
                materiasProfesor.clear()
                val materiasFB = snap.child("materiasAsignadas").children.mapNotNull { it.key }
                if (materiasFB.isNotEmpty()) materiasProfesor.addAll(materiasFB.sorted())
                else materiasProfesor.addAll(DatosUniversidad.materias)
                if (grupos.isEmpty()) Toast.makeText(this, "No tienes grupos asignados", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error cargando datos del profesor", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarMateriasParaGrupo(grupo: String) {
        FirebaseDatabase.getInstance().reference.child("asistencias").child(grupo).get()
            .addOnSuccessListener { snap ->
                val materiasEnFirebase = snap.children.mapNotNull { it.key }
                val listaFinal = when {
                    materiasEnFirebase.isNotEmpty() && materiasProfesor.isNotEmpty() -> {
                        val interseccion = materiasProfesor.filter { it in materiasEnFirebase }
                        if (interseccion.isNotEmpty()) interseccion else materiasProfesor.toList()
                    }
                    materiasProfesor.isNotEmpty() -> materiasProfesor.toList()
                    else -> DatosUniversidad.materias
                }
                buscadorMateria.actualizarOpciones(listaFinal)
            }
            .addOnFailureListener {
                buscadorMateria.actualizarOpciones(
                    if (materiasProfesor.isNotEmpty()) materiasProfesor.toList() else DatosUniversidad.materias
                )
            }
    }

    private fun cargarAlumnos(grupo: String) {
        progressBar.visibility = View.VISIBLE
        FirebaseDatabase.getInstance().reference.child("alumnos").child(grupo).get()
            .addOnSuccessListener { snap ->
                progressBar.visibility = View.GONE
                alumnosMap.clear(); alumnosNombres.clear()
                for (s in snap.children) {
                    val matricula = s.key ?: continue
                    val nombre    = s.child("nombre").value?.toString() ?: continue
                    alumnosMap[nombre] = matricula
                    alumnosNombres.add(nombre)
                }
                alumnosNombres.sort()
                buscadorAlumno.actualizarOpciones(alumnosNombres)
                if (alumnosNombres.isEmpty()) Toast.makeText(this, "No hay alumnos en $grupo", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error cargando alumnos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun registrarAsistencia() {
        if (grupoActual.isEmpty())   { Toast.makeText(this, "Selecciona un grupo",   Toast.LENGTH_SHORT).show(); return }
        if (materiaActual.isEmpty()) { Toast.makeText(this, "Selecciona una materia", Toast.LENGTH_SHORT).show(); return }

        val nombreAlumno = findViewById<TextView>(R.id.txtAlumnoManualSeleccionado).text
            .toString().removePrefix("👤 ").trim()
        if (nombreAlumno.isEmpty()) { Toast.makeText(this, "Selecciona un alumno", Toast.LENGTH_SHORT).show(); return }

        val matricula = alumnosMap[nombreAlumno] ?: run {
            Toast.makeText(this, "Alumno no encontrado", Toast.LENGTH_SHORT).show(); return
        }

        val estado = when (radioEstado.checkedRadioButtonId) {
            R.id.radioAsistencia -> "asistencia"
            R.id.radioRetardo    -> "retardo"
            R.id.radioFalta      -> "falta"
            else                 -> "asistencia"
        }
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hora  = SimpleDateFormat("HH:mm",      Locale.getDefault()).format(Date())

        val ref = FirebaseDatabase.getInstance().reference
            .child("asistencias").child(grupoActual).child(materiaActual).child(fecha).child(matricula)

        btnRegistrar.isEnabled = false
        ref.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                btnRegistrar.isEnabled = true
                Toast.makeText(this, "$nombreAlumno ya fue registrado hoy", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            ref.setValue(mapOf("estado" to estado, "hora" to hora))
                .addOnSuccessListener {
                    btnRegistrar.isEnabled = true
                    Toast.makeText(this, "✓ $nombreAlumno — $estado", Toast.LENGTH_SHORT).show()
                    radioEstado.check(R.id.radioAsistencia)
                    buscadorAlumno.limpiar()
                }
                .addOnFailureListener { e ->
                    btnRegistrar.isEnabled = true
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}