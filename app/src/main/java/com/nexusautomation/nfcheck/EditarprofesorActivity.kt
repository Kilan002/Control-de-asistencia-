package com.nexusautomation.nfcheck

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.database.FirebaseDatabase

class EditarProfesorActivity : AppCompatActivity() {

    private lateinit var etNombre: EditText
    private lateinit var etBuscarGrupo: EditText
    private lateinit var listViewGrupos: ListView
    private lateinit var chipGroupGrupos: ChipGroup
    private lateinit var etBuscarMateria: EditText
    private lateinit var listViewMaterias: ListView
    private lateinit var chipGroupMaterias: ChipGroup
    private lateinit var btnGuardar: Button

    private var uidProfesor = ""

    // Listas para el buscador — iguales a CrearProfesorActivity
    private val gruposDisponibles = listOf(
        "RBM11","RBM21","RBM31","RBM41","RBM51",
        "RBM12","RBM22","RBM32","RBM42","RBM52",
        "RMB13","RMB23","RBM33","RMB43","RBM53",
        "IMTM11","IMTM21","IMTM31","IMTM41","IMTM51",
        "IMTM12","IMTM22","IMTM32","IMTM42","IMTM52",
        "IMTM13","IMTM23","IMTM33","IMTM43","IMTM53",
        "ATM11","ATM21","ATM31","ATM41","ATM51",
        "ATM12","ATM22","ATM32","ATM42","ATM52",
        "ATM13","ATM23","ATM33","ATM43","ATM53",
        "SMM11","SMM21","SMM31","SMM41","SMM51",
        "SMM12","SMM22","SMM32","SMM42","SMM52",
        "SMM13","SMM23","SMM33","SMM43","SMM53"
    )
    private val materiasDisponibles = listOf(
        "PROYECTO INTEGRADOR II","FUNDAMENTOS DE PROGRAMACION","INGLES V",
        "ECUACIONES DIFERENCIALES","MANTENIMIENTO A SISTEMAS ROBOT",
        "LEAD","INTRODUCCION A SISTEMAS DE VISION",
        "INTEGRADORA III","PLC","MAxC","SIS MAN FLEX","INT ROB IND"
    )

    private val gruposSeleccionados   = mutableSetOf<String>()
    private val materiasSeleccionadas = mutableSetOf<String>()
    private val gruposFiltrados       = mutableListOf<String>()
    private val materiasFiltradas     = mutableListOf<String>()
    private lateinit var adapterGrupos: ArrayAdapter<String>
    private lateinit var adapterMaterias: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editarprofesor)

        uidProfesor = intent.getStringExtra("uidProfesor") ?: run {
            Toast.makeText(this, "Error: UID no recibido", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val nombreProfesor = intent.getStringExtra("nombreProfesor") ?: ""

        etNombre          = findViewById(R.id.etNombreEditarProfesor)
        etBuscarGrupo     = findViewById(R.id.etBuscarGrupoEditar)
        listViewGrupos    = findViewById(R.id.listViewGruposEditar)
        chipGroupGrupos   = findViewById(R.id.chipGroupGruposEditar)
        etBuscarMateria   = findViewById(R.id.etBuscarMateriaEditar)
        listViewMaterias  = findViewById(R.id.listViewMateriasEditar)
        chipGroupMaterias = findViewById(R.id.chipGroupMateriasEditar)
        btnGuardar        = findViewById(R.id.btnGuardarEditar)

        etNombre.setText(nombreProfesor)

        gruposFiltrados.addAll(gruposDisponibles)
        materiasFiltradas.addAll(materiasDisponibles)
        adapterGrupos   = ArrayAdapter(this, android.R.layout.simple_list_item_1, gruposFiltrados)
        adapterMaterias = ArrayAdapter(this, android.R.layout.simple_list_item_1, materiasFiltradas)
        listViewGrupos.adapter   = adapterGrupos
        listViewMaterias.adapter = adapterMaterias

        configurarBusqueda()
        cargarDatosActuales()

        btnGuardar.setOnClickListener { guardarCambios() }
    }

    private fun cargarDatosActuales() {
        FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uidProfesor).get()
            .addOnSuccessListener { snap ->
                // Pre-carga grupos actuales como chips
                for (grupoSnap in snap.child("gruposAsignados").children) {
                    val g = grupoSnap.key ?: continue
                    agregarChipGrupo(g)
                }
                // Pre-carga materias actuales como chips
                for (matSnap in snap.child("materiasAsignadas").children) {
                    val m = matSnap.key ?: continue
                    agregarChipMateria(m)
                }
            }
    }

    private fun configurarBusqueda() {
        etBuscarGrupo.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) listViewGrupos.visibility = View.VISIBLE
        }
        etBuscarMateria.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) listViewMaterias.visibility = View.VISIBLE
        }

        etBuscarGrupo.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                gruposFiltrados.clear()
                gruposFiltrados.addAll(gruposDisponibles.filter {
                    it.contains(s.toString().uppercase()) && !gruposSeleccionados.contains(it)
                })
                adapterGrupos.notifyDataSetChanged()
                listViewGrupos.visibility = if (gruposFiltrados.isEmpty()) View.GONE else View.VISIBLE
            }
        })

        etBuscarMateria.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                materiasFiltradas.clear()
                materiasFiltradas.addAll(materiasDisponibles.filter {
                    it.contains(s.toString().uppercase()) && !materiasSeleccionadas.contains(it)
                })
                adapterMaterias.notifyDataSetChanged()
                listViewMaterias.visibility = if (materiasFiltradas.isEmpty()) View.GONE else View.VISIBLE
            }
        })

        listViewGrupos.setOnItemClickListener { _, _, pos, _ ->
            agregarChipGrupo(gruposFiltrados[pos])
            etBuscarGrupo.text.clear()
            etBuscarGrupo.clearFocus()
            listViewGrupos.visibility = View.GONE
        }

        listViewMaterias.setOnItemClickListener { _, _, pos, _ ->
            agregarChipMateria(materiasFiltradas[pos])
            etBuscarMateria.text.clear()
            etBuscarMateria.clearFocus()
            listViewMaterias.visibility = View.GONE
        }
    }

    private fun agregarChipGrupo(grupo: String) {
        if (gruposSeleccionados.contains(grupo)) return
        gruposSeleccionados.add(grupo)
        val chip = Chip(this).apply {
            text = grupo
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                chipGroupGrupos.removeView(this)
                gruposSeleccionados.remove(grupo)
                gruposFiltrados.add(grupo); gruposFiltrados.sort()
                adapterGrupos.notifyDataSetChanged()
            }
        }
        chipGroupGrupos.addView(chip)
        gruposFiltrados.remove(grupo)
        adapterGrupos.notifyDataSetChanged()
    }

    private fun agregarChipMateria(materia: String) {
        if (materiasSeleccionadas.contains(materia)) return
        materiasSeleccionadas.add(materia)
        val chip = Chip(this).apply {
            text = materia
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                chipGroupMaterias.removeView(this)
                materiasSeleccionadas.remove(materia)
                materiasFiltradas.add(materia); materiasFiltradas.sort()
                adapterMaterias.notifyDataSetChanged()
            }
        }
        chipGroupMaterias.addView(chip)
        materiasFiltradas.remove(materia)
        adapterMaterias.notifyDataSetChanged()
    }

    private fun guardarCambios() {
        val nombre = etNombre.text.toString().trim()
        if (nombre.isEmpty()) {
            Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show(); return
        }
        if (gruposSeleccionados.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un grupo", Toast.LENGTH_SHORT).show(); return
        }
        if (materiasSeleccionadas.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos una materia", Toast.LENGTH_SHORT).show(); return
        }

        btnGuardar.isEnabled = false

        val updates = mapOf(
            "nombre"            to nombre,
            "gruposAsignados"   to gruposSeleccionados.associateWith { true },
            "materiasAsignadas" to materiasSeleccionadas.associateWith { true }
        )

        FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uidProfesor)
            .updateChildren(updates)
            .addOnSuccessListener {
                btnGuardar.isEnabled = true
                Toast.makeText(this, "Profesor actualizado ✓", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnGuardar.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}