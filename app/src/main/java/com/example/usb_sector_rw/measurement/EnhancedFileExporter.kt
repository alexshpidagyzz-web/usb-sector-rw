package com.example.usb_sector_rw.measurement

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Улучшенный класс для экспорта данных с понятной структурой папок
 */
class EnhancedFileExporter(private val context: Context) {

    companion object {
        private const val EXPORT_BASE_DIR = "USB_Sensor_Data"
        private const val SESSIONS_SUBDIR = "Sessions"
        private const val EXPORTS_SUBDIR = "Exports"
        private const val LOGS_SUBDIR = "Logs"

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        private val displayDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

        /**
         * Получить MIME тип по расширению файла
         */
        fun getMimeType(extension: String): String {
            return when (extension.lowercase(Locale.ROOT)) {
                ".csv" -> "text/csv"
                ".json" -> "application/json"
                ".kml" -> "application/vnd.google-earth.kml+xml"
                ".zip" -> "application/zip"
                ".txt", ".log" -> "text/plain"
                else -> "*/*"
            }
        }
    }

    /**
     * Получить основную папку приложения в Downloads
     */
    fun getAppBaseDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            EXPORT_BASE_DIR
        ).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Получить папку для сессий
     */
    fun getSessionsDirectory(): File {
        return File(getAppBaseDirectory(), SESSIONS_SUBDIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Получить папку для экспортов
     */
    fun getExportsDirectory(): File {
        return File(getAppBaseDirectory(), EXPORTS_SUBDIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Получить папку для логов
     */
    fun getLogsDirectory(): File {
        return File(getAppBaseDirectory(), LOGS_SUBDIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Сохранить сессию в стандартном формате с понятным именем
     */
    fun saveSession(session: MeasurementSession): File {
        val dir = getSessionsDirectory()

        // Создаем понятное имя файла
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(session.startTime))
        val time = SimpleDateFormat("HH-mm-ss", Locale.getDefault()).format(Date(session.startTime))
        val safeName = session.name.replace(Regex("[^a-zA-Z0-9а-яА-Я_\\- ]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')

        val fileName = "${date}_${safeName}_${time}.csv"
        val file = File(dir, fileName)

        // Формируем CSV с заголовком
        val csvContent = buildSessionCsv(session)

        // Записываем файл
        file.writeText(csvContent, Charsets.UTF_8)

        // Создаем файл README с описанием
        createReadmeForSession(session, dir, fileName)

        return file
    }

    private fun buildSessionCsv(session: MeasurementSession): String {
        val csv = StringBuilder()

        // Метаданные сессии
        csv.append("# Сессия измерений USB Sensor\n")
        csv.append("# =================================\n")
        csv.append("# Имя: ${session.name}\n")
        csv.append("# ID: ${session.id}\n")
        csv.append("# Начало: ${displayDateFormat.format(Date(session.startTime))}\n")
        session.endTime?.let {
            csv.append("# Окончание: ${displayDateFormat.format(Date(it))}\n")
            csv.append("# Длительность: ${session.getDurationFormatted()}\n")
        }
        csv.append("# Количество точек: ${session.getMeasurementCount()}\n")
        csv.append("# Расстояние: ${String.format("%.2f", session.totalDistance)} м\n")
        csv.append("# Средняя скорость: ${String.format("%.2f", session.averageSpeed)} км/ч\n")
        csv.append("# Макс. скорость: ${String.format("%.2f", session.maxSpeed)} км/ч\n")
        csv.append("# Средняя частота: ${String.format("%.2f", session.avgFrequency)} Гц\n")
        csv.append("# Средняя доза: ${String.format("%.6f", session.avgDoseRate)} мкЗв/ч\n")
        csv.append("# Средняя температура: ${String.format("%.1f", session.avgTemperature)} °C\n")
        csv.append("# Средний УФ: ${String.format("%.6f", session.avgUvValue)} Вт/см²\n")
        csv.append("# =================================\n")
        csv.append("# Формат данных:\n")
        csv.append("# Timestamp - время в миллисекундах\n")
        csv.append("# DateTime - дата и время\n")
        csv.append("# Latitude, Longitude - координаты (градусы)\n")
        csv.append("# Altitude - высота (метры)\n")
        csv.append("# Accuracy - точность GPS (метры)\n")
        csv.append("# Speed - скорость (км/ч)\n")
        csv.append("# Frequency - частота (Гц)\n")
        csv.append("# DoseRate - мощность дозы (мкЗв/ч)\n")
        csv.append("# Temperature - температура (°C)\n")
        csv.append("# UV - ультрафиолет (Вт/см²)\n")
        csv.append("# Humidity - влажность (%)\n")
        csv.append("# Battery - уровень батареи (%)\n")
        csv.append("# DeviceID - ID устройства\n")
        csv.append("# Valid - валидность данных (1/0)\n")
        csv.append("# =================================\n")
        csv.append("\n")

        // Заголовок данных
        csv.append(GeoMeasurement.getCsvHeader()).append("\n")

        // Данные
        session.getMeasurements().forEach { measurement ->
            csv.append(measurement.toCsvRow()).append("\n")
        }

        return csv.toString()
    }

    private fun createReadmeForSession(session: MeasurementSession, dir: File, fileName: String) {
        val readmeFile = File(dir, "${fileName}.README.txt")
        val readmeContent = """
            Файл: $fileName
            Создан: ${Date()}
            
            Информация о сессии:
            Имя: ${session.name}
            ID: ${session.id}
            Начало: ${displayDateFormat.format(Date(session.startTime))}
            ${if (session.endTime != null) "Окончание: ${displayDateFormat.format(Date(session.endTime!!))}" else "Окончание: В процессе"}
            Длительность: ${session.getDurationFormatted()}
            Точек измерений: ${session.getMeasurementCount()}
            Расстояние: ${String.format("%.2f", session.totalDistance)} м
            Средняя скорость: ${String.format("%.2f", session.averageSpeed)} км/ч
            Макс. скорость: ${String.format("%.2f", session.maxSpeed)} км/ч
            
            Программа: USB Sensor RW
            Версия: 1.0
            Контакты: support@example.com
            
            Файл можно открыть в:
            - Excel / LibreOffice Calc
            - Google Sheets
            - Любом текстовом редакторе
        """.trimIndent()

        readmeFile.writeText(readmeContent, Charsets.UTF_8)
    }

    /**
     * Экспортировать выбранные сессии в ZIP архив
     */
    fun exportSessionsToZip(sessionFiles: List<File>): File? {
        if (sessionFiles.isEmpty()) return null

        val exportDir = getExportsDirectory()
        val timestamp = dateFormat.format(Date())
        val zipFileName = "export_${timestamp}.zip"
        val zipFile = File(exportDir, zipFileName)

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                // Добавляем сессии
                sessionFiles.forEachIndexed { index, sessionFile ->
                    if (sessionFile.exists()) {
                        val entryName = if (sessionFiles.size > 1) {
                            "session_${index + 1}_${sessionFile.name}"
                        } else {
                            sessionFile.name
                        }

                        val entry = ZipEntry(entryName)
                        zipOut.putNextEntry(entry)
                        zipOut.write(sessionFile.readBytes())
                        zipOut.closeEntry()
                    }
                }

                // Добавляем README файл
                val readmeEntry = ZipEntry("README.txt")
                zipOut.putNextEntry(readmeEntry)
                zipOut.write(createExportReadme(sessionFiles).toByteArray())
                zipOut.closeEntry()

                // Добавляем информацию о приложении
                val infoEntry = ZipEntry("APP_INFO.txt")
                zipOut.putNextEntry(infoEntry)
                zipOut.write(createAppInfo().toByteArray())
                zipOut.closeEntry()
            }

            return zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun createExportReadme(sessionFiles: List<File>): String {
        return """
            ЭКСПОРТ ДАННЫХ ИЗМЕРЕНИЙ
            =================================
            Дата экспорта: ${Date()}
            Количество сессий: ${sessionFiles.size}
            
            Содержимое архива:
            ${sessionFiles.mapIndexed { i, file -> "${i + 1}. ${file.name}" }.joinToString("\n")}
            
            Информация о файлах:
            --------------------
            Формат: CSV (Comma Separated Values)
            Кодировка: UTF-8
            Разделитель: запятая
            Заголовок: присутствует
            
            Как открыть:
            1. Двойной клик по файлу в проводнике
            2. Импорт в Excel: Данные -> Из текста/CSV
            3. Открыть в текстовом редакторе
            
            Программа: USB Sensor RW
            Контакты: support@example.com
            =================================
        """.trimIndent()
    }

    private fun createAppInfo(): String {
        return """
            Информация о приложении
            ========================
            Имя: USB Sensor RW
            Версия: 1.0
            Назначение: Запись измерений с USB датчиков
            Поддерживаемые датчики:
            - Частотомер
            - Дозиметр
            - Термометр
            - УФ-метр
            
            Формат данных:
            - CSV для табличных редакторов
            - KML для Google Earth
            - JSON для программирования
            
            Структура папок:
            Downloads/USB_Sensor_Data/
            ├── Sessions/     # Отдельные сессии
            ├── Exports/      # Архивы экспортов
            └── Logs/         # Логи приложения
            
            Контакты: support@example.com
            Поддержка: 24/7
            ========================
        """.trimIndent()
    }

    /**
     * Получить список всех сохраненных сессий
     */
    fun getAllSessions(): List<File> {
        val sessionsDir = getSessionsDirectory()
        return if (sessionsDir.exists() && sessionsDir.isDirectory) {
            sessionsDir.listFiles { file ->
                file.isFile && file.name.endsWith(".csv")
            }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Получить список экспортированных архивов
     */
    fun getExportArchives(): List<File> {
        val exportsDir = getExportsDirectory()
        return if (exportsDir.exists() && exportsDir.isDirectory) {
            exportsDir.listFiles { file ->
                file.isFile && (file.name.endsWith(".zip") || file.name.endsWith(".7z"))
            }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Удалить файл сессии
     */
    fun deleteSession(file: File): Boolean {
        return try {
            if (file.exists()) {
                // Также удаляем README файл если есть
                val readmeFile = File(file.parent, "${file.nameWithoutExtension}.README.txt")
                if (readmeFile.exists()) {
                    readmeFile.delete()
                }
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Поделиться файлом через Intent
     */
    fun shareFile(file: File, title: String = "Экспорт данных"): Intent {
        val fileUri = getFileUri(file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(file.extension)
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Файл измерений: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, title)
    }

    /**
     * Открыть файл в файловом менеджере
     */
    fun openFileInFileManager(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(
            getFileUri(file),
            "resource/folder"
        )
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }

    /**
     * Получить URI файла через FileProvider
     */
    fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Получить информацию о файле
     */
    fun getFileInfo(file: File): Map<String, String> {
        return mapOf(
            "Имя" to file.name,
            "Размер" to formatFileSize(file.length()),
            "Дата создания" to SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                .format(Date(file.lastModified())),
            "Путь" to file.absolutePath
        )
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 * 1024 -> "${String.format("%.2f", size / (1024.0 * 1024 * 1024))} GB"
            size >= 1024 * 1024 -> "${String.format("%.2f", size / (1024.0 * 1024))} MB"
            size >= 1024 -> "${String.format("%.2f", size / 1024.0)} KB"
            else -> "$size B"
        }
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