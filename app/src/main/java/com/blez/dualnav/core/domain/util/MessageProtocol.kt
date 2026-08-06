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

@Serializable
data class PairingRequestPayload(val deviceName: String, val pin: String)

/** An incoming pairing request, with the sender's stable device id pulled from the envelope. */
data class PairingRequest(val senderId: String, val deviceName: String, val pin: String)

@Serializable
data class HelloPayload(val deviceName: String)

/** A peer announcing its stable identity over an already-connected socket (Bluetooth has no PIN
 * handshake like WiFi's, so this is how each side otherwise learns the other's persistent id). */
data class Hello(val senderId: String, val deviceName: String)

/**
 * Wraps/unwraps the string each transport's `sendMessage(String)`/`receiveMessage(): Flow<String>`
 * actually carries. Kept separate from the per-transport data sources since every transport
 * (Bluetooth/WiFi/Firebase) shares the same envelope format.
 */
object MessageProtocol {
    private const val TYPE_STATUS = "STATUS"
    private const val TYPE_PAIR_REQUEST = "PAIR_REQUEST"
    private const val TYPE_PAIR_ACCEPT = "PAIR_ACCEPT"
    private const val TYPE_PAIR_REJECT = "PAIR_REJECT"
    private const val TYPE_HELLO = "HELLO"

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
        if (envelope.messageType != TYPE_STATUS && envelope.messageType != TYPE_PAIR_REQUEST &&
            envelope.messageType != TYPE_PAIR_ACCEPT && envelope.messageType != TYPE_PAIR_REJECT &&
            envelope.messageType != TYPE_HELLO
        ) {
            return runCatching {
                json.decodeFromString(
                    NavigationCommand.serializer(),
                    envelope.payload
                )
            }.getOrNull()
        }
        return null
    }

    fun wrapPairingRequest(
        deviceName: String,
        pin: String,
        senderId: String,
        receiverId: String,
        json: Json
    ): String {
        val envelope = MessageEnvelope(
            messageType = TYPE_PAIR_REQUEST,
            payload = json.encodeToString(
                PairingRequestPayload.serializer(),
                PairingRequestPayload(deviceName, pin)
            ),
            senderId = senderId,
            receiverId = receiverId
        )
        return json.encodeToString(MessageEnvelope.serializer(), envelope)
    }

    fun wrapPairingResponse(
        accepted: Boolean,
        senderId: String,
        receiverId: String,
        json: Json
    ): String {
        val envelope = MessageEnvelope(
            messageType = if (accepted) TYPE_PAIR_ACCEPT else TYPE_PAIR_REJECT,
            payload = "",
            senderId = senderId,
            receiverId = receiverId
        )
        return json.encodeToString(MessageEnvelope.serializer(), envelope)
    }

    /** Returns null for anything that isn't a pairing request envelope, or malformed input. */
    fun unwrapPairingRequest(raw: String, json: Json): PairingRequest? {
        val envelope =
            runCatching { json.decodeFromString(MessageEnvelope.serializer(), raw) }.getOrNull()
                ?: return null
        if (envelope.messageType != TYPE_PAIR_REQUEST) return null
        val payload = runCatching {
            json.decodeFromString(PairingRequestPayload.serializer(), envelope.payload)
        }.getOrNull() ?: return null
        return PairingRequest(
            senderId = envelope.senderId,
            deviceName = payload.deviceName,
            pin = payload.pin
        )
    }

    /** Returns null for anything that isn't a pairing response envelope, or malformed input. Otherwise true = accepted. */
    fun unwrapPairingResponse(raw: String, json: Json): Boolean? {
        val envelope =
            runCatching { json.decodeFromString(MessageEnvelope.serializer(), raw) }.getOrNull()
                ?: return null
        return when (envelope.messageType) {
            TYPE_PAIR_ACCEPT -> true
            TYPE_PAIR_REJECT -> false
            else -> null
        }
    }

    fun wrapHello(deviceName: String, senderId: String, receiverId: String, json: Json): String {
        val envelope = MessageEnvelope(
            messageType = TYPE_HELLO,
            payload = json.encodeToString(HelloPayload.serializer(), HelloPayload(deviceName)),
            senderId = senderId,
            receiverId = receiverId
        )
        return json.encodeToString(MessageEnvelope.serializer(), envelope)
    }

    /** Returns null for anything that isn't a hello envelope, or malformed input. */
    fun unwrapHello(raw: String, json: Json): Hello? {
        val envelope =
            runCatching { json.decodeFromString(MessageEnvelope.serializer(), raw) }.getOrNull()
                ?: return null
        if (envelope.messageType != TYPE_HELLO) return null
        val payload = runCatching {
            json.decodeFromString(HelloPayload.serializer(), envelope.payload)
        }.getOrNull() ?: return null
        return Hello(senderId = envelope.senderId, deviceName = payload.deviceName)
    }

    private fun NavigationCommand.messageType(): String = when (this) {
        is NavigationCommand.Navigate -> "NAVIGATE"
        NavigationCommand.Stop -> "STOP"
        NavigationCommand.Resume -> "RESUME"
        is NavigationCommand.AddStop -> "ADD_STOP"
        is NavigationCommand.StatusCheck -> "STATUS_CHECK"
    }
}
