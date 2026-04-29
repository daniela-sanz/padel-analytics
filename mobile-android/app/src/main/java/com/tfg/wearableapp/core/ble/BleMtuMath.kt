package com.tfg.wearableapp.core.ble

object BleMtuMath {
    private const val attNotificationOverheadBytes = 3

    fun maxNotificationValueBytes(attMtu: Int): Int {
        return (attMtu - attNotificationOverheadBytes).coerceAtLeast(0)
    }

    fun maxChunkPayloadBytes(
        attMtu: Int,
        chunkHeaderSizeBytes: Int = BleTransportConfig.chunkHeaderSizeBytes,
    ): Int {
        return (maxNotificationValueBytes(attMtu) - chunkHeaderSizeBytes).coerceAtLeast(0)
    }
}
