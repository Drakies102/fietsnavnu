package com.fietsrouten.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fietsrouten.data.model.NominatimResult
import com.fietsrouten.data.model.RouteResult
import com.fietsrouten.data.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

    private val fromQuery = MutableStateFlow("")
    private val toQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            fromQuery
                .debounce(300)
                .flatMapLatest { query ->
                    flow {
                        Log.d("Autocomplete", "[FROM] querying Nominatim: \"$query\" (length=${query.length})")
                        if (query.length >= 3) {
                            val result = runCatching { repository.searchAddress(query) }
                            Log.d("Autocomplete", "[FROM] Nominatim result: ${result.map { it.size }} items, error=${result.exceptionOrNull()?.message}")
                            emit(result.getOrDefault(emptyList()))
                        } else {
                            Log.d("Autocomplete", "[FROM] skipped — query too short")
                            emit(emptyList())
                        }
                    }
                }
                .collect { _fromSuggestions.value = it }
        }
        viewModelScope.launch {
            toQuery
                .debounce(300)
                .flatMapLatest { query ->
                    flow {
                        Log.d("Autocomplete", "[TO] querying Nominatim: \"$query\" (length=${query.length})")
                        if (query.length >= 3) {
                            val result = runCatching { repository.searchAddress(query) }
                            Log.d("Autocomplete", "[TO] Nominatim result: ${result.map { it.size }} items, error=${result.exceptionOrNull()?.message}")
                            emit(result.getOrDefault(emptyList()))
                        } else {
                            Log.d("Autocomplete", "[TO] skipped — query too short")
                            emit(emptyList())
                        }
                    }
                }
                .collect { _toSuggestions.value = it }
        }
    }

    fun searchFrom(query: String) { fromQuery.value = query }

    fun searchTo(query: String) { toQuery.value = query }

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
