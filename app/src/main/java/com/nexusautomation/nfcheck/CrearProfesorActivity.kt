package com.nexusautomation.nfcheck

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.functions.FirebaseFunctions

class CrearProfesorActivity : AppCompatActivity() {

    private lateinit var etNombre:            EditText
    private lateinit var etCorreo:            EditText
    private lateinit var etPassword:          EditText
    private lateinit var etConfirmarPassword: EditText
    private lateinit var etBuscarMateria:     EditText
    private lateinit var listViewMaterias:    ListView
    private lateinit var chipGroupMaterias:   ChipGroup
    private lateinit var etBuscarGrupo:       EditText
    private lateinit var listViewGrupos:      ListView
    private lateinit var chipGroupGrupos:     ChipGroup
    private lateinit var btnCrear:            Button
    private lateinit var progressBar:         android.widget.ProgressBar

    private val materiasDisponibles get() = DatosUniversidad.materias
    private val gruposDisponibles   get() = DatosUniversidad.grupos

    private val materiasSeleccionadas = mutableSetOf<String>()
    private val gruposSeleccionados   = mutableSetOf<String>()
    private val materiasFiltradas     = mutableListOf<String>()
    private val gruposFiltrados       = mutableListOf<String>()

    private lateinit var adapterMaterias: ArrayAdapter<String>
    private lateinit var adapterGrupos:   ArrayAdapter<String>

    private val functions = FirebaseFunctions.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crear_profesor)

        etNombre            = findViewById(R.id.etNombreProfesor)
        etCorreo            = findViewById(R.id.etCorreoProfesor)
        etPassword          = findViewById(R.id.etPasswordProfesor)
        etConfirmarPassword = findViewById(R.id.etConfirmarPasswordProfesor)
        etBuscarMateria     = findViewById(R.id.etBuscarMateria)
        listViewMaterias    = findViewById(R.id.listViewMaterias)
        chipGroupMaterias   = findViewById(R.id.chipGroupMaterias)
        etBuscarGrupo       = findViewById(R.id.etBuscarGrupo)
        listViewGrupos      = findViewById(R.id.listViewGrupos)
        chipGroupGrupos     = findViewById(R.id.chipGroupGrupos)
        btnCrear            = findViewById(R.id.btnCrearProfesor)
        progressBar         = findViewById(R.id.progressCrearProfesor)

        materiasFiltradas.addAll(materiasDisponibles)
        gruposFiltrados.addAll(gruposDisponibles)

        adapterMaterias = ArrayAdapter(this, android.R.layout.simple_list_item_1, materiasFiltradas)
        adapterGrupos   = ArrayAdapter(this, android.R.layout.simple_list_item_1, gruposFiltrados)
        listViewMaterias.adapter = adapterMaterias
        listViewGrupos.adapter   = adapterGrupos

        etBuscarGrupo.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && gruposFiltrados.isNotEmpty()) listViewGrupos.visibility = View.VISIBLE
        }
        etBuscarMateria.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && materiasFiltradas.isNotEmpty()) listViewMaterias.visibility = View.VISIBLE
        }

        etBuscarGrupo.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s.toString().uppercase()
                gruposFiltrados.clear()
                gruposFiltrados.addAll(gruposDisponibles.filter { it.contains(q) && it !in gruposSeleccionados })
                adapterGrupos.notifyDataSetChanged()
                listViewGrupos.visibility = if (gruposFiltrados.isEmpty()) View.GONE else View.VISIBLE
            }
        })

        etBuscarMateria.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s.toString().uppercase()
                materiasFiltradas.clear()
                materiasFiltradas.addAll(materiasDisponibles.filter { it.contains(q) && it !in materiasSeleccionadas })
                adapterMaterias.notifyDataSetChanged()
                listViewMaterias.visibility = if (materiasFiltradas.isEmpty()) View.GONE else View.VISIBLE
            }
        })

        listViewGrupos.setOnItemClickListener { _, _, pos, _ ->
            agregarChipGrupo(gruposFiltrados[pos])
            etBuscarGrupo.text.clear(); etBuscarGrupo.clearFocus()
            listViewGrupos.visibility = View.GONE
        }
        listViewMaterias.setOnItemClickListener { _, _, pos, _ ->
            agregarChipMateria(materiasFiltradas[pos])
            etBuscarMateria.text.clear(); etBuscarMateria.clearFocus()
            listViewMaterias.visibility = View.GONE
        }

        btnCrear.setOnClickListener { crearProfesor() }
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
                if (!gruposFiltrados.contains(grupo)) { gruposFiltrados.add(grupo); gruposFiltrados.sort() }
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
                if (!materiasFiltradas.contains(materia)) { materiasFiltradas.add(materia); materiasFiltradas.sort() }
                adapterMaterias.notifyDataSetChanged()
            }
        }
        chipGroupMaterias.addView(chip)
        materiasFiltradas.remove(materia)
        adapterMaterias.notifyDataSetChanged()
    }

    private fun crearProfesor() {
        val nombre   = etNombre.text.toString().trim()
        val correo   = etCorreo.text.toString().trim().lowercase()
        val password = etPassword.text.toString()
        val confirm  = etConfirmarPassword.text.toString()

        if (nombre.isEmpty() || correo.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show(); return
        }
        if (password.length < 8) {
            Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show(); return
        }
        if (password != confirm) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show(); return
        }
        if (gruposSeleccionados.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos un grupo", Toast.LENGTH_SHORT).show(); return
        }
        if (materiasSeleccionadas.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos una materia", Toast.LENGTH_SHORT).show(); return
        }

        btnCrear.isEnabled     = false
        progressBar.visibility = View.VISIBLE

        val datos = hashMapOf(
            "nombre"   to nombre,
            "correo"   to correo,
            "password" to password,
            "grupos"   to gruposSeleccionados.toList(),
            "materias" to materiasSeleccionadas.toList()
        )

        // ✅ Nombre exacto de la Function en index.js
        functions.getHttpsCallable("crearProfesor")
            .call(datos)
            .addOnSuccessListener {
                btnCrear.isEnabled     = true
                progressBar.visibility = View.GONE
                Toast.makeText(this, "✅ Profesor creado correctamente", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnCrear.isEnabled     = true
                progressBar.visibility = View.GONE
                android.util.Log.e("NFCheck_Functions", "Error: ${e.message}", e)
                val msg = when {
                    e.message?.contains("already-exists") == true   -> "El correo ya está registrado"
                    e.message?.contains("permission-denied") == true -> "Sin permisos para esta operación"
                    e.message?.contains("unauthenticated") == true   -> "Sesión caducada, vuelve a iniciar sesión"
                    e.message?.contains("unavailable") == true       -> "Sin conexión a internet"
                    else -> "Error: ${e.message}"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
    }
}