package com.example.usb_sector_rw.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.usb_sector_rw.R
import com.example.usb_sector_rw.measurement.MeasurementRecorder
import com.example.usb_sector_rw.measurement.MeasurementSession
import com.example.usb_sector_rw.msd.LospDev
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapRecordingManager(
    private val context: Context,
    private val mapView: MapView,
    private val locationOverlay: MyLocationNewOverlay?,
    private val lospDev: LospDev?,
    private val routeDrawer: RouteDrawer
) {

    companion object {
        private const val TAG = "MapRecordingManager"
        private const val UPDATE_INTERVAL_MS = 1000L // Обновление каждую секунду
    }

    // UI компоненты
    private lateinit var recordButton: Button
    private lateinit var statsTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var exportButton: Button

    // Recorder
    private lateinit var measurementRecorder: MeasurementRecorder

    // Handler для обновления UI
    private val uiHandler = Handler(Looper.getMainLooper())
    private val updateStatsRunnable = object : Runnable {
        override fun run() {
            updateStatsDisplay()
            uiHandler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    // Состояние
    private var isInitialized = false

    /**
     * Инициализация
     */
    @SuppressLint("SetTextI18n")
    fun initialize(
        recordBtn: Button,
        statsText: TextView,
        statusText: TextView,
        exportBtn: Button
    ) {
        if (isInitialized) return

        // Сохраняем ссылки на UI компоненты
        recordButton = recordBtn
        statsTextView = statsText
        statusTextView = statusText
        exportButton = exportBtn

        // Инициализируем Recorder
        measurementRecorder = MeasurementRecorder(context, lospDev)

        // Добавляем слушателя для обновления UI при изменении сессии
        measurementRecorder.addSessionListener { session ->
            updateStatsDisplay() // ОБНОВЛЯЕМ статистику при каждом новом измерении
        }

        // Настраиваем кнопки
        setupButtons()

        // Изначальное состояние
        updateUIForRecording(false)
        updateStatsDisplay()

        // Запускаем обновление статистики
        uiHandler.post(updateStatsRunnable)

        isInitialized = true
    }

    /**
     * Настройка кнопок
     */
    private fun setupButtons() {
        // Кнопка записи - ИСПРАВЛЕНО: добавлена защита от двойного нажатия
        recordButton.setOnClickListener {
            recordButton.isEnabled = false // Блокируем кнопку

            try {
                if (measurementRecorder.isRecording()) {
                    stopRecording()
                } else {
                    startRecording()
                }
            } catch (e: Exception) {
                statusTextView.text = "Ошибка: ${e.message}"
            } finally {
                // Разблокируем кнопку через 500мс
                uiHandler.postDelayed({ recordButton.isEnabled = true }, 500)
            }
        }

        // Кнопка экспорта
        exportButton.setOnClickListener {
            exportCurrentSession()
        }
    }

    /**
     * Начать запись
     */
    fun startRecording(sessionName: String = "") {
        measurementRecorder.startRecording(sessionName)
        updateUIForRecording(true)
        statusTextView.text = "Запись..."
        statusTextView.setTextColor(ContextCompat.getColor(context, R.color.recording_active))

        // Очищаем маршрут при начале новой записи
        routeDrawer.clearRoute()
    }

    /**
     * Остановить запись
     */
    fun stopRecording() {
        try {
            measurementRecorder.stopRecording()
            updateUIForRecording(false)
            statusTextView.text = "Остановлено"
            statusTextView.setTextColor(ContextCompat.getColor(context, R.color.recording_inactive))
        } catch (e: Exception) {
            statusTextView.text = "Ошибка остановки: ${e.message}"
        }
    }

    /**
     * Записать одно измерение
     */
    fun recordSingleMeasurement(location: Location) {
        measurementRecorder.recordSingleMeasurement(location)?.let { measurement ->
            // Добавляем точку на маршрут
            routeDrawer.addPointToRoute(measurement)
            // Статистика обновится автоматически через слушателя
        }
    }

    /**
     * Обновить местоположение
     */
    fun onLocationUpdate(location: Location) {
        if (measurementRecorder.isRecording()) {
            // Автоматическая запись при движении
            recordSingleMeasurement(location)
        }
    }

    /**
     * Обновить отображение статистики - ИСПРАВЛЕНО: показывает актуальное количество точек
     */
    private fun updateStatsDisplay() {
        val stats = measurementRecorder.getRecordingStats()

        // Получаем актуальное количество точек ИЗ ТЕКУЩЕЙ СЕССИИ
        val session = measurementRecorder.getCurrentSession()
        val measurementCount = session?.getMeasurementCount() ?: 0

        val statsText = buildString {
            append("Статус: ${stats["Статус"] ?: "Неактивно"}\n")
            append("Точек: $measurementCount\n") // ВСЕГДА показывает актуальное количество

            if (measurementCount > 0) {
                val distance = stats["Расстояние"] ?: "0.00"
                val avgFreq = stats["Ср. частота"] ?: "0.00"
                val avgDose = stats["Ср. доза"] ?: "0.000000"

                append("Расстояние: $distance м\n")
                append("Ср. частота: $avgFreq Гц\n")
                append("Ср. доза: $avgDose мкЗв/ч")
            }
        }

        statsTextView.text = statsText

        // Обновляем видимость кнопки экспорта
        updateExportButtonVisibility(measurementCount)
    }

    /**
     * Обновить видимость кнопки экспорта
     */
    private fun updateExportButtonVisibility(measurementCount: Int) {
        uiHandler.post {
            exportButton.visibility = if (measurementCount > 0) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    /**
     * Обновить UI в зависимости от состояния записи
     */
    private fun updateUIForRecording(isRecording: Boolean) {
        uiHandler.post {
            if (isRecording) {
                recordButton.text = "СТОП"
                recordButton.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.recording_active)
                )
                recordButton.setTextColor(
                    ContextCompat.getColor(context, android.R.color.white)
                )
            } else {
                recordButton.text = "ЗАПИСЬ"
                recordButton.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.recording_inactive)
                )
                recordButton.setTextColor(
                    ContextCompat.getColor(context, android.R.color.black)
                )
            }
        }
    }

    /**
     * Экспортировать текущую сессию
     */
    private fun exportCurrentSession() {
        val session = measurementRecorder.getCurrentSession()
        if (session == null || session.getMeasurementCount() == 0) {
            statusTextView.text = "Нет данных для экспорта"
            return
        }

        statusTextView.text = "Экспорт..."

        // Экспортируем в CSV
        val exporter = com.example.usb_sector_rw.measurement.FileExporter(context)
        val result = exporter.exportSessionToCsv(session)

        if (result.isSuccess) {
            statusTextView.text = "Экспортировано: ${result.fileName}"
        } else {
            statusTextView.text = "Ошибка экспорта: ${result.errorMessage}"
        }
    }

    /**
     * Очистка ресурсов
     */
    fun cleanup() {
        uiHandler.removeCallbacks(updateStatsRunnable)
        measurementRecorder.cleanup()
        routeDrawer.clearRoute()
    }

    /**
     * Получить состояние записи
     */
    fun isRecording(): Boolean = measurementRecorder.isRecording()

    /**
     * Получить текущую сессию
     */
    fun getCurrentSession(): MeasurementSession? = measurementRecorder.getCurrentSession()

    /**
     * Получить количество измерений
     */
    fun getMeasurementCount(): Int = measurementRecorder.getMeasurementCount()
}