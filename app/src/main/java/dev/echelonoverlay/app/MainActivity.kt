package dev.echelonoverlay.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity(), BleDiagnosticController.Listener {
    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var gattText: TextView
    private lateinit var discoveredText: TextView
    private lateinit var packetsText: TextView
    private lateinit var logText: TextView
    private lateinit var diagnosticController: BleDiagnosticController

    private var currentStatus: String = "Idle"
    private var selectedDevice: DiscoveredBleDevice? = null
    private var serviceUuid: String? = null
    private var notifyCharacteristicUuid: String? = null
    private val discoveredDevices = linkedMapOf<String, DiscoveredBleDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnosticController = BleDiagnosticController(applicationContext, this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val heading = TextView(this).apply {
            text = getString(R.string.ble_diagnostic_title)
            textSize = 26f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        statusText = TextView(this).apply {
            text = "Status: Idle"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        deviceText = TextView(this).apply {
            text = "Device:\nUnknown\n\nRSSI: n/a"
            textSize = 16f
            setPadding(0, 16, 0, 0)
        }

        gattText = TextView(this).apply {
            text = "GATT:\nService: not discovered\nNotify characteristic: none"
            textSize = 16f
            setPadding(0, 16, 0, 0)
        }

        discoveredText = TextView(this).apply {
            text = "Discovered devices:\nNone"
            textSize = 15f
            setPadding(0, 16, 0, 0)
        }

        packetsText = TextView(this).apply {
            text = "Notifications:\nNo packets captured."
            textSize = 15f
            setPadding(0, 16, 0, 0)
        }

        logText = TextView(this).apply {
            text = "Connection log:\nRead-only diagnostic mode active. No vendor writes are sent."
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        val scanButton = Button(this).apply {
            text = "START SCAN"
            setOnClickListener {
                ensurePermissionsAndStartScan()
            }
        }

        val connectButton = Button(this).apply {
            text = "CONNECT"
            setOnClickListener {
                val address = selectedDevice?.address
                if (address == null) {
                    onError("Select a likely Echelon device before attempting a connection.")
                    return@setOnClickListener
                }
                diagnosticController.connectToSelectedDevice(address)
            }
        }

        val disconnectButton = Button(this).apply {
            text = "DISCONNECT"
            setOnClickListener {
                diagnosticController.disconnect()
            }
        }

        val clearLogButton = Button(this).apply {
            text = "CLEAR LOG"
            setOnClickListener {
                diagnosticController.clearLog()
                logText.text = "Connection log:\nLog cleared."
                packetsText.text = "Notifications:\nNo packets captured."
            }
        }

        buttonRow.addView(scanButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttonRow.addView(connectButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttonRow.addView(disconnectButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttonRow.addView(clearLogButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val scroll = ScrollView(this).apply {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(heading)
                addView(statusText)
                addView(deviceText)
                addView(gattText)
                addView(discoveredText)
                addView(packetsText)
                addView(logText)
                addView(buttonRow)
            })
        }

        root.addView(scroll)
        setContentView(root)
        renderScreen()
    }

    override fun onStatusChanged(status: String) {
        currentStatus = status
        renderScreen()
    }

    override fun onDiscoveredDevice(device: DiscoveredBleDevice) {
        discoveredDevices[device.address] = device
        if (selectedDevice == null && device.isLikelyEchelon) {
            selectedDevice = device
        }
        renderScreen()
    }

    override fun onSelectedDevice(device: DiscoveredBleDevice) {
        selectedDevice = device
        renderScreen()
    }

    override fun onServiceUuid(uuid: String) {
        serviceUuid = uuid
        renderScreen()
    }

    override fun onNotifyCharacteristic(uuid: String) {
        notifyCharacteristicUuid = uuid
        renderScreen()
    }

    override fun onPacket(entry: DiagnosticPacketEntry) {
        val packetLines = diagnosticController.packetEntries().takeLast(150).joinToString("\n") { "${it.formattedTimestamp()}  ${it.packetHex}" }
        packetsText.text = if (packetLines.isEmpty()) {
            "Notifications:\nNo packets captured."
        } else {
            "Notifications:\n$packetLines\nPacket count: ${diagnosticController.packetEntries().size}"
        }
        logText.text = "Connection log:\n${diagnosticController.packetEntries().joinToString("\n") { "${it.formattedTimestamp()}  ${it.packetHex}" }}"
    }

    override fun onLog(message: String) {
        val text = logText.text.toString().removePrefix("Connection log:\n")
        val next = if (text == "Read-only diagnostic mode active. No vendor writes are sent." || text == "Log cleared.") {
            message
        } else {
            "$text\n$message"
        }
        logText.text = "Connection log:\n$next"
        diagnosticController.writeLocalLog(message)
    }

    override fun onError(message: String) {
        currentStatus = "Error"
        logText.text = "Connection log:\n$message"
        renderScreen()
    }

    private fun renderScreen() {
        statusText.text = "Status: $currentStatus"

        val deviceName = selectedDevice?.name ?: "Unknown"
        val deviceAddress = selectedDevice?.address ?: "device unavailable"
        val rssi = selectedDevice?.rssi ?: -127
        deviceText.text = "Device:\n$deviceName\n$deviceAddress\nRSSI: $rssi"

        gattText.text = "GATT:\nService: ${serviceUuid ?: "not discovered"}\nNotify characteristic: ${notifyCharacteristicUuid ?: "none"}"

        val discoveredSummary = if (discoveredDevices.isEmpty()) {
            "None"
        } else {
            discoveredDevices.values.joinToString("\n") { device ->
                val label = if (device.isLikelyEchelon) " [likely Echelon]" else ""
                "${device.name} ${device.address} RSSI ${device.rssi}$label"
            }
        }
        discoveredText.text = "Discovered devices:\n$discoveredSummary"

        val packetEntries = diagnosticController.packetEntries()
        val packetTextValue = if (packetEntries.isEmpty()) {
            "Notifications:\nNo packets captured."
        } else {
            val lines = packetEntries.takeLast(150).joinToString("\n") { "${it.formattedTimestamp()}  ${it.packetHex}" }
            "Notifications:\n$lines\nPacket count: ${packetEntries.size}"
        }
        packetsText.text = packetTextValue
    }

    private fun ensurePermissionsAndStartScan() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required += Manifest.permission.BLUETOOTH_SCAN
            required += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            required += Manifest.permission.ACCESS_FINE_LOCATION
        }

        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 3001)
            return
        }

        diagnosticController.startScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 3001) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                diagnosticController.startScan()
            } else {
                onError("Bluetooth permissions were not granted; scanning and connection are blocked.")
                currentStatus = "Error"
                renderScreen()
            }
        }
    }
}

