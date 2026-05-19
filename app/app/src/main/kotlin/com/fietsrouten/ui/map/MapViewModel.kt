package com.fietsrouten.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fietsrouten.data.model.NominatimResult
import com.fietsrouten.data.model.RouteResult
import com.fietsrouten.data.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {

    private val repository = RouteRepository()

    private val _fromSuggestions = MutableStateFlow<List<NominatimResult>>(emptyList())
    val fromSuggestions: StateFlow<List<NominatimResult>> = _fromSuggestions

    private val _toSuggestions = MutableStateFlow<List<NominatimResult>>(emptyList())
    val toSuggestions: StateFlow<List<NominatimResult>> = _toSuggestions

    private val _route = MutableStateFlow<RouteResult?>(null)
    val route: StateFlow<RouteResult?> = _route

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var fromLocation: NominatimResult? = null
    var toLocation: NominatimResult? = null

    fun searchFrom(query: String) {
        if (query.length < 3) { _fromSuggestions.value = emptyList(); return }
        viewModelScope.launch {
            runCatching { repository.searchAddress(query) }
                .onSuccess { _fromSuggestions.value = it }
        }
    }

    fun searchTo(query: String) {
        if (query.length < 3) { _toSuggestions.value = emptyList(); return }
        viewModelScope.launch {
            runCatching { repository.searchAddress(query) }
                .onSuccess { _toSuggestions.value = it }
        }
    }

    fun calculateRoute() {
        val from = fromLocation ?: run { _error.value = "Selecteer eerst een startadres."; return }
        val to = toLocation ?: run { _error.value = "Selecteer eerst een bestemming."; return }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                repository.getRoute(
                    fromLat = from.lat.toDouble(), fromLon = from.lon.toDouble(),
                    toLat = to.lat.toDouble(), toLon = to.lon.toDouble()
                )
            }.onSuccess {
                _route.value = it
            }.onFailure {
                _error.value = "Route niet gevonden: ${it.message}"
            }
            _isLoading.value = false
        }
    }
}
