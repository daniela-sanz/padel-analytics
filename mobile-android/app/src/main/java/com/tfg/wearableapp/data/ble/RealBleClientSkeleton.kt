package com.tfg.wearableapp.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import com.tfg.wearableapp.core.ble.BleMtuMath
import com.tfg.wearableapp.core.ble.BleTransportConfig
import com.tfg.wearableapp.core.ble.model.ImuLogicalBlock
import com.tfg.wearableapp.core.ble.pipeline.BlePipelineEvent
import com.tfg.wearableapp.core.ble.pipeline.ChunkToBlockPipeline

class RealBleClientSkeleton(
    private val pipeline: ChunkToBlockPipeline = ChunkToBlockPipeline(),
) {
    var negotiatedAttMtu: Int = 23
        private set

    val maxChunkPayloadBytes: Int
        get() = BleMtuMath.maxChunkPayloadBytes(
            attMtu = negotiatedAttMtu,
            chunkHeaderSizeBytes = BleTransportConfig.chunkHeaderSizeBytes,
        )

    fun createGattCallback(
        onBlockReady: (ImuLogicalBlock) -> Unit,
    ): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                negotiatedAttMtu = mtu
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                val block = pipeline.processNotification(
                    rawChunk = value,
                    nowMs = System.currentTimeMillis(),
                )
                val completedBlock = (block as? BlePipelineEvent.BlockCompleted)?.block
                if (completedBlock != null) {
                    onBlockReady(completedBlock)
                }
            }
        }
    }
}
