// Файл: app/src/main/java/com/example/usb_sector_rw/history/FileBrowser.kt
package com.example.usb_sector_rw.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Класс для работы с файлами экспорта
 */
class FileBrowser(private val context: Context) {

    companion object {
        private const val AUTHORITY_SUFFIX = ".fileprovider"
    }

    /**
     * Получить список экспортированных файлов
     */
    fun getExportedFiles(): List<File> {
        val exportDir = getExportDirectory()
        return if (exportDir.exists() && exportDir.isDirectory) {
            exportDir.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Получить файлы по расширению
     */
    fun getFilesByExtension(extension: String): List<File> {
        return getExportedFiles().filter { it.name.endsWith(extension, ignoreCase = true) }
    }

    /**
     * Открыть файл с помощью Intent
     */
    fun openFileWithIntent(file: File, mimeType: String = "*/*"): Intent {
        val fileUri = getFileUri(file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(intent, "Открыть файл")
    }

    /**
     * Поделиться файлом
     */
    fun shareFile(file: File, title: String = "Экспорт данных"): Intent {
        val fileUri = getFileUri(file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(file.extension)
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Файл экспорта измерений")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, title)
    }

    /**
     * Получить URI файла через FileProvider
     */
    fun getFileUri(file: File): Uri {
        val authority = "${context.packageName}$AUTHORITY_SUFFIX"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Удалить файл
     */
    fun deleteFile(file: File): Boolean = file.delete()

    /**
     * Удалить все экспортированные файлы
     */
    fun deleteAllExportedFiles(): Boolean {
        val exportDir = getExportDirectory()
        return if (exportDir.exists() && exportDir.isDirectory) {
            exportDir.listFiles()?.all { it.delete() } ?: true
        } else {
            false
        }
    }

    /**
     * Получить размер папки экспорта
     */
    fun getExportDirectorySize(): Long {
        return calculateFolderSize(getExportDirectory())
    }

    /**
     * Получить MIME тип по расширению
     */
    private fun getMimeType(extension: String): String {
        return when (extension.toLowerCase()) {
            ".csv" -> "text/csv"
            ".json" -> "application/json"
            ".kml" -> "application/vnd.google-earth.kml+xml"
            ".zip" -> "application/zip"
            ".txt", ".log" -> "text/plain"
            else -> "*/*"
        }
    }

    /**
     * Получить директорию экспорта
     */
    private fun getExportDirectory(): File {
        return File(context.getExternalFilesDir(null), "exports").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Рассчитать размер папки
     */
    private fun calculateFolderSize(directory: File): Long {
        if (!directory.exists() || !directory.isDirectory) return 0L

        return directory.listFiles()?.sumOf { file ->
            if (file.isFile) {
                file.length()
            } else {
                calculateFolderSize(file)
            }
        } ?: 0L
    }
}