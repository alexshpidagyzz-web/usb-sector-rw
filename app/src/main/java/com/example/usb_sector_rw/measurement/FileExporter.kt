package com.example.usb_sector_rw.measurement

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.usb_sector_rw.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Класс для экспорта данных в различные форматы (CSV, JSON, KML, ZIP)
 * и предоставления доступа к файлам через Share Intent.
 */
class FileExporter(private val context: Context) {

    companion object {
        private const val TAG = "FileExporter"
        private const val EXPORT_DIR = "USB_Sensor_Exports"
        private const val CSV_EXTENSION = ".csv"
        private const val JSON_EXTENSION = ".json"
        private const val KML_EXTENSION = ".kml"
        private const val ZIP_EXTENSION = ".zip"

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

        /**
         * Получить директорию для экспорта
         */
        fun getExportDirectory(context: Context): File {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ используем MediaStore
                File(context.getExternalFilesDir(null), EXPORT_DIR)
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), EXPORT_DIR)
            }.apply {
                if (!exists()) {
                    mkdirs()
                }
            }
        }

        /**
         * Получить MIME тип по расширению файла
         */
        fun getMimeType(extension: String): String {
            return when (extension.toLowerCase(Locale.ROOT)) {
                ".csv" -> "text/csv"
                ".json" -> "application/json"
                ".kml" -> "application/vnd.google-earth.kml+xml"
                ".zip" -> "application/zip"
                ".txt" -> "text/plain"
                else -> "*/*"
            }
        }
    }

    /**
     * Экспортировать сессию в CSV файл
     */
    fun exportSessionToCsv(
        session: MeasurementSession,
        fileName: String = ""
    ): ExportResult {
        return try {
            val exportName = if (fileName.isBlank()) {
                "session_${dateFormat.format(Date(session.startTime))}$CSV_EXTENSION"
            } else {
                if (!fileName.endsWith(CSV_EXTENSION)) "$fileName$CSV_EXTENSION" else fileName
            }

            val file = createExportFile(exportName)

            // Создаем содержимое CSV
            val csvContent = buildCsvContent(session)

            // Записываем в файл
            file.writeText(csvContent)

            ExportResult.success(file, exportName, csvContent.length.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка экспорта в CSV: ${e.message}", e)
            ExportResult.error("Ошибка экспорта: ${e.message}")
        }
    }

    /**
     * Экспортировать сессию в JSON файл
     */
    fun exportSessionToJson(
        session: MeasurementSession,
        fileName: String = ""
    ): ExportResult {
        return try {
            val exportName = if (fileName.isBlank()) {
                "session_${dateFormat.format(Date(session.startTime))}$JSON_EXTENSION"
            } else {
                if (!fileName.endsWith(JSON_EXTENSION)) "$fileName$JSON_EXTENSION" else fileName
            }

            val file = createExportFile(exportName)

            // Создаем содержимое JSON
            val jsonContent = buildJsonContent(session)

            // Записываем в файл
            file.writeText(jsonContent)

            ExportResult.success(file, exportName, jsonContent.length.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка экспорта в JSON: ${e.message}", e)
            ExportResult.error("Ошибка экспорта: ${e.message}")
        }
    }

    /**
     * Экспортировать сессию в KML (для Google Earth)
     */
    fun exportSessionToKml(
        session: MeasurementSession,
        fileName: String = ""
    ): ExportResult {
        return try {
            val exportName = if (fileName.isBlank()) {
                "session_${dateFormat.format(Date(session.startTime))}$KML_EXTENSION"
            } else {
                if (!fileName.endsWith(KML_EXTENSION)) "$fileName$KML_EXTENSION" else fileName
            }

            val file = createExportFile(exportName)

            // Создаем содержимое KML
            val kmlContent = buildKmlContent(session)

            // Записываем в файл
            file.writeText(kmlContent)

            ExportResult.success(file, exportName, kmlContent.length.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка экспорта в KML: ${e.message}", e)
            ExportResult.error("Ошибка экспорта: ${e.message}")
        }
    }

    /**
     * Экспортировать несколько сессий в ZIP архив
     */
    fun exportSessionsToZip(
        sessions: List<MeasurementSession>,
        fileName: String = ""
    ): ExportResult {
        return try {
            val exportName = if (fileName.isBlank()) {
                "export_${dateFormat.format(Date())}$ZIP_EXTENSION"
            } else {
                if (!fileName.endsWith(ZIP_EXTENSION)) "$fileName$ZIP_EXTENSION" else fileName
            }

            val zipFile = createExportFile(exportName)

            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                sessions.forEachIndexed { index, session ->
                    // Добавляем CSV файл для каждой сессии
                    val sessionFileName = "session_${index + 1}_${dateFormat.format(Date(session.startTime))}$CSV_EXTENSION"
                    val csvContent = buildCsvContent(session)

                    val entry = ZipEntry(sessionFileName)
                    zipOut.putNextEntry(entry)
                    zipOut.write(csvContent.toByteArray())
                    zipOut.closeEntry()

                    // Добавляем readme файл
                    if (index == 0) {
                        val readmeContent = buildReadmeContent(sessions)
                        val readmeEntry = ZipEntry("README.txt")
                        zipOut.putNextEntry(readmeEntry)
                        zipOut.write(readmeContent.toByteArray())
                        zipOut.closeEntry()
                    }
                }
            }

            ExportResult.success(zipFile, exportName, zipFile.length())
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка создания ZIP: ${e.message}", e)
            ExportResult.error("Ошибка создания архива: ${e.message}")
        }
    }

    /**
     * Экспортировать в Excel-совместимый CSV
     */
    fun exportToExcelCsv(
        measurements: List<GeoMeasurement>,
        fileName: String = ""
    ): ExportResult {
        return try {
            val exportName = if (fileName.isBlank()) {
                "excel_export_${dateFormat.format(Date())}$CSV_EXTENSION"
            } else {
                if (!fileName.endsWith(CSV_EXTENSION)) "$fileName$CSV_EXTENSION" else fileName
            }

            val file = createExportFile(exportName)

            // CSV с разделителем ; для Excel
            val csvContent = StringBuilder()

            // Заголовок
            csvContent.append("Время;Дата;Широта;Долгота;Высота;Точность (м);Скорость (км/ч);")
            csvContent.append("Частота (Гц);Мощность дозы (мкЗв/ч);Температура (°C);УФ (Вт/см²);Влажность (%)\n")

            // Данные
            measurements.forEach { measurement ->
                csvContent.append("${measurement.getTimeFormatted()};")
                csvContent.append("${measurement.getDateFormatted()};")
                csvContent.append("${measurement.latitude};")
                csvContent.append("${measurement.longitude};")
                csvContent.append("${measurement.altitude};")
                csvContent.append("${measurement.accuracy};")
                csvContent.append("${String.format(Locale.US, "%.2f", measurement.speed)};")
                csvContent.append("${String.format(Locale.US, "%.2f", measurement.frequencyHz)};")
                csvContent.append("${String.format(Locale.US, "%.6f", measurement.doseRate)};")
                csvContent.append("${String.format(Locale.US, "%.1f", measurement.temperature)};")
                csvContent.append("${String.format(Locale.US, "%.6f", measurement.uvValue)};")
                csvContent.append("${String.format(Locale.US, "%.1f", measurement.humidity)}\n")
            }

            file.writeText(csvContent.toString())

            ExportResult.success(file, exportName, csvContent.length.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка экспорта в Excel CSV: ${e.message}", e)
            ExportResult.error("Ошибка экспорта: ${e.message}")
        }
    }

    /**
     * Поделиться файлом через Intent
     */
    fun shareFile(file: File, title: String = "Экспорт данных"): Intent {
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(file.extension)
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Экспорт данных измерений")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, title)
    }

    /**
     * Получить список экспортированных файлов
     */
    fun getExportedFiles(): List<File> {
        val exportDir = getExportDirectory(context)
        return if (exportDir.exists() && exportDir.isDirectory) {
            exportDir.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Удалить экспортированный файл
     */
    fun deleteExportedFile(fileName: String): Boolean {
        return try {
            val exportDir = getExportDirectory(context)
            val file = File(exportDir, fileName)
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Очистить все экспортированные файлы
     */
    fun clearExportedFiles(): Boolean {
        return try {
            val exportDir = getExportDirectory(context)
            if (exportDir.exists() && exportDir.isDirectory) {
                exportDir.listFiles()?.forEach { it.delete() }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Получить URI файла для просмотра
     */
    fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Создать файл для экспорта
     */
    private fun createExportFile(fileName: String): File {
        val exportDir = getExportDirectory(context)
        return File(exportDir, fileName).apply {
            // Удаляем старый файл с таким же именем
            if (exists()) {
                delete()
            }
        }
    }

    /**
     * Создать содержимое CSV файла
     */
    private fun buildCsvContent(session: MeasurementSession): String {
        val csvContent = StringBuilder()

        // Заголовок сессии
        csvContent.append("# Сессия: ${session.name}\n")
        csvContent.append("# Начало: ${Date(session.startTime)}\n")
        csvContent.append("# Окончание: ${session.endTime?.let { Date(it) } ?: "В процессе"}\n")
        csvContent.append("# Продолжительность: ${session.getDurationFormatted()}\n")
        csvContent.append("# Количество точек: ${session.getMeasurementCount()}\n")
        csvContent.append("# Расстояние: ${String.format("%.2f", session.totalDistance)} м\n")
        csvContent.append("# Средняя скорость: ${String.format("%.2f", session.averageSpeed)} км/ч\n")
        csvContent.append("# Макс. скорость: ${String.format("%.2f", session.maxSpeed)} км/ч\n")
        csvContent.append("# Средняя частота: ${String.format("%.2f", session.avgFrequency)} Гц\n")
        csvContent.append("# Средняя доза: ${String.format("%.6f", session.avgDoseRate)} мкЗв/ч\n")
        csvContent.append("# Средняя температура: ${String.format("%.1f", session.avgTemperature)} °C\n")
        csvContent.append("# Средний УФ: ${String.format("%.6f", session.avgUvValue)} Вт/см²\n")
        csvContent.append("#\n")

        // Заголовок CSV
        csvContent.append(getCsvHeader()).append("\n")

        // Данные
        session.getMeasurements().forEach { measurement ->
            csvContent.append(measurement.toCsvRow()).append("\n")
        }

        return csvContent.toString()
    }

    /**
     * Создать содержимое JSON файла
     */
    private fun buildJsonContent(session: MeasurementSession): String {
        val measurementsJson = session.getMeasurements().joinToString(",\n") { measurement ->
            """
            {
                "time": "${measurement.getDateTimeFormatted()}",
                "timestamp": ${measurement.timestamp},
                "latitude": ${measurement.latitude},
                "longitude": ${measurement.longitude},
                "altitude": ${measurement.altitude},
                "accuracy": ${measurement.accuracy},
                "speed": ${measurement.speed},
                "frequency": ${measurement.frequencyHz},
                "doseRate": ${measurement.doseRate},
                "temperature": ${measurement.temperature},
                "uv": ${measurement.uvValue},
                "humidity": ${measurement.humidity}
            }
            """.trimIndent()
        }

        return """
        {
            "session": {
                "id": "${session.id}",
                "name": "${session.name}",
                "startTime": ${session.startTime},
                "endTime": ${session.endTime ?: "null"},
                "duration": ${session.getDuration()},
                "measurementCount": ${session.getMeasurementCount()},
                "totalDistance": ${session.totalDistance},
                "averageSpeed": ${session.averageSpeed},
                "maxSpeed": ${session.maxSpeed},
                "averageFrequency": ${session.avgFrequency},
                "averageDoseRate": ${session.avgDoseRate},
                "averageTemperature": ${session.avgTemperature},
                "averageUv": ${session.avgUvValue}
            },
            "measurements": [
                $measurementsJson
            ]
        }
        """.trimIndent()
    }

    /**
     * Создать содержимое KML файла
     */
    private fun buildKmlContent(session: MeasurementSession): String {
        val points = session.getMeasurements().joinToString("\n") { measurement ->
            """
            <Placemark>
                <name>${measurement.getTimeFormatted()}</name>
                <description>
                    Частота: ${measurement.frequencyHz} Гц
                    Доза: ${measurement.doseRate} мкЗв/ч
                    Температура: ${measurement.temperature} °C
                    УФ: ${measurement.uvValue} Вт/см²
                    Скорость: ${measurement.speed} км/ч
                </description>
                <Point>
                    <coordinates>${measurement.longitude},${measurement.latitude},${measurement.altitude}</coordinates>
                </Point>
            </Placemark>
            """.trimIndent()
        }

        // Создаем линию маршрута
        val coordinates = session.getMeasurements().joinToString(" ") { measurement ->
            "${measurement.longitude},${measurement.latitude},${measurement.altitude}"
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
<Document>
    <name>${session.name}</name>
    <description>Сессия измерений: ${session.name}</description>
    
    <Style id="trackStyle">
        <LineStyle>
            <color>ff00ffff</color>
            <width>4</width>
        </LineStyle>
    </Style>
    
    <Style id="pointStyle">
        <IconStyle>
            <scale>0.5</scale>
            <Icon>
                <href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href>
            </Icon>
        </IconStyle>
    </Style>
    
    <Placemark>
        <name>Маршрут</name>
        <styleUrl>#trackStyle</styleUrl>
        <LineString>
            <tessellate>1</tessellate>
            <coordinates>
                $coordinates
            </coordinates>
        </LineString>
    </Placemark>
    
    $points
</Document>
</kml>"""
    }

    /**
     * Создать содержимое README файла для ZIP
     */
    private fun buildReadmeContent(sessions: List<MeasurementSession>): String {
        return """
        =================================
        ЭКСПОРТ ДАННЫХ ИЗМЕРЕНИЙ
        =================================
        
        Содержимое архива:
        ${sessions.mapIndexed { i, session -> "session_${i + 1}_*.csv - ${session.name}" }.joinToString("\n")}
        
        Формат данных CSV:
        - Timestamp: Время в миллисекундах
        - DateTime: Дата и время в формате ГГГГ-ММ-ДД ЧЧ:ММ:СС
        - Latitude, Longitude: Координаты в градусах
        - Altitude: Высота над уровнем моря (метры)
        - Accuracy: Точность GPS (метры)
        - Speed: Скорость (км/ч)
        - Frequency: Частота (Гц)
        - DoseRate: Мощность дозы (мкЗв/ч)
        - Temperature: Температура (°C)
        - UV: Ультрафиолетовое излучение (Вт/см²)
        - Humidity: Влажность (%)
        - Battery: Уровень заряда батареи (%)
        - DeviceID: Идентификатор устройства
        - Valid: Валидность данных (1=валидно, 0=невалидно)
        
        Экспорт создан: ${Date()}
        Приложение: USB Sensor RW
        =================================
        """.trimIndent()
    }

    /**
     * Получить заголовок CSV файла (статистический метод)
     */
    private fun getCsvHeader(): String {
        return "Timestamp,DateTime,Latitude,Longitude,Altitude," +
                "Accuracy(m),Speed(km/h),Frequency(Hz),DoseRate(uSv/h)," +
                "Temperature(C),UV(W/cm2),Humidity(%),Battery(%),DeviceID,Valid"
    }

    /**
     * Результат экспорта
     */
    data class ExportResult(
        val isSuccess: Boolean,
        val file: File? = null,
        val fileName: String = "",
        val fileSize: Long = 0,
        val errorMessage: String = ""
    ) {
        companion object {
            fun success(file: File, fileName: String, fileSize: Long): ExportResult {
                return ExportResult(
                    isSuccess = true,
                    file = file,
                    fileName = fileName,
                    fileSize = fileSize
                )
            }

            fun error(message: String): ExportResult {
                return ExportResult(
                    isSuccess = false,
                    errorMessage = message
                )
            }
        }
    }
}