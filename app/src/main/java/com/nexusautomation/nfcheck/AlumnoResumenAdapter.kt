package com.nexusautomation.nfcheck

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlumnoResumenAdapter(
    private var lista: MutableList<AlumnoResumen>,
    private val onClick: (AlumnoResumen) -> Unit
) : RecyclerView.Adapter<AlumnoResumenAdapter.ViewHolder>() {

    companion object {
        private const val UMBRAL_RIESGO_PORCENTAJE = 70
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombre: TextView = view.findViewById(R.id.itemNombre)
        val stats: TextView  = view.findViewById(R.id.itemStats)
        val riesgo: TextView = view.findViewById(R.id.itemRiesgo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alumno_resumen, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alumno = lista[position]

        holder.nombre.text = alumno.nombre
        holder.stats.text  = "✔ ${alumno.asistencias}   ❌ ${alumno.faltas}   ⏰ ${alumno.retardos}"

        if (calcularRiesgo(alumno)) {
            holder.riesgo.visibility = View.VISIBLE
            holder.riesgo.text = "⚠ Riesgo de reprobar"
        } else {
            holder.riesgo.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(alumno) }
    }

    fun actualizarLista(nuevaLista: List<AlumnoResumen>) {
        lista.clear()
        lista.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    // ✅ CORREGIDO: incluye retardos en el cálculo (como asistencia parcial)
    // El original solo dividía asistencias / total, ignorando retardos
    private fun calcularRiesgo(alumno: AlumnoResumen): Boolean {
        val total = alumno.asistencias + alumno.faltas + alumno.retardos
        if (total == 0) return false
        val porcentaje = (alumno.asistencias + alumno.retardos * 0.5) / total * 100
        return porcentaje < UMBRAL_RIESGO_PORCENTAJE
    }
}