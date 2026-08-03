package com.blez.dualnav.core.domain.util

import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.model.NavigationCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class MessageEnvelope(
    val messageType: String,
    val payload: String,
    val senderId: String,
    val receiverId: String,
    val messageId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Wraps/unwraps the string each transport's `sendMessage(String)`/`receiveMessage(): Flow<String>`
 * actually carries. Kept separate from the per-transport data sources since every transport
 * (Bluetooth/WiFi/Firebase) shares the same envelope format.
 */
object MessageProtocol {
    private const val TYPE_STATUS = "STATUS"

    fun wrapCommand(command: NavigationCommand, senderId: String, receiverId: String, json: Json): String {
        val envelope = MessageEnvelope(
            messageType = command.messageType(),
            payload = json.encodeToString(NavigationCommand.serializer(), command),
            senderId = senderId,
            receiverId = receiverId
        )
        return json.encodeToString(MessageEnvelope.serializer(), envelope)
    }

    fun wrapStatus(deviceInfo: DeviceInfo, senderId: String, receiverId: String, json: Json): String {
        val envelope = MessageEnvelope(
            messageType = TYPE_STATUS,
            payload = json.encodeToString(DeviceInfo.serializer(), deviceInfo),
            senderId = senderId,
            receiverId = receiverId
        )
        return json.encodeToString(MessageEnvelope.serializer(), envelope)
    }

    /** Returns null for anything that isn't a command envelope (e.g. a STATUS update), or malformed input. */
    fun unwrapCommand(raw: String, json: Json): NavigationCommand? {
        val envelope = runCatching { json.decodeFromString(MessageEnvelope.serializer(), raw) }.getOrNull()
            ?: return null
        if (envelope.messageType == TYPE_STATUS) return null
        return runCatching { json.decodeFromString(NavigationCommand.serializer(), envelope.payload) }.getOrNull()
    }

    private fun NavigationCommand.messageType(): String = when (this) {
        is NavigationCommand.Navigate -> "NAVIGATE"
        NavigationCommand.Stop -> "STOP"
        NavigationCommand.Resume -> "RESUME"
        is NavigationCommand.AddStop -> "ADD_STOP"
        is NavigationCommand.StatusCheck -> "STATUS_CHECK"
    }
}
