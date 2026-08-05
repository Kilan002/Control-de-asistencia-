package com.nexusautomation.nfcheck

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var etCorreo: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var txtBloqueo: TextView

    private val MAX_INTENTOS      = 3
    private val BLOQUEO_MS        = 30_000L
    private val PREFS_NAME        = "login_prefs"
    private val KEY_INTENTOS      = "intentos_fallidos"
    private val KEY_BLOQUEO_HASTA = "bloqueado_hasta"

    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val usuarioActual = FirebaseAuth.getInstance().currentUser
        if (usuarioActual != null) {
            redirigirSegunRol(usuarioActual.uid)
            return
        }

        setContentView(R.layout.activity_login)
        etCorreo   = findViewById(R.id.etCorreo)
        etPassword = findViewById(R.id.etPassword)
        btnLogin   = findViewById(R.id.btnLogin)
        txtBloqueo = findViewById(R.id.txtBloqueo)

        verificarBloqueoActivo()

        btnLogin.setOnClickListener {
            if (estaActualmenteBloqueado()) return@setOnClickListener
            val correo   = etCorreo.text.toString().trim()
            val password = etPassword.text.toString()
            if (correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "Contraseña mínimo 6 caracteres", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            btnLogin.isEnabled = false
            iniciarSesion(correo, password)
        }
    }

    override fun onDestroy() { super.onDestroy(); countDownTimer?.cancel() }

    private fun getPrefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private fun estaActualmenteBloqueado() =
        System.currentTimeMillis() < getPrefs().getLong(KEY_BLOQUEO_HASTA, 0L)

    private fun verificarBloqueoActivo() {
        val bloqueadoHasta = getPrefs().getLong(KEY_BLOQUEO_HASTA, 0L)
        val ahora = System.currentTimeMillis()
        if (ahora < bloqueadoHasta) {
            btnLogin.isEnabled    = false
            txtBloqueo.visibility = View.VISIBLE
            iniciarCountDown(bloqueadoHasta - ahora)
        }
    }

    private fun registrarIntentoFallido() {
        val intentos = getPrefs().getInt(KEY_INTENTOS, 0) + 1
        getPrefs().edit().putInt(KEY_INTENTOS, intentos).apply()
        if (intentos >= MAX_INTENTOS) activarBloqueo()
        else Toast.makeText(this, "Credenciales incorrectas. Intentos restantes: ${MAX_INTENTOS - intentos}", Toast.LENGTH_LONG).show()
    }

    private fun activarBloqueo() {
        getPrefs().edit()
            .putLong(KEY_BLOQUEO_HASTA, System.currentTimeMillis() + BLOQUEO_MS)
            .putInt(KEY_INTENTOS, 0).apply()
        btnLogin.isEnabled    = false
        txtBloqueo.visibility = View.VISIBLE
        iniciarCountDown(BLOQUEO_MS)
    }

    private fun iniciarCountDown(duracionMs: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(duracionMs, 1_000) {
            override fun onTick(ms: Long) { txtBloqueo.text = "Demasiados intentos. Espera ${ms / 1000}s" }
            override fun onFinish() {
                getPrefs().edit().putLong(KEY_BLOQUEO_HASTA, 0L).apply()
                btnLogin.isEnabled    = true
                txtBloqueo.visibility = View.GONE
            }
        }.start()
    }

    private fun iniciarSesion(correo: String, password: String) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(correo, password)
            .addOnSuccessListener { result ->
                getPrefs().edit().putInt(KEY_INTENTOS, 0).apply()
                result.user?.let { user ->
                    redirigirSegunRol(user.uid)
                } ?: run {
                    btnLogin.isEnabled = true
                    Toast.makeText(this, "Error de autenticación", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { btnLogin.isEnabled = true; registrarIntentoFallido() }
    }

    private fun redirigirSegunRol(uid: String) {
        FirebaseDatabase.getInstance().reference.child("usuarios").child(uid).get()
            .addOnSuccessListener { snap ->
                val rol = snap.child("rol").value?.toString()
                when (rol) {
                    "admin"    -> startActivity(Intent(this, PanelAdminActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    "profesor" -> HorarioManager.cargarDesdeFirebase(uid) {
                        startActivity(Intent(this, PanelProfesorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }
                    "alumno"   -> startActivity(Intent(this, PanelAlumnoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    else -> {
                        FirebaseAuth.getInstance().signOut()
                        val msg = if (rol == null) "Perfil no encontrado (UID: $uid)" else "Rol desconocido: '$rol'"
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                }
            }
            .addOnFailureListener { e ->
                if (::btnLogin.isInitialized) btnLogin.isEnabled = true
                Toast.makeText(this, "Error al leer perfil: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}