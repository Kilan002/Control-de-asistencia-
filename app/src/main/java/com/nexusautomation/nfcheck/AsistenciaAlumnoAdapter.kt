package com.nexusautomation.nfcheck

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// ✅ CORREGIDO: usa RegistroAsistenciaAlumno con package correcto
// El original hacía `import RegistroAsistenciaAlumno` sin package (error de compilación)
class AsistenciaAlumnoAdapter(
    private var lista: MutableList<RegistroAsistenciaAlumno>
) : RecyclerView.Adapter<AsistenciaAlumnoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fecha:  TextView = view.findViewById(R.id.textFecha)
        val hora:   TextView = view.findViewById(R.id.textHora)
        val estado: TextView = view.findViewById(R.id.textEstado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_asistencia_alumno, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]

        // ✅ Fecha formateada: "2026-03-10" → "10/03/2026"
        holder.fecha.text  = formatearFecha(item.fecha)
        holder.hora.text   = item.hora

        holder.estado.text = when (item.estado) {
            "asistencia" -> "✔ Asistencia"
            "retardo"    -> "⏰ Retardo"
            "falta"      -> "❌ Falta"
            else         -> item.estado
        }

        // ✅ Color por estado para lectura rápida
        holder.estado.setTextColor(
            when (item.estado) {
                "asistencia" -> Color.parseColor("#16A34A")
                "retardo"    -> Color.parseColor("#D97706")
                else         -> Color.parseColor("#DC2626")
            }
        )
    }

    override fun getItemCount() = lista.size

    fun actualizarLista(nuevaLista: List<RegistroAsistenciaAlumno>) {
        lista.clear()
        lista.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    private fun formatearFecha(fecha: String): String {
        return try {
            val p = fecha.split("-")
            "${p[2]}/${p[1]}/${p[0]}"
        } catch (e: Exception) { fecha }
    }
}