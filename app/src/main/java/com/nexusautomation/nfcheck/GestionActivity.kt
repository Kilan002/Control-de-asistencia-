package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class GestionActivity : AppCompatActivity() {

    private lateinit var etBuscarAlumno: EditText
    private lateinit var listViewAlumnos: ListView
    private lateinit var chipAlumno: Chip
    private lateinit var btnVerAlumno: Button
    private lateinit var btnEditarAlumno: Button

    private lateinit var etBuscarProfesor: EditText
    private lateinit var listViewProfesores: ListView
    private lateinit var chipProfesor: Chip
    private lateinit var btnEditarProfesor: Button
    private lateinit var btnHorarioProfesor: Button
    private lateinit var btnListaProfesores: Button

    private data class UsuarioItem(val uid: String, val nombre: String, val grupo: String = "", val matricula: String = "")

    private val todosAlumnos        = mutableListOf<UsuarioItem>()
    private val alumnosFiltrados    = mutableListOf<UsuarioItem>()
    private val todosProfesores     = mutableListOf<UsuarioItem>()
    private val profesoresFiltrados = mutableListOf<UsuarioItem>()

    private lateinit var adapterAlumnos: ArrayAdapter<String>
    private lateinit var adapterProfesores: ArrayAdapter<String>

    private var alumnoSeleccionado:   UsuarioItem? = null
    private var profesorSeleccionado: UsuarioItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gestion)

        etBuscarAlumno     = findViewById(R.id.etBuscarAlumno)
        listViewAlumnos    = findViewById(R.id.listViewAlumnos)
        chipAlumno         = findViewById(R.id.chipAlumnoSeleccionado)
        btnVerAlumno       = findViewById(R.id.btnVerAsistenciasAlumno)
        btnEditarAlumno    = findViewById(R.id.btnEditarAlumno)
        etBuscarProfesor   = findViewById(R.id.etBuscarProfesor)
        listViewProfesores = findViewById(R.id.listViewProfesores)
        chipProfesor       = findViewById(R.id.chipProfesorSeleccionado)
        btnEditarProfesor  = findViewById(R.id.btnEditarMateriasProfesor)
        btnHorarioProfesor = findViewById(R.id.btnHorarioProfesor)
        btnListaProfesores = findViewById(R.id.btnListaProfesores)

        adapterAlumnos    = ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1, mutableListOf<String>())
        adapterProfesores = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        listViewAlumnos.adapter    = adapterAlumnos
        listViewProfesores.adapter = adapterProfesores

        cargarUsuarios()
        configurarBusquedas()

        // Chips removibles con ✕
        chipAlumno.isCloseIconVisible = true
        chipAlumno.setOnCloseIconClickListener {
            alumnoSeleccionado        = null
            chipAlumno.visibility     = View.GONE
            btnVerAlumno.isEnabled    = false
            btnEditarAlumno.isEnabled = false
        }

        chipProfesor.isCloseIconVisible = true
        chipProfesor.setOnCloseIconClickListener {
            profesorSeleccionado         = null
            chipProfesor.visibility      = View.GONE
            btnEditarProfesor.isEnabled   = false
            btnHorarioProfesor.isEnabled  = false
        }

        btnVerAlumno.isEnabled      = false
        btnEditarAlumno.isEnabled   = false
        btnEditarProfesor.isEnabled  = false
        btnHorarioProfesor.isEnabled = false

        btnVerAlumno.setOnClickListener {
            val alumno = alumnoSeleccionado ?: return@setOnClickListener
            startActivity(Intent(this, DetalleAsistenciasActivity::class.java).apply {
                putExtra("grupo",     alumno.grupo)
                putExtra("matricula", alumno.matricula)
                putExtra("nombre",    alumno.nombre)
                putExtra("esAdmin",   true)
            })
        }

        btnEditarAlumno.setOnClickListener {
            val alumno = alumnoSeleccionado ?: return@setOnClickListener
            startActivity(Intent(this, EditarAlumnoActivity::class.java).apply {
                putExtra("grupo",     alumno.grupo)
                putExtra("matricula", alumno.matricula)
                putExtra("userId",    alumno.uid)
            })
        }

        btnEditarProfesor.setOnClickListener {
            val prof = profesorSeleccionado ?: return@setOnClickListener
            startActivity(Intent(this, EditarProfesorActivity::class.java).apply {
                putExtra("uidProfesor",    prof.uid)
                putExtra("nombreProfesor", prof.nombre)
            })
        }

        // ✅ Abre VerHorarioActivity con el uid del profesor y esAdmin=true
        // para que aparezca el botón de configurar dentro de VerHorarioActivity
        btnHorarioProfesor.setOnClickListener {
            val prof = profesorSeleccionado ?: return@setOnClickListener
            startActivity(Intent(this, VerHorarioActivity::class.java).apply {
                putExtra("uidProfesor", prof.uid)
                putExtra("esAdmin",     true)
            })
        }

        btnListaProfesores.setOnClickListener {
            startActivity(Intent(this, ListaProfesoresActivity::class.java))
        }
    }

    private fun cargarUsuarios() {
        FirebaseDatabase.getInstance().reference.child("usuarios")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    todosAlumnos.clear(); todosProfesores.clear()
                    for (snap in snapshot.children) {
                        val uid    = snap.key ?: continue
                        val nombre = snap.child("nombre").value?.toString() ?: continue
                        when (snap.child("rol").value?.toString()) {
                            "alumno"   -> todosAlumnos.add(UsuarioItem(uid, nombre,
                                snap.child("grupo").value?.toString() ?: "",
                                snap.child("matricula").value?.toString() ?: ""))
                            "profesor" -> todosProfesores.add(UsuarioItem(uid, nombre))
                        }
                    }
                    todosAlumnos.sortBy { it.nombre }
                    todosProfesores.sortBy { it.nombre }
                    actualizarListaAlumnos("")
                    actualizarListaProfesores("")
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun configurarBusquedas() {
        etBuscarAlumno.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) { actualizarListaAlumnos(etBuscarAlumno.text.toString()); listViewAlumnos.visibility = View.VISIBLE }
        }
        etBuscarAlumno.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                actualizarListaAlumnos(s.toString())
                listViewAlumnos.visibility = if (alumnosFiltrados.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })
        listViewAlumnos.setOnItemClickListener { _, _, pos, _ ->
            val alumno = alumnosFiltrados[pos]
            alumnoSeleccionado = alumno
            chipAlumno.text = "${alumno.nombre}  ·  ${alumno.grupo}"
            chipAlumno.visibility     = View.VISIBLE
            btnVerAlumno.isEnabled    = true
            btnEditarAlumno.isEnabled = true
            etBuscarAlumno.text.clear(); etBuscarAlumno.clearFocus()
            listViewAlumnos.visibility = View.GONE
        }

        etBuscarProfesor.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) { actualizarListaProfesores(etBuscarProfesor.text.toString()); listViewProfesores.visibility = View.VISIBLE }
        }
        etBuscarProfesor.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                actualizarListaProfesores(s.toString())
                listViewProfesores.visibility = if (profesoresFiltrados.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })
        listViewProfesores.setOnItemClickListener { _, _, pos, _ ->
            val prof = profesoresFiltrados[pos]
            profesorSeleccionado = prof
            chipProfesor.text = prof.nombre
            chipProfesor.visibility      = View.VISIBLE
            btnEditarProfesor.isEnabled   = true
            btnHorarioProfesor.isEnabled  = true
            etBuscarProfesor.text.clear(); etBuscarProfesor.clearFocus()
            listViewProfesores.visibility = View.GONE
        }
    }

    private fun actualizarListaAlumnos(query: String) {
        alumnosFiltrados.clear()
        alumnosFiltrados.addAll(todosAlumnos.filter {
            it.nombre.contains(query, ignoreCase = true) ||
                    it.matricula.contains(query) ||
                    it.grupo.contains(query, ignoreCase = true)
        })
        adapterAlumnos.clear()
        adapterAlumnos.addAll(alumnosFiltrados.map { "${it.nombre}\n${it.grupo} · ${it.matricula}" })
        adapterAlumnos.notifyDataSetChanged()
    }

    private fun actualizarListaProfesores(query: String) {
        profesoresFiltrados.clear()
        profesoresFiltrados.addAll(todosProfesores.filter { it.nombre.contains(query, ignoreCase = true) })
        adapterProfesores.clear()
        adapterProfesores.addAll(profesoresFiltrados.map { it.nombre })
        adapterProfesores.notifyDataSetChanged()
    }
}