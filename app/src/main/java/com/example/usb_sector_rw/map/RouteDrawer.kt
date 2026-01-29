package com.example.usb_sector_rw.map

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.usb_sector_rw.R
import com.example.usb_sector_rw.measurement.GeoMeasurement
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * Класс для отрисовки маршрута на карте
 */
class RouteDrawer(
    private val context: Context,
    private val mapView: MapView
) {

    // Основная линия маршрута
    private val routePolyline: Polyline = Polyline().apply {
        color = ContextCompat.getColor(context, R.color.route_color)
        width = 8.0f
        outlinePaint.strokeWidth = 12.0f
        outlinePaint.color = ContextCompat.getColor(context, R.color.route_outline_color)
    }

    // Точки измерений (цветные маркеры)
    private val measurementPoints: MutableList<Polyline> = mutableListOf()

    init {
        // Добавляем полилинию на карту
        mapView.overlays.add(routePolyline)
    }

    /**
     * Добавить точку на маршрут
     */
    fun addPointToRoute(measurement: GeoMeasurement) {
        val geoPoint = GeoPoint(measurement.latitude, measurement.longitude)
        routePolyline.addPoint(geoPoint)

        // Добавляем цветную точку измерения
        addMeasurementPoint(measurement)

        // Обновляем карту
        mapView.invalidate()
    }

    /**
     * Добавить цветную точку измерения
     */
    private fun addMeasurementPoint(measurement: GeoMeasurement) {
        val point = Polyline().apply {
            addPoint(GeoPoint(measurement.latitude, measurement.longitude))
            color = getColorForMeasurement(measurement)
            width = 10.0f
        }

        measurementPoints.add(point)
        mapView.overlays.add(point)
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
        clearRoute()

        measurements.forEach { measurement ->
            addPointToRoute(measurement)
        }

        // Центрируем карту на первом измерении
        if (measurements.isNotEmpty()) {
            val firstMeasurement = measurements.first()
            val geoPoint = GeoPoint(firstMeasurement.latitude, firstMeasurement.longitude)
            mapView.controller.animateTo(geoPoint)
            mapView.controller.setZoom(15.0)
        }

        mapView.invalidate()
    }

    /**
     * Очистить маршрут
     */
    fun clearRoute() {
        // Способ 1: Создаем новую полилинию
        val newPolyline = Polyline().apply {
            color = ContextCompat.getColor(context, R.color.route_color)
            width = 8.0f
            outlinePaint.strokeWidth = 12.0f
            outlinePaint.color = ContextCompat.getColor(context, R.color.route_outline_color)
        }

        // Удаляем старую полилинию и добавляем новую
        mapView.overlays.remove(routePolyline)
        routePolyline.actualPoints.clear() // Очищаем точки старой полилинии
        mapView.overlays.add(newPolyline)

        // Обновляем ссылку (этот способ требует изменения класса, чтобы routePolyline был var)
        // routePolyline = newPolyline

        // Альтернативный способ: очищаем точки напрямую
        routePolyline.actualPoints.clear()

        // Удаляем все точки измерений
        measurementPoints.forEach { point ->
            mapView.overlays.remove(point)
        }
        measurementPoints.clear()

        mapView.invalidate()
    }

    /**
     * Получить количество точек на маршруте
     */
    fun getPointCount(): Int = routePolyline.actualPoints.size

    /**
     * Установить цвет маршрута
     */
    fun setRouteColor(color: Int) {
        routePolyline.color = color
        mapView.invalidate()
    }

    /**
     * Установить ширину маршрута
     */
    fun setRouteWidth(width: Float) {
        routePolyline.width = width
        mapView.invalidate()
    }

    /**
     * Получить границы маршрута (для зума)
     */
    fun getRouteBounds(): org.osmdroid.util.BoundingBox? {
        return if (routePolyline.actualPoints.isNotEmpty()) {
            org.osmdroid.util.BoundingBox.fromGeoPoints(routePolyline.actualPoints)
        } else {
            null
        }
    }

    /**
     * Центрировать карту на маршруте
     */
    fun centerMapOnRoute() {
        getRouteBounds()?.let { bounds ->
            mapView.zoomToBoundingBox(bounds, false)
        }
    }

    /**
     * Получить цвет маршрута
     */
    fun getRouteColor(): Int = routePolyline.color

    /**
     * Получить ширину маршрута
     */
    fun getRouteWidth(): Float = routePolyline.width

    /**
     * Получить все точки маршрута
     */
    fun getRoutePoints(): List<GeoPoint> = routePolyline.actualPoints

    /**
     * Получить количество точек измерений
     */
    fun getMeasurementPointCount(): Int = measurementPoints.size

    /**
     * Удалить последнюю точку маршрута
     */
    fun removeLastPoint() {
        if (routePolyline.actualPoints.isNotEmpty()) {
            // Используем removeAt вместо removeLast для совместимости с API < 35
            val lastIndex = routePolyline.actualPoints.size - 1
            if (lastIndex >= 0) {
                routePolyline.actualPoints.removeAt(lastIndex)
            }

            // Удаляем последнюю точку измерения
            if (measurementPoints.isNotEmpty()) {
                val lastIndex = measurementPoints.size - 1
                if (lastIndex >= 0) {
                    val lastPoint = measurementPoints.removeAt(lastIndex)
                    mapView.overlays.remove(lastPoint)
                }
            }

            mapView.invalidate()
        }
    }

    /**
     * Получить статистику по цветам точек (распределение по уровням дозы)
     */
    fun getColorStatistics(): Map<String, Int> {
        val stats = mutableMapOf(
            "low" to 0,
            "medium" to 0,
            "high" to 0,
            "very_high" to 0
        )

        measurementPoints.forEach { point ->
            when (point.color) {
                ContextCompat.getColor(context, R.color.dose_low) -> stats["low"] = stats["low"]!! + 1
                ContextCompat.getColor(context, R.color.dose_medium) -> stats["medium"] = stats["medium"]!! + 1
                ContextCompat.getColor(context, R.color.dose_high) -> stats["high"] = stats["high"]!! + 1
                ContextCompat.getColor(context, R.color.dose_very_high) -> stats["very_high"] = stats["very_high"]!! + 1
            }
        }

        return stats
    }

    /**
     * Показать/скрыть точки измерений
     */
    fun setMeasurementPointsVisible(visible: Boolean) {
        measurementPoints.forEach { point ->
            point.isEnabled = visible
        }
        mapView.invalidate()
    }

    /**
     * Показать/скрыть линию маршрута
     */
    fun setRouteLineVisible(visible: Boolean) {
        routePolyline.isEnabled = visible
        mapView.invalidate()
    }

    /**
     * Изменить прозрачность маршрута
     */
    fun setRouteAlpha(alpha: Int) {
        val newColor = (alpha shl 24) or (routePolyline.color and 0x00FFFFFF)
        routePolyline.color = newColor
        mapView.invalidate()
    }

    /**
     * Экспортировать маршрут в список координат
     */
    fun exportRoutePoints(): List<Pair<Double, Double>> {
        return routePolyline.actualPoints.map { point ->
            Pair(point.latitude, point.longitude)
        }
    }

    /**
     * Рассчитать длину маршрута в метрах
     */
    fun calculateRouteLength(): Float {
        var totalDistance = 0f

        if (routePolyline.actualPoints.size < 2) {
            return totalDistance
        }

        for (i in 1 until routePolyline.actualPoints.size) {
            val prevPoint = routePolyline.actualPoints[i - 1]
            val currentPoint = routePolyline.actualPoints[i]

            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                prevPoint.latitude,
                prevPoint.longitude,
                currentPoint.latitude,
                currentPoint.longitude,
                results
            )

            totalDistance += results[0]
        }

        return totalDistance
    }

    /**
     * Получить среднюю точку маршрута
     */
    fun getRouteCenter(): GeoPoint? {
        return if (routePolyline.actualPoints.isNotEmpty()) {
            val bounds = getRouteBounds()
            if (bounds != null) {
                GeoPoint(
                    bounds.centerLatitude,
                    bounds.centerLongitude
                )
            } else {
                null
            }
        } else {
            null
        }
    }

    /**
     * Сбросить настройки маршрута к значениям по умолчанию
     */
    fun resetToDefaults() {
        routePolyline.color = ContextCompat.getColor(context, R.color.route_color)
        routePolyline.width = 8.0f
        routePolyline.outlinePaint.strokeWidth = 12.0f
        routePolyline.outlinePaint.color = ContextCompat.getColor(context, R.color.route_outline_color)
        routePolyline.isEnabled = true

        measurementPoints.forEach { point ->
            point.isEnabled = true
        }

        mapView.invalidate()
    }

    /**
     * Очистка ресурсов
     */
    fun cleanup() {
        clearRoute()
        mapView.overlays.remove(routePolyline)
    }
}