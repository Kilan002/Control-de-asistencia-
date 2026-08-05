package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class PanelAlumnoActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var spinnerMateria: Spinner
    private lateinit var txtBienvenida: TextView
    private lateinit var txtPorcentaje: TextView
    private lateinit var progressBar: ProgressBar

    private val lista = mutableListOf<RegistroAsistenciaAlumno>()
    private lateinit var adapter: AsistenciaAlumnoAdapter

    private var grupoAlumno: String     = ""
    private var matriculaAlumno: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panel_alumno)

        recycler       = findViewById(R.id.recyclerAsistencias)
        spinnerMateria = findViewById(R.id.spinnerMateria)
        txtBienvenida  = findViewById(R.id.txtBienvenidaAlumno)
        txtPorcentaje  = findViewById(R.id.txtPorcentajeAlumno)
        progressBar    = findViewById(R.id.progressAlumno)

        recycler.layoutManager = LinearLayoutManager(this)
        adapter = AsistenciaAlumnoAdapter(lista)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnCerrarSesion).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Sesión inválida", Toast.LENGTH_LONG).show()
            finish(); return
        }

        cargarDatosAlumno(uid)
    }

    private fun cargarDatosAlumno(uid: String) {
        progressBar.visibility = View.VISIBLE

        // ✅ Lee el perfil del alumno desde usuarios/{uid}
        // Aquí tiene permiso porque es su propio nodo (auth.uid == $uid)
        FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uid).get()
            .addOnSuccessListener { snap ->
                val nombre   = snap.child("nombre").value?.toString()    ?: "Alumno"
                grupoAlumno    = snap.child("grupo").value?.toString()     ?: ""
                matriculaAlumno = snap.child("matricula").value?.toString() ?: ""

                txtBienvenida.text = "Bienvenido, $nombre"

                if (grupoAlumno.isEmpty() || matriculaAlumno.isEmpty()) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Perfil incompleto. Contacta al administrador.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // ✅ Con las reglas nuevas, el alumno puede leer asistencias/{su_grupo}
                // porque root.child('usuarios').child(auth.uid).child('grupo').val() == $grupo
                cargarMaterias()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun cargarMaterias() {
        FirebaseDatabase.getInstance().reference
            .child("asistencias").child(grupoAlumno)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    progressBar.visibility = View.GONE
                    val materias = snap.children.mapNotNull { it.key }.toMutableList()

                    if (materias.isEmpty()) {
                        txtPorcentaje.text = "Sin asistencias registradas aún"
                        return
                    }

                    spinnerMateria.adapter = ArrayAdapter(
                        this@PanelAlumnoActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        materias
                    )
                    spinnerMateria.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                            cargarAsistencias(materias[pos])
                        }
                        override fun onNothingSelected(p: AdapterView<*>) {}
                    }
                    cargarAsistencias(materias[0])
                }

                override fun onCancelled(error: DatabaseError) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@PanelAlumnoActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun cargarAsistencias(materia: String) {
        lista.clear()
        adapter.notifyDataSetChanged()

        FirebaseDatabase.getInstance().reference
            .child("asistencias").child(grupoAlumno).child(materia)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var asistencias = 0; var totalClases = 0

                    for (fechaSnap in snapshot.children) {
                        val fecha = fechaSnap.key ?: continue
                        totalClases++

                        val alumnoSnap = fechaSnap.child(matriculaAlumno)
                        val estado = if (alumnoSnap.exists())
                            alumnoSnap.child("estado").value?.toString() ?: "falta"
                        else "falta"

                        val hora = alumnoSnap.child("hora").value?.toString() ?: "--:--"
                        lista.add(RegistroAsistenciaAlumno(fecha, hora, estado))

                        when (estado) {
                            "asistencia" -> asistencias++
                            "retardo"    -> asistencias++ // cuenta como asistencia
                        }
                    }

                    lista.sortByDescending { it.fecha }
                    adapter.notifyDataSetChanged()

                    val pct = if (totalClases > 0) asistencias.toDouble() / totalClases * 100 else 0.0
                    txtPorcentaje.text = "Asistencia: ${"%.1f".format(pct)}%  ($asistencias/$totalClases clases)"
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@PanelAlumnoActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
    }
}