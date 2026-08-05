package com.nexusautomation.nfcheck

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.nexusautomation.nfcheck.NFCManager
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class CrearAlumnoActivity : AppCompatActivity() {

    private lateinit var edtNombre:            EditText
    private lateinit var edtMatricula:         EditText
    private lateinit var edtNfcUid:            EditText
    private lateinit var edtCorreo:            EditText
    private lateinit var edtPassword:          EditText
    private lateinit var edtConfirmarPassword: EditText
    private lateinit var txtNfcEstado:         TextView
    private lateinit var imgAlumno:            ImageView
    private lateinit var btnSeleccionarFoto:   Button
    private lateinit var btnGuardar:           Button
    private lateinit var progressBar:          android.widget.ProgressBar
    private lateinit var etBuscarGrupo:        EditText
    private lateinit var listViewGrupos:       ListView
    private lateinit var txtGrupoSeleccionado: TextView

    private var grupoSeleccionado: String = ""
    private var imagenUri: Uri? = null
    private var userId: String? = null

    private val database  = FirebaseDatabase.getInstance()
    private val storage   = FirebaseStorage.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    private lateinit var nfcManager: NFCManager
    private var nfcAdapter: NfcAdapter? = null

    private val PICK_IMAGE = 1001
    private val gruposFiltrados = mutableListOf<String>()
    private lateinit var adapterGrupos: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crear_alumno)

        edtNombre            = findViewById(R.id.edtNombreAlumno)
        edtMatricula         = findViewById(R.id.edtMatricula)
        edtNfcUid            = findViewById(R.id.edtNfcUid)
        edtCorreo            = findViewById(R.id.edtCorreo)
        edtPassword          = findViewById(R.id.edtPassword)
        edtConfirmarPassword = findViewById(R.id.edtConfirmarPassword)
        txtNfcEstado         = findViewById(R.id.txtNfcEstado)
        imgAlumno            = findViewById(R.id.imgAlumno)
        btnSeleccionarFoto   = findViewById(R.id.btnSeleccionarFoto)
        btnGuardar           = findViewById(R.id.btnGuardarAlumno)
        progressBar          = findViewById(R.id.progressCrearAlumno)
        etBuscarGrupo        = findViewById(R.id.etBuscarGrupo)
        listViewGrupos       = findViewById(R.id.listViewGruposAlumno)
        txtGrupoSeleccionado = findViewById(R.id.txtGrupoSeleccionado)

        gruposFiltrados.addAll(DatosUniversidad.grupos)
        adapterGrupos = ArrayAdapter(this, android.R.layout.simple_list_item_1, gruposFiltrados)
        listViewGrupos.adapter = adapterGrupos

        etBuscarGrupo.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                filtrarGrupos(etBuscarGrupo.text.toString())
                listViewGrupos.visibility = if (gruposFiltrados.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
        etBuscarGrupo.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarGrupos(s.toString())
                listViewGrupos.visibility = if (gruposFiltrados.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })
        listViewGrupos.setOnItemClickListener { _, _, pos, _ ->
            grupoSeleccionado = gruposFiltrados[pos]
            txtGrupoSeleccionado.text = "📍 $grupoSeleccionado"
            txtGrupoSeleccionado.visibility = View.VISIBLE
            etBuscarGrupo.text.clear(); etBuscarGrupo.clearFocus()
            listViewGrupos.visibility = View.GONE
        }

        nfcManager = NFCManager(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        btnSeleccionarFoto.setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), PICK_IMAGE
            )
        }
        btnGuardar.setOnClickListener { validarYGuardar() }
    }

    private fun filtrarGrupos(query: String) {
        gruposFiltrados.clear()
        gruposFiltrados.addAll(DatosUniversidad.grupos.filter { it.contains(query.uppercase()) })
        adapterGrupos.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(this, { tag -> leerTagNFC(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            imagenUri = data.data
            Glide.with(this).load(imagenUri).circleCrop().into(imgAlumno)
        }
    }

    private fun leerTagNFC(tag: Tag?) {
        val uid = nfcManager.leerTag(tag)
        if (uid.isNotEmpty()) {
            runOnUiThread {
                edtNfcUid.setText(uid)
                txtNfcEstado.text = "✅ NFC detectado: $uid"
            }
        }
    }

    private fun validarYGuardar() {
        val nombre      = edtNombre.text.toString().trim()
        val matricula   = edtMatricula.text.toString().trim().uppercase()
        val uidNFC      = edtNfcUid.text.toString().trim().uppercase()
        val correo      = edtCorreo.text.toString().trim().lowercase()
        val pass        = edtPassword.text.toString()
        val confirmPass = edtConfirmarPassword.text.toString()

        if (nombre.isEmpty() || matricula.isEmpty() || correo.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_LONG).show(); return
        }
        if (grupoSeleccionado.isEmpty()) {
            Toast.makeText(this, "Selecciona un grupo", Toast.LENGTH_LONG).show(); return
        }
        if (uidNFC.isEmpty()) {
            Toast.makeText(this, "Escanea la tarjeta NFC del alumno", Toast.LENGTH_LONG).show(); return
        }
        if (pass.length < 8) {
            Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show(); return
        }
        if (pass != confirmPass) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_LONG).show(); return
        }

        verificarNFCUnico(uidNFC) {
            guardarAlumnoViaFunction(nombre, matricula, uidNFC, correo, pass)
        }
    }

    private fun verificarNFCUnico(nfcId: String, onProceder: () -> Unit) {
        btnGuardar.isEnabled   = false
        progressBar.visibility = View.VISIBLE
        database.reference.child("nfc_index").child(nfcId).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    btnGuardar.isEnabled   = true
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Esta tarjeta NFC ya está registrada", Toast.LENGTH_LONG).show()
                } else {
                    onProceder()
                }
            }
            .addOnFailureListener {
                btnGuardar.isEnabled   = true
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error verificando tarjeta. Intenta de nuevo.", Toast.LENGTH_LONG).show()
            }
    }

    private fun guardarAlumnoViaFunction(
        nombre: String, matricula: String, nfcId: String, correo: String, pass: String
    ) {
        val datos = hashMapOf(
            "nombre"    to nombre,
            "matricula" to matricula,
            "nfcId"     to nfcId,
            "correo"    to correo,
            "password"  to pass,
            "grupo"     to grupoSeleccionado
        )

        // ✅ Nombre exacto de la Function en index.js
        functions.getHttpsCallable("crearAlumno")
            .call(datos)
            .addOnSuccessListener { taskResult ->
                @Suppress("UNCHECKED_CAST")
                val uid = (taskResult.getData() as? Map<String, Any>)?.get("userId")?.toString()
                userId = uid
                progressBar.visibility = View.GONE
                if (imagenUri != null && uid != null) {
                    subirFoto(uid, grupoSeleccionado, matricula)
                } else {
                    btnGuardar.isEnabled = true
                    Toast.makeText(this, "✅ Alumno registrado correctamente", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                btnGuardar.isEnabled   = true
                progressBar.visibility = View.GONE
                android.util.Log.e("NFCheck_Functions", "Error: ${e.message}", e)
                val msg = when {
                    e.message?.contains("already-exists") == true   -> "El correo ya está registrado"
                    e.message?.contains("permission-denied") == true -> "Sin permisos para esta operación"
                    e.message?.contains("unauthenticated") == true   -> "Sesión caducada, vuelve a iniciar sesión"
                    e.message?.contains("unavailable") == true       -> "Sin conexión a internet"
                    else -> "Error: ${e.message}"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
    }

    private fun subirFoto(uid: String, grupo: String, matricula: String) {
        storage.reference.child("fotos_alumnos/$uid.jpg")
            .putFile(imagenUri!!)
            .addOnSuccessListener { task ->
                task.storage.downloadUrl.addOnSuccessListener { url ->
                    database.reference.updateChildren(mapOf(
                        "alumnos/$grupo/$matricula/fotoUrl" to url.toString(),
                        "usuarios/$uid/fotoUrl"             to url.toString()
                    ))
                    btnGuardar.isEnabled = true
                    Toast.makeText(this, "✅ Alumno registrado correctamente", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener {
                btnGuardar.isEnabled = true
                Toast.makeText(this, "Alumno registrado, pero la foto no se subió", Toast.LENGTH_LONG).show()
                finish()
            }
    }
}