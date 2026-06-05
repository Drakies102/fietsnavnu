package com.fietsrouten.ui.map

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fietsrouten.data.model.CyclingRoute
import com.fietsrouten.data.model.Knooppunt
import com.fietsrouten.data.model.NominatimResult
import com.fietsrouten.data.model.RouteResult
import com.fietsrouten.data.repository.OverpassRepository
import com.fietsrouten.data.repository.RouteRepository
import com.fietsrouten.navigation.NavigationEngine
import com.fietsrouten.navigation.NavigationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {

    enum class PlannerMode { ADDRESS, KNOOPPUNTEN }

    private val repository = RouteRepository()
    private val overpassRepository = OverpassRepository()
    private val navigationEngine = NavigationEngine()

    // ── Address search ────────────────────────────────────────────

    private val _fromSuggestions = MutableStateFlow<List<NominatimResult>>(emptyList())
    val fromSuggestions: StateFlow<List<NominatimResult>> = _fromSuggestions

    private val _toSuggestions = MutableStateFlow<List<NominatimResult>>(emptyList())
    val toSuggestions: StateFlow<List<NominatimResult>> = _toSuggestions

    var fromLocation: NominatimResult? = null
    var toLocation: NominatimResult? = null

    private val fromQuery = MutableStateFlow("")
    private val toQuery = MutableStateFlow("")

    // ── Route + navigation ────────────────────────────────────────

    private val _route = MutableStateFlow<RouteResult?>(null)
    val route: StateFlow<RouteResult?> = _route

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _navigationSession = MutableStateFlow<NavigationSession?>(null)
    val navigationSession: StateFlow<NavigationSession?> = _navigationSession

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating

    private var recalcCooldownUntil = 0L

    // ── Planner mode ──────────────────────────────────────────────

    private val _plannerMode = MutableStateFlow(PlannerMode.ADDRESS)
    val plannerMode: StateFlow<PlannerMode> = _plannerMode

    private val _selectedNodes = MutableStateFlow<List<Knooppunt>>(emptyList())
    val selectedNodes: StateFlow<List<Knooppunt>> = _selectedNodes

    // ── Map layers ────────────────────────────────────────────────

    private val _visibleKnoopunten = MutableStateFlow<List<Knooppunt>>(emptyList())
    val visibleKnoopunten: StateFlow<List<Knooppunt>> = _visibleKnoopunten

    private val _cyclingRoutes = MutableStateFlow<List<CyclingRoute>>(emptyList())
    val cyclingRoutes: StateFlow<List<CyclingRoute>> = _cyclingRoutes

    private val _showCyclingRoutes = MutableStateFlow(false)
    val showCyclingRoutes: StateFlow<Boolean> = _showCyclingRoutes

    init {
        viewModelScope.launch {
            fromQuery.debounce(300)
                .flatMapLatest { query ->
                    flow {
                        if (query.length >= 3) emit(runCatching { repository.searchAddress(query) }.getOrDefault(emptyList()))
                        else emit(emptyList())
                    }
                }
                .collect { _fromSuggestions.value = it }
        }
        viewModelScope.launch {
            toQuery.debounce(300)
                .flatMapLatest { query ->
                    flow {
                        if (query.length >= 3) emit(runCatching { repository.searchAddress(query) }.getOrDefault(emptyList()))
                        else emit(emptyList())
                    }
                }
                .collect { _toSuggestions.value = it }
        }
    }

    fun searchFrom(query: String) { fromQuery.value = query }
    fun searchTo(query: String) { toQuery.value = query }

    // ── Mode switching ────────────────────────────────────────────

    fun setMode(mode: PlannerMode) {
        _plannerMode.value = mode
        _selectedNodes.value = emptyList()
        _visibleKnoopunten.value = emptyList()
        _route.value = null
        _error.value = null
    }

    // ── Address-mode routing ──────────────────────────────────────

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
            }.onSuccess { route ->
                _route.value = route
                loadKnoopuntenAlongRoute(route)
            }.onFailure {
                _error.value = "Route niet gevonden: ${it.message}"
            }
            _isLoading.value = false
        }
    }

    // ── Knooppunten-mode routing ──────────────────────────────────

    fun toggleKnooppunt(node: Knooppunt) {
        val current = _selectedNodes.value.toMutableList()
        val existing = current.indexOfFirst { it.id == node.id }
        if (existing >= 0) current.removeAt(existing) else current.add(node)
        _selectedNodes.value = current
    }

    fun clearPlanner() {
        _selectedNodes.value = emptyList()
        _route.value = null
        _error.value = null
    }

    fun calculateKnooppuntenRoute() {
        val nodes = _selectedNodes.value
        if (nodes.size < 2) { _error.value = "Selecteer minimaal 2 knooppunten."; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                repository.getRoute(nodes.map { it.lat to it.lon })
            }.onSuccess { route ->
                _route.value = route
                // Keep selected nodes as visible route markers, clear selection state
                _visibleKnoopunten.value = _selectedNodes.value
                _selectedNodes.value = emptyList()
            }.onFailure {
                _error.value = "Route niet gevonden: ${it.message}"
            }
            _isLoading.value = false
        }
    }

    // ── Knoopunten layer ──────────────────────────────────────────

    private var allKnoopunten = emptyList<Knooppunt>()

    fun setAllKnoopunten(nodes: List<Knooppunt>) {
        allKnoopunten = nodes
    }

    fun filterKnoopuntenForViewport(south: Double, west: Double, north: Double, east: Double) {
        _visibleKnoopunten.value = allKnoopunten.filter { node ->
            node.lat in south..north && node.lon in west..east
        }
    }

    private fun loadKnoopuntenAlongRoute(route: RouteResult) {
        val coords = route.coordinates
        val minLat = coords.minOf { it[1] }
        val maxLat = coords.maxOf { it[1] }
        val minLon = coords.minOf { it[0] }
        val maxLon = coords.maxOf { it[0] }
        val inBox = allKnoopunten.filter { node ->
            node.lat in minLat..maxLat && node.lon in minLon..maxLon
        }
        _visibleKnoopunten.value = overpassRepository.filterKnoopuntenNearRoute(inBox, coords)
    }

    // ── Cycling routes layer ──────────────────────────────────────

    fun toggleCyclingRoutes(south: Double, west: Double, north: Double, east: Double) {
        val newState = !_showCyclingRoutes.value
        _showCyclingRoutes.value = newState
        if (newState) {
            viewModelScope.launch {
                runCatching {
                    overpassRepository.getCyclingRoutes(south, west, north, east)
                }.onSuccess {
                    _cyclingRoutes.value = it
                }.onFailure {
                    Log.w("MapViewModel", "Cycling routes load failed: ${it.message}")
                    _showCyclingRoutes.value = false
                }
            }
        } else {
            _cyclingRoutes.value = emptyList()
        }
    }

    // ── Navigation ────────────────────────────────────────────────

    fun startNavigation() {
        if (_route.value == null) return
        _isNavigating.value = true
    }

    fun stopNavigation() {
        _isNavigating.value = false
        _navigationSession.value = null
    }

    fun onLocationUpdate(location: Location) {
        if (!_isNavigating.value) return
        val route = _route.value ?: return
        val session = navigationEngine.update(location, route)
        _navigationSession.value = session

        if (session.isOffRoute && System.currentTimeMillis() > recalcCooldownUntil) {
            recalcCooldownUntil = System.currentTimeMillis() + 5000
            recalculateFromLocation(location)
        }
    }

    private fun recalculateFromLocation(location: Location) {
        val to = toLocation ?: return
        viewModelScope.launch {
            runCatching {
                repository.getRoute(
                    fromLat = location.latitude, fromLon = location.longitude,
                    toLat = to.lat.toDouble(), toLon = to.lon.toDouble()
                )
            }.onSuccess { _route.value = it }
        }
    }
}
