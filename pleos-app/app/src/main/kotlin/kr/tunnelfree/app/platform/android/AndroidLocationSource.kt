package kr.tunnelfree.app.platform.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kr.tunnelfree.app.platform.GeoPoint
import kr.tunnelfree.app.platform.LocationSource

class AndroidLocationSource(private val context: Context) : LocationSource {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    override fun start(onLocation: (GeoPoint) -> Unit) {
        stop()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(GeoPoint(location.latitude, location.longitude))
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = l
        try {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                onLocation(GeoPoint(it.latitude, it.longitude))
            }
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, l)
            } else if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, l)
            }
        } catch (_: SecurityException) {
        }
    }

    override fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
    }

    override fun release() = stop()
}
