package com.example.usb_sector_rw

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class UvActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var testUvButton: Button
    private lateinit var execUvButton: Button
    private lateinit var setRangeButton: Button
    private lateinit var clearLogButton: Button
    private lateinit var backButton: Button
    private lateinit var rangeInput: EditText
    private lateinit var logText: TextView
    private lateinit var graphButton: Button
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var clearOutSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uv)

        testUvButton = findViewById(R.id.btnUvTest)
        execUvButton = findViewById(R.id.btnUvExec)
        setRangeButton = findViewById(R.id.btnSetUvRange)
        clearLogButton = findViewById(R.id.btnClearLog)
        backButton = findViewById(R.id.backButton)
        rangeInput = findViewById(R.id.editUvRange)
        logText = findViewById(R.id.logText)
        clearOutSwitch = findViewById(R.id.clearOutSwitch)
        graphButton = findViewById(R.id.graphButton)

        LospDevVariables.log = ::log

        testUvButton.setOnClickListener {
            LospDevVariables.getUvTest()
        }

        execUvButton.setOnClickListener {
            if (LospDevVariables.measureJob == null || LospDevVariables.measureJob?.isActive == false) {
                execUvButton.text = "Остановить измерение"
                LospDevVariables.measureJob = scope.launch {
                    LospDevVariables.isRunUvMeasurement = true
                    while (LospDevVariables.isRunUvMeasurement) {
                        LospDevVariables.getUvExec(false, false)
                        delay(1000)
                    }
                }
            } else {
                execUvButton.text = "Запустить измерение"
                LospDevVariables.getUvExec(true, false)
                LospDevVariables.isRunUvMeasurement = false
            }
        }

        graphButton.setOnClickListener {
            val intent = Intent(this, GraphUvActivity::class.java)
            startActivity(intent)
        }

        setRangeButton.setOnClickListener {
            val range = rangeInput.text.toString().toIntOrNull()
            if (range != null) {
                LospDevVariables.setUvRange(range.toUInt())
            } else {
                log("Неверный ввод диапазона")
            }
        }

        clearLogButton.setOnClickListener {
            logText.text = ""
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        if (clearOutSwitch.isChecked) {
            logText.text = "$message\n"
        } else {
            logText.append("$message\n")
        }
    }
}