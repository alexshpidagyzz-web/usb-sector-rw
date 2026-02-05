package com.example.usb_sector_rw.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.usb_sector_rw.msd.LospDev
import java.util.concurrent.*

class MeasurementRecorder(
    private val context: Context,
    private val lospDev: LospDev? = null
) {
    companion object {
        private const val TAG = "MeasurementRecorder"
        private const val DEFAULT_INTERVAL_MS = 5000L
    }

    // Текущая сессия
    private var currentSession: MeasurementSession? = null

    // Настройки записи
    private var recordingIntervalMs: Long = DEFAULT_INTERVAL_MS
    private var isRecording = false
    private var autoSaveEnabled = true

    // Исполнители
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var scheduledFuture: ScheduledFuture<*>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Listeners для обновления UI
    private val listeners = mutableListOf<(MeasurementSession) -> Unit>()

    // Статистика
    private var sessionStartTime: Long = 0

    /**
     * Добавить слушателя для обновлений сессии
     */
    fun addSessionListener(listener: (MeasurementSession) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Удалить слушателя
     */
    fun removeSessionListener(listener: (MeasurementSession) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Уведомить всех слушателей об изменении сессии
     */
    private fun notifySessionUpdated() {
        val session = currentSession ?: return
        mainHandler.post {
            listeners.forEach { it(session) }
        }
    }

    /**
     * Начать новую сессию записи
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        sessionName: String = "",
        intervalMs: Long = DEFAULT_INTERVAL_MS
    ): MeasurementSession? {
        if (isRecording) {
            Log.w(TAG, "Запись уже идет")
            return currentSession
        }

        recordingIntervalMs = intervalMs.coerceIn(1000L, 60000L)

        val session = MeasurementSession(
            name = if (sessionName.isBlank()) MeasurementSession.generateDefaultName() else sessionName
        )

        currentSession = session
        isRecording = true
        sessionStartTime = System.currentTimeMillis()

        // Запускаем периодическую запись
        startRecordingScheduler()

        Log.i(TAG, "Запись начата: ${session.name}, интервал: ${recordingIntervalMs}ms")
        Log.d(TAG, "ID сессии: ${session.id}")
        notifySessionUpdated()
        return session
    }

    /**
     * Остановить запись текущей сессии
     */
    fun stopRecording(saveToFile: Boolean = true): MeasurementSession? {
        if (!isRecording || currentSession == null) {
            Log.w(TAG, "Нет активной сессии для остановки")
            return null
        }

        Log.d(TAG, "=== НАЧАЛО ОСТАНОВКИ ЗАПИСИ ===")
        Log.d(TAG, "Сессия: ${currentSession?.name}")
        Log.d(TAG, "ID: ${currentSession?.id}")
        Log.d(TAG, "Измерений: ${currentSession?.getMeasurementCount()}")

        // Останавливаем планировщик
        stopRecordingScheduler()

        // Завершаем сессию
        currentSession?.finish()
        isRecording = false

        // Автосохранение
        if (saveToFile && autoSaveEnabled) {
            Log.d(TAG, "Сохраняем сессию в файл...")
            try {
                executor.submit {
                    try {
                        val saved = currentSession?.saveToFile(context)
                        Log.i(TAG, "Результат сохранения: $saved")

                        // Проверяем директорию
                        val sessionsDir = MeasurementSession.getSessionsDirectory(context)
                        Log.d(TAG, "Директория сессий: ${sessionsDir.absolutePath}")

                        val files = sessionsDir.listFiles()
                        if (files != null && files.isNotEmpty()) {
                            Log.d(TAG, "Файлы в директории:")
                            files.forEach { file ->
                                Log.d(TAG, "  - ${file.name} (${file.length()} байт)")
                            }
                        } else {
                            Log.w(TAG, "Нет файлов в директории")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка сохранения сессии: ${e.message}", e)
                    }
                }
            } catch (e: RejectedExecutionException) {
                Log.e(TAG, "Executor отклонил задачу сохранения: ${e.message}")
            }
        }

        val session = currentSession!!
        Log.i(TAG, "Запись остановлена: ${session.name}, точек: ${session.getMeasurementCount()}")
        Log.d(TAG, "=== ЗАПИСЬ ОСТАНОВЛЕНА ===")
        notifySessionUpdated()
        return session
    }

    /**
     * Записать одно измерение вручную
     */
    fun recordSingleMeasurement(location: Location): GeoMeasurement? {
        if (currentSession == null) {
            val tempSession = MeasurementSession(name = "Единичное измерение")
            currentSession = tempSession
            isRecording = true
        }

        val measurement = recordMeasurement(location)
        if (measurement != null) {
            notifySessionUpdated()
        }
        return measurement
    }

    /**
     * Записать измерение (внутренний метод)
     */
    private fun recordMeasurement(location: Location): GeoMeasurement? {
        if (!isRecording || currentSession == null) return null

        val measurement = GeoMeasurement.create(location, lospDev, currentSession!!.id)
        currentSession!!.addMeasurement(measurement)

        Log.d(TAG, "Записано измерение: ${measurement.getTimeFormatted()}, " +
                "координаты: ${measurement.latitude}, ${measurement.longitude}")

        return measurement
    }

    /**
     * Запустить планировщик периодической записи
     */
    private fun startRecordingScheduler() {
        if (!isRecording) return

        scheduledFuture?.cancel(true)

        scheduledFuture = executor.scheduleAtFixedRate({
            try {
                Log.d(TAG, "Планировщик записи сработал")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка в планировщике записи: ${e.message}")
            }
        }, 0, recordingIntervalMs, TimeUnit.MILLISECONDS)

        Log.i(TAG, "Планировщик записи запущен с интервалом ${recordingIntervalMs}ms")
    }

    /**
     * Остановить планировщик
     */
    private fun stopRecordingScheduler() {
        scheduledFuture?.let {
            if (!it.isCancelled) {
                it.cancel(true)
                scheduledFuture = null
                Log.i(TAG, "Планировщик записи остановлен")
            }
        }
    }

    /**
     * Получить текущую сессию
     */
    fun getCurrentSession(): MeasurementSession? = currentSession

    /**
     * Получить статус записи
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Получить статистику записи
     */
    fun getRecordingStats(): Map<String, String> {
        val session = currentSession ?: return emptyMap()

        val elapsedTime = if (isRecording) {
            System.currentTimeMillis() - sessionStartTime
        } else {
            session.getDuration()
        }

        return mapOf(
            "Статус" to if (isRecording) "Запись..." else "Остановлено",
            "Сессия" to session.name,
            "Точек" to session.getMeasurementCount().toString(),
            "Время" to formatDuration(elapsedTime),
            "Интервал" to "${recordingIntervalMs / 1000.0} сек",
            "Расстояние" to String.format("%.2f м", session.totalDistance),
            "Ср. частота" to String.format("%.2f Гц", session.avgFrequency),
            "Ср. доза" to String.format("%.6f мкЗв/ч", session.avgDoseRate)
        )
    }

    /**
     * Форматирование времени
     */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    /**
     * Очистка ресурсов
     */
    fun cleanup() {
        Log.d(TAG, "cleanup() вызван")
        if (isRecording) {
            stopRecording(false)
        }

        try {
            executor.shutdown()
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }

        Log.i(TAG, "MeasurementRecorder очищен")
    }

    /**
     * Получить количество измерений в текущей сессии
     */
    fun getMeasurementCount(): Int = currentSession?.getMeasurementCount() ?: 0
}