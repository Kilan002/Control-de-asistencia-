package com.nexusautomation.nfcheck

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexusautomation.nfcheck.AlumnoResumen
import kotlinx.coroutines.launch

class AsistenciaViewModel : ViewModel() {

    private val repository = FirebaseRepository()

    val alumnos  = MutableLiveData<List<AlumnoResumen>>()
    val cargando = MutableLiveData<Boolean>()
    val error    = MutableLiveData<String?>()

    fun cargarGrupo(grupo: String) {
        cargando.value = true
        error.value    = null

        viewModelScope.launch {
            val lista = repository.obtenerResumenGrupo(grupo)
            alumnos.value  = lista
            cargando.value = false
            if (lista.isEmpty()) error.value = "No hay alumnos en el grupo $grupo"
        }
    }

    fun cargarGrupoYMateria(grupo: String, materia: String) {
        cargando.value = true
        error.value    = null

        viewModelScope.launch {
            val lista = repository.obtenerResumenGrupoMateria(grupo, materia)
            alumnos.value  = lista
            cargando.value = false
            if (lista.isEmpty()) error.value = "No hay registros para $materia en $grupo"
        }
    }
}
