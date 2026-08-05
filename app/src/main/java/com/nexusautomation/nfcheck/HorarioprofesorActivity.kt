package com.nexusautomation.nfcheck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HorarioProfesorActivity : AppCompatActivity() {

    private lateinit var spinnerProfesor: Spinner
    private lateinit var containerBloques: LinearLayout
    private lateinit var btnAgregarBloque: Button
    private lateinit var btnGuardar: Button
    private lateinit var progressBar: ProgressBar

    private val profesores = mutableListOf<Pair<String, String>>() // uid, nombre

    private var materiasProfesor = listOf<String>()
    private var gruposProfesor   = listOf<String>()

    private val dias       = listOf("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES")
    private val horas      = listOf(7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0)
    private val horasTexto = listOf("7:00", "8:00", "9:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00")

    // UID preseleccionado que puede llegar desde GestionActivity
    private var uidPreseleccionado: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_horarioprofesor)

        spinnerProfesor  = findViewById(R.id.spinnerProfesorHorario)
        containerBloques = findViewById(R.id.containerBloques)
        btnAgregarBloque = findViewById(R.id.btnAgregarBloque)
        btnGuardar       = findViewById(R.id.btnGuardarHorario)
        progressBar      = findViewById(R.id.progressHorarioAdmin)

        // ✅ Recibe profesor preseleccionado si viene de GestionActivity
        uidPreseleccionado = intent.getStringExtra("uidPreseleccionado") ?: ""

        btnAgregarBloque.isEnabled = false
        btnGuardar.isEnabled       = false

        cargarProfesores()

        btnAgregarBloque.setOnClickListener {
            if (materiasProfesor.isEmpty() || gruposProfesor.isEmpty()) {
                Toast.makeText(this, "El profesor no tiene materias o grupos asignados", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            agregarFilaBloque()
        }

        btnGuardar.setOnClickListener { guardarHorario() }
    }

    private fun cargarProfesores() {
        progressBar.visibility = View.VISIBLE

        FirebaseDatabase.getInstance().reference.child("usuarios")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    progressBar.visibility = View.GONE
                    profesores.clear()

                    for (snap in snapshot.children) {
                        if (snap.child("rol").value?.toString() == "profesor") {
                            val uid    = snap.key ?: continue
                            val nombre = snap.child("nombre").value?.toString() ?: uid
                            profesores.add(uid to nombre)
                        }
                    }

                    if (profesores.isEmpty()) {
                        Toast.makeText(this@HorarioProfesorActivity, "No hay profesores registrados", Toast.LENGTH_LONG).show()
                        return
                    }

                    spinnerProfesor.adapter = ArrayAdapter(
                        this@HorarioProfesorActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        profesores.map { it.second }
                    )

                    spinnerProfesor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                            containerBloques.removeAllViews()
                            cargarDatosProfesor(profesores[pos].first)
                        }
                        override fun onNothingSelected(p: AdapterView<*>) {}
                    }

                    // ✅ Si hay un profesor preseleccionado, lo selecciona automáticamente
                    if (uidPreseleccionado.isNotEmpty()) {
                        val idx = profesores.indexOfFirst { it.first == uidPreseleccionado }
                        if (idx >= 0) spinnerProfesor.setSelection(idx)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@HorarioProfesorActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun cargarDatosProfesor(uid: String) {
        progressBar.visibility     = View.VISIBLE
        btnAgregarBloque.isEnabled = false
        btnGuardar.isEnabled       = false

        FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uid).get()
            .addOnSuccessListener { snap ->
                progressBar.visibility = View.GONE

                materiasProfesor = snap.child("materiasAsignadas").children
                    .mapNotNull { it.key }.sorted()

                gruposProfesor = snap.child("gruposAsignados").children
                    .mapNotNull { it.key }.sorted()

                if (materiasProfesor.isEmpty())
                    Toast.makeText(this, "Este profesor no tiene materias asignadas", Toast.LENGTH_LONG).show()
                if (gruposProfesor.isEmpty())
                    Toast.makeText(this, "Este profesor no tiene grupos asignados", Toast.LENGTH_LONG).show()

                btnAgregarBloque.isEnabled = materiasProfesor.isNotEmpty() && gruposProfesor.isNotEmpty()
                btnGuardar.isEnabled       = materiasProfesor.isNotEmpty() && gruposProfesor.isNotEmpty()

                val horarioSnap = snap.child("horario")
                if (horarioSnap.exists()) cargarHorarioExistente(horarioSnap)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun cargarHorarioExistente(horarioSnap: DataSnapshot) {
        containerBloques.removeAllViews()
        for (diaSnap in horarioSnap.children) {
            val dia = diaSnap.key ?: continue
            for (bloqueSnap in diaSnap.children) {
                val materia    = bloqueSnap.child("materia").value?.toString()                 ?: continue
                val grupo      = bloqueSnap.child("grupo").value?.toString()                   ?: continue
                val horaInicio = (bloqueSnap.child("horaInicio").value as? Number)?.toDouble() ?: continue
                val horaFin    = (bloqueSnap.child("horaFin").value as? Number)?.toDouble()    ?: continue
                agregarFilaBloque(dia, materia, grupo, horaInicio, horaFin)
            }
        }
    }

    private fun agregarFilaBloque(
        diaPreset: String     = "LUNES",
        materiaPreset: String = "",
        grupoPreset: String   = "",
        inicioPreset: Double  = 7.0,
        finPreset: Double     = 9.0
    ) {
        val fila = LayoutInflater.from(this)
            .inflate(R.layout.item_bloque_horario, containerBloques, false)

        val spinnerDia     = fila.findViewById<Spinner>(R.id.spinnerDia)
        val spinnerMateria = fila.findViewById<Spinner>(R.id.spinnerMateriaBloque)
        val spinnerGrupo   = fila.findViewById<Spinner>(R.id.spinnerGrupoBloque)
        val spinnerInicio  = fila.findViewById<Spinner>(R.id.spinnerHoraInicio)
        val spinnerFin     = fila.findViewById<Spinner>(R.id.spinnerHoraFin)
        val btnEliminar    = fila.findViewById<Button>(R.id.btnEliminarBloque)

        spinnerDia.adapter     = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dias)
        spinnerMateria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, materiasProfesor)
        spinnerGrupo.adapter   = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gruposProfesor)
        spinnerInicio.adapter  = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, horasTexto)
        spinnerFin.adapter     = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, horasTexto)

        spinnerDia.setSelection(dias.indexOf(diaPreset).coerceAtLeast(0))
        spinnerMateria.setSelection(materiasProfesor.indexOf(materiaPreset).coerceAtLeast(0))
        spinnerGrupo.setSelection(gruposProfesor.indexOf(grupoPreset).coerceAtLeast(0))
        spinnerInicio.setSelection(horas.indexOf(inicioPreset).coerceAtLeast(0))
        spinnerFin.setSelection(horas.indexOf(finPreset).coerceAtLeast(0))

        btnEliminar.setOnClickListener { containerBloques.removeView(fila) }
        containerBloques.addView(fila)
    }

    private fun guardarHorario() {
        if (profesores.isEmpty()) return

        val uid        = profesores[spinnerProfesor.selectedItemPosition].first
        val horarioMap = mutableMapOf<String, MutableList<Map<String, Any>>>()

        for (i in 0 until containerBloques.childCount) {
            val fila = containerBloques.getChildAt(i)
            val dia       = fila.findViewById<Spinner>(R.id.spinnerDia).selectedItem?.toString()           ?: continue
            val materia   = fila.findViewById<Spinner>(R.id.spinnerMateriaBloque).selectedItem?.toString() ?: continue
            val grupo     = fila.findViewById<Spinner>(R.id.spinnerGrupoBloque).selectedItem?.toString()   ?: continue
            val idxInicio = fila.findViewById<Spinner>(R.id.spinnerHoraInicio).selectedItemPosition
            val idxFin    = fila.findViewById<Spinner>(R.id.spinnerHoraFin).selectedItemPosition

            val horaInicio = horas[idxInicio]
            val horaFin    = horas[idxFin]

            if (horaFin <= horaInicio) {
                Toast.makeText(this, "Hora fin debe ser mayor que hora inicio en '$materia'", Toast.LENGTH_SHORT).show()
                return
            }

            horarioMap.getOrPut(dia) { mutableListOf() }.add(mapOf(
                "materia"    to materia,
                "grupo"      to grupo,
                "horaInicio" to horaInicio,
                "horaFin"    to horaFin
            ))
        }

        if (horarioMap.isEmpty()) {
            Toast.makeText(this, "Agrega al menos un bloque", Toast.LENGTH_SHORT).show()
            return
        }

        btnGuardar.isEnabled = false
        FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uid).child("horario")
            .setValue(horarioMap)
            .addOnSuccessListener {
                btnGuardar.isEnabled = true
                Toast.makeText(this, "Horario guardado correctamente ✓", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                btnGuardar.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}