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
import com.example.usb_sector_rw.measurement.*
import com.example.usb_sector_rw.msd.LospDev
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import android.util.Log

class MapRecordingManager(
    private val context: Context,
    private val mapView: MapView,
    private val locationOverlay: MyLocationNewOverlay?,
    private val lospDev: LospDev?,
    private val routeDrawer: RouteDrawer // Изменил OptimizeRouteDrawer на RouteDrawer
) {

    companion object {
        private const val TAG = "MapRecordingManager"
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val DEBOUNCE_DELAY_MS = 500L
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

    // Синхронизация
    private val recordingLock = ReentrantLock()
    private val isProcessing = AtomicBoolean(false)

    // Состояние
    private var isInitialized = false
    private var lastButtonClickTime = 0L

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
        measurementRecorder.addSessionListener { session: MeasurementSession? ->
            updateStatsDisplay()
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
     * Настройка кнопок с защитой от двойного нажатия
     */
    private fun setupButtons() {
        // Кнопка записи с debounce
        recordButton.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastButtonClickTime < DEBOUNCE_DELAY_MS) {
                return@setOnClickListener
            }
            lastButtonClickTime = now

            if (isProcessing.compareAndSet(false, true)) {
                try {
                    if (measurementRecorder.isRecording()) {
                        stopRecording()
                    } else {
                        startRecording()
                    }
                } catch (e: Exception) {
                    statusTextView.text = "Ошибка: ${e.message}"
                } finally {
                    isProcessing.set(false)
                }
            }
        }

        // Кнопка экспорта
        exportButton.setOnClickListener {
            exportCurrentSession()
        }
    }

    /**
     * Начать запись (синхронизировано)
     */
    fun startRecording(sessionName: String = "") {
        recordingLock.lock()
        try {
            if (measurementRecorder.isRecording()) {
                return
            }

            measurementRecorder.startRecording(sessionName)
            updateUIForRecording(true)
            statusTextView.text = "Запись..."
            statusTextView.setTextColor(ContextCompat.getColor(context, R.color.recording_active))

            // Очищаем маршрут при начале новой записи
            routeDrawer.clearRoute()
        } finally {
            recordingLock.unlock()
        }
    }

    /**
     * Остановить запись (синхронизировано)
     */
    fun stopRecording() {
        recordingLock.lock()
        try {
            Log.d(TAG, "MapRecordingManager.stopRecording() вызван")

            if (!measurementRecorder.isRecording()) {
                Log.w(TAG, "Запись уже остановлена")
                return
            }

            Log.d(TAG, "=== ОСТАНОВКА ЗАПИСИ ИЗ MapRecordingManager ===")

            // Останавливаем запись через MeasurementRecorder
            measurementRecorder.stopRecording()

            // Обновляем UI
            updateUIForRecording(false)
            statusTextView.text = "Остановлено"
            statusTextView.setTextColor(ContextCompat.getColor(context, R.color.recording_inactive))

            Log.d(TAG, "=== ЗАПИСЬ ОСТАНОВЛЕНА (MapRecordingManager) ===")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при остановке записи: ${e.message}", e)
            statusTextView.text = "Ошибка остановки: ${e.message}"
        } finally {
            recordingLock.unlock()
        }
    }

    /**
     * Записать одно измерение (синхронизировано)
     */
    fun recordSingleMeasurement(location: Location) {
        if (!measurementRecorder.isRecording()) return

        recordingLock.lock()
        try {
            measurementRecorder.recordSingleMeasurement(location)?.let { measurement: GeoMeasurement ->
                // Добавляем точку на маршрут
                routeDrawer.addPointToRoute(measurement)
            }
        } finally {
            recordingLock.unlock()
        }
    }

    /**
     * Обновить местоположение (с проверкой блокировки)
     */
    fun onLocationUpdate(location: Location) {
        if (measurementRecorder.isRecording() && recordingLock.tryLock()) {
            try {
                // Автоматическая запись при движении
                recordSingleMeasurement(location)
            } finally {
                recordingLock.unlock()
            }
        }
    }

    /**
     * Обновить отображение статистики
     */
    private fun updateStatsDisplay() {
        val stats = measurementRecorder.getRecordingStats()
        val session = measurementRecorder.getCurrentSession()
        val measurementCount = session?.getMeasurementCount() ?: 0

        val statsText = buildString {
            append("Статус: ${stats["Статус"] ?: "Неактивно"}\n")
            append("Точек: $measurementCount\n")

            if (measurementCount > 0) {
                val distance = stats["Расстояние"] ?: "0.00"
                val avgFreq = stats["Ср. частота"] ?: "0.00"
                val avgDose = stats["Ср. доза"] ?: "0.000000"

                append("Расстояние: $distance м\n")
                append("Ср. частота: $avgFreq Гц\n")
                append("Ср. доза: $avgDose мкЗв/ч")
            }
        }

        uiHandler.post {
            statsTextView.text = statsText
            updateExportButtonVisibility(measurementCount)
        }
    }

    /**
     * Обновить видимость кнопки экспорта
     */
    private fun updateExportButtonVisibility(measurementCount: Int) {
        exportButton.visibility = if (measurementCount > 0) {
            View.VISIBLE
        } else {
            View.GONE
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

        // Экспортируем в отдельном потоке
        Thread {
            try {
                val exporter = EnhancedFileExporter(context)
                val file = exporter.saveSession(session)

                uiHandler.post {
                    if (file.exists()) {
                        statusTextView.text = "Экспортировано: ${file.name}"
                    } else {
                        statusTextView.text = "Ошибка экспорта"
                    }
                }
            } catch (e: Exception) {
                uiHandler.post {
                    statusTextView.text = "Ошибка: ${e.message}"
                }
            }
        }.start()
    }

    /**
     * Очистка ресурсов
     */
    fun cleanup() {
        uiHandler.removeCallbacks(updateStatsRunnable)
        measurementRecorder.cleanup()
        // routeDrawer.cleanup() // Убрал, т.к. у RouteDrawer нет метода cleanup
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