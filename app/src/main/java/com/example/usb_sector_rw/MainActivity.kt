package com.example.usb_sector_rw

import com.example.usb_sector_rw.msd.LospDev
import com.example.usb_sector_rw.msd.temp_exec
import com.example.usb_sector_rw.msd.temp_test
import com.example.usb_sector_rw.msd.uv_exec
import com.example.usb_sector_rw.msd.uv_test
import com.example.usb_sector_rw.msd.set_uv_range

import com.example.usb_sector_rw.losp.FALSE
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.usb_sector_rw.history.HistoryActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.lang.Boolean.TRUE
import kotlin.math.sqrt
import kotlinx.coroutines.isActive
import androidx.core.view.isVisible
import kotlin.random.Random

// Добавьте эти импорты для работы с локацией
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.usb_sector_rw.LospDevVariables.lospDev
import com.google.android.gms.location.*

// Импорты для мок-данных
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import kotlin.math.cos


object LospDevVariables {
    @SuppressLint("StaticFieldLeak")
    lateinit var lospDev: LospDev
    lateinit var log: (String) -> Unit
    lateinit var blink_log: (String) -> Unit
    var isRunMeasurment : Boolean = false;
    var isStopFrecExecMeasurment : Boolean = false;
    var isRunFrecTrackingMeasurment : Boolean = false;
    var isRunUvMeasurement : Boolean = false
    var isRunTempMeasurement : Boolean = false
    internal var measureJob: Job? = null

    fun getFrec() : Float
    {
        val freq = frecUc(1, lospDev, log)

        return if (freq >= 0.0) {
            val formattedFreq = "%.2f".format(freq)
            formattedFreq.replace(',', '.').toFloat()
        } else {
            -1f
        }
    }

    fun getFrecTest()
    {
        frec_test(lospDev, log);
    }

    fun getFrecExec()
    {
        frec_exec(lospDev, log, isStopFrecExecMeasurment)
    }

    fun getFrecTracking()
    {
        frec_tracking(lospDev, log)
    }

    fun getUvTest()
    {
        uv_test(lospDev, log)
    }

    fun setUvRange(range : UInt)
    {
        set_uv_range(range, lospDev, log)
    }

    fun getUvExec(call_last : Boolean, is_graph : Boolean) : Float
    {
        var ret = uv_exec(lospDev, log, call_last, is_graph)

        return ret
    }

    fun getTempTest()
    {
        temp_test(lospDev, log)
    }

    fun getTempExec(call_last : Boolean, is_graph : Boolean) : Float
    {
        var ret = temp_exec(lospDev, log, call_last, is_graph)

        return ret
    }

}

// Простой менеджер мок-данных
object MockDataManager {
    private var isMockMode = false
    private var mockCounter = 0

    fun enableMockMode() {
        isMockMode = true
        mockCounter = 0
    }

    fun disableMockMode() {
        isMockMode = false
    }

    fun isInMockMode(): Boolean = isMockMode

    // Генерация тестовой частоты
    fun getMockFrequency(): Float {
        val base = 50f
        val variation = sin(mockCounter * 0.1).toFloat() * 20f
        mockCounter++
        return base + variation
    }

    // Генерация тестовой дозы
    fun getMockDose(): Float {
        return 0.1f + (sin(mockCounter * 0.05).toFloat() * 0.2f)
    }

    // Генерация тестовой температуры
    fun getMockTemperature(): Float {
        return 20f + (cos(mockCounter * 0.07).toFloat() * 10f)
    }

    // Генерация тестового УФ
    fun getMockUv(): Float {
        return 0.01f + (sin(mockCounter * 0.03).toFloat() * 0.04f)
    }

    // Получение всех данных сразу
    data class MeasurementData(
        val frequency: Float,
        val dose: Float,
        val temperature: Float,
        val uv: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun getMockMeasurement(): MeasurementData {
        return MeasurementData(
            frequency = getMockFrequency(),
            dose = getMockDose(),
            temperature = getMockTemperature(),
            uv = getMockUv()
        )
    }
}

/**
 * MainActivity provides a user interface for reading and writing raw sectors
 * to a USB Mass Storage device connected via Android's USB Host API.
 *
 * The activity allows users to:
 * - Scan and list available USB devices
 * - Request permission for USB access
 * - Read a sector and display its hexadecimal content
 * - Write user data to a specific sector
 * - Clear a sector by writing zero bytes
 *
 * USB sector access operations are delegated to the [UsbSectorAccess] class.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.usb_sector_rw.USB_PERMISSION"
    }

    val options = listOf("0.1s", "0.2s", "0.5s", "1s", "5s", "10s", "60s", "1h", "1d", "авто")
    val options_accuracy = listOf("0", "1", "2", "3", "4", "5", "7")

    private lateinit var sectorInput: EditText
    private lateinit var dataInput: EditText
    private lateinit var readBtn: Button
    private lateinit var writeBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var overwriteBtn: Button
    private lateinit var logText: TextView
    private lateinit var offsetInput: TextView
    private lateinit var readBytesBtn: Button
    private lateinit var lengthInput: TextView
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var detailSwitch: Switch
    private lateinit var toggleButton: ImageButton
    private lateinit var toggleContainer: LinearLayout
    private lateinit var btnOpenFrequency: Button
    private lateinit var btnOpenUv: Button
    private lateinit var btnOpenTemp: Button
    private lateinit var gauge : FrequencyGaugeView
    private lateinit var spinner : Spinner
    private lateinit var usbOverlay: View
    private lateinit var mainContent: View
    private lateinit var usbConfirmButton: Button
    private lateinit var usbAccess: UsbSectorAccess
    private lateinit var accuracyOptionsSpinner : Spinner
    private lateinit var unitSwitch: Switch
    private lateinit var logTextView: TextView
    private lateinit var clearLogSwitch: Switch

    // Добавьте кнопку для открытия карты
    private lateinit var btnOpenMap: Button

    // НОВАЯ КНОПКА ДЛЯ ИСТОРИИ
    private lateinit var btnOpenHistory: Button

    // КНОПКА ДЛЯ РЕЖИМА ОТЛАДКИ
    private lateinit var btnDebugMode: Button

    private var usbConnected = false
    private var isAutoModeEnabled = false
    private var frecJob: Job? = null

    private var clickCount = 0
    private var lastClickTime = 0L
    private val tripleClickInterval = 600L
    private var isLogVisible = false
    private var blinkingAnimator: ObjectAnimator? = null
    private var blinkingResetJob: Job? = null
    private var isStopFrecExecMeasurment = false

    var expanded = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * BroadcastReceiver that handles USB permission responses.
     *
     * Logs whether permission was granted or denied for the detected USB device.
     */
    private lateinit var usbReceiver: BroadcastReceiver

    /**
     * Initializes the user interface, sets up event handlers, registers USB permission receiver,
     * and defines logic for each button action: scan, read, write, and clear.
     *
     * @param savedInstanceState The previously saved state of the activity, if any.
     */
    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbAccess = UsbSectorAccess(this)

        gauge = findViewById<FrequencyGaugeView>(R.id.frequencyGauge)
        spinner = findViewById(R.id.frequencyOptionsSpinner)
        usbOverlay = findViewById(R.id.usbOverlay)
        mainContent = findViewById(R.id.mainContent)
        usbConfirmButton = findViewById(R.id.usbConfirmButton)
        accuracyOptionsSpinner = findViewById(R.id.accuracyOptionsSpinner)
        unitSwitch = findViewById(R.id.unitSwitch)
        logTextView = findViewById(R.id.logTextView)
        clearLogSwitch = findViewById(R.id.clearLogSwitch)

        // Инициализируем кнопку для карты
        btnOpenMap = findViewById(R.id.btnOpenMap)

        // ИНИЦИАЛИЗИРУЕМ КНОПКУ ИСТОРИИ
        btnOpenHistory = findViewById(R.id.btnOpenHistory)

        // ИНИЦИАЛИЗИРУЕМ КНОПКУ РЕЖИМА ОТЛАДКИ
        btnDebugMode = findViewById(R.id.btnDebugMode)

        val logControlContainer = findViewById<View>(R.id.logControlContainer)

        clearLogSwitch.isChecked = true
        logControlContainer.visibility = View.GONE
        logTextView.visibility = View.GONE

        // Настраиваем обработчик для кнопки карты
        btnOpenMap.setOnClickListener {
            // Проверяем разрешения перед открытием карты
            if (checkLocationPermissions()) {
                val intent = Intent(this, MapActivity::class.java)
                startActivity(intent)
            } else {
                requestLocationPermissions()
            }
        }

        // НАСТРАИВАЕМ ОБРАБОТЧИК ДЛЯ КНОПКИ ИСТОРИИ
        btnOpenHistory.setOnClickListener {
            println("DEBUG: Кнопка 'История' нажата")

            // Простая версия для тестирования
            try {
                val intent = Intent(this, HistoryActivity::class.java)
                startActivity(intent)
                Toast.makeText(this, "Открываю историю...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // НАСТРАИВАЕМ ОБРАБОТЧИК ДЛЯ КНОПКИ РЕЖИМА ОТЛАДКИ
        btnDebugMode.setOnClickListener {
            enableDebugMode()
        }

        btnOpenMap.visibility = View.GONE
        btnOpenHistory.visibility = View.GONE

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(3)

        val adapter_accuracy = ArrayAdapter(this, android.R.layout.simple_spinner_item, options_accuracy)
        adapter_accuracy.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accuracyOptionsSpinner.adapter = adapter_accuracy
        accuracyOptionsSpinner.setSelection(1)

        setupTripleClickToToggleLogs()

        // ОРИГИНАЛЬНЫЙ ОБРАБОТЧИК ДЛЯ ПОДКЛЮЧЕНИЯ РЕАЛЬНОГО USB УСТРОЙСТВА
        usbConfirmButton.setOnClickListener {
            val ret = lospDev.isLospDeviceConnected()
            if(ret == 1u){
                usbConnected = true
                usbOverlay.visibility = View.GONE
                mainContent.visibility = View.VISIBLE
                usbAccess.close()
                startFrequencyLoop()

                // Показываем кнопки карты и истории
                btnOpenMap.visibility = View.VISIBLE
                btnOpenHistory.visibility = View.VISIBLE
            } else {
                if(ret == 0u)
                {
                    Toast.makeText(
                        this,
                        "USB Mass Storage устройство не найдено или нет разрешения.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else
                {
                    Toast.makeText(
                        this,
                        "Неверный идентификатор устройства: $ret",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        gauge.colorZoneProvider = { freq ->
            when {
                freq < 10f -> Color.GREEN
                freq < 25f -> Color.rgb(255, 165, 0)
                else -> Color.RED
            }
        }

        var freqSpinnerInitialized = false
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (!freqSpinnerInitialized) {
                    freqSpinnerInitialized = true
                    return
                }

                val selected = parent.getItemAtPosition(position).toString().trim()

                when (selected) {
                    "авто" -> {
                        isAutoModeEnabled = true
                    }

                    "1h" -> {
                        averaging_period = 3600f
                        is_no_set = FALSE
                        isAutoModeEnabled = false
                    }

                    "1d" -> {
                        averaging_period = 86400f
                        is_no_set = FALSE
                        isAutoModeEnabled = false
                    }

                    else -> {
                        val clean = selected.removeSuffix("s").trim()
                        val value = clean.toFloatOrNull()

                        if (value != null) {
                            averaging_period = value
                            is_no_set = FALSE
                            isAutoModeEnabled = false
                        } else {
                            isAutoModeEnabled = true
                        }
                    }
                }

                tick_stop = 0UL

                Log.w("MAIN", "averaging_period = $averaging_period, is_no_set = $is_no_set")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        var accuracySpinnerInitialized = false
        accuracyOptionsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (!accuracySpinnerInitialized) {
                    accuracySpinnerInitialized = true
                    return
                }

                val selected = parent.getItemAtPosition(position).toString().trim()
                val value = selected.toUIntOrNull()
                if (value != null) {
                    gauge.accuracy = value
                    Log.d("MAIN", "accuracy = $value")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        unitSwitch.setOnCheckedChangeListener { _, isChecked ->
            gauge.displayUnit = if (isChecked)
                FrequencyGaugeView.DisplayUnit.RENTGEN
            else
                FrequencyGaugeView.DisplayUnit.HERTZ
        }

        FrequencyLogger.init(this)
        LospDevVariables.lospDev = LospDev(this)
        LospDevVariables.log = ::log
        LospDevVariables.blink_log = ::blink_log

        // Register USB permission receiver
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
                if (intent.action == ACTION_USB_PERMISSION) {
                    synchronized(this) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            log("Permission granted for device ${device.deviceName}")
                        } else {
                            log("Permission denied for device ${device.deviceName}")
                        }
                    }
                }
            }
        }
        registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)

        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
                usbConnected = false
                frecJob?.cancel()
                usbOverlay.visibility = View.VISIBLE
                mainContent.visibility = View.GONE
                log("Устройство отключено: ${device.deviceName}")
            }
        }, detachFilter)
    }

    /**
     * Включает режим отладки без USB устройства
     */
    private fun enableDebugMode() {
        // Скрываем overlay
        usbOverlay.visibility = View.GONE
        mainContent.visibility = View.VISIBLE

        // Включаем режим мок-данных
        MockDataManager.enableMockMode()
        usbConnected = true

        // Инициализируем LospDev
        LospDevVariables.lospDev = LospDev(this)
        LospDevVariables.log = ::log
        LospDevVariables.blink_log = ::blink_log

        // Запускаем мок-данные вместо реальных
        startMockFrequencyLoop()

        // Показываем сообщение
        Toast.makeText(
            this,
            "Включен режим отладки. Используются тестовые данные.",
            Toast.LENGTH_LONG
        ).show()

        // Показываем кнопки карты и истории
        btnOpenMap.visibility = View.VISIBLE
        btnOpenHistory.visibility = View.VISIBLE

        // Также сразу показываем лог-контролы (опционально)
        val logControlContainer = findViewById<View>(R.id.logControlContainer)
        logControlContainer.visibility = View.VISIBLE
        logTextView.visibility = View.VISIBLE
        isLogVisible = true
    }

    private fun startFrequencyLoop() {
        frecJob?.cancel()
        frecJob = lifecycleScope.launch {
            while (isActive && usbConnected && lospDev != null) {
                withContext(Dispatchers.IO) {
                    try {
                        if (isAutoModeEnabled) {
                            frec_tracking(lospDev, LospDevVariables.log)
                        } else {
                            frec_exec(lospDev, LospDevVariables.log, isStopFrecExecMeasurment)
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Ошибка в frequency loop: ${e.message}")
                    }
                }

                var freq: Float = 0f
                var err: Float? = 0f

                if (isAutoModeEnabled) {
                    freq = 1f / tracking_period_av1.toFloat()
                    err = (1f / sqrt(tracking_m_sum.toFloat())) / tracking_period_av1.toFloat()
                } else {
                    freq = frec_old.toFloat()
                    err = if (period_acc_old.toInt() == 0) null
                    else if (accuracy.toFloat() < 1e-99f) freq * period_acc_old.toFloat() / 100f
                    else period_acc_old.toFloat()
                }

                if (::gauge.isInitialized) {
                    gauge.frequency = freq
                    gauge.error = err
                }

                delay(100)
            }
        }
    }

    /**
     * Запускает цикл с мок-данными для режима отладки
     */
    private fun startMockFrequencyLoop() {
        frecJob?.cancel()
        frecJob = lifecycleScope.launch {
            while (isActive && usbConnected) {
                // Получаем мок-данные
                val data = MockDataManager.getMockMeasurement()

                // Обновляем UI в главном потоке
                withContext(Dispatchers.Main) {
                    // Обновляем датчик частоты
                    if (::gauge.isInitialized) {
                        gauge.frequency = data.frequency
                        gauge.error = data.frequency * 0.05f // 5% погрешность
                    }

                    // Добавляем запись в лог (если лог виден)
                    if (isLogVisible) {
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(data.timestamp))
                        val logMsg = """
                            $timeStr:
                            Частота: ${"%.2f".format(data.frequency)} Гц
                            Доза: ${"%.6f".format(data.dose)} мкЗв/ч
                            Температура: ${"%.1f".format(data.temperature)} °C
                            УФ: ${"%.6f".format(data.uv)} Вт/см²
                        """.trimIndent()

                        if (clearLogSwitch.isChecked) {
                            logTextView.text = logMsg
                        } else {
                            logTextView.append("\n\n$logMsg")
                        }
                    }
                }

                // Задержка между обновлениями
                delay(1000)
            }
        }
    }

    private fun setupTripleClickToToggleLogs() {
        val headerText = findViewById<TextView>(R.id.headerText)
        val logControlContainer = findViewById<View>(R.id.logControlContainer)
        val logTextView = findViewById<View>(R.id.logTextView)

        headerText.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime <= tripleClickInterval) {
                clickCount++
            } else {
                clickCount = 1
            }
            lastClickTime = now

            if (clickCount == 3) {
                val newVisibility = if (logControlContainer.isVisible) {
                    isLogVisible = false
                    View.GONE
                } else {
                    isLogVisible = true
                    View.VISIBLE
                }
                logControlContainer.visibility = newVisibility
                logTextView.visibility = newVisibility
                btnOpenMap.visibility = newVisibility
                btnOpenHistory.visibility = newVisibility
                clickCount = 0
            }
        }
    }

    // Методы для проверки разрешений GPS
    private fun checkLocationPermissions(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            101
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                val intent = Intent(this, MapActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    "Для отображения карты необходимы разрешения на доступ к местоположению",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Logs the provided message to the on-screen text view with a newline.
     *
     * @param message The message string to display in the log area.
     */
    @SuppressLint("SetTextI18n")
    private fun blink_log(message: String) {
        // Оставьте пустым или добавьте свою логику
    }

    /**
     * Logs the provided message to the on-screen text view with a newline.
     *
     * @param message The message string to display in the log area.
     */
    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        runOnUiThread {
            if(isLogVisible)
            {
                if (clearLogSwitch.isChecked) {
                    logTextView.text = message
                } else {
                    logTextView.append("\n$message")
                }
            }
        }
    }

    /**
     * Called when the activity is about to be destroyed.
     * Unregisters the USB permission receiver to prevent memory leaks.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Unregister the USB permission receiver when the activity is destroyed
        unregisterReceiver(usbReceiver)
        scope.cancel()
    }

    /**
     * Parses a hexadecimal input string into a ByteArray.
     *
     * The input string is expected to be a sequence of two-character hexadecimal values, separated by spaces.
     * The function:
     * - Trims the input to remove any leading or trailing whitespace.
     * - Replaces any consecutive whitespace with a single space.
     * - Splits the string into tokens based on spaces.
     * - Ensures each token consists of exactly two valid hexadecimal characters (0-9, A-F, a-f).
     * - Converts each token from hexadecimal to a byte.
     *
     * If the input contains invalid tokens (e.g., incorrect length or invalid characters),
     * the function returns null. If there is an exception during conversion, null is returned.
     *
     * @param input The input string containing space-separated hexadecimal values.
     * @return A ByteArray representing the parsed hexadecimal values, or null if the input is invalid.
     *
     * Example:
     *  - Input: "0A 1F FF"
     *  - Output: ByteArray with values [0x0A, 0x1F, 0xFF]
     */
    private fun parseHexInput(input: String): ByteArray? {
        val clean = input.trim().replace(Regex("\\s+"), " ")
        val tokens = clean.split(" ")
        if (tokens.any { it.length != 2 || !it.matches(Regex("[0-9A-Fa-f]{2}")) }) return null
        return try {
            tokens.map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts a hexadecimal string (e.g. "1A 2B 3C") into a ByteArray.
     *
     * @param hexString The hexadecimal string to convert.
     * @return The corresponding ByteArray, or null if the string is invalid.
     */
    private fun hexStringToByteArray(hexString: String): ByteArray? {
        val clean = hexString.trim().replace(" ", "").uppercase()
        val length = clean.length
        if (length % 2 != 0) {
            return null // Invalid string length (should be even)
        }

        val bytes = ByteArray(length / 2)
        for (i in 0 until length step 2) {
            val hexPair = clean.substring(i, i + 2)
            bytes[i / 2] = hexPair.toInt(16).toByte()
        }
        return bytes
    }

    /**
     * Formats a given byte array as hexadecimal and ASCII representations, with 16 bytes per line.
     * The output consists of each line displaying the offset (in hexadecimal), the hex values of the bytes,
     * and the ASCII equivalent of the byte values. Non-printable characters are represented by a period ('.').
     *
     * @param data The byte array to be formatted.
     * @return A string representing the formatted data in both hexadecimal and ASCII, with each line containing 16 bytes.
     */
    private fun formatSectorAsHexAscii(data: ByteArray): String {
        return data.toList().chunked(16).withIndex().joinToString("\n") { (index, line) ->
            val hex = line.joinToString(" ") { "%02X".format(it) }
            val ascii = line.map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")
            "%04X: %-48s  %s".format(index * 16, hex, ascii)
        }
    }

    /**
     * Converts a byte value to a human-readable size representation (e.g. B, KB, MB, GB).
     * The function scales the input byte value and formats it into the most appropriate size unit
     * based on the magnitude of the number.
     *
     * @param bytes The size in bytes to be formatted.
     * @return A string representing the size in the most appropriate unit (B, KB, MB, GB).
     */
    private fun formatSize(bytes: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L
        return when {
            bytes >= gb -> "%.2f GB".format(bytes.toDouble() / gb)
            bytes >= mb -> "%.2f MB".format(bytes.toDouble() / mb)
            bytes >= kb -> "%.2f KB".format(bytes.toDouble() / kb)
            else -> "$bytes B"
        }
    }

    /**
     * Функция для проверки операций чтения и записи для диапазона секторов.
     *
     * @param usbDevice Устройство, с которым выполняется работа
     * @param count Количество секторов, которые нужно проверить
     * @return true, если все операции прошли успешно, иначе false
     */
    fun echoTest(usbDevice: LospDev, count: Long = 0): Boolean {
        val writeBuffer = ByteArray(512)
        val readBuffer = ByteArray(512)

        try {
            for (sector in 10 until count) {
                Random.nextBytes(writeBuffer)

                if (!usbDevice.sectorWrite(sector.toUInt(), writeBuffer, ::log)) {
                    log("EchoTest: Не удалось записать сектор $sector")
                    return false
                }

                readBuffer.fill(0)

                if (!usbDevice.sectorRead(sector.toUInt(), readBuffer,::log)) {
                    log("EchoTest: Не удалось прочитать сектор $sector")
                    return false
                }

                if (!writeBuffer.contentEquals(readBuffer)) {
                    log("EchoTest: Несовпадение данных в секторе $sector")
                    log("Ожидалось: ${writeBuffer.joinToString(" ") { "%02X".format(it) }}")
                    log("Получено:  ${readBuffer.joinToString(" ") { "%02X".format(it) }}")
                    return false
                }

                log("EchoTest: Сектор $sector успешно проверен")
            }

            return true
        } catch (e: Exception) {
            log("EchoTest: Ошибка при echoTest: ${e.message}")
            return false
        }
    }
}