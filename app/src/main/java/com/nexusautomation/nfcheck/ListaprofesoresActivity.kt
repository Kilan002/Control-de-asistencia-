package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ListaProfesoresActivity : AppCompatActivity() {

    data class ProfesorItem(
        val uid: String,
        val nombre: String,
        val correo: String,
        val grupos: List<String>,
        val materias: List<String>,
        val horario: Map<String, List<String>> // dia → ["MATERIA (7:00-9:00)", ...]
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtVacio: TextView

    private val listaProfesores = mutableListOf<ProfesorItem>()
    private lateinit var adapter: ProfesorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_listaprofesores)

        recyclerView = findViewById(R.id.recyclerProfesores)
        progressBar  = findViewById(R.id.progressProfesores)
        txtVacio     = findViewById(R.id.txtVacioProfesores)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ProfesorAdapter(listaProfesores)
        recyclerView.adapter = adapter

        cargarProfesores()
    }

    private fun cargarProfesores() {
        progressBar.visibility = View.VISIBLE

        FirebaseDatabase.getInstance().reference.child("usuarios")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    progressBar.visibility = View.GONE
                    listaProfesores.clear()

                    for (snap in snapshot.children) {
                        if (snap.child("rol").value?.toString() != "profesor") continue

                        val uid    = snap.key ?: continue
                        val nombre = snap.child("nombre").value?.toString() ?: ""
                        val correo = snap.child("correo").value?.toString() ?: ""

                        val grupos = snap.child("gruposAsignados").children
                            .mapNotNull { it.key }.sorted()

                        val materias = snap.child("materiasAsignadas").children
                            .mapNotNull { it.key }.sorted()

                        // Convierte el horario a texto legible por día
                        val horario = mutableMapOf<String, MutableList<String>>()
                        for (diaSnap in snap.child("horario").children) {
                            val dia = diaSnap.key ?: continue
                            val bloques = mutableListOf<String>()
                            for (bloqueSnap in diaSnap.children) {
                                val mat    = bloqueSnap.child("materia").value?.toString()                  ?: continue
                                val inicio = (bloqueSnap.child("horaInicio").value as? Number)?.toInt()    ?: continue
                                val fin    = (bloqueSnap.child("horaFin").value as? Number)?.toInt()       ?: continue
                                val grp    = bloqueSnap.child("grupo").value?.toString() ?: ""
                                bloques.add("$mat ($inicio:00–$fin:00) · $grp")
                            }
                            if (bloques.isNotEmpty()) horario[dia] = bloques
                        }

                        listaProfesores.add(ProfesorItem(uid, nombre, correo, grupos, materias, horario))
                    }

                    listaProfesores.sortBy { it.nombre }
                    adapter.notifyDataSetChanged()

                    txtVacio.visibility = if (listaProfesores.isEmpty()) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: DatabaseError) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ListaProfesoresActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    inner class ProfesorAdapter(private val lista: List<ProfesorItem>) :
        RecyclerView.Adapter<ProfesorAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtNombre:   TextView   = view.findViewById(R.id.txtNombreProfesorItem)
            val txtCorreo:   TextView   = view.findViewById(R.id.txtCorreoProfesorItem)
            val txtGrupos:   TextView   = view.findViewById(R.id.txtGruposProfesorItem)
            val txtMaterias: TextView   = view.findViewById(R.id.txtMateriasProfesorItem)
            val layoutHorario: LinearLayout = view.findViewById(R.id.layoutHorarioProfesorItem)
            val btnEditar:   Button     = view.findViewById(R.id.btnEditarProfesorItem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_profesor, parent, false))

        override fun getItemCount() = lista.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val prof = lista[position]

            holder.txtNombre.text   = prof.nombre
            holder.txtCorreo.text   = prof.correo
            holder.txtGrupos.text   = if (prof.grupos.isEmpty()) "Sin grupos" else prof.grupos.joinToString(", ")
            holder.txtMaterias.text = if (prof.materias.isEmpty()) "Sin materias" else prof.materias.joinToString("\n")

            // Muestra el horario por días
            holder.layoutHorario.removeAllViews()
            val diasOrden = listOf("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES")
            for (dia in diasOrden) {
                val bloques = prof.horario[dia] ?: continue
                val txtDia = TextView(holder.itemView.context).apply {
                    text = dia
                    textSize = 11f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#6B7280"))
                    setPadding(0, 8, 0, 2)
                }
                holder.layoutHorario.addView(txtDia)
                for (bloque in bloques) {
                    val txtBloque = TextView(holder.itemView.context).apply {
                        text = "  • $bloque"
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
                        setPadding(0, 2, 0, 2)
                    }
                    holder.layoutHorario.addView(txtBloque)
                }
            }

            if (prof.horario.isEmpty()) {
                val txtSinHorario = TextView(holder.itemView.context).apply {
                    text = "Sin horario configurado"
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
                }
                holder.layoutHorario.addView(txtSinHorario)
            }

            holder.btnEditar.setOnClickListener {
                // Abre EditarProfesorActivity con el uid del profesor
                holder.itemView.context.startActivity(
                    Intent(holder.itemView.context, EditarProfesorActivity::class.java)
                        .putExtra("uidProfesor", prof.uid)
                        .putExtra("nombreProfesor", prof.nombre)
                )
            }
        }
    }
}