package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class PanelAdminActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panel_admin)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("usuarios").child(uid).child("nombre").get()
            .addOnSuccessListener { snap ->
                findViewById<TextView>(R.id.txtAdminNombre).text =
                    "Bienvenido, ${snap.value?.toString() ?: "Admin"}"
            }

        // ✅ Nuevo botón — abre GestionActivity (búsqueda de alumnos y profesores)
        findViewById<Button>(R.id.btnGestion).setOnClickListener {
            startActivity(Intent(this, GestionActivity::class.java))
        }

        findViewById<Button>(R.id.btnCrearProfesor).setOnClickListener {
            startActivity(Intent(this, CrearProfesorActivity::class.java))
        }

        findViewById<Button>(R.id.btnCrearAlumno).setOnClickListener {
            startActivity(Intent(this, CrearAlumnoActivity::class.java))
        }

        findViewById<Button>(R.id.btnConfigurarHorarios).setOnClickListener {
            startActivity(Intent(this, HorarioProfesorActivity::class.java))
        }

        findViewById<Button>(R.id.btnTomarAsistencia).setOnClickListener {
            startActivity(Intent(this, PanelProfesorActivity::class.java)
                .putExtra("esAdmin", true))
        }

        findViewById<Button>(R.id.btnConsultarAsistencias).setOnClickListener {
            startActivity(Intent(this, VerAsistenciasActivity::class.java)
                .putExtra("esAdmin", true))
        }

        findViewById<Button>(R.id.btnCerrarSesion).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }
}