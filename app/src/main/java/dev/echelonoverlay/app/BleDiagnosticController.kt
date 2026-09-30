package dev.echelonoverlay.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val APP_LOG_FILENAME = "echelon_ble_diagnostic.log"

private val ECHELON_SERVICE_UUIDS: Set<UUID> = setOf(
    UUID.fromString("0bf669f1-45f2-11e7-9598-0800200c9a66"),
    UUID.fromString("0bf669f0-45f2-11e7-9598-0800200c9a66"),
)

private val CANDIDATE_NOTIFY_UUIDS: Set<UUID> = setOf(
    UUID.fromString("0bf669f3-45f2-11e7-9598-0800200c9a66"),
    UUID.fromString("0bf669f4-45f2-11e7-9598-0800200c9a66"),
)

private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isLikelyEchelon: Boolean,
)

class BleDiagnosticController(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatusChanged(status: String)
        fun onDiscoveredDevice(device: DiscoveredBleDevice)
        fun onSelectedDevice(device: DiscoveredBleDevice)
        fun onServiceUuid(uuid: String)
        fun onNotifyCharacteristic(uuid: String)
        fun onPacket(entry: DiagnosticPacketEntry)
        fun onLog(message: String)
        fun onError(message: String)
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter

    private val packetLog = DiagnosticPacketLog()
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var selectedDevice: DiscoveredBleDevice? = null
    private var isScanning = false
    private var activeService: BluetoothGattService? = null
    private var activeNotifyCharacteristic: BluetoothGattCharacteristic? = null
    private val scanHandler = Handler(Looper.getMainLooper())

    fun startScan() {
        val adapter = bluetoothAdapter ?: run {
            listener.onError("Bluetooth adapter unavailable.")
            return
        }

        if (!adapter.isEnabled) {
            listener.onError("Bluetooth is disabled on this device.")
            listener.onStatusChanged("Error")
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            listener.onError("Bluetooth LE scanning is not available on this device.")
            listener.onStatusChanged("Error")
            return
        }

        isScanning = true
        listener.onStatusChanged("Scanning")
        listener.onLog("Starting BLE scan for likely Echelon devices.")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false)
            .build()

        scanner?.startScan(null, settings, scanCallback)
        scanHandler.postDelayed({
            if (isScanning) {
                stopScan()
                listener.onLog("Scan timed out after 15s; no automatic retry was scheduled.")
                listener.onStatusChanged("Error")
            }
        }, 15000L)
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        scanner?.stopScan(scanCallback)
        listener.onStatusChanged(if (gatt != null) "Connected" else "Idle")
    }

    fun connectToSelectedDevice(address: String) {
        val adapter = bluetoothAdapter ?: run {
            listener.onError("Bluetooth adapter unavailable.")
            return
        }

        if (gatt != null) {
            listener.onError("A GATT connection is already active; disconnect before connecting again.")
            return
        }

        val device = adapter.getRemoteDevice(address)
        if (device == null) {
            listener.onError("The selected device could not be resolved.")
            listener.onStatusChanged("Error")
            return
        }

        selectedDevice = DiscoveredBleDevice(
            name = device.name ?: "Unknown BLE device",
            address = device.address,
            rssi = -128,
            isLikelyEchelon = isLikelyEchelon(device.name),
        )
        listener.onSelectedDevice(selectedDevice!!)
        listener.onStatusChanged("Connecting")
        listener.onLog("Connecting to ${device.name ?: "Unknown BLE device"} (${device.address}).")
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        activeService = null
        activeNotifyCharacteristic = null
        listener.onStatusChanged("Idle")
        listener.onLog("Disconnected cleanly from the selected GATT session.")
    }

    fun clearLog() {
        packetLog.clear()
        listener.onLog("Diagnostic packet log cleared by the user.")
    }

    fun packetEntries(): List<DiagnosticPacketEntry> = packetLog.entries

    fun writeLocalLog(message: String) {
        try {
            val formatted = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} $message\n"
            val file = File(context.filesDir, APP_LOG_FILENAME)
            file.appendText(formatted)
        } catch (error: Exception) {
            listener.onError("Failed to persist diagnostic log: ${error.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: "Unknown device"
            val discovered = DiscoveredBleDevice(
                name = name,
                address = device.address,
                rssi = result.rssi,
                isLikelyEchelon = isLikelyEchelon(name),
            )
            if (selectedDevice == null && discovered.isLikelyEchelon) {
                selectedDevice = discovered
                listener.onSelectedDevice(discovered)
            }
            listener.onDiscoveredDevice(discovered)
            listener.onLog(
                "Discovered ${discovered.name} (${discovered.address}) RSSI ${discovered.rssi} " +
                    "${if (discovered.isLikelyEchelon) "likely Echelon" else "unclassified"}",
            )
            if (discovered.isLikelyEchelon) {
                listener.onStatusChanged("Scanning")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            listener.onError("BLE scan failed with code $errorCode.")
            listener.onStatusChanged("Error")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    listener.onLog("Connection state: connected to ${gatt.device.address}.")
                    listener.onStatusChanged("Connected")
                    gatt.discoverServices()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    listener.onLog("Connection state: disconnected from ${gatt.device.address}.")
                    listener.onStatusChanged("Idle")
                    gatt.close()
                }
                else -> {
                    listener.onError("Unexpected BLE connection state: $newState")
                    listener.onStatusChanged("Error")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("GATT service discovery failed with status $status.")
                listener.onStatusChanged("Error")
                return
            }

            val serviceList = gatt.services.joinToString { it.uuid.toString() }
            listener.onLog("GATT service discovery complete. Services: $serviceList")

            gatt.services.forEach { service ->
                if (service.uuid in ECHELON_SERVICE_UUIDS || service.uuid.toString().startsWith("0bf669f")) {
                    activeService = service
                    listener.onServiceUuid(service.uuid.toString())
                }
                service.characteristics.forEach { characteristic ->
                    val properties = characteristic.properties
                    val isNotify = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    if (isNotify) {
                        listener.onLog("Characteristic discovered: ${characteristic.uuid} (notify, properties=$properties)")
                    }
                }
            }

            val notifyCharacteristic = findNotifyCharacteristic(gatt)
            if (notifyCharacteristic == null) {
                listener.onError("No notification characteristic was discovered; no telemetry subscription was attempted.")
                listener.onStatusChanged("Error")
                return
            }

            activeNotifyCharacteristic = notifyCharacteristic
            listener.onNotifyCharacteristic(notifyCharacteristic.uuid.toString())
            listener.onLog("Attempting to subscribe to notification characteristic ${notifyCharacteristic.uuid}.")
            gatt.setCharacteristicNotification(notifyCharacteristic, true)
            val descriptor = notifyCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor == null) {
                listener.onError("Client Characteristic Configuration descriptor missing for ${notifyCharacteristic.uuid}.")
                listener.onStatusChanged("Error")
                return
            }
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val descriptorWriteResult = gatt.writeDescriptor(descriptor)
            if (!descriptorWriteResult) {
                listener.onError("Failed to write the CCCD for ${notifyCharacteristic.uuid}; notification subscription did not start.")
                listener.onStatusChanged("Error")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            val charUuid = descriptor.characteristic?.uuid?.toString() ?: "unknown"
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onLog("Notification subscription success for characteristic $charUuid.")
                listener.onStatusChanged("Connected")
            } else {
                listener.onError("Notification subscription failed for $charUuid (status $status).")
                listener.onStatusChanged("Error")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val packetHex = value.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            val entry = packetLog.addPacket(packetHex)
            listener.onPacket(entry)
            val message = "${entry.formattedTimestamp()}  $packetHex"
            listener.onLog(message)
            writeLocalLog(message)
            if (packetLog.count > 150) {
                listener.onLog("Packet buffer exceeds the UI retention limit; older entries were dropped.")
            }
        }
    }

    private fun findNotifyCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                val isCandidate = characteristic.uuid in CANDIDATE_NOTIFY_UUIDS ||
                    (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 &&
                        service.uuid in ECHELON_SERVICE_UUIDS)
                if (isCandidate) {
                    return characteristic
                }
            }
        }
        return null
    }

    private fun isLikelyEchelon(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val normalized = name.trim()
        return normalized.startsWith("ECH", ignoreCase = true) ||
            normalized.contains("Echelon", ignoreCase = true) ||
            normalized.contains("EX-5", ignoreCase = true)
    }
}
