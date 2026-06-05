package com.fietsrouten.pois.controller;

import com.fietsrouten.pois.model.Poi;
import com.fietsrouten.pois.repository.PoiRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pois")
public class PoiController {

    private final PoiRepository repository;

    public PoiController(PoiRepository repository) {
        this.repository = repository;
    }

    /**
     * GET /api/pois?south=52.1&west=4.8&north=52.4&east=5.1&types=cafe,restaurant
     * Returns a GeoJSON FeatureCollection.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getPois(
            @RequestParam double south,
            @RequestParam double west,
            @RequestParam double north,
            @RequestParam double east,
            @RequestParam(defaultValue = "cafe,restaurant,fast_food") String types
    ) {
        List<String> typeList = Arrays.asList(types.split(","));
        List<Poi> pois = repository.findInBbox(south, west, north, east, typeList);

        List<Map<String, Object>> features = pois.stream()
                .map(poi -> Map.<String, Object>of(
                        "type", "Feature",
                        "geometry", Map.of(
                                "type", "Point",
                                "coordinates", new double[]{poi.getLon(), poi.getLat()}
                        ),
                        "properties", Map.of(
                                "id", poi.getId(),
                                "name", poi.getName(),
                                "amenity", poi.getAmenity()
                        )
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "type", "FeatureCollection",
                "features", features
        ));
    }
}
