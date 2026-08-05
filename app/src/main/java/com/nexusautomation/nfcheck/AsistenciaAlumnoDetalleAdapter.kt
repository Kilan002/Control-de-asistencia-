package com.nexusautomation.nfcheck

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AsistenciaAlumnoDetalleAdapter(
    private val lista: MutableList<String>
) : RecyclerView.Adapter<AsistenciaAlumnoDetalleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textFecha: TextView = view.findViewById(R.id.textFecha)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fecha_asistencia, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textFecha.text = lista[position]
    }

    override fun getItemCount(): Int = lista.size
}