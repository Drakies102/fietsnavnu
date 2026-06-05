#!/usr/bin/env python3
"""
Extract cycling knooppunten (rcn_ref nodes) from a Netherlands OSM PBF file
and write them as a GeoJSON FeatureCollection ready to bundle in the app.

Usage:
    pip3 install osmium
    python3 extract_knoopunten.py [input.pbf] [output.geojson]

Defaults:
    input  = data/netherlands-latest.osm.pbf
    output = app/app/src/main/assets/knoopunten.geojson
"""

import sys
import json
import osmium

class KnooppuntHandler(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.features = []

    def node(self, n):
        if 'rcn_ref' not in n.tags:
            return
        ref = n.tags['rcn_ref'].strip()
        if not ref:
            return
        lat = round(float(n.location.lat), 7)
        lon = round(float(n.location.lon), 7)
        self.features.append({
            "type": "Feature",
            "geometry": {
                "type": "Point",
                "coordinates": [lon, lat]
            },
            "properties": {
                "nid": n.id,
                "ref": ref,
                "lat": lat,
                "lon": lon
            }
        })

if __name__ == "__main__":
    pbf_path = sys.argv[1] if len(sys.argv) > 1 else "data/netherlands-latest.osm.pbf"
    out_path = sys.argv[2] if len(sys.argv) > 2 else "app/app/src/main/assets/knoopunten.geojson"

    print(f"Reading {pbf_path} ...")
    handler = KnooppuntHandler()
    handler.apply_file(pbf_path, locations=True)

    geojson = {
        "type": "FeatureCollection",
        "features": handler.features
    }

    import os
    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(geojson, f, separators=(",", ":"))

    print(f"Done — {len(handler.features)} knooppunten written to {out_path}")
