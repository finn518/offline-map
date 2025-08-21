package com.example.tesjaluroffline


import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.util.shapes.GHPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    //Map Libre
    private lateinit var mapView: MapView
    private lateinit var mapLibreMap: MapLibreMap
    //Seacrh
    private var searchBar: MaterialAutoCompleteTextView? = null
    private var btnSeacrh: Button? = null
    //GraphHopper
    private var hopper: GraphHopper? = null

    //Access Location
    private val LOCATION_PERMISSION_REQUEST = 1001

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: GHPoint = GHPoint(-7.9829, 112.6304)
    private lateinit var locationCallback: LocationCallback
    private var activeRouteIndex = 0
    private val allRoutePointsList = mutableListOf<List<LatLng>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Cek izin lokasi
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            startLocationUpdates()
        }

        val locationNames = locations.map { it.name }
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapview)
        searchBar = findViewById(R.id.etSearchBar)
        btnSeacrh = findViewById(R.id.btnSearch)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, locationNames)
        searchBar?.setAdapter(adapter)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        btnSeacrh?.isEnabled = false

        btnSeacrh?.setOnClickListener {
            if (hopper == null) return@setOnClickListener
            val start = currentLocation
            Log.d(TAG, start.toString())
            if (start == null) {
                Toast.makeText(this, "Lokasi saat ini belum tersedia", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = searchBar?.text.toString()
            val selected = locations.find { it.name == name }
            if (selected != null) {
                val end = GHPoint(selected.lat, selected.lng)
                getRoute(hopper!!, end, start)
                searchBar?.setText("")
            } else {
                Toast.makeText(this, "Lokasi tidak ditemukan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    currentLocation = GHPoint(loc.latitude, loc.longitude)
                    Log.d(TAG, "Lokasi terbaru: $currentLocation")

                    if (::mapLibreMap.isInitialized) {
                        val camPos = CameraPosition.Builder()
                            .target(LatLng(loc.latitude, loc.longitude))
                            .zoom(12.0)
                            .build()
                        mapLibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(camPos), 1000)
                    }

                    // optional: stop updates jika hanya ingin lokasi pertama
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Izin lokasi ditolak, menggunakan default", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.mapLibreMap = mapLibreMap
        val mbtilesPath = copyMBTilesFromAssets()
        if (mbtilesPath != null) {
            setupOfflineMap(mbtilesPath)
            loadGH()
            startLocationUpdates()
        } else {
            Log.e(TAG, "Gagal menyalin file MBTiles")
        }
    }
    private fun copyMBTilesFromAssets(): String? {
        return try {
            val internalDir = File(filesDir, "mbtiles")
            if (!internalDir.exists()) internalDir.mkdirs()

            val outputFile = File(internalDir, "OSM_Jawa.mbtiles")
            if (outputFile.exists()) return outputFile.absolutePath
            assets.open("OSM_Jawa.mbtiles").use { input ->
         FileOutputStream(outputFile).use { output ->
             val buffer = ByteArray(8192)
             var length: Int
             while (input.read(buffer).also { length = it } > 0) {
                 output.write(buffer, 0, length)
             }
         } }
            outputFile.absolutePath } catch (e: IOException) { Log.e(TAG, "Error copying MBTiles file", e)
            null
        }
    }
    private fun setupOfflineMap(mbtilesPath: String) {
        val mbtilesUrl = "mbtiles://$mbtilesPath"
        Log.d(TAG, "Setting up offline map with URL: $mbtilesUrl")

        val rasterSource = RasterSource("offline-source", TileSet("2.2.0", mbtilesUrl), 256)
        val rasterLayer = RasterLayer("offline-layer", "offline-source")

        val emptyStyleJson = """
            {
              "version": 8,
              "sources": {},
              "layers": []
            }
        """.trimIndent()

        mapLibreMap.setStyle(Style.Builder().fromJson(emptyStyleJson)) { style ->
            style.addSource(rasterSource)
            style.addLayer(rasterLayer)

            Log.d(TAG, "Offline MBTiles style loaded successfully")

            // Posisi kamera awal
            val malang = LatLng(-7.9811, 112.6304)
            val cameraPosition = CameraPosition.Builder()
                .target(malang)
                .zoom(8.0)
                .build()
            mapLibreMap.cameraPosition = cameraPosition

            mapLibreMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(malang)
                        .zoom(12.0)
                        .build()
                ), 1000
            )
        }
    }
    private fun saveGHtoInternal() {
        val assetManager = assets
        val targetDir = File(filesDir, "graph-cache")

        if (targetDir.exists()){
            return
        }

        targetDir.mkdirs()

        try {
            val file = assetManager.list("graph-cache") ?: return
            for (filename in file){
                val inputStream = assetManager.open("graph-cache/$filename")
                val outFile = File(targetDir, filename)
                val outputStream = FileOutputStream(outFile)

                val buffer = ByteArray(1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }

                inputStream.close()
                outputStream.flush()
                outputStream.close()
            }
            Log.d(TAG, "Udah ke save nih ghnya")

        }catch (e: Exception){
            e.printStackTrace()
        }
    }
    private fun loadGH(){
        lifecycleScope.launch(Dispatchers.IO){
            try {
                saveGHtoInternal()
                val ghFolder = File(filesDir, "graph-cache")
                Log.d(TAG, "udah berhasil copy gh-cache")
                val gh = GraphHopper().apply {
                    graphHopperLocation = ghFolder.absolutePath
                    EncodingManager.create("car")
                    setProfiles(Profile("car").setVehicle("car").setWeighting("fastest"))
                    chPreparationHandler.setCHProfiles(CHProfile("car"))
                    Log.d(TAG, "Mau di load nihhh")
                    Log.d(TAG, "Isi folder GH: " + ghFolder.listFiles()?.joinToString { it.name })
                }
                gh.importOrLoad()
                Log.d(TAG, "udah selesai load next apa?")
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "INI sudah ada ghnya")
                    this@MainActivity.hopper = gh
                    btnSeacrh?.isEnabled = true
                    Toast.makeText(this@MainActivity, "GraphHopper 10.2 berhasil diload!", Toast.LENGTH_SHORT).show()
                }

            }catch (e: Exception){
                Log.e(TAG, "Error waktu load GH", e)
            }
        }
    }
    private fun getRoute(gh: GraphHopper, start: GHPoint, end: GHPoint, profileName: String = "car") {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val req = GHRequest(start, end).apply {
                    setProfile(profileName)
                    algorithm = "alternative_route"
                    hints.put("alternative_route.max_paths", "2") // maksimal 2 jalur
                }
                val rsp: GHResponse = gh.route(req)

                if (rsp.hasErrors()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${rsp.errors}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val allPaths = rsp.all
                if (allPaths.isEmpty()) return@launch

                allRoutePointsList.clear()
                withContext(Dispatchers.Main) {
                    // Gambar setiap jalur dengan warna berbeda
                    allPaths.forEachIndexed { index, path ->
                        val points = path.points
                        val routePoints = mutableListOf<LatLng>()
                        for (i in 0 until points.size()) {
                            val lat = points.getLat(i)
                            val lon = points.getLon(i)
                            routePoints.add(LatLng(lat, lon))
                        }
                        allRoutePointsList.add(routePoints)
                        drawRoute(routePoints, index)
                    }

                    // Zoom kamera ke semua jalur
                    val allPointsFlat = allRoutePointsList.flatten()
                    if (allPointsFlat.isNotEmpty()) {
                        val bounds = LatLngBounds.Builder().includes(allPointsFlat).build()
                        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                    }

                    // Info jalur terbaik
                    val best = rsp.best
                    val distanceKm = best.distance / 1000.0
                    val timeMin = best.time / 1000.0 / 60.0
                    Toast.makeText(
                        this@MainActivity,
                        "Jarak jalur terbaik: %.1f km, Waktu: %.1f menit".format(distanceKm, timeMin),
                        Toast.LENGTH_LONG
                    ).show()

                    // Setup listener tap interaktif
                    setupRouteClickListener(allRoutePointsList)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error load rute", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    //Menampilkan jalur, jika active maka berwarna biru jika tidak maka berwarna abu abu
    private fun drawRoute(routePoints: List<LatLng>, index: Int) {
        val lineString = LineString.fromLngLats(
            routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        )

        val sourceId = "route-source-$index"
        val layerId = "route-layer-$index"

        mapLibreMap?.getStyle { style ->
            val geoJsonSource = GeoJsonSource(sourceId, FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(lineString))))
            if (style.getSource(sourceId) == null) {
                style.addSource(geoJsonSource)
            } else {
                (style.getSource(sourceId) as GeoJsonSource).setGeoJson(lineString)
            }

            if (style.getLayer(layerId) == null) {
                val lineLayer = LineLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.lineColor(if (index == activeRouteIndex) Color.BLUE else Color.LTGRAY),
                        PropertyFactory.lineWidth(6f)
                    )
                }
                style.addLayer(lineLayer)
            } else {
                (style.getLayer(layerId) as LineLayer).setProperties(
                    PropertyFactory.lineColor(if (index == activeRouteIndex) Color.BLUE else Color.LTGRAY)
                )
            }
        }
    }

    //ketika ditekan akan merubah nilai activeroute
    private fun setupRouteClickListener(allRoutePointsList: List<List<LatLng>>) {
        mapLibreMap?.addOnMapClickListener { tapPoint ->
            var closestIndex = -1
            var minDistance = Float.MAX_VALUE

            allRoutePointsList.forEachIndexed { index, routePoints ->
                for (i in 0 until routePoints.size - 1) {
                    val start = routePoints[i]
                    val end = routePoints[i + 1]
                    val distance = distanceToSegment(tapPoint.latitude, tapPoint.longitude, start, end)
                    if (distance < minDistance) {
                        minDistance = distance
                        closestIndex = index
                    }
                }
            }

            if (minDistance < 0.0005) { // threshold ~50m
                activeRouteIndex = closestIndex
                mapLibreMap?.getStyle { style ->
                    allRoutePointsList.forEachIndexed { index, _ ->
                        val layer = style.getLayer("route-layer-$index") as? LineLayer ?: return@forEachIndexed
                        layer.setProperties(
                            PropertyFactory.lineColor(if (index == activeRouteIndex) Color.BLUE else Color.LTGRAY)
                        )
                    }
                }
            }
            true
        }
    }

    //Menghitung jarak tap dan jalur yang terdekat
    private fun distanceToSegment(lat: Double, lon: Double, start: LatLng, end: LatLng): Float {
        val x0 = lon; val y0 = lat
        val x1 = start.longitude; val y1 = start.latitude
        val x2 = end.longitude; val y2 = end.latitude

        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) return distance(lat, lon, start.latitude, start.longitude)

        val t = ((x0 - x1) * dx + (y0 - y1) * dy) / (dx*dx + dy*dy)
        return when {
            t < 0 -> distance(lat, lon, y1, x1)
            t > 1 -> distance(lat, lon, y2, x2)
            else -> distance(lat, lon, y1 + t*dy, x1 + t*dx)
        }
    }


    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLat = lat1 - lat2
        val dLon = lon1 - lon2
        return Math.sqrt(dLat*dLat + dLon*dLon).toFloat()
    }




    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}