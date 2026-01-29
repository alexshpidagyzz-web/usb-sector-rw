// Файл: app/src/main/java/com/example/usb_sector_rw/MapActivity.kt
package com.example.usb_sector_rw

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.usb_sector_rw.map.GpsTracker
import com.example.usb_sector_rw.map.MapRecordingManager
import com.example.usb_sector_rw.map.RouteDrawer
import com.example.usb_sector_rw.msd.LospDev
import com.example.usb_sector_rw.utils.permissions.PermissionHelper
import com.google.android.gms.location.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvAccuracy: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnCenterMap: Button
    private lateinit var btnBack: Button
    private lateinit var progressBar: ProgressBar

    // НОВЫЕ КОМПОНЕНТЫ ДЛЯ ЗАПИСИ
    private lateinit var btnRecord: Button
    private lateinit var btnExport: Button
    private lateinit var tvStats: TextView
    private lateinit var tvStatus: TextView
    private lateinit var recordingControls: View

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var currentMarker: Marker? = null

    // НОВЫЕ: Компоненты для записи
    private lateinit var recordingManager: MapRecordingManager
    private lateinit var gpsTracker: GpsTracker
    private lateinit var routeDrawer: RouteDrawer

    // USB устройство
    private var lospDev: LospDev? = null

    private val PERMISSION_REQUEST_CODE = 100
    private var isFirstLocationUpdate = true

    companion object {
        private const val UPDATE_INTERVAL = 5000L // 5 секунд
        private const val FASTEST_INTERVAL = 2000L // 2 секунды
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Инициализация OSM
        Configuration.getInstance().userAgentValue = packageName

        // Находим View
        mapView = findViewById(R.id.mapView)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvAccuracy = findViewById(R.id.tvAccuracy)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvTime = findViewById(R.id.tvTime)
        btnCenterMap = findViewById(R.id.btnCenterMap)
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBar)

        // НОВЫЕ VIEW ДЛЯ ЗАПИСИ
        btnRecord = findViewById(R.id.btnRecord)
        btnExport = findViewById(R.id.btnExport)
        tvStats = findViewById(R.id.tvStats)
        tvStatus = findViewById(R.id.tvStatus)
        recordingControls = findViewById(R.id.recordingControls)

        // Настройка карты
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(true)
        mapView.controller.setZoom(15.0)

        // Инициализация FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Получаем USB устройство из MainActivity
        lospDev = LospDevVariables.lospDev

        // Инициализация новых компонентов
        gpsTracker = GpsTracker(this)
        routeDrawer = RouteDrawer(this, mapView)

        // Инициализируем менеджер записи С ПЕРЕДАЧЕЙ RouteDrawer
        recordingManager = MapRecordingManager(this, mapView, myLocationOverlay, lospDev, routeDrawer)

        // Настройка callback для обновлений местоположения
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocation(location)
                    // Передаем местоположение в менеджер записи
                    recordingManager.onLocationUpdate(location)
                }
            }
        }

        // Кнопка центрирования карты
        btnCenterMap.setOnClickListener {
            centerMapOnMyLocation()
        }

        // Кнопка назад
        btnBack.setOnClickListener {
            finish()
        }

        // Инициализируем UI менеджера записи
        recordingManager.initialize(btnRecord, tvStats, tvStatus, btnExport)

        // Проверка и запрос разрешений
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (!PermissionHelper.checkLocationPermissions(this)) {
            PermissionHelper.requestLocationPermissions(this, PERMISSION_REQUEST_CODE)
        } else {
            startLocationUpdates()
            recordingControls.visibility = View.VISIBLE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = PermissionHelper.handlePermissionResult(
                requestCode,
                permissions,
                grantResults
            )

            if (allGranted) {
                startLocationUpdates()
                recordingControls.visibility = View.VISIBLE
            } else {
                Toast.makeText(
                    this,
                    "Для работы GPS-навигации необходимы разрешения",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        progressBar.visibility = View.VISIBLE

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .build()

        // Получаем последнее известное местоположение
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    updateLocation(it)
                    // Передаем первое местоположение в менеджер записи
                    recordingManager.onLocationUpdate(it)
                }
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка получения местоположения: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }

        // Запускаем обновления местоположения
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )

        // Настраиваем overlay для отображения местоположения на карте
        setupLocationOverlay()

        // Запускаем GpsTracker для дополнительного отслеживания
        startGpsTracker()
    }

    private fun startGpsTracker() {
        gpsTracker.startTracking(
            intervalMs = UPDATE_INTERVAL,
            fastestIntervalMs = FASTEST_INTERVAL,
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        ) { location ->
            // Обрабатываем обновления от GpsTracker
            if (recordingManager.isRecording()) {
                recordingManager.onLocationUpdate(location)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLocation(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude
        val accuracy = location.accuracy
        val speed = location.speed * 3.6 // конвертируем м/с в км/ч
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.time))

        // Обновляем TextView с координатами
        tvLatitude.text = "Широта: ${String.format("%.6f", latitude)}°"
        tvLongitude.text = "Долгота: ${String.format("%.6f", longitude)}°"
        tvAccuracy.text = "Точность: ${String.format("%.1f", accuracy)} м"
        tvSpeed.text = "Скорость: ${String.format("%.1f", speed)} км/ч"
        tvTime.text = "Время: $time"

        // Обновляем позицию на карте
        val geoPoint = GeoPoint(latitude, longitude)

        // Убираем старый маркер
        currentMarker?.let {
            mapView.overlays.remove(it)
        }

        // Создаем новый маркер
        currentMarker = Marker(mapView).apply {
            position = geoPoint
            title = "Текущее местоположение"
            snippet = "Точность: ${String.format("%.1f", accuracy)} м\nВремя: $time"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        mapView.overlays.add(currentMarker)

        // Центрируем карту при первом обновлении
        if (isFirstLocationUpdate) {
            mapView.controller.animateTo(geoPoint)
            isFirstLocationUpdate = false
        }

        // Обновляем позицию в overlay
        myLocationOverlay?.enableMyLocation()
    }

    private fun setupLocationOverlay() {
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
            setDrawAccuracyEnabled(true)
        }

        mapView.overlays.add(myLocationOverlay)
    }

    private fun centerMapOnMyLocation() {
        currentMarker?.position?.let { geoPoint ->
            mapView.controller.animateTo(geoPoint)
            Toast.makeText(this, "Карта центрирована на вашем местоположении", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "Местоположение еще не определено", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Показать маршрут из сессии
     */
    private fun showSessionRoute(session: com.example.usb_sector_rw.measurement.MeasurementSession) {
        routeDrawer.showSessionRoute(session.getMeasurements())
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()

        // Если запись активна, сохраняем сессию
        if (recordingManager.isRecording()) {
            showRecordingInProgressDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        gpsTracker.cleanup()
        routeDrawer.cleanup()
        recordingManager.cleanup()
    }

    /**
     * Показать диалог о том, что запись в процессе
     */
    private fun showRecordingInProgressDialog() {
        if (recordingManager.isRecording()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Запись активна")
                .setMessage("Запись измерений все еще активна. Остановить запись перед выходом?")
                .setPositiveButton("Остановить и выйти") { dialog, _ ->
                    recordingManager.stopRecording()
                    dialog.dismiss()
                    finish()
                }
                .setNegativeButton("Продолжить запись") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNeutralButton("Отмена") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    /**
     * Обработка кнопки назад
     */
    override fun onBackPressed() {
        if (recordingManager.isRecording()) {
            showRecordingInProgressDialog()
        } else {
            super.onBackPressed()
        }
    }
}