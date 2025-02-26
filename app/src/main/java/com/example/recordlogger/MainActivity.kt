package com.example.recordlogger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import android.net.Uri
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import java.util.*



class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val records = mutableListOf<Record>()
    private lateinit var adapter: ArrayAdapter<String>

    // Launcher for creating document (export)
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { output ->
                val jsonString = Gson().toJson(records)
                output.write(jsonString.toByteArray())
                Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Setup ListView adapter to show records (as strings)
        val recordsListView = findViewById<ListView>(R.id.recordsListView)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        recordsListView.adapter = adapter

        // Handle Record button click
        findViewById<Button>(R.id.recordButton).setOnClickListener {
            recordData()
        }

        // Handle Export button click
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            exportData()
        }

        // Check location permissions (simplified version)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
            )
        }
    }

    private fun recordData() {
        // Get current timestamp
        val timestamp = System.currentTimeMillis()
        // Request last known location (requires permission)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.location_permission_not_granted), Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val record = Record(
                timestamp = timestamp,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
            records.add(record)
            // Update ListView adapter with a formatted string showing the record details
            adapter.add("Time: ${Date(timestamp)} | Loc: ${record.latitude ?: "N/A"}, ${record.longitude ?: "N/A"}")
            adapter.notifyDataSetChanged()
            Toast.makeText(this, getString(R.string.record_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private var fileUri: Uri? = null

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // User approved, now write the data
            fileUri?.let { uri -> writeDataToUri(uri)}
        } else {
            Toast.makeText(this, "Write permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeDataToUri(uri: Uri) {
        // Convert your records to JSON (ensure you have the Gson dependency)
        val jsonString = Gson().toJson(records)
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
        // Step 1: Insert a new file entry into MediaStore
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "records_${System.currentTimeMillis()}.json")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }

        fileUri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
        if (fileUri == null) {
            Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show()
            return
        }

        // Step 2: Request write access using MediaStore.createWriteRequest (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val intent = MediaStore.createWriteRequest(contentResolver, listOf(fileUri!!))
            try {
                // Launch the intent to get user permission
                writePermissionLauncher.launch(
                    IntentSenderRequest.Builder(intent).build()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Error launching write request", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            // For earlier API levels, you can directly write to fileUri using an output stream
            writeDataToUri(fileUri!!)
        }
    }

}
