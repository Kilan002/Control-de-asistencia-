package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class VerHorarioActivity : AppCompatActivity() {

    private lateinit var tableLayout: TableLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var txtVacio: TextView
    private lateinit var btnConfigurarHorario: View

    private val dias  = listOf("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES")
    private val horas = listOf(7, 8, 9, 10, 11, 12, 13, 14)

    // Si viene de GestionActivity con un profesor específico
    private var uidProfesorVer: String = ""
    private var esAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verhorario)

        tableLayout          = findViewById(R.id.tableHorario)
        progressBar          = findViewById(R.id.progressHorario)
        txtVacio             = findViewById(R.id.txtHorarioVacio)
        btnConfigurarHorario = findViewById(R.id.btnConfigurarHorario)

        // Puede venir de GestionActivity con el uid del profesor
        uidProfesorVer = intent.getStringExtra("uidProfesor") ?: ""
        esAdmin        = intent.getBooleanExtra("esAdmin", false)

        // ✅ Si es admin, muestra botón para configurar horario
        if (esAdmin || uidProfesorVer.isNotEmpty()) {
            btnConfigurarHorario.visibility = View.VISIBLE
            btnConfigurarHorario.setOnClickListener {
                val i = Intent(this, HorarioProfesorActivity::class.java)
                if (uidProfesorVer.isNotEmpty()) {
                    i.putExtra("uidPreseleccionado", uidProfesorVer)
                }
                startActivity(i)
            }
        } else {
            btnConfigurarHorario.visibility = View.GONE
        }

        val uid = if (uidProfesorVer.isNotEmpty()) uidProfesorVer
        else FirebaseAuth.getInstance().currentUser?.uid ?: return

        cargarHorario(uid)
    }

    private fun cargarHorario(uid: String) {
        progressBar.visibility = View.VISIBLE

        FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uid).child("horario")
            .get()
            .addOnSuccessListener { snap ->
                progressBar.visibility = View.GONE
                if (!snap.exists()) { txtVacio.visibility = View.VISIBLE; return@addOnSuccessListener }
                construirTabla(snap)
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                txtVacio.visibility    = View.VISIBLE
                txtVacio.text          = "Error cargando horario"
            }
    }

    private fun construirTabla(horarioSnap: com.google.firebase.database.DataSnapshot) {
        val celdas = Array(horas.size) { arrayOfNulls<String>(dias.size) }

        for (diaSnap in horarioSnap.children) {
            val dia    = diaSnap.key ?: continue
            val colIdx = dias.indexOf(dia.uppercase())
            if (colIdx < 0) continue

            for (bloqueSnap in diaSnap.children) {
                val materia    = bloqueSnap.child("materia").value?.toString()                  ?: continue
                val horaInicio = (bloqueSnap.child("horaInicio").value as? Number)?.toInt()    ?: continue
                val horaFin    = (bloqueSnap.child("horaFin").value as? Number)?.toInt()       ?: continue
                val grupo      = bloqueSnap.child("grupo").value?.toString()                   ?: ""

                for (h in horaInicio until horaFin) {
                    val rowIdx = horas.indexOf(h)
                    if (rowIdx >= 0) celdas[rowIdx][colIdx] = "${materiaCorta(materia)}\n$grupo"
                }
            }
        }

        tableLayout.removeAllViews()

        // Encabezado
        val headerRow = TableRow(this)
        headerRow.addView(celdaTexto("", bgColor = "#1E40AF", textColor = "#FFFFFF", isBold = true))
        for (dia in dias) {
            headerRow.addView(celdaTexto(dia.take(3), bgColor = "#1E40AF", textColor = "#FFFFFF", isBold = true))
        }
        tableLayout.addView(headerRow)

        // Filas de horas
        for ((rowIdx, hora) in horas.withIndex()) {
            val row = TableRow(this)
            row.addView(celdaTexto("$hora:00", bgColor = "#F0F4FF", isBold = true))
            for (colIdx in dias.indices) {
                val contenido = celdas[rowIdx][colIdx]
                row.addView(celdaTexto(contenido ?: "", bgColor = if (contenido != null) colorMateria(contenido) else "#FFFFFF"))
            }
            tableLayout.addView(row)
        }
    }

    private fun celdaTexto(texto: String, bgColor: String = "#FFFFFF", textColor: String = "#1A1A2E", isBold: Boolean = false): TextView {
        return TextView(this).apply {
            text      = texto
            textSize  = 10f
            gravity   = Gravity.CENTER
            setPadding(6, 8, 6, 8)
            setTextColor(android.graphics.Color.parseColor(textColor))
            setBackgroundColor(android.graphics.Color.parseColor(bgColor))
            if (isBold) setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(1, 1, 1, 1) }
        }
    }

    private fun colorMateria(texto: String): String = when {
        texto.contains("PROY",   true) -> "#E8D5F5"
        texto.contains("FUND",   true) -> "#FFF9C4"
        texto.contains("INGLES", true) -> "#C8E6C9"
        texto.contains("EC",     true) -> "#FFE0B2"
        texto.contains("MANT",   true) -> "#FFCCBC"
        texto.contains("LEAD",   true) -> "#B3E5FC"
        texto.contains("INT",    true) -> "#C5E1A5"
        else                           -> "#F3F4F6"
    }

    private fun materiaCorta(m: String): String = when {
        m.contains("PROYECTO",    true) -> "Proy Int II"
        m.contains("FUNDAMENTOS", true) -> "Fund Prog"
        m.contains("INGLES",      true) -> "Inglés V"
        m.contains("ECUACIONES",  true) -> "Ec Dif"
        m.contains("MANTENIMIENTO",true)-> "Mant Rob"
        m.contains("LEAD",        true) -> "LEAD"
        m.contains("INTRODUCCION",true) -> "Int Visión"
        else -> m.take(12)
    }
}