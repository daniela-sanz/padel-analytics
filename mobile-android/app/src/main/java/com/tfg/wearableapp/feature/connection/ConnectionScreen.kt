package com.tfg.wearableapp.feature.connection

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tfg.wearableapp.app.TechPanelCard
import com.tfg.wearableapp.app.TechScreenBackground
import com.tfg.wearableapp.app.TechStyle

@Composable
fun ConnectionScreen(
    paddingValues: PaddingValues,
    uiState: ConnectionUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onPermissionsDenied: () -> Unit,
    onPermissionsSnapshot: (Boolean) -> Unit,
    onConnectToDevice: (String) -> Unit,
    onDisconnect: () -> Unit,
    onGoToSession: () -> Unit,
) {
    val context = LocalContext.current
    val requiredBlePermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }
    val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    val bluetoothEnabled = remember(context) {
        val manager = context.getSystemService(BluetoothManager::class.java)
        manager?.adapter?.isEnabled == true
    }
    val blePermissionGranted = requiredBlePermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    val locationPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        locationPermission,
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(locationPermissionGranted) {
        onPermissionsSnapshot(locationPermissionGranted)
    }
    val permissionsToRequest = remember(requiredBlePermissions) {
        buildList {
            addAll(requiredBlePermissions)
            add(locationPermission)
        }
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val locationGranted = results[locationPermission] == true || locationPermissionGranted
        onPermissionsSnapshot(locationGranted)
        val bleGranted = requiredBlePermissions.all { permission -> results[permission] == true || blePermissionGranted }
        val allGranted = bleGranted && locationGranted
        if (allGranted) {
            onStartScan()
        } else {
            onPermissionsDenied()
        }
    }

    fun ensureBlePermissionsThenStartScan() {
        val missing = permissionsToRequest.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onStartScan()
        } else {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    TechScreenBackground(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Conexion",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TechStyle.title,
                )
            }

            item {
                Text(
                    text = "Diagnostico BLE real: descubrir la XIAO, conectar, observar MTU real, medir chunks, telemetria y acercarnos a la captura continua.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TechStyle.body,
                )
            }

            item {
                TechPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Estado BLE real",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TechStyle.title,
                        )
                        KeyValueLine("Bluetooth disponible", if (uiState.bluetoothAvailable) "Si" else "No")
                        KeyValueLine("Bluetooth activo", if (bluetoothEnabled) "Si" else "No")
                        KeyValueLine("Permisos BLE", if (blePermissionGranted || requiredBlePermissions.isEmpty()) "Si" else "No")
                        KeyValueLine("Ubicacion activa", if (uiState.locationEnabled) "Si" else "No")
                        KeyValueLine("Permiso ubicacion", if (locationPermissionGranted) "Si" else "No")
                        KeyValueLine("Conectado", if (uiState.isConnected) "Si" else "No")
                        KeyValueLine("Dispositivo", uiState.connectedDeviceName ?: "-")
                        KeyValueLine("Direccion", uiState.connectedDeviceAddress ?: "-")
                        KeyValueLine("MTU negociado", uiState.negotiatedMtu?.toString() ?: "-")
                        KeyValueLine("Callbacks scan", uiState.scanCallbacksSeen.toString())
                        KeyValueLine("Notificaciones", uiState.notificationsReceived.toString())
                        KeyValueLine("Modo transporte", uiState.transportMode)
                        KeyValueLine("Ultimo contador", uiState.lastCounter?.toString() ?: "-")
                        KeyValueLine("Ultima bateria", uiState.lastBattery?.let { "$it%" } ?: "-")
                        KeyValueLine("Ultimos pasos", uiState.lastStepCountTotal?.toString() ?: "-")
                        KeyValueLine(
                            "Ultimo status",
                            uiState.lastStatusFlags?.let { "0x" + it.toString(16).uppercase() } ?: "-",
                        )
                        KeyValueLine("Ultimo flag", uiState.lastFlagHex ?: "-")
                        if (uiState.transportMode.startsWith("Chunk")) {
                            KeyValueLine("Chunks recibidos", uiState.chunkNotificationsReceived.toString())
                            KeyValueLine("Ultimo chunk packet_id", uiState.lastChunkPacketId?.toString() ?: "-")
                            KeyValueLine(
                                "Ultimo chunk idx",
                                if (uiState.lastChunkIndex != null && uiState.lastChunkCount != null) {
                                    "${uiState.lastChunkIndex}/${uiState.lastChunkCount - 1}"
                                } else if (uiState.lastChunkIndex != null) {
                                    uiState.lastChunkIndex.toString()
                                } else {
                                    "-"
                                }
                            )
                            KeyValueLine("Bloques reensamblados", uiState.blocksReassembled.toString())
                            KeyValueLine("Ultimo bloque packet_id", uiState.lastBlockPacketId?.toString() ?: "-")
                            KeyValueLine("Ultimo bloque muestras", uiState.lastBlockSampleCount?.toString() ?: "-")
                            KeyValueLine("Bateria ultimo bloque", uiState.lastBlockBattery?.let { "$it%" } ?: "-")
                            KeyValueLine("Huecos packet_id", uiState.packetGapCount.toString())
                            KeyValueLine("Huecos sample_index", uiState.sampleGapCount.toString())
                            KeyValueLine(
                                "Frecuencia efectiva",
                                uiState.effectiveSampleRateHz?.let { String.format("%.1f Hz", it) } ?: "-",
                            )
                        }
                        Text(
                            text = "Payload: ${uiState.lastPayloadHex}",
                            color = TechStyle.faint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = uiState.statusText,
                            color = TechStyle.accentSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                TechPanelCard(
                    modifier = Modifier.fillMaxWidth(),
                    alt = true,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Acciones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TechStyle.title,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = ::ensureBlePermissionsThenStartScan,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TechStyle.accent,
                                    contentColor = TechStyle.bgTop,
                                ),
                            ) {
                                Text(if (uiState.isScanning) "Reiniciar scan" else "Escanear", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(onClick = onStopScan) {
                                Text("Detener scan")
                            }

                            OutlinedButton(onClick = onDisconnect) {
                                Text("Desconectar")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Dispositivos encontrados (${uiState.discoveredDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TechStyle.title,
                )
            }

            if (uiState.discoveredDevices.isEmpty()) {
                item {
                    TechPanelCard(modifier = Modifier.fillMaxWidth(), alt = true) {
                        Text(
                            text = "Todavia no hay dispositivos BLE visibles para la prueba. Enciende la XIAO y lanza el scan.",
                            modifier = Modifier.padding(16.dp),
                            color = TechStyle.body,
                        )
                    }
                }
            } else {
                items(
                    items = uiState.discoveredDevices,
                    key = { it.address },
                ) { device ->
                    TechPanelCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = device.name,
                                color = TechStyle.title,
                                fontWeight = FontWeight.Bold,
                            )
                            if (device.looksLikeSmokeTest) {
                                Text(
                                    text = "Servicio smoke test detectado",
                                    color = TechStyle.accentSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text("MAC: ${device.address}", color = TechStyle.body)
                            Text("RSSI: ${device.rssi} dBm", color = TechStyle.faint)
                            Text(
                                "Advertised name: ${device.advertisedName ?: "-"}",
                                color = TechStyle.faint,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "System name: ${device.systemName ?: "-"}",
                                color = TechStyle.faint,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = { onConnectToDevice(device.address) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TechStyle.accentSecondary,
                                    contentColor = TechStyle.bgTop,
                                ),
                            ) {
                                Text("Conectar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onGoToSession,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TechStyle.accentSecondary,
                        contentColor = TechStyle.bgTop,
                    ),
                ) {
                    Text("Ir a sesion simulada", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun KeyValueLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = TechStyle.label)
        Text(text = value, color = TechStyle.title, fontWeight = FontWeight.SemiBold)
    }
}
