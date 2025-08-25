package com.example.tesjaluroffline

import android.content.Context
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

data class LocationItem(
    val name: String,
    val lat: Double,
    val lng: Double
)

fun loadLocationsFromGeoJson(context: Context, fileName: String): List<LocationItem> {
    val list = mutableListOf<LocationItem>()
    try {
        // Baca file GeoJSON dari assets
        val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val featureCollection = FeatureCollection.fromJson(json)

        featureCollection.features()?.forEach { feature ->
            val name = feature.getStringProperty("name") ?: return@forEach

            when (val geometry = feature.geometry()) {
                is Point -> {
                    val coord = geometry
                    // INGAT: GeoJSON = [longitude, latitude]
                    list.add(LocationItem(name, coord.latitude(), coord.longitude()))
                }
                is Polygon -> {
                    val coords = geometry.coordinates().firstOrNull() ?: return@forEach
                    // ambil rata-rata sebagai titik representatif
                    val avgLat = coords.map { it.latitude() }.average()
                    val avgLon = coords.map { it.longitude() }.average()
                    list.add(LocationItem(name, avgLat, avgLon))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}
