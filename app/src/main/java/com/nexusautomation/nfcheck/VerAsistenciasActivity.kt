package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexusautomation.nfcheck.AsistenciaViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class VerAsistenciasActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AlumnoResumenAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var txtVacio: TextView

    // Búsqueda de grupo
    private lateinit var etBuscarGrupo: EditText
    private lateinit var listViewGrupos: ListView
    private lateinit var txtGrupoSeleccionado: TextView

    // Búsqueda de materia
    private lateinit var etBuscarMateria: EditText
    private lateinit var listViewMaterias: ListView
    private lateinit var txtMateriaSeleccionada: TextView

    private val viewModel: AsistenciaViewModel by viewModels()
    private var esAdmin = false

    // Grupos disponibles para este usuario
    private val todosLosGrupos  = mutableListOf<String>()
    private val gruposFiltrados = mutableListOf<String>()

    // Materias del grupo seleccionado
    private val todasLasMaterias  = mutableListOf<String>()
    private val materiasFiltradas = mutableListOf<String>()

    private lateinit var adapterGrupos: ArrayAdapter<String>
    private lateinit var adapterMaterias: ArrayAdapter<String>

    private var grupoActual:   String = ""
    private var materiaActual: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ver_asistencias)

        esAdmin     = intent.getBooleanExtra("esAdmin", false)
        recycler    = findViewById(R.id.recyclerAsistencias)
        progressBar = findViewById(R.id.progressBar)
        txtVacio    = findViewById(R.id.txtVacio)

        etBuscarGrupo          = findViewById(R.id.etBuscarGrupoVer)
        listViewGrupos         = findViewById(R.id.listViewGruposVer)
        txtGrupoSeleccionado   = findViewById(R.id.txtGrupoSeleccionadoVer)
        etBuscarMateria        = findViewById(R.id.etBuscarMateriaVer)
        listViewMaterias       = findViewById(R.id.listViewMateriasVer)
        txtMateriaSeleccionada = findViewById(R.id.txtMateriaSeleccionadaVer)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = AlumnoResumenAdapter(mutableListOf()) { alumno ->
            startActivity(Intent(this, DetalleAsistenciasActivity::class.java).apply {
                putExtra("matricula", alumno.matricula)
                putExtra("nombre",    alumno.nombre)
                putExtra("grupo",     grupoActual)
                putExtra("esAdmin",   esAdmin)
            })
        }
        recycler.adapter = adapter

        adapterGrupos   = ArrayAdapter(this, android.R.layout.simple_list_item_1, gruposFiltrados)
        adapterMaterias = ArrayAdapter(this, android.R.layout.simple_list_item_1, materiasFiltradas)
        listViewGrupos.adapter   = adapterGrupos
        listViewMaterias.adapter = adapterMaterias

        viewModel.alumnos.observe(this) { lista ->
            adapter.actualizarLista(lista)
            txtVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.cargando.observe(this) { cargando ->
            progressBar.visibility = if (cargando) View.VISIBLE else View.GONE
        }
        viewModel.error.observe(this) { msg ->
            if (!msg.isNullOrEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        configurarBusquedaGrupos()
        configurarBusquedaMaterias()
        cargarGrupos()
    }

    // ─── Carga grupos según rol ───────────────────────────────────────────────

    private fun cargarGrupos() {
        if (esAdmin) {
            // Admin: todos los grupos de la universidad
            todosLosGrupos.clear()
            todosLosGrupos.addAll(DatosUniversidad.grupos)
            actualizarListaGrupos("")
        } else {
            // Profesor: solo sus grupos asignados
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseDatabase.getInstance().reference
                .child("usuarios").child(uid).child("gruposAsignados").get()
                .addOnSuccessListener { snap ->
                    todosLosGrupos.clear()
                    todosLosGrupos.addAll(snap.children.mapNotNull { it.key }.sorted())
                    actualizarListaGrupos("")
                    if (todosLosGrupos.isEmpty()) {
                        txtVacio.text = "No tienes grupos asignados"
                        txtVacio.visibility = View.VISIBLE
                    }
                }
        }
    }

    // ─── Búsqueda de grupos ───────────────────────────────────────────────────

    private fun configurarBusquedaGrupos() {
        etBuscarGrupo.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                actualizarListaGrupos(etBuscarGrupo.text.toString())
                listViewGrupos.visibility = View.VISIBLE
            }
        }
        etBuscarGrupo.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                actualizarListaGrupos(s.toString())
                listViewGrupos.visibility = if (gruposFiltrados.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })
        listViewGrupos.setOnItemClickListener { _, _, pos, _ ->
            val grupo = gruposFiltrados[pos]
            seleccionarGrupo(grupo)
        }
    }

    private fun seleccionarGrupo(grupo: String) {
        grupoActual = grupo
        txtGrupoSeleccionado.text = "📍 $grupo"
        txtGrupoSeleccionado.visibility = View.VISIBLE
        etBuscarGrupo.text.clear()
        etBuscarGrupo.clearFocus()
        listViewGrupos.visibility = View.GONE

        // Resetea materia
        materiaActual = ""
        txtMateriaSeleccionada.visibility = View.GONE
        etBuscarMateria.text.clear()

        // Carga materias del grupo seleccionado y alumnos
        cargarMaterias(grupo)
        viewModel.cargarGrupo(grupo)
    }

    private fun actualizarListaGrupos(query: String) {
        gruposFiltrados.clear()
        gruposFiltrados.addAll(
            todosLosGrupos.filter { it.contains(query.uppercase()) }
        )
        adapterGrupos.notifyDataSetChanged()
    }

    // ─── Búsqueda de materias ─────────────────────────────────────────────────

    private fun cargarMaterias(grupo: String) {
        // Carga materias que ya tienen registros en Firebase para este grupo
        FirebaseDatabase.getInstance().reference
            .child("asistencias").child(grupo).get()
            .addOnSuccessListener { snap ->
                todasLasMaterias.clear()
                todasLasMaterias.add("Todas las materias")
                val materiasFirebase = snap.children.mapNotNull { it.key }.sorted()
                if (materiasFirebase.isNotEmpty()) {
                    todasLasMaterias.addAll(materiasFirebase)
                } else {
                    // Sin registros aún: muestra todas las materias de la universidad
                    todasLasMaterias.addAll(DatosUniversidad.materias)
                }
                actualizarListaMaterias("")
            }
    }

    private fun configurarBusquedaMaterias() {
        etBuscarMateria.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && todasLasMaterias.isNotEmpty()) {
                actualizarListaMaterias(etBuscarMateria.text.toString())
                listViewMaterias.visibility = View.VISIBLE
            } else if (hasFocus && grupoActual.isEmpty()) {
                Toast.makeText(this, "Selecciona un grupo primero", Toast.LENGTH_SHORT).show()
            }
        }
        etBuscarMateria.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                actualizarListaMaterias(s.toString())
                listViewMaterias.visibility = if (materiasFiltradas.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })
        listViewMaterias.setOnItemClickListener { _, _, pos, _ ->
            val materia = materiasFiltradas[pos]
            materiaActual = if (materia == "Todas las materias") "" else materia
            txtMateriaSeleccionada.text = "📚 $materia"
            txtMateriaSeleccionada.visibility = View.VISIBLE
            etBuscarMateria.text.clear()
            etBuscarMateria.clearFocus()
            listViewMaterias.visibility = View.GONE

            if (grupoActual.isNotEmpty()) {
                if (materiaActual.isEmpty()) {
                    viewModel.cargarGrupo(grupoActual)
                } else {
                    viewModel.cargarGrupoYMateria(grupoActual, materiaActual)
                }
            }
        }
    }

    private fun actualizarListaMaterias(query: String) {
        materiasFiltradas.clear()
        materiasFiltradas.addAll(
            todasLasMaterias.filter { it.contains(query, ignoreCase = true) }
        )
        adapterMaterias.notifyDataSetChanged()
    }
}