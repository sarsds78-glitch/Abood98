package iq.iraqwaz.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

private val IraqGreen = Color(0xFF16845B)
private val IraqDark = Color(0xFF12231D)
private val worker = Executors.newSingleThreadExecutor()
private const val ROUTE_SOURCE_ID = "iraq-waz-route-source"
private const val ROUTE_LAYER_ID = "iraq-waz-route-layer"

data class PlaceResult(val title: String, val subtitle: String, val latitude: Double, val longitude: Double)
data class RouteSummary(val distanceKm: Double, val durationMin: Int)

class MainActivity : ComponentActivity() {
    private var mapRef: MapLibreMap? = null
    private lateinit var mapView: MapView
    private var currentLocation: LatLng? = null
    private var routeSummary by mutableStateOf<RouteSummary?>(null)
    private var statusMessage by mutableStateOf("الخريطة جاهزة — ابحث عن وجهتك")

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) moveToCurrentLocation()
        else statusMessage = "لم يتم منح إذن الموقع. يمكنك استخدام البحث يدوياً."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapRef = map
            map.setStyle(getString(R.string.map_style_url)) { style ->
                try {
                    if (style.getSource(ROUTE_SOURCE_ID) == null) style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
                    if (style.getLayer(ROUTE_LAYER_ID) == null) {
                        style.addLayer(LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                            lineColor("#16845B"), lineWidth(6f), lineCap("round"), lineJoin("round")
                        ))
                    }
                } catch (_: Exception) { statusMessage = "تعذر تجهيز طبقة المسار على الخريطة" }
            }
            map.cameraPosition = CameraPosition.Builder().target(LatLng(33.425, 43.30)).zoom(12.0).build()
        }
        mapView.onStart()
        mapView.onResume()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = IraqGreen, background = Color(0xFFF4F6F4))) {
                MapHome(
                    mapView = mapView,
                    status = statusMessage,
                    routeSummary = routeSummary,
                    onMyLocation = ::requestLocationAndCenter,
                    onSearch = ::searchPlace,
                    onChooseDestination = ::routeTo
                )
            }
        }
    }

    private fun requestLocationAndCenter() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) moveToCurrentLocation() else locationPermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    @Suppress("MissingPermission")
    private fun moveToCurrentLocation() {
        statusMessage = "جارٍ تحديد موقعك..."
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                    mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 16.0))
                    statusMessage = "تم تحديد موقعك الحالي"
                } else statusMessage = "لا يوجد موقع حديث. فعّل GPS وتحرك قليلاً ثم أعد المحاولة."
            }
            .addOnFailureListener { statusMessage = "تعذر قراءة الموقع: ${it.localizedMessage ?: "خطأ غير معروف"}" }
    }

    private fun searchPlace(query: String, callback: (List<PlaceResult>, String?) -> Unit) {
        if (query.isBlank()) { callback(emptyList(), "اكتب اسم مدينة أو مكان"); return }
        statusMessage = "جارٍ البحث عن: $query"
        worker.execute {
            try {
                val geocoder = Geocoder(this, Locale("ar", "IQ"))
                @Suppress("DEPRECATION")
                val found: List<Address> = geocoder.getFromLocationName("$query, Iraq", 8) ?: emptyList()
                val results = found.mapNotNull { a ->
                    val lat = a.latitude; val lon = a.longitude
                    if (lat.isFinite() && lon.isFinite()) PlaceResult(
                        a.featureName ?: a.getAddressLine(0) ?: query,
                        a.getAddressLine(0) ?: "العراق", lat, lon
                    ) else null
                }
                runOnUiThread {
                    statusMessage = if (results.isEmpty()) "لم نعثر على نتائج. جرّب كتابة الاسم بشكل آخر." else "تم العثور على ${results.size} نتيجة"
                    callback(results, if (results.isEmpty()) "لا توجد نتائج متاحة من خدمة البحث على هذا الجهاز" else null)
                }
            } catch (e: Exception) {
                runOnUiThread { callback(emptyList(), "تعذر البحث حالياً: ${e.localizedMessage ?: "تحقق من الإنترنت"}") }
            }
        }
    }

    private fun routeTo(destination: PlaceResult) {
        val origin = currentLocation
        if (origin == null) {
            statusMessage = "حدد موقعك أولاً حتى نحسب الطريق"
            requestLocationAndCenter()
            return
        }
        statusMessage = "جارٍ حساب الطريق إلى ${destination.title}..."
        worker.execute {
            var connection: HttpURLConnection? = null
            try {
                val endpoint = "https://router.project-osrm.org/route/v1/driving/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson"
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 12000; readTimeout = 15000
                    setRequestProperty("User-Agent", "IraqWaz-Android/0.3 (development prototype)")
                }
                if (connection.responseCode !in 200..299) error("خدمة المسارات غير متاحة (${connection.responseCode})")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val route = JSONObject(body).getJSONArray("routes").optJSONObject(0) ?: error("لم يتم العثور على طريق")
                val geometry = route.getJSONObject("geometry").getJSONArray("coordinates")
                val points = (0 until geometry.length()).map { i ->
                    val pair = geometry.getJSONArray(i)
                    LatLng(pair.getDouble(1), pair.getDouble(0))
                }
                val summary = RouteSummary(route.getDouble("distance") / 1000.0, (route.getDouble("duration") / 60.0).toInt())
                runOnUiThread {
                    drawRoute(points)
                    routeSummary = summary
                    statusMessage = "المسار محسوب تجريبياً إلى ${destination.title}"
                    mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(destination.latitude, destination.longitude), 13.0))
                }
            } catch (e: Exception) {
                runOnUiThread { statusMessage = "تعذر حساب المسار: ${e.localizedMessage ?: "تحقق من الإنترنت"}" }
            } finally { connection?.disconnect() }
        }
    }

    private fun drawRoute(points: List<LatLng>) {
        val style = mapRef?.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return
        if (points.size >= 2) {
            val line = LineString.fromLngLats(points.map { org.maplibre.geojson.Point.fromLngLat(it.longitude, it.latitude) })
            source.setGeoJson(Feature.fromGeometry(line))
        }
    }

    override fun onStart() { super.onStart(); if (::mapView.isInitialized) mapView.onStart() }
    override fun onResume() { super.onResume(); if (::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { if (::mapView.isInitialized) mapView.onPause(); super.onPause() }
    override fun onStop() { if (::mapView.isInitialized) mapView.onStop(); super.onStop() }
    override fun onDestroy() { if (::mapView.isInitialized) mapView.onDestroy(); worker.shutdown(); super.onDestroy() }
}

@Composable
private fun MapHome(
    mapView: MapView,
    status: String,
    routeSummary: RouteSummary?,
    onMyLocation: () -> Unit,
    onSearch: (String, (List<PlaceResult>, String?) -> Unit) -> Unit,
    onChooseDestination: (PlaceResult) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Surface(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp, color = Color.White
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = IraqGreen)
                Spacer(Modifier.width(10.dp))
                Text("وين تريد تروح؟", color = Color(0xFF59645F), modifier = Modifier.weight(1f))
                TextButton(onClick = { showSearch = true }) { Text("بحث") }
            }
        }
        FloatingActionButton(
            onClick = onMyLocation,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 170.dp),
            containerColor = Color.White, contentColor = IraqGreen
        ) { Icon(Icons.Default.MyLocation, contentDescription = "موقعي الحالي") }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color.White, shadowElevation = 12.dp
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("IRAQ WAZ", color = IraqGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text("نسخة تطوير 0.3", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
                Text("طريقك بالعراق صار أذكى", color = IraqDark, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(status, color = Color(0xFF68736D), style = MaterialTheme.typography.bodySmall)
                routeSummary?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("المسافة: ${"%.1f".format(it.distanceKm)} كم  •  الوقت التقديري: ${it.durationMin} دقيقة", color = IraqGreen, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { showSearch = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("ابحث عن وجهتك")
                }
            }
        }
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Text("البحث عن وجهة", modifier = Modifier.weight(1f)); IconButton(onClick = { showSearch = false }) { Icon(Icons.Default.Close, contentDescription = "إغلاق") } } },
            text = {
                Column {
                    val keyboard = LocalSoftwareKeyboardController.current
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("مدينة، شارع، مستشفى أو مكان") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searching = true; onSearch(query) { r, e -> results = r; searchError = e; searching = false }; keyboard?.hide() })
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { searching = true; onSearch(query) { r, e -> results = r; searchError = e; searching = false } }, enabled = !searching, modifier = Modifier.fillMaxWidth()) {
                        Text(if (searching) "جارٍ البحث..." else "ابحث")
                    }
                    searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(results) { place ->
                            TextButton(onClick = { showSearch = false; onChooseDestination(place) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(place.title, fontWeight = FontWeight.Bold)
                                    Text(place.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showSearch = false }) { Text("إغلاق") } }
        )
    }
}
