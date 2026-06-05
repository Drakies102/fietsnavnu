package com.fietsrouten.pois.repository;

import com.fietsrouten.pois.model.Poi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class PoiRepository {

    // Whitelist to prevent injection via the types parameter
    private static final Set<String> ALLOWED_AMENITIES = Set.of(
            "restaurant", "cafe", "fast_food", "bar", "bakery", "ice_cream", "pub"
    );

    private final JdbcTemplate jdbc;

    public PoiRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Poi> findInBbox(double south, double west, double north, double east,
                                List<String> requestedTypes) {
        List<String> types = requestedTypes.stream()
                .filter(ALLOWED_AMENITIES::contains)
                .toList();

        if (types.isEmpty()) return Collections.emptyList();

        String placeholders = String.join(",", Collections.nCopies(types.size(), "?"));
        String sql = "SELECT id, name, amenity, lat, lon FROM pois " +
                     "WHERE lat BETWEEN ? AND ? " +
                     "AND lon BETWEEN ? AND ? " +
                     "AND amenity IN (" + placeholders + ") " +
                     "LIMIT 2000";

        Object[] params = new Object[4 + types.size()];
        params[0] = south;
        params[1] = north;
        params[2] = west;
        params[3] = east;
        for (int i = 0; i < types.size(); i++) params[4 + i] = types.get(i);

        return jdbc.query(sql, params, (rs, rowNum) -> new Poi(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("amenity"),
                rs.getDouble("lat"),
                rs.getDouble("lon")
        ));
    }
}
