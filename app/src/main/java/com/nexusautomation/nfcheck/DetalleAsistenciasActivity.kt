package com.nexusautomation.nfcheck

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*

class DetalleAsistenciasActivity : AppCompatActivity() {

    data class RegistroDetalle(
        val fecha: String,
        val materia: String,
        val hora: String,
        val estado: String,
        val justificante: Boolean,
        val nota: String,
        val matricula: String,
        val grupo: String
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var nombreText: TextView
    private lateinit var matriculaText: TextView
    private lateinit var totalAsistenciasText: TextView
    private lateinit var totalRetardosText: TextView
    private lateinit var totalFaltasText: TextView
    private lateinit var porcentajeText: TextView
    private lateinit var imgFotoAlumno: android.widget.ImageView
    private lateinit var fabEditarAlumno: FloatingActionButton
    private lateinit var spinnerMateria: Spinner
    private lateinit var txtSinRegistros: TextView

    private val listaRegistros      = mutableListOf<RegistroDetalle>()
    private val todosLosRegistros   = mutableListOf<RegistroDetalle>()
    private val materiasDisponibles = mutableListOf<String>()
    private lateinit var adapter: RegistroDetalleAdapter

    private var grupo: String     = ""
    private var matricula: String = ""
    private var userId: String    = ""
    private var esAdmin: Boolean  = false
    private var datosListos       = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_asistencias)

        recyclerView         = findViewById(R.id.recyclerViewAsistencias)
        nombreText           = findViewById(R.id.textNombre)
        matriculaText        = findViewById(R.id.textMatricula)
        totalAsistenciasText = findViewById(R.id.textTotalAsistencias)
        totalRetardosText    = findViewById(R.id.textTotalRetardos)
        totalFaltasText      = findViewById(R.id.textTotalFaltas)
        porcentajeText       = findViewById(R.id.textPorcentaje)
        imgFotoAlumno        = findViewById(R.id.imgFotoAlumno)
        fabEditarAlumno      = findViewById(R.id.fabEditarAlumno)
        spinnerMateria       = findViewById(R.id.spinnerMateriaDetalle)
        txtSinRegistros      = findViewById(R.id.txtSinRegistros)

        grupo     = intent.getStringExtra("grupo")     ?: ""
        matricula = intent.getStringExtra("matricula") ?: ""
        esAdmin   = intent.getBooleanExtra("esAdmin", false)
        val nombre = intent.getStringExtra("nombre")  ?: ""

        if (grupo.isEmpty() || matricula.isEmpty()) {
            Toast.makeText(this, "Datos incompletos", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        nombreText.text    = nombre
        matriculaText.text = "Matrícula: $matricula"

        adapter = RegistroDetalleAdapter(listaRegistros) { registro ->
            mostrarDialogoEditar(registro)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // ✅ Solo queda el FAB de editar alumno (foto se gestiona desde EditarAlumnoActivity)
        if (esAdmin) {
            fabEditarAlumno.visibility = View.VISIBLE
            fabEditarAlumno.isEnabled  = false
        } else {
            fabEditarAlumno.visibility = View.GONE
        }

        cargarDatosAlumno()
        cargarAsistencias()

        fabEditarAlumno.setOnClickListener {
            if (!datosListos) return@setOnClickListener
            startActivity(Intent(this, EditarAlumnoActivity::class.java).apply {
                putExtra("grupo",     grupo)
                putExtra("matricula", matricula)
                putExtra("userId",    userId)
            })
        }
    }

    // ─── Spinner materias ─────────────────────────────────────────────────────

    private fun configurarSpinnerMaterias() {
        if (materiasDisponibles.isEmpty()) return
        val opciones = mutableListOf("Todas las materias") + materiasDisponibles
        spinnerMateria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opciones)
        spinnerMateria.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                filtrarPorMateria(if (pos == 0) null else materiasDisponibles[pos - 1])
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    private fun filtrarPorMateria(materia: String?) {
        listaRegistros.clear()
        val filtrados = if (materia == null) todosLosRegistros
        else todosLosRegistros.filter { it.materia == materia }
        listaRegistros.addAll(filtrados.sortedByDescending { it.fecha })
        adapter.notifyDataSetChanged()
        txtSinRegistros.visibility = if (listaRegistros.isEmpty()) View.VISIBLE else View.GONE
        calcularEstadisticas(filtrados, materia)
    }

    private fun calcularEstadisticas(registros: List<RegistroDetalle>, materia: String?) {
        var a = 0; var r = 0; var f = 0
        val total = registros.size
        for (reg in registros) when (reg.estado) { "asistencia" -> a++; "retardo" -> r++; "falta" -> f++ }
        val pct = if (total > 0) ((a + r * 0.5) / total) * 100 else 0.0
        totalAsistenciasText.text = "$a"
        totalRetardosText.text    = "$r"
        totalFaltasText.text      = "$f"
        val etiqueta = if (materia != null) "en ${materia.take(20)}" else "general"
        porcentajeText.text = "Asistencia $etiqueta: ${"%.1f".format(pct)}%"
    }

    // ─── Diálogo editar ───────────────────────────────────────────────────────

    private fun mostrarDialogoEditar(registro: RegistroDetalle) {
        val dialogView        = layoutInflater.inflate(R.layout.dialog_editar_asistencia, null)
        val radioGroup        = dialogView.findViewById<RadioGroup>(R.id.radioGroupEstado)
        val checkJustificante = dialogView.findViewById<android.widget.CheckBox>(R.id.checkJustificante)
        val edtNota           = dialogView.findViewById<android.widget.EditText>(R.id.edtNotaJustificante)

        when (registro.estado) {
            "asistencia" -> radioGroup.check(R.id.radioAsistencia)
            "retardo"    -> radioGroup.check(R.id.radioRetardo)
            "falta"      -> radioGroup.check(R.id.radioFalta)
        }
        checkJustificante.isChecked = registro.justificante
        edtNota.setText(registro.nota)
        edtNota.visibility = if (registro.justificante) View.VISIBLE else View.GONE
        checkJustificante.setOnCheckedChangeListener { _, checked ->
            edtNota.visibility = if (checked) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("${formatearFecha(registro.fecha)}\n${registro.materia}")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoEstado = when (radioGroup.checkedRadioButtonId) {
                    R.id.radioAsistencia -> "asistencia"
                    R.id.radioRetardo    -> "retardo"
                    R.id.radioFalta      -> "falta"
                    else                 -> registro.estado
                }
                guardarCambio(registro, nuevoEstado, checkJustificante.isChecked, edtNota.text.toString().trim())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarCambio(registro: RegistroDetalle, estado: String, justificante: Boolean, nota: String) {
        FirebaseDatabase.getInstance().reference
            .child("asistencias")
            .child(registro.grupo).child(registro.materia)
            .child(registro.fecha).child(registro.matricula)
            .updateChildren(mapOf("estado" to estado, "justificante" to justificante, "nota" to nota))
            .addOnSuccessListener {
                Toast.makeText(this, "Asistencia actualizada ✓", Toast.LENGTH_SHORT).show()
                cargarAsistencias()
            }
            .addOnFailureListener { e -> Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    // ─── Carga ────────────────────────────────────────────────────────────────

    private fun cargarDatosAlumno() {
        FirebaseDatabase.getInstance().reference
            .child("alumnos").child(grupo).child(matricula).get()
            .addOnSuccessListener { snap ->
                userId = snap.child("uid").value?.toString() ?: ""
                val fotoUrl = snap.child("fotoUrl").value?.toString()
                if (!fotoUrl.isNullOrEmpty()) {
                    Glide.with(this).load(fotoUrl)
                        .placeholder(R.drawable.ic_person_placeholder)
                        .circleCrop().into(imgFotoAlumno)
                } else {
                    imgFotoAlumno.setImageResource(R.drawable.ic_person_placeholder)
                }
                datosListos = true
                if (esAdmin) fabEditarAlumno.isEnabled = true
            }
    }

    private fun cargarAsistencias() {
        FirebaseDatabase.getInstance().reference
            .child("asistencias").child(grupo)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    todosLosRegistros.clear()
                    materiasDisponibles.clear()

                    for (matSnap in snapshot.children) {
                        val mat = matSnap.key ?: continue
                        var tieneRegistros = false
                        for (fechaSnap in matSnap.children) {
                            val fecha = fechaSnap.key ?: continue
                            val aSnap = fechaSnap.child(matricula)
                            if (aSnap.exists()) {
                                tieneRegistros = true
                                todosLosRegistros.add(RegistroDetalle(
                                    fecha        = fecha,
                                    materia      = mat,
                                    hora         = aSnap.child("hora").value?.toString() ?: "--:--",
                                    estado       = aSnap.child("estado").value?.toString() ?: "asistencia",
                                    justificante = aSnap.child("justificante").value == true,
                                    nota         = aSnap.child("nota").value?.toString() ?: "",
                                    matricula    = matricula,
                                    grupo        = grupo
                                ))
                            }
                        }
                        if (tieneRegistros && !materiasDisponibles.contains(mat)) materiasDisponibles.add(mat)
                    }

                    materiasDisponibles.sort()
                    configurarSpinnerMaterias()
                    filtrarPorMateria(null)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@DetalleAsistenciasActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun formatearFecha(f: String) =
        try { val p = f.split("-"); "${p[2]}/${p[1]}/${p[0]}" } catch (_: Exception) { f }
}