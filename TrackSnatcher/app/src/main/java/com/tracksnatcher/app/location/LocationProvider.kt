package com.tracksnatcher.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tracksnatcher.app.domain.model.PlaceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface LocationProvider {
    /** Best-effort coarse place, or null if permission is missing or no fix is available. */
    suspend fun currentPlace(): PlaceInfo?
}

/**
 * Resolves a coarse [PlaceInfo] via [com.google.android.gms.location.FusedLocationProviderClient]
 * and reverse-geocodes it to a "City, Region" label. Permission is checked internally so
 * callers can invoke it unconditionally and simply get null when location isn't available.
 */
@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : LocationProvider {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val geocoder by lazy { Geocoder(context, Locale.getDefault()) }

    @SuppressLint("MissingPermission") // guarded by hasLocationPermission() below
    override suspend fun currentPlace(): PlaceInfo? = withContext(ioDispatcher) {
        if (!hasLocationPermission()) return@withContext null

        val location: Location = runCatching {
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        }.getOrNull()
            ?: runCatching { fusedClient.lastLocation.await() }.getOrNull()
            ?: return@withContext null

        PlaceInfo(
            name = reverseGeocode(location.latitude, location.longitude) ?: "Unknown spot",
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    private fun hasLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        return coarse == PackageManager.PERMISSION_GRANTED || fine == PackageManager.PERMISSION_GRANTED
    }

    // The blocking Geocoder form is deprecated on API 33+ but still functional; a production
    // build should switch to the async getFromLocation(..., listener) overload there.
    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lng: Double): String? = runCatching {
        val address = geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() ?: return null
        val city = address.locality ?: address.subAdminArea ?: address.subLocality
        val region = address.adminArea
        listOfNotNull(city, region).joinToString(", ").ifBlank { null }
    }.getOrNull()
}
