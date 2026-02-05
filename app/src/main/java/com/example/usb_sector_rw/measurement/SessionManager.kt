package com.example.usb_sector_rw.measurement

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.usb_sector_rw.R
import com.example.usb_sector_rw.MapActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SessionManager : AppCompatActivity() {

    companion object {
        private const val TAG = "SessionManager"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val REQUEST_SHOW_ON_MAP = 100
    }

    // UI элементы
    private lateinit var tvSessionName: TextView
    private lateinit var tvSessionInfo: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: Button
    private lateinit var btnExport: Button
    private lateinit var btnShowOnMap: Button
    private lateinit var btnDelete: Button
    private lateinit var selectionControls: LinearLayout
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button

    // Данные - ИЗМЕНЕНИЕ: инициализируем пустым списком
    private lateinit var session: MeasurementSession
    private var measurements: List<GeoMeasurement> = emptyList() // ИЗМЕНЕНО

    // Адаптер
    private lateinit var adapter: MeasurementAdapter

    // Выбранные измерения
    private val selectedMeasurements = mutableSetOf<Long>()
    private var isSelectionMode = false

    // FileExporter
    private lateinit var fileExporter: EnhancedFileExporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_detail)

        // Получаем ID сессии из Intent
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        Log.d(TAG, "Получен sessionId: $sessionId")

        if (sessionId.isNullOrEmpty()) {
            Toast.makeText(this, "Ошибка: не указана сессия", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Инициализация UI
        initViews()

        // Инициализация FileExporter
        fileExporter = EnhancedFileExporter(this)

        // Загрузка сессии
        loadSession(sessionId)

        // Настройка обработчиков
        setupListeners()
    }

    private fun initViews() {
        tvSessionName = findViewById(R.id.tvSessionName)
        tvSessionInfo = findViewById(R.id.tvSessionInfo)
        recyclerView = findViewById(R.id.recyclerView)
        emptyStateView = findViewById(R.id.emptyStateView)
        progressBar = findViewById(R.id.progressBar)
        btnBack = findViewById(R.id.btnBack)
        btnExport = findViewById(R.id.btnExport)
        btnShowOnMap = findViewById(R.id.btnShowOnMap)
        btnDelete = findViewById(R.id.btnDelete)
        selectionControls = findViewById(R.id.selectionControls)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeselectAll = findViewById(R.id.btnDeselectAll)

        // Настройка RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MeasurementAdapter(emptyList()) // ИЗМЕНЕНО: передаем пустой список
        recyclerView.adapter = adapter
    }

    private fun loadSession(sessionId: String) {
        progressBar.visibility = View.VISIBLE
        Log.d(TAG, "Ищем файл для sessionId: $sessionId")

        Thread {
            try {
                // Загружаем сессию из файла
                val sessionsDir = MeasurementSession.getSessionsDirectory(this@SessionManager)
                Log.d(TAG, "Директория сессий: ${sessionsDir.absolutePath}")

                // Покажем все файлы в директории
                val files = sessionsDir.listFiles()
                files?.forEach { file ->
                    Log.d(TAG, "Найден файл: ${file.name}")
                }

                // Пробуем найти файл
                var foundFile: File? = null

                // 1. Ищем файл с точным совпадением ID
                foundFile = File(sessionsDir, "$sessionId.csv")
                if (!foundFile.exists()) {
                    // 2. Ищем файлы, которые содержат часть ID
                    val matchingFiles = sessionsDir.listFiles { _, name ->
                        name.contains(sessionId.take(10)) // Берем первые 10 символов
                    }

                    if (matchingFiles != null && matchingFiles.isNotEmpty()) {
                        foundFile = matchingFiles.first()
                        Log.d(TAG, "Найден файл по части ID: ${foundFile.name}")
                    }
                }

                // 3. Берем самый новый файл, если не нашли
                if (foundFile == null || !foundFile.exists()) {
                    val newestFile = sessionsDir.listFiles()?.maxByOrNull { it.lastModified() }
                    if (newestFile != null) {
                        foundFile = newestFile
                        Log.d(TAG, "Берем самый новый файл: ${foundFile.name}")
                    }
                }

                if (foundFile == null || !foundFile.exists()) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(
                            this@SessionManager,
                            "Файлы сессий не найдены в директории",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "Файлы сессий не найдены. Проверьте создаются ли сессии на карте.")
                        finish()
                    }
                    return@Thread
                }

                Log.d(TAG, "Загружаем файл: ${foundFile.name}")
                val loadedSession = MeasurementSession.loadFromFile(foundFile)

                if (loadedSession == null) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(
                            this@SessionManager,
                            "Не удалось загрузить сессию",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    return@Thread
                }

                session = loadedSession
                measurements = session.getMeasurements()

                runOnUiThread {
                    updateSessionInfo()
                    adapter.updateMeasurements(measurements)
                    updateEmptyState()
                    progressBar.visibility = View.GONE

                    btnShowOnMap.isEnabled = measurements.isNotEmpty()

                    Log.d(TAG, "Загружена сессия: ${session.name}")
                    Log.d(TAG, "Измерений: ${measurements.size}")
                    Log.d(TAG, "Путь файла: ${foundFile.absolutePath}")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@SessionManager,
                        "Ошибка загрузки: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Ошибка загрузки сессии", e)
                    finish()
                }
            }
        }.start()
    }

    @SuppressLint("SetTextI18n")
    private fun updateSessionInfo() {
        // Название сессии
        tvSessionName.text = session.name

        // Информация о сессии
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        val startTime = dateFormat.format(Date(session.startTime))
        val endTime = session.endTime?.let { dateFormat.format(Date(it)) } ?: "В процессе"

        val infoText = """
            ID: ${session.id}
            Начало: $startTime
            Окончание: $endTime
            Продолжительность: ${session.getDurationFormatted()}
            Точек измерений: ${measurements.size}
            Расстояние: ${String.format("%.2f", session.totalDistance)} м
            Средняя скорость: ${String.format("%.2f", session.averageSpeed)} км/ч
            Макс. скорость: ${String.format("%.2f", session.maxSpeed)} км/ч
            Средняя частота: ${String.format("%.2f", session.avgFrequency)} Гц
            Средняя доза: ${String.format("%.6f", session.avgDoseRate)} мкЗв/ч
            Средняя температура: ${String.format("%.1f", session.avgTemperature)} °C
            Средний УФ: ${String.format("%.6f", session.avgUvValue)} Вт/см²
        """.trimIndent()

        tvSessionInfo.text = infoText
    }

    private fun updateEmptyState() {
        if (measurements.isEmpty()) {
            emptyStateView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            btnShowOnMap.isEnabled = false
        } else {
            emptyStateView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            btnShowOnMap.isEnabled = true
        }
    }

    private fun setupListeners() {
        // Кнопка назад
        btnBack.setOnClickListener {
            finish()
        }

        // Кнопка экспорта
        btnExport.setOnClickListener {
            showExportDialog()
        }

        // Кнопка показа на карте
        btnShowOnMap.setOnClickListener {
            showOnMap()
        }

        // Кнопка удаления
        btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }

        // Кнопка выбрать все
        btnSelectAll.setOnClickListener {
            selectAllMeasurements()
        }

        // Кнопка отменить все
        btnDeselectAll.setOnClickListener {
            deselectAllMeasurements()
        }
    }

    private fun showExportDialog() {
        val formats = arrayOf("CSV (Excel)", "JSON", "Только выбранные", "Весь файл")

        AlertDialog.Builder(this)
            .setTitle("Экспорт измерений")
            .setItems(formats) { dialog, which ->
                when (which) {
                    0 -> exportToCsv(false)
                    1 -> exportToJson()
                    2 -> exportSelected()
                    3 -> exportToCsv(true)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun exportToCsv(exportAll: Boolean) {
        val measurementsToExport = if (exportAll) measurements else {
            if (selectedMeasurements.isEmpty()) {
                Toast.makeText(this, "Нет выбранных измерений", Toast.LENGTH_SHORT).show()
                return
            }
            measurements.filter { it.id in selectedMeasurements }
        }

        if (measurementsToExport.isEmpty()) {
            Toast.makeText(this, "Нет данных для экспорта", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        Thread {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${session.name.replace(Regex("[^a-zA-Z0-9]"), "_")}_$timestamp.csv"

            try {
                // Создаем временный файл с измерениями
                val tempSession = MeasurementSession(
                    name = "Выбранные измерения",
                    startTime = System.currentTimeMillis(),
                    endTime = System.currentTimeMillis()
                )

                // Добавляем измерения в сессию
                measurementsToExport.forEach { measurement ->
                    tempSession.addMeasurement(measurement)
                }

                // Используем saveSession вместо exportToExcelCsv
                val resultFile = fileExporter.saveSession(tempSession)

                runOnUiThread {
                    progressBar.visibility = View.GONE

                    if (resultFile != null && resultFile.exists()) {
                        showExportSuccessDialog(resultFile)
                    } else {
                        Toast.makeText(this, "Ошибка экспорта", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun exportToJson() {
        Toast.makeText(this, "Экспорт в JSON в разработке", Toast.LENGTH_SHORT).show()
    }

    private fun exportSelected() {
        if (selectedMeasurements.isEmpty()) {
            Toast.makeText(this, "Сначала выберите измерения", Toast.LENGTH_SHORT).show()
            return
        }

        exportToCsv(false)
    }

    private fun showExportSuccessDialog(file: File) {
        val fileSizeKB = if (file.exists()) file.length() / 1024 else 0

        AlertDialog.Builder(this)
            .setTitle("Экспорт успешен")
            .setMessage("Файл сохранен: ${file.name}\n\nРазмер: ${fileSizeKB} KB")
            .setPositiveButton("Поделиться") { dialog, _ ->
                val shareIntent = fileExporter.shareFile(file, "Экспорт измерений")
                startActivity(shareIntent)
                dialog.dismiss()
            }
            .setNeutralButton("Открыть папку") { dialog, _ ->
                fileExporter.openFileInFileManager(file)
                dialog.dismiss()
            }
            .setNegativeButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * ПОКАЗАТЬ СЕССИЮ НА КАРТЕ
     */
    private fun showOnMap() {
        if (measurements.isEmpty()) {
            Toast.makeText(this, "Нет измерений для отображения на карте", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, MapActivity::class.java).apply {
            putExtra("show_session_id", session.id)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        startActivityForResult(intent, REQUEST_SHOW_ON_MAP)

        Toast.makeText(this, "Загружаем маршрут на карту...", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SHOW_ON_MAP) {
            Toast.makeText(this, "Возвращены с карты", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation() {
        if (selectedMeasurements.isEmpty()) {
            // Удаление всей сессии
            AlertDialog.Builder(this)
                .setTitle("Удалить сессию")
                .setMessage("Вы уверены, что хотите удалить сессию \"${session.name}\"?")
                .setPositiveButton("Удалить") { dialog, _ ->
                    deleteSession()
                    dialog.dismiss()
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            // Удаление выбранных измерений
            AlertDialog.Builder(this)
                .setTitle("Удалить выбранные")
                .setMessage("Удалить ${selectedMeasurements.size} измерений?")
                .setPositiveButton("Удалить") { dialog, _ ->
                    deleteSelectedMeasurements()
                    dialog.dismiss()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun deleteSession() {
        val success = MeasurementSession.deleteSession(this, session.id)

        if (success) {
            Toast.makeText(this, "Сессия удалена", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Ошибка удаления", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSelectedMeasurements() {
        Toast.makeText(this, "Функция в разработке", Toast.LENGTH_SHORT).show()
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        selectedMeasurements.clear()

        // Показываем панель выбора
        selectionControls.visibility = View.VISIBLE
        updateSelectionCount()

        adapter.notifyDataSetChanged()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedMeasurements.clear()

        // Скрываем панель выбора
        selectionControls.visibility = View.GONE

        adapter.notifyDataSetChanged()
    }

    private fun toggleMeasurementSelection(measurementId: Long) {
        if (selectedMeasurements.contains(measurementId)) {
            selectedMeasurements.remove(measurementId)
        } else {
            selectedMeasurements.add(measurementId)
        }

        updateSelectionCount()
        adapter.notifyDataSetChanged()

        // Если ничего не выбрано - выходим из режима выбора
        if (selectedMeasurements.isEmpty()) {
            exitSelectionMode()
        }
    }

    private fun selectAllMeasurements() {
        selectedMeasurements.clear()
        selectedMeasurements.addAll(measurements.map { it.id })

        updateSelectionCount()
        adapter.notifyDataSetChanged()
    }

    private fun deselectAllMeasurements() {
        selectedMeasurements.clear()
        updateSelectionCount()
        adapter.notifyDataSetChanged()
    }

    private fun updateSelectionCount() {
        tvSelectionCount.text = "Выбрано: ${selectedMeasurements.size}"
    }

    private fun showMeasurementDetails(measurement: GeoMeasurement) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS", Locale.getDefault())

        val details = """
            Время: ${dateFormat.format(Date(measurement.timestamp))}
            
            Координаты:
            Широта: ${measurement.latitude}
            Долгота: ${measurement.longitude}
            Высота: ${measurement.altitude} м
            
            GPS:
            Точность: ${measurement.accuracy} м
            Скорость: ${String.format("%.2f", measurement.speed)} км/ч
            
            Измерения:
            Частота: ${String.format("%.2f", measurement.frequencyHz)} Гц
            Мощность дозы: ${String.format("%.6f", measurement.doseRate)} мкЗв/ч
            Температура: ${String.format("%.1f", measurement.temperature)} °C
            УФ излучение: ${String.format("%.6f", measurement.uvValue)} Вт/см²
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Детали измерения")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Адаптер для списка измерений
     */
    inner class MeasurementAdapter(
        private var measurements: List<GeoMeasurement> // ИЗМЕНЕНО: передаем в конструктор
    ) : RecyclerView.Adapter<MeasurementAdapter.ViewHolder>() {

        @SuppressLint("NotifyDataSetChanged")
        fun updateMeasurements(newMeasurements: List<GeoMeasurement>) {
            measurements = newMeasurements
            notifyDataSetChanged()
        }

        private var onItemClickListener: ((GeoMeasurement) -> Unit)? = null
        private var onItemLongClickListener: ((GeoMeasurement) -> Unit)? = null

        fun setOnItemClickListener(listener: (GeoMeasurement) -> Unit) {
            onItemClickListener = listener
        }

        fun setOnItemLongClickListener(listener: (GeoMeasurement) -> Unit) {
            onItemLongClickListener = listener
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTime: TextView = itemView.findViewById(R.id.tvMeasurementTime)
            val tvCoordinates: TextView = itemView.findViewById(R.id.tvMeasurementCoordinates)
            val tvData: TextView = itemView.findViewById(R.id.tvMeasurementData)
            val checkbox: CheckBox = itemView.findViewById(R.id.checkboxSelect)
            val cardView: View = itemView.findViewById(R.id.cardView)

            fun bind(measurement: GeoMeasurement) {
                // Время
                tvTime.text = measurement.getTimeFormatted()

                // Координаты
                tvCoordinates.text = String.format(
                    Locale.getDefault(),
                    "%.6f, %.6f",
                    measurement.latitude,
                    measurement.longitude
                )

                // Данные
                val dataText = buildString {
                    append("Частота: ${String.format("%.2f", measurement.frequencyHz)} Гц | ")
                    append("Доза: ${String.format("%.6f", measurement.doseRate)} мкЗв/ч")
                    if (measurement.temperature > 0) {
                        append(" | Темп.: ${String.format("%.1f", measurement.temperature)}°C")
                    }
                }
                tvData.text = dataText

                // Цвет фона в зависимости от уровня дозы
                val doseRate = measurement.doseRate
                val backgroundColor = when {
                    doseRate < 0.1f -> 0xFFE8F5E9.toInt()
                    doseRate < 0.5f -> 0xFFFFF8E1.toInt()
                    doseRate < 1.0f -> 0xFFFFF3E0.toInt()
                    else -> 0xFFFFEBEE.toInt()
                }
                cardView.setBackgroundColor(backgroundColor)

                // Checkbox в режиме выбора
                if (isSelectionMode) {
                    checkbox.visibility = View.VISIBLE
                    checkbox.isChecked = selectedMeasurements.contains(measurement.id)
                } else {
                    checkbox.visibility = View.GONE
                }

                // Обработчики кликов
                itemView.setOnClickListener {
                    onItemClickListener?.invoke(measurement)
                }

                itemView.setOnLongClickListener {
                    onItemLongClickListener?.invoke(measurement)
                    true
                }

                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedMeasurements.add(measurement.id)
                    } else {
                        selectedMeasurements.remove(measurement.id)
                    }
                    updateSelectionCount()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_measurement, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val measurement = measurements[position]
            holder.bind(measurement)
        }

        override fun getItemCount(): Int = measurements.size
    }
}