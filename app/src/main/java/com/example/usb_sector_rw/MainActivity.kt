package com.example.usb_sector_rw


import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.usb_sector_rw.history.SimpleHistoryActivity
import com.example.usb_sector_rw.losp.FALSE

import com.example.usb_sector_rw.msd.LospDev
import com.example.usb_sector_rw.msd.temp_exec
import com.example.usb_sector_rw.msd.temp_test
import com.example.usb_sector_rw.msd.uv_exec
import com.example.usb_sector_rw.msd.uv_test
import com.example.usb_sector_rw.msd.set_uv_range
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object LospDevVariables {
    @SuppressLint("StaticFieldLeak")
    lateinit var lospDev: LospDev
    lateinit var log: (String) -> Unit
    var isRunMeasurment: Boolean = false
    var isStopFrecExecMeasurment: Boolean = false
    var isRunFrecTrackingMeasurment: Boolean = false
    var isRunUvMeasurement: Boolean = false
    var isRunTempMeasurement: Boolean = false
    internal var measureJob: Job? = null

    fun getFrec(): Float {
        val freq = frecUc(1, lospDev, log)

        return if (freq >= 0.0) {
            val formattedFreq = "%.2f".format(freq)
            formattedFreq.replace(',', '.').toFloat()
        } else {
            -1f
        }
    }

    fun getFrecTest() {
        frec_test(lospDev, log)
    }

    fun getFrecExec() {
        frec_exec(lospDev, log, isStopFrecExecMeasurment)
    }

    fun getFrecTracking() {
        frec_tracking(lospDev, log)
    }

    fun getUvTest() {
        uv_test(lospDev, log)
    }

    fun setUvRange(range: UInt) {
        set_uv_range(range, lospDev, log)
    }

    fun getUvExec(call_last: Boolean, is_graph: Boolean): Float {
        return uv_exec(lospDev, log, call_last, is_graph)
    }

    fun getTempTest() {
        temp_test(lospDev, log)
    }

    fun getTempExec(call_last: Boolean, is_graph: Boolean): Float {
        return temp_exec(lospDev, log, call_last, is_graph)
    }
}

object MockDataManager {
    private var isMockMode = AtomicBoolean(false)
    private var mockCounter = 0

    fun enableMockMode() {
        isMockMode.set(true)
        mockCounter = 0
    }

    fun disableMockMode() {
        isMockMode.set(false)
        mockCounter = 0
    }

    fun isInMockMode(): Boolean = isMockMode.get()

    fun getMockFrequency(): Float {
        val base = 50f
        val variation = sin(mockCounter * 0.1).toFloat() * 20f
        mockCounter++
        return base + variation
    }

    fun getMockDose(): Float {
        return 0.1f + (sin(mockCounter * 0.05).toFloat() * 0.2f)
    }

    fun getMockTemperature(): Float {
        return 20f + (cos(mockCounter * 0.07).toFloat() * 10f)
    }

    fun getMockUv(): Float {
        return 0.01f + (sin(mockCounter * 0.03).toFloat() * 0.04f)
    }

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

class MainActivity : AppCompatActivity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.usb_sector_rw.USB_PERMISSION"
        private const val PERMISSION_REQUEST_LOCATION = 101
    }

    private val options = listOf("0.1s", "0.2s", "0.5s", "1s", "5s", "10s", "60s", "1h", "1d", "авто")
    private val optionsAccuracy = listOf("0", "1", "2", "3", "4", "5", "7")

    private lateinit var gauge: FrequencyGaugeView
    private lateinit var spinner: Spinner
    private lateinit var usbOverlay: View
    private lateinit var mainContent: View
    private lateinit var usbConfirmButton: Button
    private lateinit var usbAccess: UsbSectorAccess
    private lateinit var accuracyOptionsSpinner: Spinner
    private lateinit var unitSwitch: SwitchCompat
    private lateinit var logTextView: TextView
    private lateinit var clearLogSwitch: SwitchCompat
    private lateinit var btnDebugMode: Button
    private lateinit var logControlContainer: View
    private lateinit var headerText: TextView

    // Меню элементы
    private lateinit var btnMenu: ImageButton
    private lateinit var sidebarMenu: LinearLayout
    private lateinit var menuOverlay: View
    private lateinit var btnCloseMenu: ImageButton

    private var usbConnected = false
    private var isAutoModeEnabled = false
    private var frecJob: Job? = null
    private var mockUpdateJob: Job? = null
    private var clickCount = 0
    private var lastClickTime = 0L
    private val tripleClickInterval = 600L
    private var isLogVisible = false
    private var isStopFrecExecMeasurment = false
    private var currentMockSessionFile: File? = null
    private var isMockModeActive = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var usbReceiver: BroadcastReceiver

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация компонентов
        initializeViews()
        setupSpinners()
        setupClickListeners()
        setupMenuListeners()
        setupTripleClickToToggleLogs()
        initializeLospDev()
        setupUsbReceivers()
        initializeLocationClient()

        // Начальное состояние
        updateUiState(false)
    }

    private fun initializeViews() {
        gauge = findViewById(R.id.frequencyGauge)
        spinner = findViewById(R.id.frequencyOptionsSpinner)
        usbOverlay = findViewById(R.id.usbOverlay)
        mainContent = findViewById(R.id.mainContent)
        usbConfirmButton = findViewById(R.id.usbConfirmButton)
        accuracyOptionsSpinner = findViewById(R.id.accuracyOptionsSpinner)
        unitSwitch = findViewById(R.id.unitSwitch)
        logTextView = findViewById(R.id.logTextView)
        clearLogSwitch = findViewById(R.id.clearLogSwitch)
        btnDebugMode = findViewById(R.id.btnDebugMode)
        logControlContainer = findViewById(R.id.logControlContainer)
        headerText = findViewById(R.id.headerText)

        // Меню элементы
        btnMenu = findViewById(R.id.btnMenu)
        sidebarMenu = findViewById(R.id.sidebarMenu)
        menuOverlay = findViewById(R.id.menuOverlay)
        btnCloseMenu = findViewById(R.id.btnCloseMenu)

        usbAccess = UsbSectorAccess(this)
        clearLogSwitch.isChecked = true
    }

    private fun setupSpinners() {
        // Настройка спиннера частоты
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(3)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = parent.getItemAtPosition(position).toString().trim()
                handleFrequencySelection(selected)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Настройка спиннера точности
        val accuracyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, optionsAccuracy)
        accuracyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accuracyOptionsSpinner.adapter = accuracyAdapter
        accuracyOptionsSpinner.setSelection(1)

        accuracyOptionsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = parent.getItemAtPosition(position).toString().trim()
                selected.toUIntOrNull()?.let { gauge.accuracy = it }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Настройка переключателя единиц измерения
        unitSwitch.setOnCheckedChangeListener { _, isChecked ->
            gauge.displayUnit = if (isChecked)
                FrequencyGaugeView.DisplayUnit.RENTGEN
            else
                FrequencyGaugeView.DisplayUnit.HERTZ
        }
    }

    private fun handleFrequencySelection(selected: String) {
        when (selected) {
            "авто" -> {
                isAutoModeEnabled = true
                tick_stop = 0UL
            }
            "1h" -> {
                averaging_period = 3600f
                is_no_set = FALSE
                isAutoModeEnabled = false
                tick_stop = 0UL
            }
            "1d" -> {
                averaging_period = 86400f
                is_no_set = FALSE
                isAutoModeEnabled = false
                tick_stop = 0UL
            }
            else -> {
                val clean = selected.removeSuffix("s").trim()
                val value = clean.toFloatOrNull()
                if (value != null) {
                    averaging_period = value
                    is_no_set = FALSE
                    isAutoModeEnabled = false
                    tick_stop = 0UL
                } else {
                    isAutoModeEnabled = true
                }
            }
        }
        Log.w("MAIN", "averaging_period = $averaging_period, is_no_set = $is_no_set")
    }

    private fun setupClickListeners() {
        // Кнопка подключения USB
        usbConfirmButton.setOnClickListener {
            handleUsbConnection()
        }

        // Кнопка режима отладки
        btnDebugMode.setOnClickListener {
            enableDebugMode()
        }

        // Кнопка открытия меню
        btnMenu.setOnClickListener {
            showMenu()
        }

        // Кнопка закрытия меню
        btnCloseMenu.setOnClickListener {
            hideMenu()
        }

        // Клик по затемнению закрывает меню
        menuOverlay.setOnClickListener {
            hideMenu()
        }
    }

    private fun setupMenuListeners() {
        // Главный экран (скрыть меню)
        findViewById<View>(R.id.menuItemMain).setOnClickListener {
            hideMenu()
        }

        // Карта GPS
        findViewById<View>(R.id.menuItemMap).setOnClickListener {
            hideMenu()
            openMapActivity()
        }

        // История
        findViewById<View>(R.id.menuItemHistory).setOnClickListener {
            hideMenu()
            openHistoryActivity()
        }

        // Частота
        findViewById<View>(R.id.menuItemFreq).setOnClickListener {
            hideMenu()
            openFreqActivity()
        }

        // Температура
        findViewById<View>(R.id.menuItemTemp).setOnClickListener {
            hideMenu()
            openTempActivity()
        }

        // УФ излучение
        findViewById<View>(R.id.menuItemUv).setOnClickListener {
            hideMenu()
            openUvActivity()
        }

        // Графики
        findViewById<View>(R.id.menuItemGraphs).setOnClickListener {
            hideMenu()
            showGraphSelectionDialog()
        }

        // О приложении
        findViewById<View>(R.id.menuItemAbout).setOnClickListener {
            hideMenu()
            showAboutDialog()
        }

        // Выход
        findViewById<View>(R.id.menuItemExit).setOnClickListener {
            hideMenu()
            showExitDialog()
        }
    }

    private fun openFreqActivity() {
        val intent = Intent(this, FreqActivity::class.java)
        startActivity(intent)
    }

    private fun openTempActivity() {
        val intent = Intent(this, TempActivity::class.java)
        startActivity(intent)
    }

    private fun openUvActivity() {
        val intent = Intent(this, UvActivity::class.java)
        startActivity(intent)
    }

    @SuppressLint("DiscouragedApi")
    private fun showGraphSelectionDialog() {
        val items = arrayOf(
            "📊 График частоты",
            "🌡️ График температуры",
            "☀️ График УФ излучения"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите график")
            .setItems(items) { _, which ->
                when(which) {
                    0 -> {
                        val intent = Intent(this, GraphFreqActivity::class.java)
                        startActivity(intent)
                    }
                    1 -> {
                        val intent = Intent(this, GraphTempActivity::class.java)
                        startActivity(intent)
                    }
                    2 -> {
                        val intent = Intent(this, GraphUvActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("О приложении")
            .setMessage(
                """
                Radiation meter for PRAM
                Версия: 1.0
                
                Приложение для измерения радиационного фона
                с использованием USB-датчиков.
                
                Функции:
                • Измерение частоты излучения
                • GPS-карта с записью маршрута
                • История измерений
                • Графики в реальном времени
                
                © 2024 PRAM Project
                """.trimIndent()
            )
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showExitDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выход")
            .setMessage("Вы уверены, что хотите выйти из приложения?")
            .setPositiveButton("Да") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showMenu() {
        sidebarMenu.visibility = View.VISIBLE
        menuOverlay.visibility = View.VISIBLE

        // Анимация выезда
        sidebarMenu.translationX = sidebarMenu.width.toFloat()
        sidebarMenu.animate()
            .translationX(0f)
            .setDuration(300)
            .start()

        // Анимация появления затемнения
        menuOverlay.alpha = 0f
        menuOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun hideMenu() {
        // Анимация выезда
        sidebarMenu.animate()
            .translationX(sidebarMenu.width.toFloat())
            .setDuration(300)
            .withEndAction {
                sidebarMenu.visibility = View.GONE
            }
            .start()

        // Анимация исчезновения затемнения
        menuOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                menuOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun handleUsbConnection() {
        val ret = LospDevVariables.lospDev.isLospDeviceConnected()
        when (ret) {
            1u -> {
                usbConnected = true
                updateUiState(true)
                usbAccess.close()
                startFrequencyLoop()
                logToUi("USB устройство подключено")
            }
            0u -> {
                Toast.makeText(
                    this,
                    "USB Mass Storage устройство не найдено или нет разрешения.",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                Toast.makeText(
                    this,
                    "Неверный идентификатор устройства: $ret",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openMapActivity() {
        if (checkLocationPermissions()) {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        } else {
            requestLocationPermissions()
        }
    }

    private fun openHistoryActivity() {
        try {
            val intent = Intent(this, SimpleHistoryActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            logToUi("Ошибка открытия истории: ${e.message}")
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun enableDebugMode() {
        usbOverlay.visibility = View.GONE
        mainContent.visibility = View.VISIBLE

        MockDataManager.enableMockMode()
        usbConnected = true
        isMockModeActive = true

        // Инициализируем LospDev для совместимости
        LospDevVariables.lospDev = LospDev(this)
        LospDevVariables.log = ::logToUi

        startMockFrequencyLoop()
        createMockSessionFile()

        Toast.makeText(
            this,
            "Включен режим отладки. Используются тестовые данные.",
            Toast.LENGTH_LONG
        ).show()

        updateUiState(true)
        logControlContainer.visibility = View.VISIBLE
        logTextView.visibility = View.VISIBLE
        isLogVisible = true
    }

    private fun createMockSessionFile() {
        try {
            val baseDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "USB_Sensor_Data/Sessions"
            )
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val fileName = "mock_session_${dateFormat.format(Date())}.csv"
            currentMockSessionFile = File(baseDir, fileName)

            // Создаем заголовок CSV
            val csvHeader = """
                # Сессия измерений (Тестовый режим)
                # Дата создания: ${Date()}
                # Режим: Отладка (мок-данные)
                #
                Timestamp,DateTime,Latitude,Longitude,Altitude,Accuracy(m),Speed(km/h),Frequency(Hz),DoseRate(uSv/h),Temperature(C),UV(W/cm2),Humidity(%),Battery(%),DeviceID,Valid
                
            """.trimIndent()

            currentMockSessionFile?.writeText(csvHeader)
            logToUi("Создан файл сессии: ${currentMockSessionFile?.name}")
        } catch (e: Exception) {
            logToUi("Ошибка создания файла сессии: ${e.message}")
        }
    }

    private fun appendMockDataToFile(data: MockDataManager.MeasurementData) {
        currentMockSessionFile?.let { file ->
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val csvRow = "${System.currentTimeMillis()},${dateFormat.format(Date(data.timestamp))},0.0,0.0,0.0,10.0,0.0," +
                        "${String.format(Locale.US, "%.2f", data.frequency)}," +
                        "${String.format(Locale.US, "%.6f", data.dose)}," +
                        "${String.format(Locale.US, "%.1f", data.temperature)}," +
                        "${String.format(Locale.US, "%.6f", data.uv)},0.0,100,mock_device,1\n"

                file.appendText(csvRow)
            } catch (e: Exception) {
                Log.e("MainActivity", "Ошибка записи в файл: ${e.message}")
            }
        }
    }

    private fun startFrequencyLoop() {
        stopAllUpdateJobs()

        frecJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive && usbConnected && LospDevVariables.lospDev != null && !isMockModeActive) {
                try {
                    if (isAutoModeEnabled) {
                        frec_tracking(LospDevVariables.lospDev, LospDevVariables.log)
                    } else {
                        frec_exec(LospDevVariables.lospDev, LospDevVariables.log, isStopFrecExecMeasurment)
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

                    // Обновляем UI в главном потоке
                    withContext(Dispatchers.Main) {
                        if (::gauge.isInitialized) {
                            gauge.frequency = freq
                            gauge.error = err
                        }
                    }

                    delay(100)
                } catch (e: CancellationException) {
                    // Корректное завершение при отмене корутины
                    Log.d("MainActivity", "Frequency loop cancelled")
                    break
                }
            }
        }
    }

    private fun startMockFrequencyLoop() {
        stopAllUpdateJobs()
        isMockModeActive = true

        mockUpdateJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive && usbConnected && isMockModeActive) {
                try {
                    val data = MockDataManager.getMockMeasurement()

                    // Обновляем UI и записываем в файл в главном потоке
                    withContext(Dispatchers.Main) {
                        // Обновляем датчик
                        if (::gauge.isInitialized) {
                            gauge.frequency = data.frequency
                            gauge.error = data.frequency * 0.05f
                        }

                        // Добавляем в лог
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

                    // Записываем в файл
                    appendMockDataToFile(data)

                    delay(1000)
                } catch (e: CancellationException) {
                    // Корректное завершение при отмене корутины
                    Log.d("MainActivity", "Mock frequency loop cancelled")
                    break
                }
            }
        }
    }

    private fun stopAllUpdateJobs() {
        frecJob?.cancel()
        mockUpdateJob?.cancel()

        // Даем время на корректное завершение
        runBlocking {
            delay(100)
        }

        frecJob = null
        mockUpdateJob = null
        isMockModeActive = false
    }

    private fun setupTripleClickToToggleLogs() {
        headerText.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime <= tripleClickInterval) {
                clickCount++
            } else {
                clickCount = 1
            }
            lastClickTime = now

            if (clickCount == 3) {
                isLogVisible = !isLogVisible
                val newVisibility = if (isLogVisible) View.VISIBLE else View.GONE
                logControlContainer.visibility = newVisibility
                logTextView.visibility = newVisibility
                clickCount = 0

                if (isLogVisible) {
                    logToUi("Лог включен")
                }
            }
        }
    }

    private fun initializeLospDev() {
        FrequencyLogger.init(this)
        LospDevVariables.lospDev = LospDev(this)
        LospDevVariables.log = ::logToUi

        gauge.colorZoneProvider = { freq ->
            when {
                freq < 10f -> Color.GREEN
                freq < 25f -> Color.rgb(255, 165, 0)
                else -> Color.RED
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun setupUsbReceivers() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                if (intent.action == ACTION_USB_PERMISSION && device != null) {
                    synchronized(this) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            logToUi("Разрешение получено для устройства ${device.deviceName}")
                        } else {
                            logToUi("Разрешение отклонено для устройства ${device.deviceName}")
                        }
                    }
                }
            }
        }

        // Для Android 14+ используем RECEIVER_NOT_EXPORTED, для старых версий - 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(usbReceiver, filter)
            }
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, filter)
        }

        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        val detachReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                if (device != null) {
                    usbConnected = false
                    stopAllUpdateJobs()
                    updateUiState(false)
                    logToUi("Устройство отключено: ${device.deviceName}")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(detachReceiver, detachFilter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(detachReceiver, detachFilter)
            }
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(detachReceiver, detachFilter)
        }
    }

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun updateUiState(connected: Boolean) {
        uiHandler.post {
            if (connected) {
                usbOverlay.visibility = View.GONE
                mainContent.visibility = View.VISIBLE
            } else {
                usbOverlay.visibility = View.VISIBLE
                mainContent.visibility = View.GONE
            }
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED)
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            PERMISSION_REQUEST_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_LOCATION -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    openMapActivity()
                } else {
                    Toast.makeText(
                        this,
                        "Для отображения карты необходимы разрешения на доступ к местоположению",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun logToUi(message: String) {
        uiHandler.post {
            if (isLogVisible) {
                if (clearLogSwitch.isChecked) {
                    logTextView.text = "$message\n"
                } else {
                    logTextView.append("$message\n")
                }
            }
        }
    }

    override fun onBackPressed() {
        if (sidebarMenu.visibility == View.VISIBLE) {
            hideMenu()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        // Останавливаем только обновления, но не отменяем полностью
        frecJob?.cancel()
        mockUpdateJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (usbConnected) {
            if (MockDataManager.isInMockMode()) {
                startMockFrequencyLoop()
            } else {
                startFrequencyLoop()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Полностью останавливаем все корутины
        stopAllUpdateJobs()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: IllegalArgumentException) {
            // Игнорируем если receiver не зарегистрирован
        }
    }

    // Вспомогательные методы для работы с USB
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

    private fun hexStringToByteArray(hexString: String): ByteArray? {
        val clean = hexString.trim().replace(" ", "").uppercase()
        val length = clean.length
        if (length % 2 != 0) return null

        return ByteArray(length / 2).apply {
            for (i in 0 until length step 2) {
                val hexPair = clean.substring(i, i + 2)
                this[i / 2] = hexPair.toInt(16).toByte()
            }
        }
    }

    private fun formatSectorAsHexAscii(data: ByteArray): String {
        return data.toList().chunked(16).withIndex().joinToString("\n") { (index, line) ->
            val hex = line.joinToString(" ") { "%02X".format(it) }
            val ascii = line.map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")
            "%04X: %-48s  %s".format(index * 16, hex, ascii)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024L * 1024 -> "%.2f MB".format(bytes.toDouble() / (1024 * 1024))
            bytes >= 1024L -> "%.2f KB".format(bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    fun echoTest(usbDevice: LospDev, count: Long = 0): Boolean {
        val writeBuffer = ByteArray(512)
        val readBuffer = ByteArray(512)

        try {
            for (sector in 10 until count) {
                Random.nextBytes(writeBuffer)

                if (!usbDevice.sectorWrite(sector.toUInt(), writeBuffer, ::logToUi)) {
                    logToUi("EchoTest: Не удалось записать сектор $sector")
                    return false
                }

                readBuffer.fill(0)

                if (!usbDevice.sectorRead(sector.toUInt(), readBuffer, ::logToUi)) {
                    logToUi("EchoTest: Не удалось прочитать сектор $sector")
                    return false
                }

                if (!writeBuffer.contentEquals(readBuffer)) {
                    logToUi("EchoTest: Несовпадение данных в секторе $sector")
                    return false
                }

                logToUi("EchoTest: Сектор $sector успешно проверен")
            }
            return true
        } catch (e: Exception) {
            logToUi("EchoTest: Ошибка при echoTest: ${e.message}")
            return false
        }
    }

    // Новые методы для поддержки отключения режима отладки
    fun disableDebugMode() {
        MockDataManager.disableMockMode()
        isMockModeActive = false
        usbConnected = false
        updateUiState(false)
        stopAllUpdateJobs()

        Toast.makeText(
            this,
            "Режим отладки отключен",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Метод для проверки статуса режима отладки
    fun isDebugModeEnabled(): Boolean {
        return MockDataManager.isInMockMode()
    }
}