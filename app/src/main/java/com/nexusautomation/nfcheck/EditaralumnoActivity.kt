package com.nexusautomation.nfcheck

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.nexusautomation.nfcheck.NFCManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class EditarAlumnoActivity : AppCompatActivity() {

    private lateinit var imgFoto: ImageView
    private lateinit var btnFoto: Button
    private lateinit var edtNombre: EditText
    private lateinit var edtNfcUid: EditText
    private lateinit var txtNfcEstado: TextView
    private lateinit var spinnerGrupo: Spinner
    private lateinit var btnGuardar: Button

    private var imagenUri: Uri? = null
    private var fotoUrlActual: String = ""

    // Datos recibidos del intent
    private var grupo: String     = ""
    private var matricula: String = ""
    private var userId: String    = ""

    private lateinit var nfcManager: NFCManager
    private var nfcAdapter: NfcAdapter? = null

    private val PICK_IMAGE = 3001
    private val grupos     = DatosUniversidad.grupos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editaralumno)

        imgFoto      = findViewById(R.id.imgFotoEditar)
        btnFoto      = findViewById(R.id.btnCambiarFoto)
        edtNombre    = findViewById(R.id.edtNombreEditar)
        edtNfcUid    = findViewById(R.id.edtNfcUidEditar)
        txtNfcEstado = findViewById(R.id.txtNfcEstadoEditar)
        spinnerGrupo = findViewById(R.id.spinnerGrupoEditar)
        btnGuardar   = findViewById(R.id.btnGuardarEditar)

        // Recibe los datos actuales del alumno
        grupo     = intent.getStringExtra("grupo")     ?: ""
        matricula = intent.getStringExtra("matricula") ?: ""
        userId    = intent.getStringExtra("userId")    ?: ""

        if (grupo.isEmpty() || matricula.isEmpty()) {
            Toast.makeText(this, "Datos de alumno incompletos", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        spinnerGrupo.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, grupos
        )

        nfcManager = NFCManager(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        cargarDatosActuales()

        btnFoto.setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                PICK_IMAGE
            )
        }

        btnGuardar.setOnClickListener { guardarCambios() }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            { tag -> leerTagNFC(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data?.data != null) {
            imagenUri = data.data
            Glide.with(this).load(imagenUri).circleCrop().into(imgFoto)
        }
    }

    private fun cargarDatosActuales() {
        FirebaseDatabase.getInstance().reference
            .child("alumnos").child(grupo).child(matricula)
            .get()
            .addOnSuccessListener { snap ->
                val nombre   = snap.child("nombre").value?.toString() ?: ""
                val nfcId    = snap.child("nfcId").value?.toString()  ?: ""
                val fotoUrl  = snap.child("fotoUrl").value?.toString() ?: ""

                // Si userId no vino en el intent, lo recuperamos de aquí
                if (userId.isEmpty()) {
                    userId = snap.child("uid").value?.toString() ?: ""
                }

                fotoUrlActual = fotoUrl
                edtNombre.setText(nombre)
                edtNfcUid.setText(nfcId)

                // Posiciona el spinner en el grupo actual
                val idx = grupos.indexOf(grupo)
                if (idx >= 0) spinnerGrupo.setSelection(idx)

                if (fotoUrl.isNotEmpty()) {
                    Glide.with(this)
                        .load(fotoUrl)
                        .placeholder(R.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(imgFoto)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error cargando datos: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun guardarCambios() {
        val nuevoNombre  = edtNombre.text.toString().trim()
        val nuevoNfcId   = edtNfcUid.text.toString().trim()
        val nuevoGrupo   = spinnerGrupo.selectedItem.toString()

        if (nuevoNombre.isEmpty() || nuevoNfcId.isEmpty()) {
            Toast.makeText(this, "Nombre y NFC son obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        btnGuardar.isEnabled = false

        if (imagenUri != null && userId.isNotEmpty()) {
            // Sube foto nueva primero, luego actualiza DB
            subirFotoYActualizar(nuevoNombre, nuevoNfcId, nuevoGrupo)
        } else {
            // Sin cambio de foto — actualiza solo los datos
            actualizarDatos(nuevoNombre, nuevoNfcId, nuevoGrupo, fotoUrlActual)
        }
    }

    private fun subirFotoYActualizar(nombre: String, nfcId: String, nuevoGrupo: String) {
        FirebaseStorage.getInstance().reference
            .child("fotos_alumnos/$userId.jpg")
            .putFile(imagenUri!!)
            .addOnSuccessListener { task ->
                task.storage.downloadUrl.addOnSuccessListener { url ->
                    actualizarDatos(nombre, nfcId, nuevoGrupo, url.toString())
                }
            }
            .addOnFailureListener { e ->
                btnGuardar.isEnabled = true
                Toast.makeText(this, "Error subiendo foto: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun actualizarDatos(nombre: String, nfcId: String, nuevoGrupo: String, fotoUrl: String) {
        val ref = FirebaseDatabase.getInstance().reference

        // Si el grupo cambió, hay que mover el nodo del alumno
        if (nuevoGrupo != grupo) {
            // Lee el nodo actual completo
            ref.child("alumnos").child(grupo).child(matricula).get()
                .addOnSuccessListener { snap ->
                    val datosActualizados = mutableMapOf<String, Any>(
                        "nombre" to nombre,
                        "nfcId"  to nfcId,
                        "uid"    to userId
                    )
                    if (fotoUrl.isNotEmpty()) datosActualizados["fotoUrl"] = fotoUrl

                    val updates = mutableMapOf<String, Any?>(
                        // Escribe en el nuevo grupo
                        "alumnos/$nuevoGrupo/$matricula" to datosActualizados,
                        // Borra del grupo anterior
                        "alumnos/$grupo/$matricula"      to null,
                        // Actualiza usuarios/{uid}
                        "usuarios/$userId/grupo"         to nuevoGrupo,
                        "usuarios/$userId/nombre"        to nombre
                    )
                    if (fotoUrl.isNotEmpty()) updates["usuarios/$userId/fotoUrl"] = fotoUrl

                    ref.updateChildren(updates)
                        .addOnSuccessListener {
                            btnGuardar.isEnabled = true
                            Toast.makeText(this, "Alumno actualizado correctamente", Toast.LENGTH_LONG).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            btnGuardar.isEnabled = true
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
        } else {
            // Mismo grupo — actualiza en sitio
            val datosActualizados = mutableMapOf<String, Any>(
                "nombre" to nombre,
                "nfcId"  to nfcId,
                "uid"    to userId
            )
            if (fotoUrl.isNotEmpty()) datosActualizados["fotoUrl"] = fotoUrl

            val updates = mutableMapOf<String, Any?>(
                "alumnos/$grupo/$matricula/nombre"   to nombre,
                "alumnos/$grupo/$matricula/nfcId"    to nfcId,
                "usuarios/$userId/nombre"            to nombre
            )
            if (fotoUrl.isNotEmpty()) {
                updates["alumnos/$grupo/$matricula/fotoUrl"] = fotoUrl
                updates["usuarios/$userId/fotoUrl"]          = fotoUrl
            }

            ref.updateChildren(updates)
                .addOnSuccessListener {
                    btnGuardar.isEnabled = true
                    Toast.makeText(this, "Alumno actualizado correctamente", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    btnGuardar.isEnabled = true
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}