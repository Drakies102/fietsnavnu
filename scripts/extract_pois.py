#!/usr/bin/env python3
"""
Extract POIs (restaurants, cafes, etc.) from a Netherlands OSM PBF file
into a SQLite database for the pois-service.

Usage:
    pip3 install osmium
    python3 extract_pois.py [input.pbf] [output.db]

Defaults:
    input  = data/netherlands-latest.osm.pbf
    output = data/pois.db
"""

import sys
import sqlite3
import osmium

AMENITY_TYPES = {'restaurant', 'cafe', 'fast_food', 'bar', 'bakery', 'ice_cream', 'pub'}

class PoiHandler(osmium.SimpleHandler):
    def __init__(self, cursor):
        super().__init__()
        self.cursor = cursor
        self.count = 0

    def node(self, n):
        amenity = n.tags.get('amenity')
        if amenity not in AMENITY_TYPES:
            return
        name = n.tags.get('name', '').strip()
        if not name:
            return
        self.cursor.execute(
            'INSERT OR REPLACE INTO pois (id, name, amenity, lat, lon) VALUES (?, ?, ?, ?, ?)',
            (n.id, name, amenity, round(float(n.location.lat), 7), round(float(n.location.lon), 7))
        )
        self.count += 1
        if self.count % 5000 == 0:
            print(f'  {self.count} POIs extracted...')


if __name__ == '__main__':
    pbf_path = sys.argv[1] if len(sys.argv) > 1 else 'data/netherlands-latest.osm.pbf'
    db_path  = sys.argv[2] if len(sys.argv) > 2 else 'data/pois.db'

    conn = sqlite3.connect(db_path)
    conn.execute('''
        CREATE TABLE IF NOT EXISTS pois (
            id      INTEGER PRIMARY KEY,
            name    TEXT    NOT NULL,
            amenity TEXT    NOT NULL,
            lat     REAL    NOT NULL,
            lon     REAL    NOT NULL
        )
    ''')
    conn.execute('CREATE INDEX IF NOT EXISTS idx_lat_lon ON pois (lat, lon)')
    conn.execute('CREATE INDEX IF NOT EXISTS idx_amenity ON pois (amenity)')
    conn.commit()

    print(f'Reading {pbf_path} ...')
    handler = PoiHandler(conn.cursor())
    handler.apply_file(pbf_path, locations=True)
    conn.commit()
    conn.close()

    print(f'Done — {handler.count} POIs written to {db_path}')
