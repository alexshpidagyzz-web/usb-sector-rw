// Файл: app/src/main/java/com/example/usb_sector_rw/utils/extensions/ContextExtensions.kt
package com.example.usb_sector_rw.utils.extensions

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Расширения для Context для работы с файлами
 */
fun Context.getAppFilesDirectory(): File {
    return filesDir
}

fun Context.getExternalAppFilesDirectory(): File? {
    return getExternalFilesDir(null)
}

fun Context.getDownloadsDirectory(): File {
    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
}

fun Context.getAppCacheDirectory(): File {
    return cacheDir
}

fun Context.getExternalCacheDirectory(): File? {
    return externalCacheDir
}

fun Context.createFileInAppDir(fileName: String): File {
    return File(filesDir, fileName)
}

fun Context.createFileInExternalAppDir(fileName: String): File {
    val dir = getExternalFilesDir(null) ?: filesDir
    return File(dir, fileName)
}

fun Context.createFileInDownloads(fileName: String): File {
    return File(getDownloadsDirectory(), fileName)
}

fun Context.fileExistsInAppDir(fileName: String): Boolean {
    return File(filesDir, fileName).exists()
}

fun Context.fileExistsInExternalAppDir(fileName: String): Boolean {
    val dir = getExternalFilesDir(null) ?: return false
    return File(dir, fileName).exists()
}

fun Context.deleteFileFromAppDir(fileName: String): Boolean {
    return File(filesDir, fileName).delete()
}

fun Context.deleteFileFromExternalAppDir(fileName: String): Boolean {
    val dir = getExternalFilesDir(null) ?: return false
    return File(dir, fileName).delete()
}

fun Context.getFileSizeInAppDir(fileName: String): Long {
    return File(filesDir, fileName).length()
}

fun Context.getFileSizeInExternalAppDir(fileName: String): Long {
    val dir = getExternalFilesDir(null) ?: return 0L
    return File(dir, fileName).length()
}

fun Context.getAvailableStorageSpace(): Long {
    return filesDir.freeSpace
}

fun Context.getTotalStorageSpace(): Long {
    return filesDir.totalSpace
}