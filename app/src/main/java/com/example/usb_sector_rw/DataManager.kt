// Файл: app/src/main/java/com/example/usb_sector_rw/utils/DataManager.kt
package com.example.usb_sector_rw.utils

import android.content.Context
import android.location.Location
import com.example.usb_sector_rw.measurement.GeoMeasurement
import com.example.usb_sector_rw.measurement.MeasurementSession
import com.example.usb_sector_rw.msd.LospDev
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Улучшенный DataManager для работы с данными измерений
 */
object DataManager {

    /**
     * Data class для статистики измерений
     */
    data class Statistics(
        val totalSessions: Int,
        val totalMeasurements: Int,
        val totalDistance: Float,
        val totalDuration: Long,
        val lastSessionDate: Long
    )

    // Текущая активная сессия
    private var currentSession: MeasurementSession? = null

    /**
     * Начать новую сессию измерений
     */
    fun startNewSession(sessionName: String = ""): MeasurementSession {
        val name = if (sessionName.isBlank()) {
            "Измерение ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}"
        } else {
            sessionName
        }

        currentSession = MeasurementSession(name = name)
        return currentSession!!
    }

    /**
     * Добавить измерение в текущую сессию
     */
    fun addMeasurement(
        location: Location,
        lospDev: LospDev? = null
    ): GeoMeasurement? {
        val session = currentSession ?: return null

        val measurement = GeoMeasurement.create(location, lospDev, session.id)
        session.addMeasurement(measurement)

        return measurement
    }

    /**
     * Остановить текущую сессию
     */
    fun stopCurrentSession(context: Context): MeasurementSession? {
        currentSession?.let { session ->
            session.finish()
            session.saveToFile(context)

            val stoppedSession = session
            currentSession = null
            return stoppedSession
        }
        return null
    }

    /**
     * Получить текущую сессию
     */
    fun getCurrentSession(): MeasurementSession? = currentSession

    /**
     * Проверить, активна ли сессия
     */
    fun isSessionActive(): Boolean = currentSession != null

    /**
     * Получить количество измерений в текущей сессии
     */
    fun getCurrentMeasurementCount(): Int = currentSession?.getMeasurementCount() ?: 0

    /**
     * Сохранить текущую сессию в файл
     */
    fun saveCurrentSession(context: Context): Boolean {
        return currentSession?.saveToFile(context) ?: false
    }

    /**
     * Очистить текущую сессию
     */
    fun clearCurrentSession() {
        currentSession?.clear()
    }

    /**
     * Получить все сохраненные сессии
     */
    fun getAllSessions(context: Context): List<MeasurementSession> {
        return MeasurementSession.getSavedSessions(context)
    }

    /**
     * Получить статистику по всем сессиям
     */
    fun getStatistics(context: Context): Statistics {
        val sessions = getAllSessions(context)

        return Statistics(
            totalSessions = sessions.size,
            totalMeasurements = sessions.sumOf { it.getMeasurementCount() },
            totalDistance = sessions.sumOf { it.totalDistance.toDouble() }.toFloat(),
            totalDuration = sessions.sumOf { it.getDuration() },
            lastSessionDate = sessions.firstOrNull()?.startTime ?: 0L
        )
    }

    /**
     * Удалить сессию
     */
    fun deleteSession(context: Context, sessionId: String): Boolean {
        return MeasurementSession.deleteSession(context, sessionId)
    }

    /**
     * Экспортировать сессию
     */
    fun exportSession(context: Context, sessionId: String, fileName: String = ""): File? {
        val sessions = getAllSessions(context)
        val session = sessions.find { it.id == sessionId }

        return session?.exportToDownloads(context, fileName)
    }

    /**
     * Получить путь к файлу сессии
     */
    fun getSessionFilePath(context: Context, sessionId: String): String? {
        return getAllSessions(context)
            .find { it.id == sessionId }
            ?.getSessionFilePath(context)
    }
}