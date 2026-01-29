package com.example.usb_sector_rw.measurement

import android.location.Location
import com.example.usb_sector_rw.msd.LospDev
import java.text.SimpleDateFormat
import java.util.*

/**
 * Одна запись измерения с координатами и данными датчиков.
 * Используется для хранения в памяти и экспорта в файлы.
 */
data class GeoMeasurement(
    // Идентификаторы
    val id: Long = System.currentTimeMillis(),
    val sessionId: String = "",

    // Время
    val timestamp: Long = System.currentTimeMillis(),

    // GPS координаты
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,  // км/ч

    // Данные с датчиков USB
    val frequencyHz: Float = 0f,      // Частота (Гц)
    val doseRate: Float = 0f,         // Мощность дозы (мкЗв/ч)
    val temperature: Float = 0f,      // Температура (°C)
    val uvValue: Float = 0f,          // УФ излучение (Вт/см²)
    val humidity: Float = 0f,         // Влажность (%)

    // Дополнительные поля
    val batteryLevel: Int = 100,      // Уровень батареи
    val deviceId: String = "",        // ID USB устройства
    val isValid: Boolean = true       // Валидность данных
) {
    // Формат времени для отображения
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    companion object {
        /**
         * Создать измерение из Location и USB устройства
         */
        fun create(
            location: Location,
            lospDev: LospDev?,
            sessionId: String = ""
        ): GeoMeasurement {
            // Получаем данные с USB устройства
            val frequency = getFrequencyFromDevice(lospDev)
            val doseCoeff = getDoseCoefficient()
            val temperature = getTemperatureFromDevice(lospDev)
            val uvValue = getUvFromDevice(lospDev)

            return GeoMeasurement(
                sessionId = sessionId,
                timestamp = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracy,
                speed = location.speed * 3.6f, // конвертируем м/с в км/ч
                frequencyHz = frequency,
                doseRate = frequency * doseCoeff,
                temperature = temperature,
                uvValue = uvValue,
                humidity = 0f, // TODO: добавить если есть датчик влажности
                deviceId = getDeviceIdFromLospDev(lospDev)
            )
        }

        /**
         * Получить частоту с USB устройства
         */
        private fun getFrequencyFromDevice(lospDev: LospDev?): Float {
            return try {
                if (isLospDevConnected(lospDev)) {
                    // Используем существующую функцию из MsdFreq.kt
                    com.example.usb_sector_rw.frecUc(1, lospDev!!) { "" } ?: 0f
                } else {
                    0f
                }
            } catch (e: Exception) {
                0f
            }
        }

        /**
         * Получить коэффициент для пересчета частоты в дозу
         */
        private fun getDoseCoefficient(): Float {
            return try {
                com.example.usb_sector_rw.DOEZ_COEFF
            } catch (e: Exception) {
                1.0f // коэффициент по умолчанию
            }
        }

        /**
         * Получить температуру с USB устройства
         */
        private fun getTemperatureFromDevice(lospDev: LospDev?): Float {
            return try {
                if (isLospDevConnected(lospDev)) {
                    com.example.usb_sector_rw.msd.temp_exec(lospDev!!, { "" }, false, false)
                } else {
                    0f
                }
            } catch (e: Exception) {
                0f
            }
        }

        /**
         * Получить УФ значение с USB устройства
         */
        private fun getUvFromDevice(lospDev: LospDev?): Float {
            return try {
                if (isLospDevConnected(lospDev)) {
                    com.example.usb_sector_rw.msd.uv_exec(lospDev!!, { "" }, false, false)
                } else {
                    0f
                }
            } catch (e: Exception) {
                0f
            }
        }

        /**
         * Получить ID устройства из LospDev
         */
        private fun getDeviceIdFromLospDev(lospDev: LospDev?): String {
            return if (isLospDevConnected(lospDev)) {
                // Используем хеш объекта как идентификатор
                "device_${lospDev.hashCode()}"
            } else {
                "unknown"
            }
        }

        /**
         * Проверить подключено ли устройство LospDev
         * Используем метод isLospDeviceConnected() из класса LospDev
         */
        private fun isLospDevConnected(lospDev: LospDev?): Boolean {
            return try {
                if (lospDev != null) {
                    val result = lospDev.isLospDeviceConnected()
                    result == 1u // 1u означает, что устройство подключено
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Заголовок CSV файла
         */
        fun getCsvHeader(): String {
            return "Timestamp,DateTime,Latitude,Longitude,Altitude," +
                    "Accuracy(m),Speed(km/h),Frequency(Hz),DoseRate(uSv/h)," +
                    "Temperature(C),UV(W/cm2),Humidity(%),Battery(%),DeviceID,Valid"
        }

        /**
         * Парсинг из CSV строки
         */
        fun fromCsvRow(row: String): GeoMeasurement? {
            return try {
                val parts = row.split(",")
                if (parts.size >= 15) {
                    GeoMeasurement(
                        id = parts[0].toLongOrNull() ?: System.currentTimeMillis(),
                        sessionId = "",
                        timestamp = parts[0].toLongOrNull() ?: System.currentTimeMillis(),
                        latitude = parts[2].toDoubleOrNull() ?: 0.0,
                        longitude = parts[3].toDoubleOrNull() ?: 0.0,
                        altitude = parts[4].toDoubleOrNull() ?: 0.0,
                        accuracy = parts[5].toFloatOrNull() ?: 0f,
                        speed = parts[6].toFloatOrNull() ?: 0f,
                        frequencyHz = parts[7].toFloatOrNull() ?: 0f,
                        doseRate = parts[8].toFloatOrNull() ?: 0f,
                        temperature = parts[9].toFloatOrNull() ?: 0f,
                        uvValue = parts[10].toFloatOrNull() ?: 0f,
                        humidity = parts[11].toFloatOrNull() ?: 0f,
                        batteryLevel = parts[12].toIntOrNull() ?: 100,
                        deviceId = parts[13],
                        isValid = parts[14] == "1"
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Форматированное время для отображения
     */
    fun getTimeFormatted(): String {
        return timeFormat.format(Date(timestamp))
    }

    /**
     * Полная дата и время
     */
    fun getDateTimeFormatted(): String {
        return dateTimeFormat.format(Date(timestamp))
    }

    /**
     * Дата
     */
    fun getDateFormatted(): String {
        return dateFormat.format(Date(timestamp))
    }

    /**
     * Конвертировать в строку CSV
     */
    fun toCsvRow(): String {
        return listOf(
            timestamp.toString(),
            getDateTimeFormatted(),
            latitude.toString(),
            longitude.toString(),
            altitude.toString(),
            accuracy.toString(),
            String.format(Locale.US, "%.2f", speed),
            String.format(Locale.US, "%.2f", frequencyHz),
            String.format(Locale.US, "%.6f", doseRate),
            String.format(Locale.US, "%.1f", temperature),
            String.format(Locale.US, "%.6f", uvValue),
            String.format(Locale.US, "%.1f", humidity),
            batteryLevel.toString(),
            deviceId,
            if (isValid) "1" else "0"
        ).joinToString(",")
    }

    /**
     * Конвертировать в JSON строку
     */
    fun toJson(): String {
        return """
        {
            "id": $id,
            "sessionId": "$sessionId",
            "timestamp": $timestamp,
            "latitude": $latitude,
            "longitude": $longitude,
            "altitude": $altitude,
            "accuracy": $accuracy,
            "speed": $speed,
            "frequencyHz": $frequencyHz,
            "doseRate": $doseRate,
            "temperature": $temperature,
            "uvValue": $uvValue,
            "humidity": $humidity,
            "batteryLevel": $batteryLevel,
            "deviceId": "$deviceId",
            "isValid": $isValid
        }
        """.trimIndent()
    }
}