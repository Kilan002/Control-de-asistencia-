package com.nexusautomation.nfcheck

import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.*

/**
 * BuscadorHelper — versión con soporte para cerrar la lista con el botón Atrás
 * sin forzar una selección.
 *
 * CAMBIO: la Activity que use BuscadorHelper debe llamar a
 *   buscador.onBackPressed()  dentro de su propio onBackPressed() para que
 *   el helper pueda interceptar el evento antes de que la Activity lo procese.
 *
 * Ejemplo en la Activity:
 *   override fun onBackPressed() {
 *       if (buscadorGrupo.cerrarSiAbierto()) return
 *       if (buscadorMateria.cerrarSiAbierto()) return
 *       if (buscadorAlumno.cerrarSiAbierto()) return
 *       super.onBackPressed()
 *   }
 */
class BuscadorHelper(
    private val editText: EditText,
    private val listView: ListView,
    private val txtSeleccionado: TextView,
    private val btnLimpiar: View,
    opciones: List<String>,
    private val prefixBadge: String = "",
    private val onSeleccion: (String) -> Unit,
    private val onLimpiado: (() -> Unit)? = null
) {
    private val opcionesActuales = mutableListOf<String>().also { it.addAll(opciones) }
    private val filtradas = mutableListOf<String>().also { it.addAll(opciones) }
    private val adapter: ArrayAdapter<String>

    init {
        adapter = ArrayAdapter(editText.context, android.R.layout.simple_list_item_1, filtradas)
        listView.adapter = adapter

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                filtrar(editText.text.toString())
                listView.visibility = if (filtradas.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrar(s.toString())
                listView.visibility = if (filtradas.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })

        // ✅ Tecla Atrás en el teclado también cierra la lista
        editText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (listView.visibility == View.VISIBLE) {
                    cerrarLista()
                    return@setOnKeyListener true
                }
            }
            false
        }

        listView.setOnItemClickListener { _, _, pos, _ ->
            if (pos < filtradas.size) seleccionar(filtradas[pos])
        }

        btnLimpiar.setOnClickListener {
            limpiar()
            onLimpiado?.invoke()
        }

        txtSeleccionado.visibility = View.GONE
        btnLimpiar.visibility      = View.GONE
        listView.visibility        = View.GONE
    }

    private fun filtrar(query: String) {
        filtradas.clear()
        val q = query.uppercase()
        filtradas.addAll(opcionesActuales.filter { it.uppercase().contains(q) })
        adapter.notifyDataSetChanged()
    }

    fun seleccionar(valor: String) {
        val texto = if (prefixBadge.isNotEmpty()) "$prefixBadge $valor" else valor
        txtSeleccionado.text       = texto
        txtSeleccionado.visibility = View.VISIBLE
        btnLimpiar.visibility      = View.VISIBLE
        editText.text.clear()
        editText.clearFocus()
        listView.visibility = View.GONE
        onSeleccion(valor)
    }

    fun limpiar() {
        txtSeleccionado.visibility = View.GONE
        btnLimpiar.visibility      = View.GONE
        editText.text.clear()
        listView.visibility = View.GONE
    }

    fun actualizarOpciones(nuevasOpciones: List<String>) {
        opcionesActuales.clear()
        opcionesActuales.addAll(nuevasOpciones)
        filtrar(editText.text.toString())
    }

    /**
     * Llama esto desde onBackPressed() de la Activity.
     * Retorna true si la lista estaba abierta y la cerró (consume el evento).
     * Retorna false si la lista ya estaba cerrada (deja que la Activity lo procese).
     */
    fun cerrarSiAbierto(): Boolean {
        return if (listView.visibility == View.VISIBLE) {
            cerrarLista()
            true
        } else {
            false
        }
    }

    private fun cerrarLista() {
        listView.visibility = View.GONE
        editText.clearFocus()
    }
}