package com.tfg.wearableapp.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID

class BleSmokeTestClient(
    context: Context,
) {
    interface Listener {
        fun onStatusChanged(status: String)
        fun onScanDevice(
            device: BluetoothDevice,
            rssi: Int,
            advertisedName: String?,
            advertisesExpectedService: Boolean,
        )
        fun onConnectionChanged(isConnected: Boolean, deviceName: String?, address: String?)
        fun onMtuNegotiated(mtu: Int)
        fun onNotification(payload: ByteArray)
    }

    companion object {
        const val deviceName = "XIAO-Padel-Test"
        val serviceUuid: UUID = UUID.fromString("19B10010-E8F2-537E-4F6C-D104768A1214")
        val notifyCharacteristicUuid: UUID =
            UUID.fromString("19B10011-E8F2-537E-4F6C-D104768A1214")
        val controlCharacteristicUuid: UUID =
            UUID.fromString("19B10012-E8F2-537E-4F6C-D104768A1214")
        private val cccdUuid: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        private val startCommand = byteArrayOf(0x01)
        private val stopCommand = byteArrayOf(0x00)
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val listeners = linkedSetOf<Listener>()
    private var currentGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var servicesDiscoveryStarted = false
    private var currentConnectionName: String? = null
    private var currentConnectionAddress: String? = null

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isConnected(): Boolean = currentGatt != null

    fun connectedDeviceName(): String? = currentConnectionName

    fun connectedDeviceAddress(): String? = currentConnectionAddress

    @SuppressLint("MissingPermission")
    fun sendStartSessionCommand() {
        writeControlCommand(startCommand, "START_SESSION")
    }

    @SuppressLint("MissingPermission")
    fun sendStopSessionCommand() {
        writeControlCommand(stopCommand, "STOP_SESSION")
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            notifyStatus("Bluetooth LE no disponible en este dispositivo.")
            return
        }

        stopScan()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                emitScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::emitScanResult)
            }

            override fun onScanFailed(errorCode: Int) {
                notifyStatus("Error de scan BLE: $errorCode")
            }

            private fun emitScanResult(result: ScanResult) {
                val advertisesExpectedService = result.scanRecord
                    ?.serviceUuids
                    ?.contains(ParcelUuid(serviceUuid)) == true
                listeners.forEach {
                    it.onScanDevice(
                        device = result.device,
                        rssi = result.rssi,
                        advertisedName = result.scanRecord?.deviceName,
                        advertisesExpectedService = advertisesExpectedService,
                    )
                }
            }
        }

        try {
            scanner.startScan(scanCallback)
            notifyStatus("Escaneando BLE con API simple...")
        } catch (_: SecurityException) {
            notifyStatus("SecurityException al iniciar scan BLE. Revisa permisos de Dispositivos cercanos.")
        } catch (illegalStateException: IllegalStateException) {
            notifyStatus(
                "IllegalStateException al iniciar scan BLE: ${illegalStateException.message ?: "sin detalle"}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val callback = scanCallback ?: return
        try {
            scanner.stopScan(callback)
        } catch (_: SecurityException) {
            notifyStatus("No se pudo detener el scan BLE por permisos.")
        }
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        stopScan()
        servicesDiscoveryStarted = false
        notifyStatus("Conectando con ${device.name ?: device.address}...")
        currentGatt = device.connectGatt(
            appContext,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE,
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        currentGatt?.disconnect()
        currentGatt?.close()
        currentGatt = null
        currentConnectionName = null
        currentConnectionAddress = null
        servicesDiscoveryStarted = false
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToNotifyCharacteristic(
        gatt: BluetoothGatt,
        service: BluetoothGattService,
    ) {
        val characteristic = service.getCharacteristic(notifyCharacteristicUuid)
        if (characteristic == null) {
            notifyStatus("Caracteristica notify no encontrada.")
            return
        }

        val enabled = gatt.setCharacteristicNotification(characteristic, true)
        if (!enabled) {
            notifyStatus("No se pudo activar setCharacteristicNotification.")
            return
        }

        val descriptor = characteristic.getDescriptor(cccdUuid)
        if (descriptor == null) {
            notifyStatus("CCCD no encontrada para la caracteristica notify.")
            return
        }

        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val writeOk = gatt.writeDescriptor(descriptor)
        if (!writeOk) {
            notifyStatus("No se pudo escribir la CCCD.")
        } else {
            notifyStatus("Suscribiendo notificaciones...")
        }
    }

    private fun notifyStatus(status: String) {
        listeners.forEach { it.onStatusChanged(status) }
    }

    @SuppressLint("MissingPermission")
    private fun writeControlCommand(
        payload: ByteArray,
        label: String,
    ) {
        val gatt = currentGatt
        if (gatt == null) {
            notifyStatus("No hay GATT activa para enviar $label.")
            return
        }

        val service = gatt.getService(serviceUuid)
        if (service == null) {
            notifyStatus("Servicio BLE no disponible para enviar $label.")
            return
        }

        val characteristic = service.getCharacteristic(controlCharacteristicUuid)
        if (characteristic == null) {
            notifyStatus("Caracteristica de control no encontrada para $label.")
            return
        }

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = payload
        val ok = gatt.writeCharacteristic(characteristic)
        if (ok) {
            notifyStatus("Comando enviado: $label")
        } else {
            notifyStatus("No se pudo enviar el comando $label")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    currentGatt = gatt
                    currentConnectionName = gatt.device.name
                    currentConnectionAddress = gatt.device.address
                    listeners.forEach {
                        it.onConnectionChanged(
                            isConnected = true,
                            deviceName = gatt.device.name,
                            address = gatt.device.address,
                        )
                    }
                    notifyStatus("Conectado. Solicitando MTU...")

                    val mtuRequested = gatt.requestMtu(185)
                    if (!mtuRequested) {
                        notifyStatus("MTU no solicitado; descubriendo servicios...")
                        gatt.discoverServices()
                        servicesDiscoveryStarted = true
                    }
                }

                BluetoothGatt.STATE_DISCONNECTED -> {
                    currentConnectionName = null
                    currentConnectionAddress = null
                    listeners.forEach {
                        it.onConnectionChanged(
                            isConnected = false,
                            deviceName = gatt.device.name,
                            address = gatt.device.address,
                        )
                    }
                    notifyStatus("Desconectado. Status GATT=$status")
                    gatt.close()
                    if (currentGatt === gatt) {
                        currentGatt = null
                    }
                    servicesDiscoveryStarted = false
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            listeners.forEach { it.onMtuNegotiated(mtu) }
            notifyStatus("MTU negociado: $mtu. Descubriendo servicios...")
            if (!servicesDiscoveryStarted) {
                servicesDiscoveryStarted = true
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(serviceUuid)
            if (service == null) {
                notifyStatus("Servicio BLE de smoke test no encontrado.")
                return
            }
            subscribeToNotifyCharacteristic(gatt, service)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.characteristic.uuid == notifyCharacteristicUuid) {
                notifyStatus("Notificaciones activadas. Esperando bytes...")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == notifyCharacteristicUuid) {
                listeners.forEach { it.onNotification(value) }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == notifyCharacteristicUuid) {
                val payload = characteristic.value ?: byteArrayOf()
                listeners.forEach { it.onNotification(payload) }
            }
        }
    }
}
