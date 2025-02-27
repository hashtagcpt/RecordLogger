package com.example.recordlogger

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val logRecords = mutableListOf<LogRecord>()
    private lateinit var adapter: ArrayAdapter<String>
    private var pressStartTime: Long = 0
    private var initialX: Float = 0f
    private val swipeThreshold = 100 // threshold in pixels for a swipe

    // Launcher for creating document (export)
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            writeDataToUri(uri)
        } else {
            Toast.makeText(this, "Export cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recordData(pressDuration: Long, swipeOption: Int) {
        val timestamp = System.currentTimeMillis()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                getString(R.string.location_permission_not_granted),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val logRecord = LogRecord(
                timestamp = timestamp,
                latitude = location?.latitude,
                longitude = location?.longitude,
                pressDuration = pressDuration,
                swipeOption = swipeOption
            )
            logRecords.add(logRecord)
            adapter.add("Time: ${Date(timestamp)} | Loc: ${logRecord.latitude ?: "N/A"}, ${logRecord.longitude ?: "N/A"} | Duration: ${pressDuration}ms | Swipe: $swipeOption")
            adapter.notifyDataSetChanged()
            Toast.makeText(this, getString(R.string.record_saved), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupRecordButton()
        setupRecordsListView()
        setupExportButton()
        checkLocationPermissions()
    }

    private fun setupRecordButton() {
        val recordButton = findViewById<Button>(R.id.recordButton)
        recordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pressStartTime = System.currentTimeMillis()
                    initialX = event.x
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val pressDuration = System.currentTimeMillis() - pressStartTime
                    val deltaX = event.x - initialX
                    val swipeOption = when {
                        deltaX > swipeThreshold -> 1
                        deltaX < -swipeThreshold -> 0
                        else -> 0
                    }
                    recordData(pressDuration, swipeOption)
                    true
                }

                else -> false
            }
        }
    }

    private fun setupRecordsListView() {
        val recordsListView = findViewById<ListView>(R.id.recordsListView)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        recordsListView.adapter = adapter
    }

    private fun setupExportButton() {
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            exportData()
        }
    }

    private fun checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
            )
        }
    }

    private var fileUri: Uri? = null

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            fileUri?.let { uri -> writeDataToUri(uri) }
        } else {
            Toast.makeText(this, "Write permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeDataToUri(uri: Uri) {
        val jsonString = Gson().toJson(logRecords)
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(jsonString.toByteArray())
            }
            Toast.makeText(this, "Data exported successfully.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error writing data", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun exportData() {
        exportLauncher.launch("records_${System.currentTimeMillis()}.json")
    }
}

data class LogRecord(
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val pressDuration: Long,
    val swipeOption: Int
)