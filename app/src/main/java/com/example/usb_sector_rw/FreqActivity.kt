package com.example.usb_sector_rw

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FreqActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var startFreqMeasure: Button
    private lateinit var testMeasureButton: Button
    private lateinit var btnMeasureExec: Button
    private lateinit var graphButton: Button
    private lateinit var clearLog: Button
    private lateinit var backButton: Button
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var clearOutSwitch: Switch
    private lateinit var logText: TextView
    private lateinit var btnMeasureTracking: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_freq)

        startFreqMeasure = findViewById(R.id.btnEchoTest)
        testMeasureButton = findViewById(R.id.btnMeasureTest)
        btnMeasureExec = findViewById(R.id.btnMeasureExec)
        graphButton = findViewById(R.id.graphButton)
        clearLog = findViewById(R.id.сlearLog)
        logText = findViewById(R.id.logText)
        backButton = findViewById(R.id.backButton)
        clearOutSwitch = findViewById(R.id.clearOutSwitch)
        btnMeasureTracking = findViewById(R.id.btnMeasureTracking)

        LospDevVariables.log = ::log

        startFreqMeasure.setOnClickListener {
            if (LospDevVariables.measureJob == null || LospDevVariables.measureJob?.isActive == false) {
                LospDevVariables.measureJob = scope.launch {
                    LospDevVariables.isRunMeasurment = true
                    startFreqMeasure.text = "Остановить измерение"
                    while (LospDevVariables.isRunMeasurment) {
                        val frequency = withContext(Dispatchers.IO) {
                            LospDevVariables.getFrec()
                        }

                        withContext(Dispatchers.Main) {
                            LospDevVariables.log("Частота = $frequency Гц")
                        }

                        delay(1000)
                    }
                }
            }
            else
            {
                startFreqMeasure.text = "Запуск измерения"
                LospDevVariables.isRunMeasurment = false
            }
        }

        testMeasureButton.setOnClickListener {
            LospDevVariables.getFrecTest()
        }

        btnMeasureExec.setOnClickListener {
            if (LospDevVariables.measureJob == null || LospDevVariables.measureJob?.isActive == false) {
                LospDevVariables.measureJob = scope.launch {
                    LospDevVariables.isStopFrecExecMeasurment = false
                    btnMeasureExec.text = "Остановка измерения"
                    while (!LospDevVariables.isStopFrecExecMeasurment) {
                        LospDevVariables.getFrecExec()
                        delay(1000)
                    }
                }
            }
            else
            {
                btnMeasureExec.text = "Частота + нормирование"
                LospDevVariables.isStopFrecExecMeasurment = true
                LospDevVariables.getFrecExec()
                // FrequencyLogger.getCurrentLogFile()?.let { openLogFile(this, it) } TODO
            }
        }

        graphButton.setOnClickListener {
            val intent = Intent(this, GraphFreqActivity::class.java)
            startActivity(intent)
        }

        clearLog.setOnClickListener {
            logText.text = ""
        }

        backButton.setOnClickListener {
            finish()
        }

        btnMeasureTracking.setOnClickListener {
            if (LospDevVariables.measureJob == null || LospDevVariables.measureJob?.isActive == false) {
                LospDevVariables.measureJob = scope.launch {
                    LospDevVariables.isRunFrecTrackingMeasurment = true
                    btnMeasureTracking.text = "Остановить отслеживание"
                    while (LospDevVariables.isRunFrecTrackingMeasurment) {
                        LospDevVariables.getFrecTracking()
                        delay(1000)
                    }
                }
            }
            else
            {
                btnMeasureTracking.text = "Отслеживание частоты"
                LospDevVariables.isRunFrecTrackingMeasurment = false
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        if(clearOutSwitch.isChecked)
        {
            logText.text = "$message\n"
        }
        else
        {
            logText.append("$message\n")
        }
    }
}