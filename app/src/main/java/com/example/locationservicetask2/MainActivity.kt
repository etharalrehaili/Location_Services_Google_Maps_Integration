package com.example.locationservicetask2

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.locationservicetask2.ui.theme.LocationServiceTask2Theme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale
import kotlin.collections.isNotEmpty

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocationServiceTask2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MapScreenWithLocation()
//                        BottomCard()
//                        GetCurrentLocation()
                    }
                }
            }
        }
    }
}

@Composable
fun MapScreenWithLocation() {
    val context = LocalContext.current
    var latitude by remember { mutableStateOf(0.0) }  // Initialize with default values
    var longitude by remember { mutableStateOf(0.0) }

    // Get the current location
    GetCurrentLocation { newLatitude, newLongitude ->
        latitude = newLatitude
        longitude = newLongitude
    }

    if (latitude != 0.0 && longitude != 0.0) {
        MapScreen(latitude, longitude)
    } else {
        Text("Fetching current location...", style = MaterialTheme.typography.bodyLarge, color = Color.Black)
    }
}

@Composable
fun MapScreen(latitude: Double, longitude: Double) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 15f)
    }

    var uiSettings by remember {
        mutableStateOf(MapUiSettings(zoomControlsEnabled = true))
    }
    var properties by remember {
        mutableStateOf(MapProperties(mapType = MapType.SATELLITE))
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings
    ) {
        // Marker on the map
        Marker(
            state = MarkerState(position = LatLng(latitude, longitude)),
            title = "Current Location"
        )
    }
}

@Composable
fun GetCurrentLocation(onLocationFetched: (Double, Double) -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf("Fetching address...") }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchLocationAndAddress(context, fusedLocationClient) { location, fetchedAddress ->
                address = fetchedAddress ?: "Unable to fetch address"
                location?.let {
                    onLocationFetched(it.latitude, it.longitude)  // Pass lat/long to callback
                }
            }
        } else {
            address = "Permission denied"
        }
    }

    LaunchedEffect(Unit) {
        when (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            PermissionChecker.PERMISSION_GRANTED -> {
                fetchLocationAndAddress(context, fusedLocationClient) { location, fetchedAddress ->
                    address = fetchedAddress ?: "Unable to fetch address"
                    location?.let {
                        onLocationFetched(it.latitude, it.longitude)
                    }
                }
            }
            else -> locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 64.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(text = "Current Location: \n" + address, style = MaterialTheme.typography.bodyLarge, color = Color.Black)
    }
}

@SuppressLint("MissingPermission")
private fun fetchLocationAndAddress(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    onLocationAndAddressFetched: (android.location.Location?, String?) -> Unit
) {
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        location?.let {
            getAddressFromLocation(context, location) { address ->
                onLocationAndAddressFetched(location, address)
            }
        } ?: onLocationAndAddressFetched(null, "Location not found")
    }.addOnFailureListener {
        onLocationAndAddressFetched(null, "Error fetching location: ${it.message}")
    }
}

private fun getAddressFromLocation(
    context: Context,
    location: android.location.Location,
    onAddressFetched: (String?) -> Unit
) {
    val geocoder = Geocoder(context, Locale.getDefault())
    val latitude = location.latitude
    val longitude = location.longitude

    try {
        val addresses: MutableList<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
        if (addresses != null) {
            if (addresses.isNotEmpty()) {
                val address = addresses[0].getAddressLine(0)
                onAddressFetched(address)
            } else {
                onAddressFetched("No address found")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onAddressFetched("Error fetching address: ${e.message}")
    }
}


@Composable
fun BottomCard() {
    // Get screen height
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val offsetY = with(LocalDensity.current) { (screenHeight / 2.8f).toPx().toInt() } // Convert Dp to pixels

    Box(modifier = Modifier.fillMaxSize()) {
        // Card positioned half from the bottom
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(x = 0, y = offsetY) } // Offset using calculated pixel value
                .fillMaxSize(), // Adjust card width as needed
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.LightGray)
        )  {
            // Card content
            Text(
                text = "This is a half-screen rounded card!",
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                color = Color.Black
            )
        }
    }
}