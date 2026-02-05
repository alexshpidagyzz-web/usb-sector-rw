package com.example.usb_sector_rw.map

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.usb_sector_rw.R
import com.example.usb_sector_rw.measurement.GeoMeasurement
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Оптимизированный класс для отрисовки маршрута на карте
 */
class RouteDrawer(
    private val context: Context,
    private val mapView: MapView
) {
    // Основная линия маршрута
    private var routePolyline: Polyline = createDefaultPolyline()

    // Точки измерений (потокобезопасный список)
    private val measurementPoints = CopyOnWriteArrayList<Polyline>()

    // Оптимизация обновлений
    private var lastInvalidateTime = 0L
    private val INVALIDATE_THROTTLE_MS = 100L
    private val MAX_POINTS_TO_KEEP = 500

    // Блокировка для синхронизации
    private val lock = Any()

    init {
        // Добавляем полилинию на карту
        mapView.overlays.add(routePolyline)
    }

    private fun createDefaultPolyline(): Polyline {
        return Polyline().apply {
            color = ContextCompat.getColor(context, R.color.route_color)
            width = 8.0f
            outlinePaint.strokeWidth = 12.0f
            outlinePaint.color = ContextCompat.getColor(context, R.color.route_outline_color)
            isEnabled = true
        }
    }

    /**
     * Добавить точку на маршрут (оптимизированно)
     */
    fun addPointToRoute(measurement: GeoMeasurement) {
        synchronized(lock) {
            val geoPoint = GeoPoint(measurement.latitude, measurement.longitude)
            routePolyline.addPoint(geoPoint)

            // Добавляем цветную точку измерения
            addMeasurementPoint(measurement)

            // Оптимизированное обновление карты
            throttleMapInvalidate()
        }
    }

    /**
     * Троттлинг обновлений карты
     */
    private fun throttleMapInvalidate() {
        val now = System.currentTimeMillis()
        if (now - lastInvalidateTime > INVALIDATE_THROTTLE_MS) {
            mapView.postInvalidate()
            lastInvalidateTime = now
        }
    }

    /**
     * Добавить цветную точку измерения с ограничением количества
     */
    private fun addMeasurementPoint(measurement: GeoMeasurement) {
        // Ограничиваем количество хранимых точек
        if (measurementPoints.size >= MAX_POINTS_TO_KEEP) {
            val removedPoint = measurementPoints.removeAt(0)
            mapView.overlays.remove(removedPoint)
        }

        val point = createMeasurementPoint(measurement)
        measurementPoints.add(point)
        mapView.overlays.add(point)
    }

    private fun createMeasurementPoint(measurement: GeoMeasurement): Polyline {
        return Polyline().apply {
            addPoint(GeoPoint(measurement.latitude, measurement.longitude))
            color = getColorForMeasurement(measurement)
            width = 10.0f
            isEnabled = true
        }
    }

    /**
     * Получить цвет для измерения на основе уровня дозы
     */
    private fun getColorForMeasurement(measurement: GeoMeasurement): Int {
        val doseRate = measurement.doseRate

        return when {
            doseRate < 0.1f -> ContextCompat.getColor(context, R.color.dose_low)
            doseRate < 0.5f -> ContextCompat.getColor(context, R.color.dose_medium)
            doseRate < 1.0f -> ContextCompat.getColor(context, R.color.dose_high)
            else -> ContextCompat.getColor(context, R.color.dose_very_high)
        }
    }

    /**
     * Показать маршрут из сессии
     */
    fun showSessionRoute(measurements: List<GeoMeasurement>) {
        synchronized(lock) {
            clearRoute()

            measurements.forEach { measurement ->
                addPointToRoute(measurement)
            }

            // Центрируем карту на маршруте
            centerMapOnRoute()

            mapView.postInvalidate()
        }
    }

    /**
     * Очистить маршрут
     */
    fun clearRoute() {
        synchronized(lock) {
            // Очищаем точки полилинии
            routePolyline.actualPoints.clear()

            // Удаляем все точки измерений
            measurementPoints.forEach { point ->
                mapView.overlays.remove(point)
            }
            measurementPoints.clear()

            mapView.postInvalidate()
        }
    }

    /**
     * Получить количество точек на маршруте
     */
    fun getPointCount(): Int = routePolyline.actualPoints.size

    /**
     * Рассчитать длину маршрута в метрах (оптимизированно)
     */
    fun calculateRouteLength(): Float {
        val points = routePolyline.actualPoints
        if (points.size < 2) return 0f

        var totalDistance = 0f

        // Используем шаг для больших маршрутов
        val step = if (points.size > 1000) 5 else 1

        for (i in 1 until points.size step step) {
            val prevPoint = points[i - 1]
            val currentPoint = points[i]

            totalDistance += calculateDistance(prevPoint, currentPoint)
        }

        return totalDistance
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
    }

    /**
     * Центрировать карту на маршруте
     */
    fun centerMapOnRoute() {
        getRouteBounds()?.let { bounds ->
            mapView.post {
                mapView.zoomToBoundingBox(bounds, false, 50)
            }
        }
    }

    private fun getRouteBounds(): org.osmdroid.util.BoundingBox? {
        val points = routePolyline.actualPoints
        return if (points.isNotEmpty()) {
            org.osmdroid.util.BoundingBox.fromGeoPoints(points)
        } else {
            null
        }
    }

    /**
     * Удалить старые точки для оптимизации памяти
     */
    fun cleanupOldPoints(maxPoints: Int = MAX_POINTS_TO_KEEP) {
        synchronized(lock) {
            val pointsToRemove = routePolyline.actualPoints.size - maxPoints
            if (pointsToRemove > 0) {
                // Удаляем старые точки из полилинии
                val newPoints = routePolyline.actualPoints.drop(pointsToRemove)
                routePolyline.actualPoints.clear()
                routePolyline.actualPoints.addAll(newPoints)

                // Удаляем соответствующие точки измерений
                repeat(pointsToRemove) {
                    if (measurementPoints.isNotEmpty()) {
                        val point = measurementPoints.removeAt(0)
                        mapView.overlays.remove(point)
                    }
                }

                mapView.postInvalidate()
            }
        }
    }

    /**
     * Очистка ресурсов
     */
    fun cleanup() {
        synchronized(lock) {
            clearRoute()
            mapView.overlays.remove(routePolyline)
        }
    }
}