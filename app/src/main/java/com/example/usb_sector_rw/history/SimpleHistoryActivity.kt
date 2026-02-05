package com.example.usb_sector_rw.history

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.usb_sector_rw.R
import com.example.usb_sector_rw.measurement.EnhancedFileExporter
import com.example.usb_sector_rw.measurement.SessionManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SimpleHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SessionAdapter
    private lateinit var emptyView: TextView
    private lateinit var exportButton: Button
    private lateinit var deleteButton: Button
    private lateinit var selectAllButton: Button
    private lateinit var backButton: Button

    private val selectedSessions = mutableSetOf<String>()
    private lateinit var fileExporter: EnhancedFileExporter
    private var sessionsList: List<SessionItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_simple)

        fileExporter = EnhancedFileExporter(this)

        // Находим элементы
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        exportButton = findViewById(R.id.exportButton)
        deleteButton = findViewById(R.id.deleteButton)
        selectAllButton = findViewById(R.id.selectAllButton)
        backButton = findViewById(R.id.backButton)

        // Настраиваем RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SessionAdapter(sessionsList) { sessionId, isSelected ->
            if (isSelected) {
                selectedSessions.add(sessionId)
            } else {
                selectedSessions.remove(sessionId)
            }
            updateActionButtons()
        }
        recyclerView.adapter = adapter

        // Загружаем данные
        loadSessions()

        // Настраиваем кнопки
        setupButtons()
    }

    private fun loadSessions() {
        sessionsList = fileExporter.getAllSessions()
            .sortedByDescending { it.lastModified() }
            .map { file: File ->
                SessionItem(
                    id = file.nameWithoutExtension, // Без .csv
                    name = formatSessionName(file),
                    date = formatDate(file.lastModified()),
                    size = formatFileSize(file.length()),
                    file = file
                )
            }

        adapter.updateSessions(sessionsList)
        emptyView.visibility = if (sessionsList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun formatSessionName(file: File): String {
        val name = file.nameWithoutExtension
        return if (name.contains('_')) {
            name.split('_').drop(1).dropLast(1).joinToString(" ")
        } else {
            name
        }.ifBlank { "Без имени" }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(timestamp))
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 -> "${String.format("%.1f", size / (1024.0 * 1024))} MB"
            size >= 1024 -> "${String.format("%.1f", size / 1024.0)} KB"
            else -> "$size B"
        }
    }

    private fun setupButtons() {
        exportButton.setOnClickListener {
            if (selectedSessions.isEmpty()) {
                showMessage("Выберите сессии для экспорта")
                return@setOnClickListener
            }
            exportSelectedSessions()
        }

        deleteButton.setOnClickListener {
            if (selectedSessions.isEmpty()) {
                showMessage("Выберите сессии для удаления")
                return@setOnClickListener
            }
            deleteSelectedSessions()
        }

        selectAllButton.setOnClickListener {
            if (selectedSessions.size == sessionsList.size) {
                selectedSessions.clear()
                selectAllButton.text = "Выбрать все"
            } else {
                selectedSessions.clear()
                selectedSessions.addAll(sessionsList.map { it.id })
                selectAllButton.text = "Снять все"
            }
            adapter.notifyDataSetChanged()
            updateActionButtons()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun exportSelectedSessions() {
        val selectedFiles = sessionsList
            .filter { sessionItem -> sessionItem.id in selectedSessions }
            .map { sessionItem -> sessionItem.file }

        AlertDialog.Builder(this)
            .setTitle("Экспорт сессий")
            .setMessage("Экспортировать ${selectedFiles.size} сессий в ZIP архив?")
            .setPositiveButton("Экспорт") { _, _ ->
                showProgress("Экспорт...")

                Thread {
                    try {
                        val zipFile = fileExporter.exportSessionsToZip(selectedFiles)

                        runOnUiThread {
                            hideProgress()
                            if (zipFile != null) {
                                showExportSuccessDialog(zipFile)
                            } else {
                                showMessage("Ошибка создания архива")
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            hideProgress()
                            showMessage("Ошибка: ${e.message}")
                        }
                    }
                }.start()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteSelectedSessions() {
        AlertDialog.Builder(this)
            .setTitle("Удаление сессий")
            .setMessage("Удалить ${selectedSessions.size} выбранных сессий?\nЭто действие нельзя отменить!")
            .setPositiveButton("УДАЛИТЬ") { _, _ ->
                var successCount = 0
                var failCount = 0

                selectedSessions.forEach { sessionId ->
                    val session = sessionsList.find { sessionItem -> sessionItem.id == sessionId }
                    if (session != null && fileExporter.deleteSession(session.file)) {
                        successCount++
                    } else {
                        failCount++
                    }
                }

                selectedSessions.clear()
                loadSessions()
                updateActionButtons()

                if (failCount > 0) {
                    showMessage("Удалено: $successCount, Не удалено: $failCount")
                } else {
                    showMessage("Удалено $successCount сессий")
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateActionButtons() {
        val hasSelection = selectedSessions.isNotEmpty()
        exportButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection
        deleteButton.text = if (hasSelection) "Удалить (${selectedSessions.size})" else "Удалить"

        selectAllButton.text = if (selectedSessions.size == sessionsList.size) {
            "Снять все"
        } else {
            "Выбрать все"
        }
    }

    private fun showExportSuccessDialog(zipFile: File) {
        AlertDialog.Builder(this)
            .setTitle("Экспорт успешен")
            .setMessage("Архив создан: ${zipFile.name}\n\nРазмер: ${formatFileSize(zipFile.length())}")
            .setPositiveButton("Поделиться") { dialog, _ ->
                val shareIntent = fileExporter.shareFile(zipFile, "Экспорт измерений")
                startActivity(shareIntent)
                dialog.dismiss()
            }
            .setNeutralButton("Открыть папку") { dialog, _ ->
                fileExporter.openFileInFileManager(zipFile)
                dialog.dismiss()
            }
            .setNegativeButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showProgress(message: String) {
        val progressOverlay = findViewById<View>(R.id.progressOverlay)
        val progressText = findViewById<TextView>(R.id.progressText)

        progressText?.text = message
        progressOverlay?.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        val progressOverlay = findViewById<View>(R.id.progressOverlay)
        progressOverlay?.visibility = View.GONE
    }

    private fun showMessage(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    data class SessionItem(
        val id: String,
        val name: String,
        val date: String,
        val size: String,
        val file: File
    )

    class SessionAdapter(
        private var items: List<SessionItem>,
        private val onSelectionChange: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

        @SuppressLint("NotifyDataSetChanged")
        fun updateSessions(newItems: List<SessionItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun getCurrentList(): List<SessionItem> = items

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val checkBox: CheckBox = itemView.findViewById(R.id.checkbox)
            private val nameText: TextView = itemView.findViewById(R.id.sessionName)
            private val dateText: TextView = itemView.findViewById(R.id.sessionDate)
            private val sizeText: TextView = itemView.findViewById(R.id.sessionSize)
            private val cardView: View = itemView.findViewById(R.id.cardView)

            init {
                // Обработчик клика по всей карточке
                cardView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = items[position]
                        openSessionDetails(item)
                    }
                }

                // Длинный клик для выбора
                cardView.setOnLongClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        checkBox.isChecked = !checkBox.isChecked
                    }
                    true
                }
            }

            fun bind(item: SessionItem) {
                nameText.text = item.name
                dateText.text = item.date
                sizeText.text = item.size

                // Сбрасываем состояние чекбокса
                checkBox.isChecked = false
                checkBox.setOnCheckedChangeListener(null)

                // Устанавливаем новый обработчик
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChange(item.id, isChecked)
                }
            }

            private fun openSessionDetails(item: SessionItem) {
                val context = itemView.context
                // Открываем SessionManager вместо диалога
                val intent = Intent(context, SessionManager::class.java)
                intent.putExtra("session_id", item.id) // Важно: передаем ID без .csv
                context.startActivity(intent)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session_simple, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }
}