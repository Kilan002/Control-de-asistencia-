package com.nexusautomation.nfcheck

import android.content.Context
import androidx.work.*
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dbHelper   = DatabaseHelper(applicationContext)
        val pendientes = dbHelper.obtenerPendientes()

        if (pendientes.isEmpty()) return Result.success()

        var todosExitosos = true

        for (registro in pendientes) {
            try {
                val grupo     = registro["grupo"]     ?: continue
                val materia   = registro["materia"]   ?: continue
                val fecha     = registro["fecha"]     ?: continue
                val matricula = registro["matricula"] ?: continue
                val id        = registro["id"]        ?: continue

                val datos = mutableMapOf<String, Any>(
                    "estado" to (registro["estado"] ?: "asistencia"),
                    "hora"   to (registro["hora"]   ?: "--:--")
                )
                if (registro["justificante"] == "1") datos["justificante"] = true
                val nota = registro["nota"]
                if (!nota.isNullOrEmpty()) datos["nota"] = nota

                // ✅ .await() funciona ahora que tenemos los permisos de internet
                FirebaseDatabase.getInstance().reference
                    .child("asistencias")
                    .child(grupo).child(materia).child(fecha).child(matricula)
                    .setValue(datos)
                    .await()

                dbHelper.marcarSincronizado(id)

            } catch (e: Exception) {
                todosExitosos = false
            }
        }

        return if (todosExitosos) Result.success() else Result.retry()
    }

    companion object {
        private const val TRABAJO_UNICO = "sync_asistencias"

        fun programar(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TRABAJO_UNICO,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun sincronizarAhora(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}