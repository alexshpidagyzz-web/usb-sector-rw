package com.example.usb_sector_rw.history

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.usb_sector_rw.R
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    // UI элементы из нового XML
    private lateinit var btnLoadHistory: Button
    private lateinit var btnSearchSessions: Button
    private lateinit var btnFilterSessions: Button
    private lateinit var btnShowOnMap: Button
    private lateinit var btnExportAll: Button
    private lateinit var btnDeleteAll: Button
    private lateinit var btnClearLog: Button
    private lateinit var backButton: Button
    private lateinit var logText: TextView  // В XML: logTextView
    private lateinit var emptyStateView: TextView
    private lateinit var clearOutSwitch: Switch

    // Для списка сессий
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var sessionsContainer: FrameLayout

    // Данные
    private val sessions = mutableListOf<String>()
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        println("DEBUG: HistoryActivity запущена (новая версия)")

        // Находим все элементы
        initViews()

        // Настройка RecyclerView
        setupRecyclerView()

        // Начальное состояние
        setupInitialState()

        // Обработчики кнопок
        setupClickListeners()
    }

    private fun initViews() {
        btnLoadHistory = findViewById(R.id.btnLoadHistory)
        btnSearchSessions = findViewById(R.id.btnSearch)
        btnFilterSessions = findViewById(R.id.btnFilter)
        btnShowOnMap = findViewById(R.id.btnShowOnMap)
        btnExportAll = findViewById(R.id.btnExportAll)
        btnDeleteAll = findViewById(R.id.btnDeleteAll)
        btnClearLog = findViewById(R.id.btnClearLog)
        backButton = findViewById(R.id.backButton)
        logText = findViewById(R.id.logTextView)  // ИСПРАВЛЕНО: был logText, стал logTextView
        emptyStateView = findViewById(R.id.emptyStateView)
        clearOutSwitch = findViewById(R.id.clearLogSwitch)  // ИСПРАВЛЕНО: был clearOutSwitch, стал clearLogSwitch
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        sessionsContainer = findViewById(R.id.sessionsContainer)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SessionAdapter(sessions)
        recyclerView.adapter = adapter
    }

    private fun setupInitialState() {
        // Начальный текст в логе
        logText.text = "История измерений\nГотово к работе\n\n"

        // Показываем пустое состояние
        emptyStateView.text = "Нет сохраненных сессий\n\nНачните запись на карте"
        emptyStateView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        // Начальное сообщение в лог
        addToLog("История загружена")
        addToLog("Готово к работе")
    }

    private fun setupClickListeners() {
        // Кнопка загрузки истории
        btnLoadHistory.setOnClickListener {
            addToLog("Загрузка истории...")
            simulateLoadingHistory()
        }

        // Кнопка поиска сессий
        btnSearchSessions.setOnClickListener {
            addToLog("Поиск сессий")
            showSearchDialog()
        }

        // Кнопка фильтрации сессий
        btnFilterSessions.setOnClickListener {
            addToLog("Фильтрация сессий")
            showFilterDialog()
        }

        // Кнопка показа на карте
        btnShowOnMap.setOnClickListener {
            addToLog("Показать на карте")
            Toast.makeText(this, "Функция 'Показать на карте' в разработке", Toast.LENGTH_SHORT).show()
        }

        // Кнопка экспорта всех сессий
        btnExportAll.setOnClickListener {
            addToLog("Экспорт всех сессий")
            showExportDialog()
        }

        // Кнопка удаления всех сессий
        btnDeleteAll.setOnClickListener {
            addToLog("Запрос на удаление всех сессий")
            showDeleteConfirmationDialog()
        }

        // Кнопка очистки лога
        btnClearLog.setOnClickListener {
            logText.text = "Лог очищен\n\n"
            addToLog("Лог очищен вручную")
            Toast.makeText(this, "Лог очищен", Toast.LENGTH_SHORT).show()
        }

        // Кнопка назад
        backButton.setOnClickListener {
            addToLog("Возврат в главное меню")
            finish()
        }

        // Переключатель автоочистки
        clearOutSwitch.setOnCheckedChangeListener { _, isChecked ->
            val state = if (isChecked) "ВКЛ" else "ВЫКЛ"
            addToLog("Автоочистка лога: $state")
        }
    }

    private fun simulateLoadingHistory() {
        progressBar.visibility = View.VISIBLE
        emptyStateView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        // Имитация загрузки (3 секунды)
        btnLoadHistory.postDelayed({
            progressBar.visibility = View.GONE

            // Генерируем тестовые данные
            sessions.clear()
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

            for (i in 1..5) {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, -i)
                val sessionName = "Сессия ${dateFormat.format(calendar.time)}"
                sessions.add(sessionName)
            }

            adapter.notifyDataSetChanged()

            if (sessions.isNotEmpty()) {
                recyclerView.visibility = View.VISIBLE
                emptyStateView.visibility = View.GONE
                addToLog("Загружено ${sessions.size} сессий")
            } else {
                emptyStateView.text = "Нет сохраненных сессий\n\nНачните запись на карте"
                emptyStateView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                addToLog("История пуста")
            }
        }, 1500)
    }

    private fun showSearchDialog() {
        val editText = EditText(this)
        editText.hint = "Введите название сессии"

        AlertDialog.Builder(this)
            .setTitle("Поиск сессий")
            .setView(editText)
            .setPositiveButton("Искать") { dialog, _ ->
                val query = editText.text.toString()
                if (query.isNotBlank()) {
                    addToLog("Поиск: '$query'")
                    Toast.makeText(this, "Ищем '$query'...", Toast.LENGTH_SHORT).show()

                    // Фильтрация списка
                    val filtered = sessions.filter { it.contains(query, ignoreCase = true) }
                    adapter.updateData(filtered)

                    if (filtered.isEmpty()) {
                        addToLog("По запросу '$query' ничего не найдено")
                    } else {
                        addToLog("Найдено ${filtered.size} сессий")
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showFilterDialog() {
        val filterOptions = arrayOf("Все", "Сегодня", "За неделю", "За месяц", "С измерениями")

        AlertDialog.Builder(this)
            .setTitle("Фильтр сессий")
            .setItems(filterOptions) { dialog, which ->
                val selectedFilter = filterOptions[which]
                addToLog("Применен фильтр: '$selectedFilter'")
                Toast.makeText(this, "Фильтр: $selectedFilter", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showExportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Экспорт сессий")
            .setMessage("Экспортировать все ${sessions.size} сессий?")
            .setPositiveButton("Экспортировать") { dialog, _ ->
                addToLog("Экспорт ${sessions.size} сессий...")

                // Имитация экспорта
                progressBar.visibility = View.VISIBLE
                btnLoadHistory.postDelayed({
                    progressBar.visibility = View.GONE
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "sessions_export_$timestamp.csv"

                    addToLog("Экспорт завершен: $fileName")
                    Toast.makeText(this, "Экспортировано в $fileName", Toast.LENGTH_LONG).show()
                }, 2000)

                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteConfirmationDialog() {
        if (sessions.isEmpty()) {
            Toast.makeText(this, "Нет сессий для удаления", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Удаление всех сессий")
            .setMessage("Вы уверены, что хотите удалить все ${sessions.size} сессий?\n\nЭто действие нельзя отменить!")
            .setPositiveButton("УДАЛИТЬ ВСЕ") { dialog, _ ->
                sessions.clear()
                adapter.notifyDataSetChanged()

                emptyStateView.text = "Все сессии удалены\n\nНачните новую запись"
                emptyStateView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE

                addToLog("Удалены все сессии (${sessions.size})")
                Toast.makeText(this, "Все сессии удалены", Toast.LENGTH_SHORT).show()

                dialog.dismiss()
            }
            .setNegativeButton("ОТМЕНА", null)
            .show()
    }

    private fun addToLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "$timestamp: $message"

        val currentText = logText.text.toString()

        if (clearOutSwitch.isChecked) {
            logText.text = "$logEntry\n"
        } else {
            logText.text = if (currentText.isEmpty()) {
                "$logEntry\n"
            } else {
                "$currentText$logEntry\n"
            }
        }
    }

    override fun onBackPressed() {
        addToLog("Назад (системная кнопка)")
        super.onBackPressed()
    }

    // Простой адаптер для списка сессий
    private class SessionAdapter(private var items: List<String>) :
        RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = items[position]

            // Обработчик клика на элемент
            holder.itemView.setOnClickListener {
                Toast.makeText(holder.itemView.context,
                    "Выбрана: ${items[position]}",
                    Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount(): Int = items.size

        fun updateData(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}