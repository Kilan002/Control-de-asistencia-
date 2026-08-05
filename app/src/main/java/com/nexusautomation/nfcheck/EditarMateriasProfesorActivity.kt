package com.nexusautomation.nfcheck

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class EditarMateriasProfesorActivity : AppCompatActivity() {

    private lateinit var layoutMaterias: LinearLayout

    // ✅ CORREGIDO: lista completa y consistente con HorarioManager
    // El original tenía solo 3 materias genéricas que no coincidían con el horario real
    private val materiasDisponibles = listOf(
        "PROYECTO INTEGRADOR II",
        "FUNDAMENTOS DE PROGRAMACION",
        "INGLES V",
        "ECUACIONES DIFERENCIALES",
        "MANTENIMIENTO A SISTEMAS ROBOT",
        "LEAD",
        "INTRODUCCION A SISTEMAS DE VISION"
    )

    private var uidProfesor: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_materias_profesor)

        layoutMaterias = findViewById(R.id.layoutMaterias)

        // ✅ CORREGIDO: manejo seguro si el intent no trae el uid
        uidProfesor = intent.getStringExtra("uidProfesor") ?: run {
            Toast.makeText(this, "Error: UID de profesor no recibido", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        cargarMateriasActuales()

        findViewById<Button>(R.id.btnGuardar).setOnClickListener {
            guardarMaterias()
        }
    }

    private fun cargarMateriasActuales() {
        FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child(uidProfesor)
            .child("materiasAsignadas")
            .get()
            .addOnSuccessListener { snapshot ->

                layoutMaterias.removeAllViews()

                for (materia in materiasDisponibles) {
                    val checkBox = CheckBox(this).apply {
                        text      = materia
                        isChecked = snapshot.hasChild(materia)
                        textSize  = 15f
                        setPadding(8, 12, 8, 12)
                    }
                    layoutMaterias.addView(checkBox)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error cargando materias: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun guardarMaterias() {

        val nuevasMaterias = mutableMapOf<String, Boolean>()

        for (i in 0 until layoutMaterias.childCount) {
            val checkBox = layoutMaterias.getChildAt(i) as? CheckBox ?: continue
            if (checkBox.isChecked) {
                nuevasMaterias[checkBox.text.toString()] = true
            }
        }

        if (nuevasMaterias.isEmpty()) {
            Toast.makeText(this, "Selecciona al menos una materia", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child(uidProfesor)
            .child("materiasAsignadas")
            .setValue(nuevasMaterias)
            .addOnSuccessListener {
                Toast.makeText(this, "Materias actualizadas", Toast.LENGTH_SHORT).show()
                finish()
            }
            // ✅ AÑADIDO: manejo de error al guardar
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}