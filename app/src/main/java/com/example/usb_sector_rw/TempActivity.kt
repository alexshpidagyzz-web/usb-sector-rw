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

class TempActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var testTempButton: Button
    private lateinit var execTempButton: Button
    private lateinit var clearLogButton: Button
    private lateinit var backButton: Button
    private lateinit var graphButton: Button
    private lateinit var logText: TextView
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var clearOutSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_temp)

        testTempButton = findViewById(R.id.btnTempTest)
        execTempButton = findViewById(R.id.btnTempExec)
        clearLogButton = findViewById(R.id.btnClearLog)
        backButton = findViewById(R.id.backButton)
        logText = findViewById(R.id.logText)
        clearOutSwitch = findViewById(R.id.clearOutSwitch)
        graphButton = findViewById(R.id.graphButton)

        LospDevVariables.log = ::log

        testTempButton.setOnClickListener {
            LospDevVariables.getTempTest()
        }

        execTempButton.setOnClickListener {
            if (LospDevVariables.measureJob == null || LospDevVariables.measureJob?.isActive == false) {
                execTempButton.text = "Остановить измерение"
                LospDevVariables.measureJob = scope.launch {
                    LospDevVariables.isRunTempMeasurement = true
                    while (LospDevVariables.isRunTempMeasurement) {
                        LospDevVariables.getTempExec(false, false)
                        delay(1000)
                    }
                }
            } else {
                execTempButton.text = "Запустить измерение"
                LospDevVariables.getTempExec(true, false)
                LospDevVariables.isRunTempMeasurement = false
            }
        }

        graphButton.setOnClickListener {
            val intent = Intent(this, GraphTempActivity::class.java)
            startActivity(intent)
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