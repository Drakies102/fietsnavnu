#!/usr/bin/env bash
# Downloads Netherlands OSM data and generates vector tiles.
# Run this once before starting docker compose.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="$SCRIPT_DIR/../data"

mkdir -p "$DATA_DIR"

PBF="$DATA_DIR/netherlands-latest.osm.pbf"
MBTILES="$DATA_DIR/netherlands.mbtiles"

if [ ! -f "$PBF" ]; then
  echo "Downloading Netherlands OSM extract (~900 MB)..."
  curl -L --progress-bar \
    -o "$PBF" \
    "https://download.geofabrik.de/europe/netherlands-latest.osm.pbf"
  echo "Download complete."
else
  echo "OSM extract already present, skipping download."
fi

if [ ! -f "$MBTILES" ]; then
  echo ""
  echo "Generating vector tiles with Tilemaker (this takes 10–30 minutes)..."
  docker run --rm \
    -v "$DATA_DIR:/data" \
    ghcr.io/systemed/tilemaker:master \
    --input /data/netherlands-latest.osm.pbf \
    --output /data/netherlands.mbtiles \
    --bbox 3.3,50.7,7.3,53.6
  echo "Tile generation complete."
else
  echo "MBTiles file already present, skipping tile generation."
fi

echo ""
echo "All data ready. Start the stack with:"
echo "  docker compose up -d"
