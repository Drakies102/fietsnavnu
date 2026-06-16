package com.fietsrouten.ui.map

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fietsrouten.data.model.CyclingRoute
import com.fietsrouten.data.model.Knooppunt
import com.fietsrouten.data.model.Poi
import com.fietsrouten.data.repository.PoiServiceRepository
import com.fietsrouten.data.model.NominatimResult
import com.fietsrouten.data.model.RouteResult
import com.fietsrouten.data.model.TripSummary
import com.fietsrouten.data.repository.OverpassRepository
import com.fietsrouten.data.repository.RouteRepository
import com.fietsrouten.navigation.NavigationEngine
import com.fietsrouten.navigation.NavigationSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {

    enum class PlannerMode { ADDRESS, KNOOPPUNTEN }

    enum class CyclingProfile(val apiName: String) {
        BIKE("bike"), MTB("mtb"), RACING("racingbike")
    }

    private val repository = RouteRepository()
    private val overpassRepository = OverpassRepository()
    private val poiRepository = PoiServiceRepository()
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

    // ── User location (for "use my location" suggestions) ─────────

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation

    fun updateUserLocation(lat: Double, lon: Double) {
        _userLocation.value = lat to lon
    }

    fun currentLocationResult(): NominatimResult? =
        _userLocation.value?.let { (lat, lon) ->
            NominatimResult(placeId = -1L, displayName = "Mijn locatie", lat = lat.toString(), lon = lon.toString())
        }

    // ── Route + navigation ────────────────────────────────────────

    private val _route = MutableStateFlow<RouteResult?>(null)
    val route: StateFlow<RouteResult?> = _route

    private val _routesByProfile = MutableStateFlow<Map<CyclingProfile, RouteResult>>(emptyMap())
    val routesByProfile: StateFlow<Map<CyclingProfile, RouteResult>> = _routesByProfile

    private val _selectedProfile = MutableStateFlow(CyclingProfile.BIKE)
    val selectedProfile: StateFlow<CyclingProfile> = _selectedProfile

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _navigationSession = MutableStateFlow<NavigationSession?>(null)
    val navigationSession: StateFlow<NavigationSession?> = _navigationSession

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating

    private val _tripSummary = MutableStateFlow<TripSummary?>(null)
    val tripSummary: StateFlow<TripSummary?> = _tripSummary

    private var navStartTimeMs = 0L
    private var navMaxSpeedKmh = 0f

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
        viewModelScope.launch {
            plannerCityQuery.debounce(300)
                .flatMapLatest { query ->
                    flow {
                        if (query.length >= 3) emit(runCatching { repository.searchAddress(query) }.getOrDefault(emptyList()))
                        else emit(emptyList())
                    }
                }
                .collect { _plannerCitySuggestions.value = it }
        }
    }

    fun searchFrom(query: String) { fromQuery.value = query }
    fun searchTo(query: String) { toQuery.value = query }
    fun searchPlannerCity(query: String) { plannerCityQuery.value = query }
    fun selectPlannerCity(result: NominatimResult) {
        _plannerCityLocation.value = result.lat.toDouble() to result.lon.toDouble()
    }

    fun clearPlannerCitySelection() {
        _plannerCityLocation.value = null
    }

    fun getLegDistances(nodes: List<Knooppunt>): List<Double> =
        nodes.zipWithNext { a, b -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val phi1 = Math.toRadians(lat1); val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dPhi / 2).let { it * it } + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    // ── Planner city search ───────────────────────────────────────

    private val _plannerCitySuggestions = MutableStateFlow<List<NominatimResult>>(emptyList())
    val plannerCitySuggestions: StateFlow<List<NominatimResult>> = _plannerCitySuggestions

    private val _plannerCityLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val plannerCityLocation: StateFlow<Pair<Double, Double>?> = _plannerCityLocation

    private val plannerCityQuery = MutableStateFlow("")

    // ── Mode switching ────────────────────────────────────────────

    fun setMode(mode: PlannerMode) {
        _plannerMode.value = mode
        _selectedNodes.value = emptyList()
        _visibleKnoopunten.value = emptyList()
        _route.value = null
        _routesByProfile.value = emptyMap()
        _error.value = null
        _pois.value = emptyList()
        _poisVisible.value = false
    }

    // ── Cycling profile selection ──────────────────────────────────

    private suspend fun fetchAllProfiles(
        request: suspend (profile: String) -> RouteResult
    ): Map<CyclingProfile, RouteResult> = coroutineScope {
        CyclingProfile.values()
            .map { profile -> profile to async { runCatching { request(profile.apiName) } } }
            .mapNotNull { (profile, deferred) -> deferred.await().getOrNull()?.let { profile to it } }
            .toMap()
    }

    private fun applyRouteResults(results: Map<CyclingProfile, RouteResult>): Boolean {
        if (results.isEmpty()) return false
        _routesByProfile.value = results
        val profile = if (_selectedProfile.value in results) _selectedProfile.value else CyclingProfile.BIKE
        _selectedProfile.value = profile
        _route.value = results[profile] ?: results.values.first()
        _pois.value = emptyList()
        _poisVisible.value = false
        return true
    }

    fun selectProfile(profile: CyclingProfile) {
        val result = _routesByProfile.value[profile] ?: return
        _selectedProfile.value = profile
        _route.value = result
        if (_poisVisible.value) loadPoisAlongRoute(result) else _pois.value = emptyList()
    }

    // ── Address-mode routing ──────────────────────────────────────

    fun calculateRoute() {
        val from = fromLocation ?: run { _error.value = "Selecteer eerst een startadres."; return }
        val to = toLocation ?: run { _error.value = "Selecteer eerst een bestemming."; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val results = fetchAllProfiles { profile ->
                repository.getRoute(
                    fromLat = from.lat.toDouble(), fromLon = from.lon.toDouble(),
                    toLat = to.lat.toDouble(), toLon = to.lon.toDouble(),
                    profile = profile
                )
            }
            if (!applyRouteResults(results)) {
                _error.value = "Route niet gevonden"
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
        _routesByProfile.value = emptyMap()
        _error.value = null
    }

    fun calculateKnooppuntenRoute() {
        val nodes = _selectedNodes.value
        if (nodes.size < 2) { _error.value = "Selecteer minimaal 2 knooppunten."; return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val results = fetchAllProfiles { profile ->
                repository.getRoute(nodes.map { it.lat to it.lon }, profile)
            }
            if (applyRouteResults(results)) {
                _visibleKnoopunten.value = _selectedNodes.value
                _selectedNodes.value = emptyList()
            } else {
                _error.value = "Route niet gevonden"
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

    // ── POI layer ─────────────────────────────────────────────────

    private val _pois = MutableStateFlow<List<Poi>>(emptyList())
    val pois: StateFlow<List<Poi>> = _pois

    private val _poisVisible = MutableStateFlow(false)
    val poisVisible: StateFlow<Boolean> = _poisVisible

    fun togglePois() {
        val route = _route.value ?: return
        val newState = !_poisVisible.value
        _poisVisible.value = newState
        if (newState) loadPoisAlongRoute(route)
        else _pois.value = emptyList()
    }

    private fun loadPoisAlongRoute(route: RouteResult) {
        viewModelScope.launch {
            val coords = route.coordinates
            val south = coords.minOf { it[1] }
            val north = coords.maxOf { it[1] }
            val west  = coords.minOf { it[0] }
            val east  = coords.maxOf { it[0] }
            runCatching {
                poiRepository.getPoisInBbox(south, west, north, east)
            }.onSuccess { all ->
                _pois.value = poiRepository.filterNearRoute(all, coords)
            }.onFailure {
                Log.w("MapViewModel", "POI load failed: ${it.message}")
                _poisVisible.value = false
            }
        }
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
        navStartTimeMs = System.currentTimeMillis()
        navMaxSpeedKmh = 0f
        _tripSummary.value = null
        _isNavigating.value = true
    }

    fun stopNavigation() {
        val route = _route.value
        val session = _navigationSession.value
        if (route != null && navStartTimeMs > 0L) {
            val durationMs = System.currentTimeMillis() - navStartTimeMs
            val traveled = route.distanceMeters - (session?.remainingDistanceMeters ?: route.distanceMeters)
            val avgKmh = if (durationMs > 0) (traveled / (durationMs / 1000.0)) * 3.6 else 0.0
            _tripSummary.value = TripSummary(
                distanceMeters = traveled.coerceAtLeast(0.0),
                durationMs = durationMs,
                avgSpeedKmh = avgKmh,
                maxSpeedKmh = navMaxSpeedKmh.toDouble()
            )
        }
        _isNavigating.value = false
        _navigationSession.value = null
        navStartTimeMs = 0L
    }

    fun onLocationUpdate(location: Location) {
        if (!_isNavigating.value) return
        val route = _route.value ?: return
        val speedKmh = location.speed * 3.6f
        if (speedKmh > navMaxSpeedKmh) navMaxSpeedKmh = speedKmh
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
                    toLat = to.lat.toDouble(), toLon = to.lon.toDouble(),
                    profile = _selectedProfile.value.apiName
                )
            }.onSuccess { _route.value = it }
        }
    }
}
