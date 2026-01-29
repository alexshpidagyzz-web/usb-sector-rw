// Файл: app/src/main/java/com/example/usb_sector_rw/measurement/SessionManager.kt
package com.example.usb_sector_rw.measurement

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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

/**
 * Активность для просмотра деталей конкретной сессии
 */
class SessionManager : AppCompatActivity() {

    companion object {
        private const val TAG = "SessionManager"
        private const val EXTRA_SESSION_ID = "session_id"
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

    // Данные
    private lateinit var session: MeasurementSession
    private lateinit var measurements: List<GeoMeasurement>

    // Адаптер
    private lateinit var adapter: MeasurementAdapter

    // Выбранные измерения
    private val selectedMeasurements = mutableSetOf<Long>()
    private var isSelectionMode = false

    // FileExporter
    private lateinit var fileExporter: FileExporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_detail)

        // Получаем ID сессии из Intent
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (sessionId.isNullOrEmpty()) {
            Toast.makeText(this, "Ошибка: не указана сессия", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Инициализация UI
        initViews()

        // Инициализация FileExporter
        fileExporter = FileExporter(this)

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
        adapter = MeasurementAdapter()
        recyclerView.adapter = adapter
    }

    private fun loadSession(sessionId: String) {
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                // Загружаем сессию из файла
                val sessionsDir = MeasurementSession.getSessionsDirectory(this@SessionManager)
                val file = File(sessionsDir, "$sessionId.csv")

                val loadedSession = MeasurementSession.loadFromFile(file)

                if (loadedSession == null) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@SessionManager, "Сессия не найдена", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@Thread
                }

                session = loadedSession
                measurements = session.getMeasurements()

                runOnUiThread {
                    updateSessionInfo()
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@SessionManager, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
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
        } else {
            emptyStateView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
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

        // Длинное нажатие на элемент - вход в режим выбора
        adapter.setOnItemLongClickListener { measurement ->
            if (!isSelectionMode) {
                enterSelectionMode()
            }
            toggleMeasurementSelection(measurement.id)
            true
        }

        // Короткое нажатие на элемент
        adapter.setOnItemClickListener { measurement ->
            if (isSelectionMode) {
                toggleMeasurementSelection(measurement.id)
            } else {
                showMeasurementDetails(measurement)
            }
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

            val result = fileExporter.exportToExcelCsv(measurementsToExport, fileName)

            runOnUiThread {
                progressBar.visibility = View.GONE

                if (result.isSuccess) {
                    showExportSuccessDialog(result.file!!)
                } else {
                    Toast.makeText(this, "Ошибка экспорта: ${result.errorMessage}", Toast.LENGTH_LONG).show()
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
        AlertDialog.Builder(this)
            .setTitle("Экспорт успешен")
            .setMessage("Файл сохранен: ${file.name}\n\nРазмер: ${file.length() / 1024} KB")
            .setPositiveButton("Поделиться") { dialog, _ ->
                val shareIntent = fileExporter.shareFile(file, "Экспорт измерений")
                startActivity(shareIntent)
                dialog.dismiss()
            }
            .setNegativeButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showOnMap() {
        val intent = Intent(this, MapActivity::class.java)
        intent.putExtra("show_session_id", session.id)
        startActivity(intent)
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
        // TODO: Реализовать удаление выбранных измерений из сессии
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
    inner class MeasurementAdapter : RecyclerView.Adapter<MeasurementAdapter.ViewHolder>() {

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
                    doseRate < 0.1f -> 0xFFE8F5E9.toInt() // Светло-зеленый
                    doseRate < 0.5f -> 0xFFFFF8E1.toInt() // Светло-желтый
                    doseRate < 1.0f -> 0xFFFFF3E0.toInt() // Светло-оранжевый
                    else -> 0xFFFFEBEE.toInt() // Светло-красный
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