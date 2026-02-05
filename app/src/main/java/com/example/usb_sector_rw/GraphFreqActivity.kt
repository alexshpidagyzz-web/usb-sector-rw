package com.example.usb_sector_rw

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class GraphFreqActivity : AppCompatActivity() {
    private lateinit var graphView: CustomGraphView
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        graphView = findViewById(R.id.graphView)

        // Запускаем обновление графика с контролем жизненного цикла
        startGraphUpdates()

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun startGraphUpdates() {
        updateJob = scope.launch {
            while (isActive) {
                try {
                    val value = LospDevVariables.getFrec()
                    graphView.isVertical = true
                    graphView.addPoint(value)

                    delay(500) // Задержка между обновлениями
                } catch (e: CancellationException) {
                    // Корректное завершение при отмене
                    break
                } catch (e: Exception) {
                    // Логируем ошибку, но продолжаем работу
                    e.printStackTrace()
                    delay(1000)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Приостанавливаем обновления на паузе
        updateJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        // Возобновляем обновления
        if (updateJob?.isActive != true) {
            startGraphUpdates()
        }
    }

    override fun onDestroy() {
        // Полная очистка ресурсов
        updateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}