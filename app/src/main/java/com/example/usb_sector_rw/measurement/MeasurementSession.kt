package com.example.usb_sector_rw.measurement

import android.content.Context
import android.location.Location
import com.example.usb_sector_rw.msd.LospDev
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Сессия измерений - группа записей от старта до стопа.
 * Хранится в памяти, сохраняется в файлы.
 */
class MeasurementSession(
    // Идентификаторы
    val id: String = "session_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
    val name: String = "",

    // Время
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,

    // Данные
    private val measurements: MutableList<GeoMeasurement> = mutableListOf(),

    // Статистика (вычисляется)
    var totalDistance: Float = 0f, // метры
    var averageSpeed: Float = 0f,  // км/ч
    var maxSpeed: Float = 0f,      // км/ч
    var minFrequency: Float = Float.MAX_VALUE,
    var maxFrequency: Float = Float.MIN_VALUE,
    var avgFrequency: Float = 0f,
    var avgDoseRate: Float = 0f,
    var avgTemperature: Float = 0f,
    var avgUvValue: Float = 0f,

    // Статус
    var isActive: Boolean = true,
    var isSaved: Boolean = false
) {
    companion object {
        private const val SESSIONS_DIR = "measurement_sessions"
        private const val SESSION_FILE_PREFIX = "session_"
        private const val SESSION_FILE_EXT = ".csv"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

        /**
         * Создать имя сессии по умолчанию
         */
        fun generateDefaultName(): String {
            val now = Date()
            val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now)
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
            return "Измерение $dateStr $timeStr"
        }

        /**
         * Получить директорию для сессий
         */
        fun getSessionsDirectory(context: Context): File {
            val dir = File(context.getExternalFilesDir(null), SESSIONS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        /**
         * Получить список всех сохраненных сессий
         */
        fun getSavedSessions(context: Context): List<MeasurementSession> {
            val sessionsDir = getSessionsDirectory(context)
            val sessions = mutableListOf<MeasurementSession>()

            if (sessionsDir.exists() && sessionsDir.isDirectory) {
                sessionsDir.listFiles { file ->
                    file.name.startsWith(SESSION_FILE_PREFIX) &&
                            file.name.endsWith(SESSION_FILE_EXT)
                }?.forEach { file ->
                    loadFromFile(file)?.let { sessions.add(it) }
                }
            }

            return sessions.sortedByDescending { it.startTime }
        }

        /**
         * Загрузить сессию из файла
         */
        fun loadFromFile(file: File): MeasurementSession? {
            return try {
                val lines = file.readLines()
                if (lines.size < 2) return null

                val header = lines[0]
                val dataLines = lines.drop(1)

                // Парсим измерения
                val measurements = dataLines.mapNotNull { GeoMeasurement.fromCsvRow(it) }

                // Создаем сессию
                val session = MeasurementSession(
                    id = file.nameWithoutExtension,
                    name = file.nameWithoutExtension.replace(SESSION_FILE_PREFIX, ""),
                    startTime = measurements.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                    endTime = measurements.lastOrNull()?.timestamp,
                    isActive = false,
                    isSaved = true
                )

                // Добавляем измерения
                measurements.forEach { session.addMeasurement(it) }

                // Пересчитываем статистику
                session.recalculateStatistics()

                session
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Удалить сессию
         */
        fun deleteSession(context: Context, sessionId: String): Boolean {
            return try {
                val sessionsDir = getSessionsDirectory(context)
                val file = File(sessionsDir, "$sessionId$SESSION_FILE_EXT")
                if (file.exists()) {
                    file.delete()
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Добавить измерение в сессию
     */
    fun addMeasurement(measurement: GeoMeasurement) {
        measurements.add(measurement)
        updateStatistics(measurement)
    }

    /**
     * Добавить измерение из Location и USB устройства
     */
    fun addMeasurementFromLocation(location: Location, lospDev: LospDev?) {
        val measurement = GeoMeasurement.create(location, lospDev, id)
        addMeasurement(measurement)
    }

    /**
     * Обновить статистику при добавлении нового измерения
     */
    private fun updateStatistics(measurement: GeoMeasurement) {
        // Обновляем частоту
        minFrequency = minOf(minFrequency, measurement.frequencyHz)
        maxFrequency = maxOf(maxFrequency, measurement.frequencyHz)

        // Обновляем скорость
        maxSpeed = maxOf(maxSpeed, measurement.speed)

        // Пересчитываем средние значения
        recalculateStatistics()

        // Обновляем расстояние (если есть предыдущая точка)
        if (measurements.size > 1) {
            val prevMeasurement = measurements[measurements.size - 2]
            val distance = calculateDistance(
                prevMeasurement.latitude, prevMeasurement.longitude,
                measurement.latitude, measurement.longitude
            )
            totalDistance += distance
        }
    }

    /**
     * Пересчитать всю статистику
     */
    fun recalculateStatistics() {
        if (measurements.isEmpty()) return

        // Сбрасываем значения
        avgFrequency = 0f
        avgDoseRate = 0f
        avgTemperature = 0f
        avgUvValue = 0f
        averageSpeed = 0f

        // Суммируем
        measurements.forEach { measurement ->
            avgFrequency += measurement.frequencyHz
            avgDoseRate += measurement.doseRate
            avgTemperature += measurement.temperature
            avgUvValue += measurement.uvValue
            averageSpeed += measurement.speed
        }

        // Делим на количество
        val count = measurements.size.toFloat()
        avgFrequency /= count
        avgDoseRate /= count
        avgTemperature /= count
        avgUvValue /= count
        averageSpeed /= count

        // Находим мин/макс частоту если не были установлены
        if (minFrequency == Float.MAX_VALUE) {
            minFrequency = measurements.minOfOrNull { it.frequencyHz } ?: 0f
        }
        if (maxFrequency == Float.MIN_VALUE) {
            maxFrequency = measurements.maxOfOrNull { it.frequencyHz } ?: 0f
        }

        // Находим макс скорость если не была установлена
        if (maxSpeed == 0f) {
            maxSpeed = measurements.maxOfOrNull { it.speed } ?: 0f
        }
    }

    /**
     * Завершить сессию
     */
    fun finish() {
        endTime = System.currentTimeMillis()
        isActive = false
        recalculateStatistics()
    }

    /**
     * Получить продолжительность сессии в мс
     */
    fun getDuration(): Long {
        val end = endTime ?: System.currentTimeMillis()
        return end - startTime
    }

    /**
     * Получить продолжительность в формате ЧЧ:ММ:СС
     */
    fun getDurationFormatted(): String {
        val duration = getDuration()
        val hours = duration / (1000 * 60 * 60)
        val minutes = (duration % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (duration % (1000 * 60)) / 1000

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Получить количество измерений
     */
    fun getMeasurementCount(): Int = measurements.size

    /**
     * Получить все измерения
     */
    fun getMeasurements(): List<GeoMeasurement> = measurements.toList()

    /**
     * Получить измерения для отображения (с фильтрацией)
     */
    fun getMeasurementsForDisplay(limit: Int = 1000): List<GeoMeasurement> {
        return if (measurements.size > limit) {
            // Берем каждое N-ое измерение для отображения
            val step = measurements.size / limit
            measurements.filterIndexed { index, _ -> index % step == 0 }
        } else {
            measurements.toList()
        }
    }

    /**
     * Сохранить сессию в файл
     */
    fun saveToFile(context: Context): Boolean {
        return try {
            val sessionsDir = getSessionsDirectory(context)

            // Создаем имя файла с датой
            val fileName = "${SESSION_FILE_PREFIX}${dateFormat.format(Date(startTime))}$SESSION_FILE_EXT"
            val file = File(sessionsDir, fileName)

            // Сохраняем в CSV
            val csvContent = StringBuilder()
            csvContent.append(GeoMeasurement.getCsvHeader()).append("\n")

            measurements.forEach { measurement ->
                csvContent.append(measurement.toCsvRow()).append("\n")
            }

            file.writeText(csvContent.toString())
            isSaved = true
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Экспортировать сессию в отдельный файл (например, в Downloads)
     */
    fun exportToDownloads(context: Context, fileName: String = ""): File? {
        return try {
            val exportName = if (fileName.isBlank()) {
                "export_${dateFormat.format(Date(startTime))}.csv"
            } else {
                if (!fileName.endsWith(".csv")) "$fileName.csv" else fileName
            }

            val downloadsDir = context.getExternalFilesDir(null)?.parentFile ?: return null
            val exportDir = File(downloadsDir, "USB_Sensor_Exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val exportFile = File(exportDir, exportName)

            // Создаем красивый CSV с заголовком сессии
            val csvContent = StringBuilder()

            // Заголовок сессии
            csvContent.append("# Session: $name\n")
            csvContent.append("# Start: ${Date(startTime)}\n")
            csvContent.append("# End: ${endTime?.let { Date(it) } ?: "In progress"}\n")
            csvContent.append("# Duration: ${getDurationFormatted()}\n")
            csvContent.append("# Points: ${measurements.size}\n")
            csvContent.append("# Distance: ${String.format("%.2f", totalDistance)} m\n")
            csvContent.append("# Avg Speed: ${String.format("%.2f", averageSpeed)} km/h\n")
            csvContent.append("# Avg Frequency: ${String.format("%.2f", avgFrequency)} Hz\n")
            csvContent.append("# Avg Dose Rate: ${String.format("%.6f", avgDoseRate)} μSv/h\n")
            csvContent.append("#\n")

            // Данные
            csvContent.append(GeoMeasurement.getCsvHeader()).append("\n")
            measurements.forEach { measurement ->
                csvContent.append(measurement.toCsvRow()).append("\n")
            }

            exportFile.writeText(csvContent.toString())
            exportFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Рассчитать расстояние между двумя точками (в метрах)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    /**
     * Получить путь к файлу сессии
     */
    fun getSessionFilePath(context: Context): String? {
        val sessionsDir = getSessionsDirectory(context)
        val fileName = "$id$SESSION_FILE_EXT"
        val file = File(sessionsDir, fileName)
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Очистить сессию (удалить все измерения)
     */
    fun clear() {
        measurements.clear()
        resetStatistics()
    }

    /**
     * Сбросить статистику
     */
    private fun resetStatistics() {
        totalDistance = 0f
        averageSpeed = 0f
        maxSpeed = 0f
        minFrequency = Float.MAX_VALUE
        maxFrequency = Float.MIN_VALUE
        avgFrequency = 0f
        avgDoseRate = 0f
        avgTemperature = 0f
        avgUvValue = 0f
    }

    /**
     * Получить информацию о сессии для отображения
     */
    fun getSessionInfo(): Map<String, String> {
        return mapOf(
            "Название" to name,
            "ID" to id,
            "Начало" to Date(startTime).toString(),
            "Окончание" to (endTime?.let { Date(it).toString() } ?: "В процессе"),
            "Продолжительность" to getDurationFormatted(),
            "Точек" to measurements.size.toString(),
            "Расстояние" to String.format("%.2f м", totalDistance),
            "Ср. скорость" to String.format("%.2f км/ч", averageSpeed),
            "Макс. скорость" to String.format("%.2f км/ч", maxSpeed),
            "Ср. частота" to String.format("%.2f Гц", avgFrequency),
            "Ср. доза" to String.format("%.6f мкЗв/ч", avgDoseRate),
            "Ср. температура" to String.format("%.1f °C", avgTemperature),
            "Ср. УФ" to String.format("%.6f Вт/см²", avgUvValue)
        )
    }
}