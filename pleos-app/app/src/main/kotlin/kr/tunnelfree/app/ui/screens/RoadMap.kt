package kr.tunnelfree.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.tunnelfree.app.domain.LatLng
import kr.tunnelfree.app.domain.RoadRoute
import kr.tunnelfree.app.ui.UiState
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

private const val DEFAULT_LAT = 36.340
private const val DEFAULT_LNG = 127.390
private const val DEFAULT_ZOOM = 12.6
private const val DRIVE_ZOOM = 15.5

private const val ROUTE_COLOR = 0xFF1E6FD9.toInt()
private const val ROUTE_FAINT = 0x661E6FD9
private const val TUNNEL_COLOR = 0xFFD93025.toInt()
private const val COMPARE_COLOR = 0xFFE08A1E.toInt()
private const val COMPARE_FAINT = 0x66E08A1E
private const val TRAVELED_COLOR = 0xFF8A949E.toInt()
private const val VIA_COLOR = 0xFFE08A1E.toInt()
private const val GPS_COLOR = 0xFF2E9E6B.toInt()

class MapHandle {
    internal var mapView: MapView? = null
    internal var follow: Boolean = true
    internal var lastRoute: RoadRoute? = null
    internal var lastComparison: RoadRoute? = null
    internal var lastRunning: Boolean = false
    internal var tunnelsDrawn: Int = -1
    internal val overlays = MapOverlays()

    fun zoomIn() { mapView?.controller?.zoomIn() }
    fun zoomOut() { mapView?.controller?.zoomOut() }

    fun fitRoute(route: RoadRoute?) {
        val map = mapView ?: return
        follow = false
        if (route == null) {
            map.controller.animateTo(GeoPoint(DEFAULT_LAT, DEFAULT_LNG), DEFAULT_ZOOM, 600L)
            return
        }
        fitBounds(map, listOf(route))
    }
}

internal class MapOverlays {
    var tunnels: List<Polyline> = emptyList()
    lateinit var comparison: Polyline
    lateinit var routeLine: Polyline
    val routeTunnels = ArrayList<Polyline>()
    lateinit var traveled: Polyline
    lateinit var connector: Polyline
    lateinit var destination: Marker
    lateinit var via: Marker
    lateinit var gps: Marker
    lateinit var car: CarOverlay
}

@Composable
fun RoadMap(
    state: UiState,
    modifier: Modifier = Modifier,
    handle: MapHandle = remember { MapHandle() },
    comparisonOnly: Boolean = false,
    interactive: Boolean = true,
    onLongPress: ((LatLng) -> Unit)? = null,
) {
    val dark = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> handle.mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> handle.mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            handle.mapView?.onDetach()
            handle.mapView = null
        }
    }
    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { ctx ->
            createMapView(ctx, handle, interactive, onLongPress).apply {
                contentDescription = if (comparisonOnly) "비교 경로 지도" else "경로 지도"
            }
        },
        update = { map -> renderMap(map, handle, state, comparisonOnly, dark) },
    )
}

@SuppressLint("ClickableViewAccessibility")
private fun createMapView(ctx: Context, handle: MapHandle, interactive: Boolean, onLongPress: ((LatLng) -> Unit)?): MapView {
    Configuration.getInstance().apply {
        userAgentValue = ctx.packageName
        osmdroidBasePath = File(ctx.filesDir, "osmdroid")
        osmdroidTileCache = File(ctx.filesDir, "osmdroid/tiles")
    }
    val density = ctx.resources.displayMetrics.density
    val map = MapView(ctx)
    map.setTileSource(TileSourceFactory.MAPNIK)
    map.isTilesScaledToDpi = true
    map.setMultiTouchControls(interactive)
    map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    map.minZoomLevel = 9.0
    map.maxZoomLevel = 19.0
    map.controller.setZoom(DEFAULT_ZOOM)
    map.controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))
    if (interactive) {
        map.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) handle.follow = false
            false
        }
    } else {
        map.setOnTouchListener { _, _ -> true }
    }
    if (onLongPress != null) {
        map.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    val g = p ?: return false
                    onLongPress(LatLng(g.latitude, g.longitude))
                    return true
                }
            })
        )
    }

    val o = handle.overlays
    o.comparison = line(map, COMPARE_COLOR, 9f * density)
    o.routeLine = line(map, ROUTE_COLOR, 10f * density)
    o.traveled = line(map, TRAVELED_COLOR, 10f * density)
    o.connector = line(map, ROUTE_COLOR, 3f * density).apply {
        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(6f * density, 6f * density), 0f)
    }
    o.destination = pin(map, ctx, pinBitmap(density, ROUTE_COLOR))
    o.via = pin(map, ctx, pinBitmap(density, VIA_COLOR))
    o.gps = pin(map, ctx, dotBitmap(density, GPS_COLOR)).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }
    o.car = CarOverlay(arrowBitmap(density, ROUTE_COLOR), 0x331E6FD9)
    map.overlays.add(o.comparison)
    map.overlays.add(o.routeLine)
    map.overlays.add(o.traveled)
    map.overlays.add(o.connector)
    map.overlays.add(o.via)
    map.overlays.add(o.destination)
    map.overlays.add(o.gps)
    map.overlays.add(o.car)
    map.overlays.add(CopyrightOverlay(ctx))
    handle.mapView = map
    return map
}

private fun line(map: MapView, color: Int, width: Float): Polyline = Polyline(map).apply {
    outlinePaint.color = color
    outlinePaint.strokeWidth = width
    outlinePaint.strokeCap = Paint.Cap.ROUND
    outlinePaint.strokeJoin = Paint.Join.ROUND
    setInfoWindow(null)
}

private fun pin(map: MapView, ctx: Context, bitmap: Bitmap): Marker = Marker(map).apply {
    icon = BitmapDrawable(ctx.resources, bitmap)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    setInfoWindow(null)
    isEnabled = false
}

private fun renderMap(map: MapView, handle: MapHandle, state: UiState, comparisonOnly: Boolean, dark: Boolean) {
    val o = handle.overlays
    val density = map.resources.displayMetrics.density
    map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)

    if (handle.tunnelsDrawn != state.mapTunnels.size) {
        handle.tunnelsDrawn = state.mapTunnels.size
        o.tunnels.forEach { map.overlays.remove(it) }
        val insertAt = map.overlays.indexOf(o.comparison).coerceAtLeast(0)
        o.tunnels = state.mapTunnels.map { t ->
            line(map, TUNNEL_COLOR, 4f * density).apply { setPoints(t.points.map { GeoPoint(it.lat, it.lng) }) }
        }
        o.tunnels.forEachIndexed { i, p -> map.overlays.add(insertAt + i, p) }
    }

    val route = state.route
    val comparison = state.comparison
    val routeChanged = route !== handle.lastRoute
    val comparisonChanged = comparison !== handle.lastComparison
    if (routeChanged || comparisonChanged) {
        handle.lastRoute = route
        handle.lastComparison = comparison
        o.routeLine.setPoints(route?.points?.map { GeoPoint(it.lat, it.lng) } ?: emptyList())
        o.comparison.setPoints(comparison?.points?.map { GeoPoint(it.lat, it.lng) } ?: emptyList())
        if (comparisonOnly) {
            o.routeLine.outlinePaint.color = ROUTE_FAINT
            o.routeLine.outlinePaint.strokeWidth = 6f * density
            o.comparison.outlinePaint.color = COMPARE_COLOR
        } else {
            o.routeLine.outlinePaint.color = ROUTE_COLOR
            o.comparison.outlinePaint.color = COMPARE_FAINT
        }
        o.routeTunnels.forEach { map.overlays.remove(it) }
        o.routeTunnels.clear()
        val tunnelSource = if (comparisonOnly) comparison else route
        tunnelSource?.tunnels?.forEach { t ->
            val seg = line(map, TUNNEL_COLOR, 10f * density).apply { setPoints(slice(tunnelSource, t.startM, t.endM)) }
            o.routeTunnels.add(seg)
            map.overlays.add(map.overlays.indexOf(o.traveled), seg)
        }
        val toFit = listOfNotNull(route, comparison)
        if (toFit.isNotEmpty() && (comparisonOnly || !state.running)) fitBounds(map, toFit)
    }

    if (!comparisonOnly && route != null && !state.running && !state.arrived) {
        o.connector.setPoints(listOf(GeoPoint(state.origin.lat, state.origin.lng), GeoPoint(route.start.lat, route.start.lng)))
    } else {
        o.connector.setPoints(emptyList())
    }

    if (!comparisonOnly && route != null && (state.running || state.arrived)) {
        o.traveled.setPoints(slice(route, 0.0, state.progressM))
    } else {
        o.traveled.setPoints(emptyList())
    }

    val dest = state.destination
    o.destination.isEnabled = dest != null
    if (dest != null) o.destination.position = GeoPoint(dest.lat, dest.lng)
    val via = state.via
    o.via.isEnabled = via != null
    if (via != null) o.via.position = GeoPoint(via.lat, via.lng)
    val gps = state.gps
    val showGps = gps != null && !(state.running && state.simulated) &&
        gps.lat in 36.1..36.6 && gps.lng in 127.1..127.7
    o.gps.isEnabled = showGps
    if (gps != null && showGps) o.gps.position = GeoPoint(gps.lat, gps.lng)

    o.car.visible = !comparisonOnly
    o.car.position = GeoPoint(state.position.lat, state.position.lng)
    o.car.bearing = state.heading.toFloat()

    if (!comparisonOnly) {
        if (state.running && !handle.lastRunning) {
            handle.follow = true
            map.controller.setZoom(DRIVE_ZOOM)
        }
        handle.lastRunning = state.running
        if (state.running && handle.follow) map.controller.setCenter(o.car.position)
    }
    map.invalidate()
}

private fun slice(route: RoadRoute, startM: Double, endM: Double): List<GeoPoint> {
    val list = ArrayList<GeoPoint>()
    val a = route.pointAt(startM)
    list.add(GeoPoint(a.lat, a.lng))
    for (i in route.points.indices) {
        val d = route.cumulativeM[i]
        if (d > startM && d < endM) list.add(GeoPoint(route.points[i].lat, route.points[i].lng))
    }
    val b = route.pointAt(endM)
    list.add(GeoPoint(b.lat, b.lng))
    return list
}

private fun fitBounds(map: MapView, routes: List<RoadRoute>) {
    val bs = routes.map { it.bounds }
    val box = BoundingBox(bs.maxOf { it.maxLat }, bs.maxOf { it.maxLng }, bs.minOf { it.minLat }, bs.minOf { it.minLng })
    val padded = box.increaseByScale(1.25f)
    if (map.width == 0 || map.height == 0) {
        map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(padded, false, 32) }
    } else {
        map.zoomToBoundingBox(padded, true, 32)
    }
}

internal class CarOverlay(private val arrow: Bitmap, haloColor: Int) : Overlay() {
    var position: GeoPoint? = null
    var bearing: Float = 0f
    var visible: Boolean = true
    private val pt = Point()
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = haloColor }

    override fun draw(canvas: Canvas, projection: Projection) {
        if (!visible) return
        val p = position ?: return
        projection.toPixels(p, pt)
        val x = pt.x.toFloat()
        val y = pt.y.toFloat()
        canvas.drawCircle(x, y, arrow.width * 0.8f, halo)
        canvas.save()
        canvas.rotate(bearing - projection.orientation, x, y)
        canvas.drawBitmap(arrow, x - arrow.width / 2f, y - arrow.height / 2f, null)
        canvas.restore()
    }
}

private fun arrowBitmap(density: Float, color: Int): Bitmap {
    val s = (40 * density).toInt()
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val path = Path().apply {
        moveTo(s / 2f, s * 0.04f)
        lineTo(s * 0.88f, s * 0.92f)
        lineTo(s / 2f, s * 0.72f)
        lineTo(s * 0.12f, s * 0.92f)
        close()
    }
    c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL })
    c.drawPath(
        path,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeJoin = Paint.Join.ROUND
        },
    )
    return bmp
}

private fun pinBitmap(density: Float, color: Int): Bitmap {
    val w = (30 * density).toInt()
    val h = (42 * density).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = w / 2f - 1.5f * density
    val cx = w / 2f
    val cy = r + 1.5f * density
    val path = Path().apply {
        addCircle(cx, cy, r, Path.Direction.CW)
        moveTo(cx - r * 0.62f, cy + r * 0.78f)
        lineTo(cx, h - 1.5f * density)
        lineTo(cx + r * 0.62f, cy + r * 0.78f)
        close()
    }
    c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL })
    c.drawPath(
        path,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        },
    )
    c.drawCircle(cx, cy, r * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
    return bmp
}

private fun dotBitmap(density: Float, color: Int): Bitmap {
    val s = (22 * density).toInt()
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawCircle(s / 2f, s / 2f, s / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0x00FFFFFF) or 0x55000000 })
    c.drawCircle(s / 2f, s / 2f, s / 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    c.drawCircle(s / 2f, s / 2f, s / 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f * density })
    return bmp
}
