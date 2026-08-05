package com.nexusautomation.nfcheck

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RegistroDetalleAdapter(
    private val lista: List<DetalleAsistenciasActivity.RegistroDetalle>,
    private val onToque: (DetalleAsistenciasActivity.RegistroDetalle) -> Unit
) : RecyclerView.Adapter<RegistroDetalleAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtFecha:       TextView = view.findViewById(R.id.txtFechaRegistro)
        val txtMateria:     TextView = view.findViewById(R.id.txtMateriaRegistro)
        val txtHora:        TextView = view.findViewById(R.id.txtHoraRegistro)
        val txtEstado:      TextView = view.findViewById(R.id.txtEstadoRegistro)
        val txtJustificante: TextView = view.findViewById(R.id.txtJustificanteRegistro)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_registro_detalle, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val r = lista[position]

        holder.txtFecha.text   = formatearFecha(r.fecha)
        holder.txtMateria.text = r.materia
        holder.txtHora.text    = r.hora

        // Color por estado
        val (estadoTexto, estadoColor) = when (r.estado) {
            "asistencia" -> "✔ Asistencia" to "#16A34A"
            "retardo"    -> "⏰ Retardo"   to "#D97706"
            "falta"      -> "✖ Falta"      to "#DC2626"
            else         -> r.estado       to "#6B7280"
        }
        holder.txtEstado.text = estadoTexto
        holder.txtEstado.setTextColor(Color.parseColor(estadoColor))

        // Muestra badge de justificante si aplica
        if (r.justificante) {
            holder.txtJustificante.visibility = View.VISIBLE
            holder.txtJustificante.text = if (r.nota.isNotEmpty()) "📋 ${r.nota}" else "📋 Justificado"
        } else {
            holder.txtJustificante.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onToque(r) }
    }

    override fun getItemCount() = lista.size

    private fun formatearFecha(fecha: String): String {
        return try { val p = fecha.split("-"); "${p[2]}/${p[1]}/${p[0]}" }
        catch (_: Exception) { fecha }
    }
}