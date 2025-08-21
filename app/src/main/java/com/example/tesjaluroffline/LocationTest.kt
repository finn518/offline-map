package com.example.tesjaluroffline


data class LocationTest(val name: String, val lat: Double, val lng: Double)

val locations = listOf(
    //Semarang
    LocationTest("Semarang - Simpang Lima", -6.9779, 110.4170),

    // Surabaya
    LocationTest("Surabaya - Tugu Pahlawan", -7.2458, 112.7378),
    LocationTest("Surabaya - Bandara Juanda", -7.3797, 112.7860),

    // Malang
    LocationTest("Malang - Alun-Alun Kota", -7.9829, 112.6304),
    LocationTest("Malang - Stasiun Kota Baru", -7.9770, 112.6336),

    // Kepanjen
    LocationTest("Kepanjen - Pusat Kota", -8.1304, 112.5723),
    LocationTest("Kepanjen - Stadion Kanjuruhan", -8.1512, 112.5728),

    // UB (Universitas Brawijaya Malang)
    LocationTest("Universitas Brawijaya - Rektorat", -7.9528, 112.6145),
    LocationTest("Universitas Brawijaya - Perpustakaan Pusat", -7.9543, 112.6148),

    // Blitar
    LocationTest("Blitar - Makam Bung Karno", -8.0956, 112.1689),
    LocationTest("Blitar - Alun-Alun Blitar", -8.0990, 112.1680),

    // Kediri
    LocationTest("Kediri - Simpang Lima Gumul", -7.8231, 112.0161),
    LocationTest("Kediri - Alun-Alun Kediri", -7.8166, 112.0111)

    //Semarang

)
