package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SeleccionarMateriaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seleccionar_materia)

        val listView = findViewById<ListView>(R.id.listMaterias)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Sesión inválida", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val db = FirebaseDatabase.getInstance().reference

        // ✅ CORREGIDO: el alumno está en usuarios/{uid}, no en alumnos/{uid}
        // El original buscaba alumnos/{uid}/grupo pero el UID de Auth no es la matrícula
        // El grupo del alumno está en usuarios/{uid}/grupo (guardado en CrearAlumnoActivity)
        db.child("usuarios").child(uid)
            .get()
            .addOnSuccessListener { userSnap ->

                val grupo     = userSnap.child("grupo").value?.toString() ?: run {
                    Toast.makeText(this, "Grupo no asignado", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val matricula = userSnap.child("matricula").value?.toString() ?: ""

                // ✅ CORREGIDO: las materias se derivan de asistencias/{grupo}
                // El original buscaba "materias_por_grupo" que no existe en la estructura Firebase
                db.child("asistencias").child(grupo)
                    .get()
                    .addOnSuccessListener { asistSnap ->

                        val materias = asistSnap.children
                            .mapNotNull { it.key }
                            .toMutableList()

                        if (materias.isEmpty()) {
                            Toast.makeText(this, "Sin materias registradas", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        listView.adapter = ArrayAdapter(
                            this,
                            android.R.layout.simple_list_item_1,
                            materias
                        )

                        listView.setOnItemClickListener { _, _, pos, _ ->
                            val materia = materias[pos]
                            startActivity(
                                Intent(this, DetalleAsistenciasActivity::class.java)
                                    .putExtra("materia",   materia)
                                    .putExtra("grupo",     grupo)
                                    // ✅ AÑADIDO: pasa la matrícula para filtrar el historial
                                    .putExtra("matricula", matricula)
                                    .putExtra("nombre",    userSnap.child("nombre").value?.toString() ?: "")
                            )
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error cargando materias: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error cargando perfil: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}