package com.usamashah.heartthreshold

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var thresholdInput: EditText
    private lateinit var statusText: TextView
    private lateinit var latestText: TextView
    private val preferences by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    private var permissionStage = PermissionStage.NONE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            when (permissionStage) {
                PermissionStage.HEART_RATE -> requestBackgroundPermissionOrContinue()
                PermissionStage.BACKGROUND -> requestNotificationPermissionOrStart()
                PermissionStage.NOTIFICATION -> startMonitoringAfterPermissions()
                PermissionStage.NONE -> Unit
            }
        } else {
            val message = when (permissionStage) {
                PermissionStage.HEART_RATE -> "Heart-rate permission is required."
                PermissionStage.BACKGROUND -> "Background heart-rate permission is required for passive monitoring."
                PermissionStage.NOTIFICATION -> "Notification permission is required for heart-rate alerts."
                PermissionStage.NONE -> "Permission not granted"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) updateStatus()
    }

    private fun buildUi() {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWidthWrapContent())

        val explanation = TextView(this).apply {
            text = "Choose an upper BPM limit. Wear OS Health Services will deliver low-power heart-rate readings in the background and this app will alert when a reading reaches the limit."
            textSize = 14f
            setPadding(0, padding / 2, 0, padding / 2)
        }
        root.addView(explanation, matchWidthWrapContent())

        thresholdInput = EditText(this).apply {
            hint = "Upper limit in BPM"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSelectAllOnFocus(true)
            setText(preferences.getInt(PREF_THRESHOLD, DEFAULT_THRESHOLD).toString())
        }
        root.addView(thresholdInput, matchWidthWrapContent())

        val startButton = Button(this).apply {
            text = "Start monitoring"
            setOnClickListener { requestPermissionsThenStart() }
        }
        root.addView(startButton, matchWidthWrapContent())

        val stopButton = Button(this).apply {
            text = "Stop monitoring"
            setOnClickListener { stopMonitoring() }
        }
        root.addView(stopButton, matchWidthWrapContent())

        statusText = TextView(this).apply {
            textSize = 15f
            setPadding(0, padding / 2, 0, 0)
        }
        root.addView(statusText, matchWidthWrapContent())

        latestText = TextView(this).apply {
            textSize = 14f
            setPadding(0, padding / 4, 0, 0)
        }
        root.addView(latestText, matchWidthWrapContent())

        val note = TextView(this).apply {
            text = "Passive readings are batched by Wear OS, so a brief spike may be delayed or missed. This is a wellness tool, not a medical alarm."
            textSize = 12f
            setPadding(0, padding, 0, 0)
        }
        root.addView(note, matchWidthWrapContent())

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun requestPermissionsThenStart() {
        val threshold = thresholdInput.text.toString().toIntOrNull()
        if (threshold == null || threshold !in MIN_THRESHOLD..MAX_THRESHOLD) {
            thresholdInput.error = "Enter a BPM between $MIN_THRESHOLD and $MAX_THRESHOLD"
            return
        }

        preferences.edit()
            .putInt(PREF_THRESHOLD, threshold)
            .putBoolean(PREF_WAS_ABOVE, false)
            .apply()

        permissionStage = PermissionStage.HEART_RATE
        val heartRatePermission = if (Build.VERSION.SDK_INT >= 36) {
            PERMISSION_READ_HEART_RATE
        } else {
            Manifest.permission.BODY_SENSORS
        }
        if (isPermissionGranted(heartRatePermission)) {
            requestBackgroundPermissionOrContinue()
        } else {
            permissionLauncher.launch(arrayOf(heartRatePermission))
        }
    }

    private fun requestBackgroundPermissionOrContinue() {
        permissionStage = PermissionStage.BACKGROUND
        val backgroundPermission = if (Build.VERSION.SDK_INT >= 36) {
            PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        } else if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.BODY_SENSORS_BACKGROUND
        } else {
            null
        }

        if (backgroundPermission == null || isPermissionGranted(backgroundPermission)) {
            requestNotificationPermissionOrStart()
        } else {
            permissionLauncher.launch(arrayOf(backgroundPermission))
        }
    }

    private fun requestNotificationPermissionOrStart() {
        permissionStage = PermissionStage.NOTIFICATION
        if (Build.VERSION.SDK_INT < 33 || isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            startMonitoringAfterPermissions()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun startMonitoringAfterPermissions() {
        val registration = PassiveHeartRateService.register(this)
        registration.addListener({
            runOnUiThread {
                try {
                    registration.get()
                    preferences.edit().putBoolean(PREF_ACTIVE, true).apply()
                    Toast.makeText(this, "Passive monitoring enabled", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Could not enable monitoring", Toast.LENGTH_LONG).show()
                }
                updateStatus()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopMonitoring() {
        val unregistration = PassiveHeartRateService.unregister(this)
        unregistration.addListener({
            runOnUiThread {
                preferences.edit()
                    .putBoolean(PREF_ACTIVE, false)
                    .putBoolean(PREF_WAS_ABOVE, false)
                    .apply()
                Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun isPermissionGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun updateStatus() {
        if (!::statusText.isInitialized) return
        val active = preferences.getBoolean(PREF_ACTIVE, false)
        val threshold = preferences.getInt(PREF_THRESHOLD, DEFAULT_THRESHOLD)
        statusText.text = if (active) {
            "Monitoring: ON (limit $threshold BPM)"
        } else {
            "Monitoring: OFF"
        }

        val latest = preferences.getInt(PREF_LAST_HEART_RATE, 0)
        latestText.text = if (latest > 0) "Latest reading: $latest BPM" else "No reading yet"
    }

    private fun matchWidthWrapContent() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    companion object {
        const val PREFERENCES = "heart_threshold_preferences"
        const val PREF_ACTIVE = "active"
        const val PREF_LAST_HEART_RATE = "last_heart_rate"
        const val PREF_THRESHOLD = "threshold"
        const val PREF_WAS_ABOVE = "was_above"

        const val DEFAULT_THRESHOLD = 120
        const val MIN_THRESHOLD = 30
        const val MAX_THRESHOLD = 240

        private const val PERMISSION_READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        private const val PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND =
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }

    private enum class PermissionStage {
        NONE,
        HEART_RATE,
        BACKGROUND,
        NOTIFICATION
    }
}
