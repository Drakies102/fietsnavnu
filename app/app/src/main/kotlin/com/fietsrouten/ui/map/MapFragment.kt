package com.fietsrouten.ui.map

import android.Manifest
import android.graphics.RectF
import android.util.Log
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.fietsrouten.Config
import com.fietsrouten.R
import com.fietsrouten.data.model.CyclingRoute
import com.fietsrouten.data.model.Knooppunt
import com.fietsrouten.data.model.Poi
import com.fietsrouten.databinding.FragmentMapBinding
import com.fietsrouten.navigation.NavigationSession
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

class MapFragment : Fragment() {

    companion object { private const val TAG_K = "Knoopunten" }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private var map: MapLibreMap? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { viewModel.onLocationUpdate(it) }
        }
    }

    // TTS state
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastAnnouncedIndex = -1
    private var announcedAt200m = false
    private var announcedAtTurn = false
    private var wasOffRoute = false

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            map?.getStyle { style -> enableLocation(style) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale("nl", "NL")
            }
        }

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.setStyle(Config.MAP_STYLE_URL) { style ->
                addRouteLayer(style)
                addKnoopuntenLayers(style)
                addCyclingRoutesLayer(style)
                addPoiLayer(style)
                checkAndRequestLocation()
            }
            mapLibreMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(52.3676, 4.9041))
                .zoom(10.0)
                .build()
            mapLibreMap.addOnCameraIdleListener { onCameraIdle() }
            mapLibreMap.addOnMapClickListener { latLng -> onMapClick(latLng) }
        }

        setupSearch()
        setupNavigation()
        setupPlannerUI()
        observeViewModel()
        loadKnoopuntenFromAssets()
    }

    // ── Search ────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.actvFrom.addTextChangedListener(watcher { viewModel.searchFrom(it) })
        binding.actvTo.addTextChangedListener(watcher { viewModel.searchTo(it) })
        binding.btnRoute.setOnClickListener { viewModel.calculateRoute() }
    }

    // ── Navigation ────────────────────────────────────────────────

    private fun setupNavigation() {
        binding.btnStartNavigation.setOnClickListener {
            viewModel.startNavigation()
            startLocationUpdates()
        }
        binding.btnStopNavigation.setOnClickListener {
            stopNavigation()
        }
    }

    private fun stopNavigation() {
        viewModel.stopNavigation()
        stopLocationUpdates()
        resetTtsState()
        binding.navInstructionCard.visibility = View.GONE
        binding.navBottomBar.visibility = View.GONE
        binding.searchCard.visibility = View.VISIBLE
        binding.instructionsPanel.visibility = View.VISIBLE
        viewModel.route.value?.let { route ->
            val latLngs = route.coordinates.map { LatLng(it[1], it[0]) }
            if (latLngs.size >= 2) {
                val bounds = LatLngBounds.Builder().includes(latLngs).build()
                map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
            }
        }
    }

    // ── Planner UI ────────────────────────────────────────────────

    private fun setupPlannerUI() {
        binding.modeToggle.check(R.id.btnModeAddress)

        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                R.id.btnModeAddress -> MapViewModel.PlannerMode.ADDRESS
                else -> MapViewModel.PlannerMode.KNOOPPUNTEN
            }
            if (newMode != viewModel.plannerMode.value) viewModel.setMode(newMode)
        }

        // City search autocomplete
        val cityAdapter = makePassthroughAdapter()
        var cityResults = emptyList<com.fietsrouten.data.model.NominatimResult>()
        binding.actvPlannerCity.setAdapter(cityAdapter)
        binding.actvPlannerCity.addTextChangedListener(watcher { viewModel.searchPlannerCity(it) })
        binding.actvPlannerCity.setOnItemClickListener { _, _, pos, _ ->
            val result = cityResults[pos]
            viewModel.selectPlannerCity(result)
            binding.actvPlannerCity.setText(result.displayName.substringBefore(","), false)
            binding.actvPlannerCity.clearFocus()
        }
        lifecycleScope.launch {
            viewModel.plannerCitySuggestions.collect { suggestions ->
                cityResults = suggestions
                cityAdapter.setNotifyOnChange(false)
                cityAdapter.clear()
                cityAdapter.addAll(suggestions.map { it.displayName })
                cityAdapter.notifyDataSetChanged()
                if (suggestions.isNotEmpty() && binding.actvPlannerCity.hasFocus())
                    binding.actvPlannerCity.showDropDown()
            }
        }
        lifecycleScope.launch {
            viewModel.plannerCityLocation.collect { loc ->
                loc ?: return@collect
                map?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(loc.first, loc.second))
                            .zoom(13.0)
                            .build()
                    )
                )
            }
        }

        binding.btnClearPlanner.setOnClickListener {
            viewModel.clearPlanner()
            loadKnoopuntenForCurrentViewport()
        }

        binding.btnCalculatePlannerRoute.setOnClickListener {
            viewModel.calculateKnooppuntenRoute()
        }

        binding.fabLayers.setOnClickListener {
            val m = map ?: return@setOnClickListener
            val bounds = m.projection.visibleRegion.latLngBounds
            val sw = bounds.southWest; val ne = bounds.northEast
            viewModel.toggleCyclingRoutes(sw.latitude, sw.longitude, ne.latitude, ne.longitude)
        }

        binding.fabPois.setOnClickListener {
            viewModel.togglePois()
        }

        binding.tvPoiClose.setOnClickListener {
            binding.poiInfoCard.visibility = View.GONE
        }
    }

    // ── Camera idle ───────────────────────────────────────────────

    private fun onCameraIdle() {
        if (viewModel.plannerMode.value == MapViewModel.PlannerMode.KNOOPPUNTEN) {
            loadKnoopuntenForCurrentViewport()
        }
    }

    private fun loadKnoopuntenForCurrentViewport() {
        val m = map ?: return
        if (m.cameraPosition.zoom < 10.0) return
        val bounds = m.projection.visibleRegion.latLngBounds
        val sw = bounds.southWest
        val ne = bounds.northEast
        viewModel.filterKnoopuntenForViewport(sw.latitude, sw.longitude, ne.latitude, ne.longitude)
    }

    private fun loadKnoopuntenFromAssets() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val json = requireContext().assets.open("knoopunten.geojson")
                    .bufferedReader().readText()
                val root = org.json.JSONObject(json)
                val features = root.getJSONArray("features")
                val knoopunten = (0 until features.length()).mapNotNull { i ->
                    val props = features.getJSONObject(i).getJSONObject("properties")
                    Knooppunt(
                        id = props.getLong("nid"),
                        lat = props.getDouble("lat"),
                        lon = props.getDouble("lon"),
                        ref = props.getString("ref")
                    )
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    viewModel.setAllKnoopunten(knoopunten)
                    Log.d(TAG_K, "Loaded ${knoopunten.size} knoopunten from assets")
                }
            } catch (e: Exception) {
                Log.e(TAG_K, "Failed to load knoopunten.geojson from assets: ${e.message}")
            }
        }
    }

    // ── Map click (knooppunt selection) ───────────────────────────

    private fun onMapClick(latLng: LatLng): Boolean {
        val m = map ?: return false
        val point = m.projection.toScreenLocation(latLng)
        val density = resources.displayMetrics.density
        val slop = 24 * density
        val rect = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)

        // POI tap (checked first — always active when layer is visible)
        val poiFeatures = m.queryRenderedFeatures(rect, "poi-circles")
        if (poiFeatures.isNotEmpty()) {
            val props = poiFeatures.first().properties()
            val name = props?.get("name")?.asString ?: return true
            val amenity = props.get("amenity")?.asString ?: ""
            binding.tvPoiName.text = name
            binding.tvPoiType.text = amenityLabel(amenity)
            binding.poiInfoCard.visibility = View.VISIBLE
            return true
        }

        // Knooppunt tap (only in Route Plannen mode)
        if (viewModel.plannerMode.value == MapViewModel.PlannerMode.KNOOPPUNTEN) {
            val features = m.queryRenderedFeatures(rect, "knooppunt-circles")
            if (features.isNotEmpty()) {
                val props = features.first().properties() ?: return false
                val id  = props.get("nid")?.asLong ?: return true
                val ref = props.get("ref")?.asString ?: return true
                val lat = props.get("lat")?.asDouble ?: return true
                val lon = props.get("lon")?.asDouble ?: return true
                viewModel.toggleKnooppunt(Knooppunt(id, lat, lon, ref))
                return true
            }
        }
        return false
    }

    private fun amenityLabel(amenity: String): String = when (amenity) {
        "cafe"       -> "Café / Koffieshop"
        "restaurant" -> "Restaurant"
        "fast_food"  -> "Snackbar / Fast food"
        "bar"        -> "Bar"
        "pub"        -> "Pub"
        "bakery"     -> "Bakkerij"
        "ice_cream"  -> "IJssalon"
        else         -> amenity.replaceFirstChar { it.uppercase() }
    }

    // ── ViewModel observation ─────────────────────────────────────

    private fun observeViewModel() {
        var fromResults = emptyList<com.fietsrouten.data.model.NominatimResult>()
        var toResults = emptyList<com.fietsrouten.data.model.NominatimResult>()

        val fromAdapter = makePassthroughAdapter()
        val toAdapter = makePassthroughAdapter()

        binding.actvFrom.setAdapter(fromAdapter)
        binding.actvFrom.setOnItemClickListener { _, _, pos, _ ->
            viewModel.fromLocation = fromResults[pos]
            binding.actvFrom.setText(fromResults[pos].displayName, false)
        }
        binding.actvTo.setAdapter(toAdapter)
        binding.actvTo.setOnItemClickListener { _, _, pos, _ ->
            viewModel.toLocation = toResults[pos]
            binding.actvTo.setText(toResults[pos].displayName, false)
        }

        lifecycleScope.launch {
            viewModel.fromSuggestions.collect { suggestions ->
                fromResults = suggestions
                fromAdapter.setNotifyOnChange(false)
                fromAdapter.clear()
                fromAdapter.addAll(suggestions.map { it.displayName })
                fromAdapter.notifyDataSetChanged()
                if (suggestions.isNotEmpty() && binding.actvFrom.hasFocus()) binding.actvFrom.showDropDown()
            }
        }
        lifecycleScope.launch {
            viewModel.toSuggestions.collect { suggestions ->
                toResults = suggestions
                toAdapter.setNotifyOnChange(false)
                toAdapter.clear()
                toAdapter.addAll(suggestions.map { it.displayName })
                toAdapter.notifyDataSetChanged()
                if (suggestions.isNotEmpty() && binding.actvTo.hasFocus()) binding.actvTo.showDropDown()
            }
        }
        lifecycleScope.launch {
            viewModel.route.collect { route ->
                if (route == null) {
                    drawRoute(emptyList())
                    binding.instructionsPanel.visibility = View.GONE
                    binding.fabPois.visibility = View.GONE
                    binding.poiInfoCard.visibility = View.GONE
                    return@collect
                }
                drawRoute(route.coordinates)
                binding.tvDistance.text = "Afstand: ${"%.1f km".format(route.distanceMeters / 1000)}"
                binding.tvDuration.text = "Tijd: ${route.durationMs / 60000} min"
                if (!viewModel.isNavigating.value) {
                    binding.instructionsPanel.visibility = View.VISIBLE
                    binding.plannerPanel.visibility = View.GONE
                }
                binding.fabPois.visibility = View.VISIBLE
            }
        }
        lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                binding.tvError.text = error ?: ""
                binding.tvError.visibility = if (error != null) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.isNavigating.collect { navigating ->
                if (navigating) {
                    binding.searchCard.visibility = View.GONE
                    binding.instructionsPanel.visibility = View.GONE
                    binding.plannerPanel.visibility = View.GONE
                    binding.navInstructionCard.visibility = View.VISIBLE
                    binding.navBottomBar.visibility = View.VISIBLE
                    viewModel.route.value?.let { route ->
                        val first = route.instructions.firstOrNull()
                        if (first != null) {
                            binding.tvCurrentInstruction.text = first.text
                            setTurnIcon(binding.ivTurnIcon, first.sign, large = true)
                        }
                        binding.tvNavRemaining.text = formatDistance(route.distanceMeters)
                        binding.tvNavEta.text = "ETA ${formatEta(route.durationMs)}"
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.navigationSession.collect { session ->
                session ?: return@collect
                updateNavigationUI(session)
            }
        }

        // Planner mode UI switching
        lifecycleScope.launch {
            viewModel.plannerMode.collect { mode ->
                when (mode) {
                    MapViewModel.PlannerMode.ADDRESS -> {
                        binding.addressModeContent.visibility = View.VISIBLE
                        binding.knooppuntenModeContent.visibility = View.GONE
                        binding.plannerPanel.visibility = View.GONE
                    }
                    MapViewModel.PlannerMode.KNOOPPUNTEN -> {
                        binding.addressModeContent.visibility = View.GONE
                        binding.knooppuntenModeContent.visibility = View.VISIBLE
                        loadKnoopuntenForCurrentViewport()
                    }
                }
            }
        }

        // Knoopunten layer updates (combines visible nodes + selected state)
        lifecycleScope.launch {
            viewModel.visibleKnoopunten.collect { nodes ->
                val selectedIds = viewModel.selectedNodes.value.map { it.id }.toSet()
                updateKnoopuntenLayer(nodes, selectedIds)
            }
        }
        lifecycleScope.launch {
            viewModel.selectedNodes.collect { selected ->
                val selectedIds = selected.map { it.id }.toSet()
                updateKnoopuntenLayer(viewModel.visibleKnoopunten.value, selectedIds)
                updateRouteList(selected)
            }
        }

        // POI layer
        lifecycleScope.launch {
            viewModel.pois.collect { pois -> updatePoiLayer(pois) }
        }
        lifecycleScope.launch {
            viewModel.poisVisible.collect { visible ->
                val tint = if (visible) R.color.route_blue else android.R.color.darker_gray
                binding.fabPois.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), tint)
                )
            }
        }

        // Cycling routes layer
        lifecycleScope.launch {
            viewModel.cyclingRoutes.collect { routes ->
                updateCyclingRoutesLayer(routes)
            }
        }
        lifecycleScope.launch {
            viewModel.showCyclingRoutes.collect { showing ->
                val tintColor = if (showing) R.color.route_blue else android.R.color.darker_gray
                binding.fabLayers.imageTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), tintColor)
                    )
            }
        }
    }

    // ── Planner route list ────────────────────────────────────────

    private fun updateRouteList(nodes: List<Knooppunt>) {
        binding.llRouteList.removeAllViews()

        if (nodes.isEmpty()) {
            binding.plannerPanel.visibility = View.GONE
            return
        }

        val dp = resources.displayMetrics.density
        val distances = viewModel.getLegDistances(nodes)
        val totalMeters = distances.sum()

        nodes.forEachIndexed { index, node ->
            // Row: [circle] [label column] [remove button]
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, (4 * dp).toInt(), 0, (4 * dp).toInt()) }
            }

            // Node number circle
            val circle = TextView(requireContext()).apply {
                text = node.ref
                textSize = 11f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                val size = (36 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                    .also { it.setMargins(0, 0, (12 * dp).toInt(), 0) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(requireContext(), R.color.route_blue))
                }
            }
            row.addView(circle)

            // Info column
            val info = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val label = TextView(requireContext()).apply {
                text = if (index == 0) getString(R.string.planner_startpunt)
                       else formatDistance(distances[index - 1])
                textSize = 13f
                setTextColor(if (index == 0) 0xFF212121.toInt() else 0xFF555555.toInt())
                if (index == 0) setTypeface(null, android.graphics.Typeface.BOLD)
            }
            info.addView(label)
            row.addView(info)

            // Remove button
            val removeBtn = TextView(requireContext()).apply {
                text = "×"
                textSize = 18f
                setTextColor(0xFF9E9E9E.toInt())
                setPadding((8 * dp).toInt(), 0, 0, 0)
                setOnClickListener { viewModel.toggleKnooppunt(node) }
            }
            row.addView(removeBtn)
            binding.llRouteList.addView(row)

            // Connector line between nodes
            if (index < nodes.size - 1) {
                val line = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (2 * dp).toInt(), (20 * dp).toInt()
                    ).also { it.setMargins((17 * dp).toInt(), 0, 0, 0) }
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.route_blue))
                }
                binding.llRouteList.addView(line)
            }
        }

        // "?" next node placeholder
        val connectorToNext = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                (2 * dp).toInt(), (20 * dp).toInt()
            ).also { it.setMargins((17 * dp).toInt(), 0, 0, 0) }
            setBackgroundColor(0xFFCCCCCC.toInt())
        }
        binding.llRouteList.addView(connectorToNext)

        val placeholder = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, (4 * dp).toInt(), 0, 0) }
        }
        val qCircle = TextView(requireContext()).apply {
            text = "?"
            textSize = 13f
            setTextColor(0xFF757575.toInt())
            gravity = android.view.Gravity.CENTER
            val size = (36 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
                .also { it.setMargins(0, 0, (12 * dp).toInt(), 0) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFEEEEEE.toInt())
                setStroke((1.5f * dp).toInt(), 0xFFAAAAAA.toInt())
            }
        }
        val nextHint = TextView(requireContext()).apply {
            text = getString(R.string.planner_next_hint)
            textSize = 13f
            setTextColor(0xFF757575.toInt())
        }
        placeholder.addView(qCircle)
        placeholder.addView(nextHint)
        binding.llRouteList.addView(placeholder)

        // Total distance
        binding.tvPlannerTotal.text = if (totalMeters >= 1000)
            "Totaal %.1f km".format(totalMeters / 1000)
        else
            "Totaal ${totalMeters.toInt()} m"

        binding.btnCalculatePlannerRoute.isEnabled = nodes.size >= 2
        binding.plannerPanel.visibility = View.VISIBLE
    }

    // ── Map layers ────────────────────────────────────────────────

    private fun addKnoopuntenLayers(style: Style) {
        style.addSource(GeoJsonSource("knooppunt-source"))
        style.addLayer(
            CircleLayer("knooppunt-circles", "knooppunt-source").withProperties(
                PropertyFactory.circleRadius(12f),
                PropertyFactory.circleColor(
                    Expression.switchCase(
                        Expression.eq(Expression.get("selected"), Expression.literal("true")),
                        Expression.literal("#0066CC"),
                        Expression.literal("#FFFFFF")
                    )
                ),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#0066CC")
            )
        )
        style.addLayer(
            SymbolLayer("knooppunt-labels", "knooppunt-source").withProperties(
                PropertyFactory.textField(Expression.get("ref")),
                PropertyFactory.textSize(10f),
                PropertyFactory.textColor(
                    Expression.switchCase(
                        Expression.eq(Expression.get("selected"), Expression.literal("true")),
                        Expression.literal("#FFFFFF"),
                        Expression.literal("#0066CC")
                    )
                ),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textAnchor("center"),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.iconAllowOverlap(true)
            )
        )
    }

    private fun addCyclingRoutesLayer(style: Style) {
        style.addSource(GeoJsonSource("cycling-routes-source"))
        // Insert below route layer so the route is always on top
        style.addLayerBelow(
            LineLayer("cycling-routes-layer", "cycling-routes-source").withProperties(
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineColor(
                    Expression.switchCase(
                        Expression.eq(Expression.get("network"), Expression.literal("ncn")),
                        Expression.literal("#FF6B35"),
                        Expression.eq(Expression.get("network"), Expression.literal("rcn")),
                        Expression.literal("#2E7D32"),
                        Expression.literal("#9E9E9E")
                    )
                )
            ),
            "route-layer"
        )
    }

    private fun updateKnoopuntenLayer(nodes: List<Knooppunt>, selectedIds: Set<Long>) {
        map?.getStyle { style ->
            val features = nodes.joinToString(",") { node ->
                val selected = if (node.id in selectedIds) "true" else "false"
                """{"type":"Feature","geometry":{"type":"Point","coordinates":[${node.lon},${node.lat}]},"properties":{"nid":${node.id},"ref":"${node.ref}","lat":${node.lat},"lon":${node.lon},"selected":"$selected"}}"""
            }
            style.getSourceAs<GeoJsonSource>("knooppunt-source")
                ?.setGeoJson("""{"type":"FeatureCollection","features":[$features]}""")
        }
    }

    private fun updateCyclingRoutesLayer(routes: List<CyclingRoute>) {
        map?.getStyle { style ->
            val features = routes.joinToString(",") { route ->
                val coordArrays = route.segments.joinToString(",") { seg ->
                    "[${seg.joinToString(",") { "[${it[0]},${it[1]}]" }}]"
                }
                val safeName = route.name.replace("\"", "'")
                """{"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[$coordArrays]},"properties":{"name":"$safeName","network":"${route.network}"}}"""
            }
            style.getSourceAs<GeoJsonSource>("cycling-routes-source")
                ?.setGeoJson("""{"type":"FeatureCollection","features":[$features]}""")
        }
    }

    // ── Navigation UI ─────────────────────────────────────────────

    private fun updateNavigationUI(session: NavigationSession) {
        val sign = session.currentInstruction.sign
        setTurnIcon(binding.ivTurnIcon, sign, large = true)

        binding.tvCurrentInstruction.text = if (session.isOffRoute) {
            getString(R.string.nav_off_route)
        } else {
            session.currentInstruction.text
        }

        binding.tvDistanceToTurn.text = formatDistance(session.distanceToTurnMeters)

        val next = session.nextInstruction
        if (next != null && !session.isOffRoute) {
            binding.ivNextTurnIcon.visibility = View.VISIBLE
            setTurnIcon(binding.ivNextTurnIcon, next.sign, large = false)
        } else {
            binding.ivNextTurnIcon.visibility = View.INVISIBLE
        }

        binding.tvNavRemaining.text = formatDistance(session.remainingDistanceMeters)
        binding.tvNavEta.text = "ETA ${formatEta(session.remainingDurationMs)}"

        announceIfNeeded(session)

        map?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(session.snappedLatLng)
                    .zoom(17.0)
                    .bearing(session.bearing.toDouble())
                    .tilt(50.0)
                    .build()
            ),
            300
        )
    }

    private fun setTurnIcon(imageView: android.widget.ImageView, sign: Int, large: Boolean) {
        if (sign == 4) {
            imageView.setImageResource(R.drawable.ic_nav_arrive)
            imageView.rotation = 0f
        } else {
            imageView.setImageResource(R.drawable.ic_nav_arrow)
            imageView.rotation = signToRotation(sign)
        }
        if (!large) {
            imageView.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.route_blue)
            )
        } else {
            imageView.imageTintList = null
        }
    }

    // ── Voice guidance ────────────────────────────────────────────

    private fun announceIfNeeded(session: NavigationSession) {
        val idx = session.currentInstructionIndex
        if (session.isOffRoute && !wasOffRoute) {
            wasOffRoute = true
            speak("Route herberekenen")
            return
        }
        if (!session.isOffRoute) wasOffRoute = false
        if (idx != lastAnnouncedIndex) {
            lastAnnouncedIndex = idx
            announcedAt200m = false
            announcedAtTurn = false
            if (session.distanceToTurnMeters > 200) {
                speak("Over ${formatDistance(session.distanceToTurnMeters)}, ${session.currentInstruction.text}")
            }
        }
        if (!announcedAt200m && session.distanceToTurnMeters in 50.0..200.0) {
            announcedAt200m = true
            speak("Over 200 meter, ${session.currentInstruction.text}")
        }
        if (!announcedAtTurn && session.distanceToTurnMeters < 20) {
            announcedAtTurn = true
            speak(session.currentInstruction.text)
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun resetTtsState() {
        lastAnnouncedIndex = -1
        announcedAt200m = false
        announcedAtTurn = false
        wasOffRoute = false
        tts?.stop()
    }

    // ── Location ──────────────────────────────────────────────────

    private fun startLocationUpdates() {
        val ok = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!ok) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateIntervalMillis(1000)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun checkAndRequestLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map?.getStyle { style -> enableLocation(style) }
        } else {
            locationPermission.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun enableLocation(style: Style) {
        map?.locationComponent?.apply {
            activateLocationComponent(
                LocationComponentActivationOptions.builder(requireContext(), style).build()
            )
            isLocationComponentEnabled = true
            cameraMode = CameraMode.NONE
            renderMode = RenderMode.NORMAL
        }
        // Zoom to user's location on first load
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location ?: return@addOnSuccessListener
            map?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(location.latitude, location.longitude))
                        .zoom(14.0)
                        .build()
                )
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun watcher(onChanged: (String) -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { onChanged(s.toString()) }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    private fun makePassthroughAdapter() = object : ArrayAdapter<String>(
        requireContext(), android.R.layout.simple_dropdown_item_1line
    ) {
        override fun getFilter() = object : Filter() {
            override fun performFiltering(c: CharSequence?) = FilterResults().apply {
                val items = (0 until count).mapNotNull { getItem(it) }
                values = items; count = items.size
            }
            override fun publishResults(c: CharSequence?, r: FilterResults?) = notifyDataSetChanged()
        }
    }

    private fun signToRotation(sign: Int): Float = when (sign) {
        0 -> 0f
        2 -> 45f
        3 -> 90f
        6 -> 135f
        8, -98 -> 180f
        -2 -> -45f
        -3 -> -90f
        -6 -> -135f
        -8 -> 180f
        else -> 0f
    }

    private fun formatDistance(meters: Double): String = when {
        meters < 100 -> "${meters.toInt()} m"
        meters < 1000 -> "${(meters / 10).toInt() * 10} m"
        else -> "%.1f km".format(meters / 1000)
    }

    private fun formatEta(remainingMs: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MILLISECOND, remainingMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }

    // ── Map layer helpers ─────────────────────────────────────────

    private fun addPoiLayer(style: Style) {
        style.addSource(GeoJsonSource("poi-source"))
        style.addLayer(
            CircleLayer("poi-circles", "poi-source").withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(
                    Expression.switchCase(
                        Expression.eq(Expression.get("amenity"), Expression.literal("cafe")),
                        Expression.literal("#795548"),
                        Expression.eq(Expression.get("amenity"), Expression.literal("restaurant")),
                        Expression.literal("#E53935"),
                        Expression.eq(Expression.get("amenity"), Expression.literal("fast_food")),
                        Expression.literal("#FB8C00"),
                        Expression.eq(Expression.get("amenity"), Expression.literal("bar")),
                        Expression.literal("#5E35B1"),
                        Expression.eq(Expression.get("amenity"), Expression.literal("pub")),
                        Expression.literal("#5E35B1"),
                        Expression.literal("#43A047")
                    )
                ),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleStrokeColor("#FFFFFF")
            )
        )
    }

    private fun updatePoiLayer(pois: List<Poi>) {
        map?.getStyle { style ->
            val features = pois.joinToString(",") { poi ->
                """{"type":"Feature","geometry":{"type":"Point","coordinates":[${poi.lon},${poi.lat}]},"properties":{"id":${poi.id},"name":"${poi.name.replace("\"","'")}","amenity":"${poi.amenity}"}}"""
            }
            style.getSourceAs<GeoJsonSource>("poi-source")
                ?.setGeoJson("""{"type":"FeatureCollection","features":[$features]}""")
        }
    }

    private fun addRouteLayer(style: Style) {
        style.addSource(GeoJsonSource("route-source"))
        style.addLayer(
            LineLayer("route-layer", "route-source").withProperties(
                PropertyFactory.lineColor("#0066CC"),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        )
    }

    private fun drawRoute(coordinates: List<List<Double>>) {
        map?.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>("route-source") ?: return@getStyle
            if (coordinates.isEmpty()) {
                source.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
                return@getStyle
            }
            val coordJson = coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
            source.setGeoJson(
                """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordJson]}}"""
            )
            if (!viewModel.isNavigating.value) {
                val latLngs = coordinates.map { LatLng(it[1], it[0]) }
                if (latLngs.size >= 2) {
                    val bounds = LatLngBounds.Builder().includes(latLngs).build()
                    map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (!viewModel.isNavigating.value) stopLocationUpdates()
    }
    override fun onStop() { super.onStop(); binding.mapView.onStop() }
    override fun onDestroyView() {
        super.onDestroyView()
        stopLocationUpdates()
        tts?.stop()
        tts?.shutdown()
        tts = null
        binding.mapView.onDestroy()
        _binding = null
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
    }
    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }
}
