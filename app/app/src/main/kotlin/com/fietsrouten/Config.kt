package com.fietsrouten

object Config {
    // 10.0.2.2 maps to the host machine's localhost inside the Android emulator.
    // For a physical device on the same Wi-Fi, replace with your machine's LAN IP
    // (e.g. "192.168.1.100") and add that IP to res/xml/network_security_config.xml.
    const val SERVER_HOST = "10.0.2.2"

    const val GRAPHHOPPER_BASE_URL = "https://fietsnav-routing.learndelingo.nl"
    const val NOMINATIM_BASE_URL = "https://fietsnav-geocoding.learndelingo.nl"

    // Vector tile URL served by Tileserver-GL from netherlands.mbtiles
    const val MAP_STYLE_URL = "https://fietsnav-tile.learndelingo.nl/styles/basic-preview/style.json"
}
