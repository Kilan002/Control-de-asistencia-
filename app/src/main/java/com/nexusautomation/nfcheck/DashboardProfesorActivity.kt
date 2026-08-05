package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class DashboardProfesorActivity : AppCompatActivity() {

    private lateinit var spinnerGrupo:     Spinner
    private lateinit var spinnerMateria:   Spinner
    private lateinit var txtTotalClases:   TextView
    private lateinit var txtPromedioAsist: TextView
    private lateinit var txtAlumnosRiesgo: TextView
    private lateinit var txtAlumnosTotal:  TextView
    private lateinit var layoutFilas:      LinearLayout
    private lateinit var progressBar:      ProgressBar
    private lateinit var btnExportar:      Button
    private lateinit var txtVacio:         TextView

    private val uid     = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var esAdmin = false

    private val gruposDisponibles   = mutableListOf<String>()
    private val materiasDisponibles = mutableListOf<String>()
    private var grupoActual:   String = ""
    private var materiaActual: String = ""

    data class FilaAlumno(
        val nombre:      String,
        val matricula:   String,
        val asistencias: Int,
        val retardos:    Int,
        val faltas:      Int,
        val totalClases: Int
    ) {
        val porcentaje: Double
            get() = if (totalClases > 0) (asistencias + retardos).toDouble() / totalClases * 100.0 else 0.0
        val enRiesgo: Boolean get() = porcentaje < 80.0
    }

    private val filas = mutableListOf<FilaAlumno>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_profesor)

        esAdmin          = intent.getBooleanExtra("esAdmin", false)
        spinnerGrupo     = findViewById(R.id.spinnerGrupoDash)
        spinnerMateria   = findViewById(R.id.spinnerMateriaDash)
        txtTotalClases   = findViewById(R.id.txtDashTotalClases)
        txtPromedioAsist = findViewById(R.id.txtDashPromedio)
        txtAlumnosRiesgo = findViewById(R.id.txtDashRiesgo)
        txtAlumnosTotal  = findViewById(R.id.txtDashTotalAlumnos)
        layoutFilas      = findViewById(R.id.layoutFilasAlumnos)
        progressBar      = findViewById(R.id.progressDash)
        btnExportar      = findViewById(R.id.btnExportarExcel)
        txtVacio         = findViewById(R.id.txtDashVacio)

        btnExportar.isEnabled = false
        btnExportar.setOnClickListener { exportarExcel() }
        cargarGrupos()
    }

    private fun cargarGrupos() {
        progressBar.visibility = View.VISIBLE
        if (esAdmin) {
            poblarSpinnerGrupos(DatosUniversidad.grupos)
        } else {
            FirebaseDatabase.getInstance().reference.child("usuarios").child(uid).get()
                .addOnSuccessListener { snap ->
                    poblarSpinnerGrupos(
                        snap.child("gruposAsignados").children.mapNotNull { it.key }.sorted()
                    )
                }
                .addOnFailureListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error cargando grupos", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun poblarSpinnerGrupos(grupos: List<String>) {
        gruposDisponibles.clear()
        gruposDisponibles.addAll(grupos)
        spinnerGrupo.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gruposDisponibles)
        spinnerGrupo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                grupoActual = gruposDisponibles[pos]; cargarMaterias(grupoActual)
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        if (gruposDisponibles.isNotEmpty()) { grupoActual = gruposDisponibles[0]; cargarMaterias(grupoActual) }
        else { progressBar.visibility = View.GONE; txtVacio.text = "No tienes grupos asignados"; txtVacio.visibility = View.VISIBLE }
    }

    private fun cargarMaterias(grupo: String) {
        FirebaseDatabase.getInstance().reference.child("asistencias").child(grupo).get()
            .addOnSuccessListener { snap ->
                val materias = snap.children.mapNotNull { it.key }.sorted().toMutableList()
                if (materias.isEmpty()) materias.addAll(DatosUniversidad.materias)
                materiasDisponibles.clear(); materiasDisponibles.addAll(materias)
                spinnerMateria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, materiasDisponibles)
                spinnerMateria.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                        materiaActual = materiasDisponibles[pos]; cargarDatos()
                    }
                    override fun onNothingSelected(p: AdapterView<*>) {}
                }
                if (materiasDisponibles.isNotEmpty()) { materiaActual = materiasDisponibles[0]; cargarDatos() }
            }
            .addOnFailureListener { progressBar.visibility = View.GONE }
    }

    private fun cargarDatos() {
        progressBar.visibility = View.VISIBLE; txtVacio.visibility = View.GONE
        filas.clear(); layoutFilas.removeAllViews(); btnExportar.isEnabled = false

        FirebaseDatabase.getInstance().reference.child("alumnos").child(grupoActual).get()
            .addOnSuccessListener { alumnosSnap ->
                val nombresMap = mutableMapOf<String, String>()
                for (s in alumnosSnap.children) {
                    nombresMap[s.key ?: continue] = s.child("nombre").value?.toString() ?: continue
                }
                if (nombresMap.isEmpty()) {
                    progressBar.visibility = View.GONE
                    txtVacio.text = "No hay alumnos en $grupoActual"; txtVacio.visibility = View.VISIBLE; return@addOnSuccessListener
                }
                FirebaseDatabase.getInstance().reference
                    .child("asistencias").child(grupoActual).child(materiaActual).get()
                    .addOnSuccessListener { asistSnap ->
                        val contadores = mutableMapOf<String, Triple<Int, Int, Int>>()
                        var totalClases = 0
                        for (fechaSnap in asistSnap.children) {
                            totalClases++
                            for (alumnoSnap in fechaSnap.children) {
                                val mat = alumnoSnap.key ?: continue
                                val prev = contadores[mat] ?: Triple(0, 0, 0)
                                contadores[mat] = when (alumnoSnap.child("estado").value?.toString()) {
                                    "asistencia" -> Triple(prev.first + 1, prev.second, prev.third)
                                    "retardo"    -> Triple(prev.first, prev.second + 1, prev.third)
                                    else         -> Triple(prev.first, prev.second, prev.third + 1)
                                }
                            }
                        }
                        for ((mat, nombre) in nombresMap) {
                            val c = contadores[mat] ?: Triple(0, 0, 0)
                            filas.add(FilaAlumno(nombre, mat, c.first, c.second, c.third, totalClases))
                        }
                        filas.sortBy { it.nombre }
                        progressBar.visibility = View.GONE
                        mostrarDatos(totalClases)
                    }
                    .addOnFailureListener { progressBar.visibility = View.GONE; Toast.makeText(this, "Error cargando asistencias", Toast.LENGTH_SHORT).show() }
            }
            .addOnFailureListener { progressBar.visibility = View.GONE; Toast.makeText(this, "Error cargando alumnos", Toast.LENGTH_SHORT).show() }
    }

    private fun mostrarDatos(totalClases: Int) {
        if (filas.isEmpty()) { txtVacio.text = "Sin asistencias para $materiaActual"; txtVacio.visibility = View.VISIBLE; return }
        txtTotalClases.text   = "$totalClases"
        txtAlumnosTotal.text  = "${filas.size}"
        txtAlumnosRiesgo.text = "${filas.count { it.enRiesgo }}"
        txtPromedioAsist.text = "${"%.1f".format(filas.map { it.porcentaje }.average())}%"
        layoutFilas.removeAllViews()
        for (fila in filas) {
            val row = layoutInflater.inflate(R.layout.item_dashboardalumno, layoutFilas, false)
            row.findViewById<TextView>(R.id.txtDashNombre).text    = fila.nombre
            row.findViewById<TextView>(R.id.txtDashMatricula).text = fila.matricula
            row.findViewById<TextView>(R.id.txtDashAsist).text     = "${fila.asistencias}"
            row.findViewById<TextView>(R.id.txtDashRet).text       = "${fila.retardos}"
            row.findViewById<TextView>(R.id.txtDashFalta).text     = "${fila.faltas}"
            val pctView = row.findViewById<TextView>(R.id.txtDashPct)
            pctView.text = "${"%.1f".format(fila.porcentaje)}%"
            if (fila.enRiesgo) { row.setBackgroundColor(0x1FDC2626.toInt()); pctView.setTextColor(0xFFDC2626.toInt()) }
            layoutFilas.addView(row)
        }
        btnExportar.isEnabled = true
    }

    private fun exportarExcel() {
        if (filas.isEmpty()) { Toast.makeText(this, "No hay datos para exportar", Toast.LENGTH_SHORT).show(); return }
        try {
            val wb = XSSFWorkbook()

            // Hoja 1: Resumen
            val resumen = wb.createSheet("Resumen")
            listOf(
                listOf("Grupo", grupoActual),
                listOf("Materia", materiaActual),
                listOf("Fecha", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())),
                listOf(""),
                listOf("Clases impartidas", filas.firstOrNull()?.totalClases?.toString() ?: "0"),
                listOf("Total alumnos", filas.size.toString()),
                listOf("En riesgo (<80%)", filas.count { it.enRiesgo }.toString()),
                listOf("Promedio asistencia", "${"%.1f".format(filas.map { it.porcentaje }.average())}%")
            ).forEachIndexed { i, row -> val r = resumen.createRow(i); row.forEachIndexed { j, v -> r.createCell(j).setCellValue(v) } }

            // Hoja 2: Detalle
            val detalle = wb.createSheet("Asistencias")
            val headStyle = wb.createCellStyle().apply {
                fillForegroundColor = IndexedColors.ROYAL_BLUE.index; fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER; verticalAlignment = VerticalAlignment.CENTER
                setFont(wb.createFont().apply { bold = true; color = IndexedColors.WHITE.index; fontHeightInPoints = 11 })
            }
            val riesgoStyle = wb.createCellStyle().apply { fillForegroundColor = IndexedColors.ROSE.index;        fillPattern = FillPatternType.SOLID_FOREGROUND }
            val bienStyle   = wb.createCellStyle().apply { fillForegroundColor = IndexedColors.LIGHT_GREEN.index; fillPattern = FillPatternType.SOLID_FOREGROUND }

            val headers = listOf("Matrícula", "Nombre", "Asistencias", "Retardos", "Faltas", "Total clases", "% Asistencia", "Estado")
            detalle.createRow(0).also { it.heightInPoints = 20f }.let { r ->
                headers.forEachIndexed { j, h -> r.createCell(j).apply { setCellValue(h); cellStyle = headStyle } }
            }
            filas.forEachIndexed { i, f ->
                detalle.createRow(i + 1).let { r ->
                    r.createCell(0).setCellValue(f.matricula)
                    r.createCell(1).setCellValue(f.nombre)
                    r.createCell(2).setCellValue(f.asistencias.toDouble())
                    r.createCell(3).setCellValue(f.retardos.toDouble())
                    r.createCell(4).setCellValue(f.faltas.toDouble())
                    r.createCell(5).setCellValue(f.totalClases.toDouble())
                    r.createCell(6).setCellValue("${"%.1f".format(f.porcentaje)}%")
                    r.createCell(7).apply {
                        if (f.enRiesgo) { setCellValue("EN RIESGO"); cellStyle = riesgoStyle }
                        else            { setCellValue("REGULAR");    cellStyle = bienStyle   }
                    }
                }
            }
            for (j in headers.indices) detalle.autoSizeColumn(j)

            val ts      = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val nombre  = "Asistencia_${grupoActual}_${materiaActual.take(10)}_$ts.xlsx".replace(" ", "_").replace("/", "-")
            val dir     = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
            dir.mkdirs()
            val archivo = File(dir, nombre)
            FileOutputStream(archivo).use { wb.write(it) }
            wb.close()

            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivo)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Asistencias $grupoActual — $materiaActual")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Exportar asistencias"
            ))
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}