package com.fietsrouten.ui.map

import android.Manifest
import android.util.Log
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.fietsrouten.Config
import com.fietsrouten.databinding.FragmentMapBinding
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
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private var map: MapLibreMap? = null

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
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.setStyle(Config.MAP_STYLE_URL) { style ->
                addRouteLayer(style)
                checkAndRequestLocation()
            }
            mapLibreMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(52.3676, 4.9041)) // Amsterdam
                .zoom(10.0)
                .build()
        }

        setupSearch()
        observeViewModel()
    }

    private fun setupSearch() {
        binding.actvFrom.addTextChangedListener(watcher("FROM") { viewModel.searchFrom(it) })
        binding.actvTo.addTextChangedListener(watcher("TO") { viewModel.searchTo(it) })
        binding.btnRoute.setOnClickListener { viewModel.calculateRoute() }
    }

    private fun watcher(tag: String, onChanged: (String) -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            val text = s.toString()
            Log.d("Autocomplete", "[$tag] typed: \"$text\" (length=${text.length})")
            onChanged(text)
        }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    // ArrayAdapter that skips the built-in prefix filter so Nominatim results are shown as-is.
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
                Log.d("Autocomplete", "[FROM] got ${suggestions.size} results: ${suggestions.map { it.displayName }}")
                fromResults = suggestions
                fromAdapter.setNotifyOnChange(false)
                fromAdapter.clear()
                fromAdapter.addAll(suggestions.map { it.displayName })
                fromAdapter.notifyDataSetChanged()
                val hasFocus = binding.actvFrom.hasFocus()
                Log.d("Autocomplete", "[FROM] hasFocus=$hasFocus → ${if (suggestions.isNotEmpty() && hasFocus) "showDropDown" else "skip"}")
                if (suggestions.isNotEmpty() && hasFocus) binding.actvFrom.showDropDown()
            }
        }
        lifecycleScope.launch {
            viewModel.toSuggestions.collect { suggestions ->
                Log.d("Autocomplete", "[TO] got ${suggestions.size} results: ${suggestions.map { it.displayName }}")
                toResults = suggestions
                toAdapter.setNotifyOnChange(false)
                toAdapter.clear()
                toAdapter.addAll(suggestions.map { it.displayName })
                toAdapter.notifyDataSetChanged()
                val hasFocus = binding.actvTo.hasFocus()
                Log.d("Autocomplete", "[TO] hasFocus=$hasFocus → ${if (suggestions.isNotEmpty() && hasFocus) "showDropDown" else "skip"}")
                if (suggestions.isNotEmpty() && hasFocus) binding.actvTo.showDropDown()
            }
        }
        lifecycleScope.launch {
            viewModel.route.collect { route ->
                route ?: return@collect
                drawRoute(route.coordinates)
                val km = "%.1f km".format(route.distanceMeters / 1000)
                val min = "${route.durationMs / 60000} min"
                binding.tvDistance.text = "Afstand: $km"
                binding.tvDuration.text = "Tijd: $min"
                binding.instructionsPanel.visibility = View.VISIBLE
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
            val coordJson = coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
            source.setGeoJson(
                """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordJson]}}"""
            )
            val latLngs = coordinates.map { LatLng(it[1], it[0]) }
            if (latLngs.size >= 2) {
                val bounds = LatLngBounds.Builder().includes(latLngs).build()
                map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
            }
        }
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
    }

    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onStop() { super.onStop(); binding.mapView.onStop() }
    override fun onDestroyView() {
        super.onDestroyView()
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
