package com.blez.dualnav.core.data.datasource.firebase

import com.blez.dualnav.core.data.datasource.FirebaseDataSource
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.core.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * Cloud relay fallback: Firebase Realtime Database under `relay_channels/{code}/messages`, with a
 * short code (shared out of band - read aloud, texted, whatever) letting Control and Companion
 * agree on the same channel, since each phone's own anonymous-auth uid is otherwise useless for
 * finding each other (it's random per install, never shared between the two devices).
 */
class FirebaseDataSourceImpl(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val logger: Logger
) : FirebaseDataSource {

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    private var uid: String? = null
    private var channelCode: String? = null
    private var connectedListener: ValueEventListener? = null

    override suspend fun connect(): EmptyResult<DataError.Connection> {
        val user = auth.currentUser ?: signInAnonymously() ?: return Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
        uid = user.uid
        attachConnectivityListener()
        return Result.Success(Unit)
    }

    override suspend fun disconnect(): EmptyResult<DataError.Connection> {
        connectedListener?.let { database.getReference(".info/connected").removeEventListener(it) }
        connectedListener = null
        channelCode = null
        _connectionStatus.value = ConnectionStatus.Disconnected
        return Result.Success(Unit)
    }

    override suspend fun createChannel(): Result<String, DataError.Connection> {
        val code = generateCode()
        val result = registerAsControl(code)
        if (result is Result.Error) return result
        return Result.Success(code)
    }

    override suspend fun registerAsControl(code: String): EmptyResult<DataError.Connection> {
        val currentUid = uid ?: return Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
        val result = writeValue(channelRef(code).child("controlUid"), currentUid)
        if (result is Result.Success) channelCode = code
        return result
    }

    override suspend fun joinChannel(code: String): Result<String, DataError.Connection> {
        val currentUid = uid ?: return Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
        val controlUid = readValue(channelRef(code).child("controlUid"))
            ?: return Result.Error(DataError.Connection.DEVICE_NOT_FOUND)
        val writeResult = writeValue(channelRef(code).child("companionUid"), currentUid)
        if (writeResult is Result.Error) return writeResult
        channelCode = code
        return Result.Success(controlUid)
    }

    override fun observePeerJoined(code: String): Flow<String> = callbackFlow {
        val ref = channelRef(code).child("companionUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(String::class.java))
            }

            override fun onCancelled(error: DatabaseError) {
                logger.warn("Peer-joined listener cancelled", error.toException())
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }.mapNotNull { it }

    override suspend fun sendMessage(message: String): EmptyResult<DataError.Connection> {
        val ref = messagesRef() ?: return Result.Error(DataError.Connection.NOT_CONNECTED)
        val currentUid = uid ?: return Result.Error(DataError.Connection.NOT_CONNECTED)
        return try {
            val payload = mapOf(
                "payload" to message,
                "senderUid" to currentUid,
                "timestamp" to ServerValue.TIMESTAMP,
                "delivered" to false
            )
            suspendCancellableCoroutine<Unit> { continuation ->
                ref.push().setValue(payload)
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
                    .addOnFailureListener { e -> if (continuation.isActive) continuation.resumeWithException(e) }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DataError.Connection.NOT_CONNECTED)
        }
    }

    override fun receiveMessage(): Flow<String> = callbackFlow {
        val ref = messagesRef()
        if (ref == null) {
            close()
            return@callbackFlow
        }
        val cutoff = System.currentTimeMillis().toDouble()
        val query = ref.orderByChild("timestamp").startAt(cutoff)
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                // The channel is a shared node both sides push to - without this we'd receive our
                // own messages echoed straight back.
                val senderUid = snapshot.child("senderUid").getValue(String::class.java)
                if (senderUid == uid) return
                val payload = snapshot.child("payload").getValue(String::class.java)
                if (payload != null) {
                    trySend(payload)
                    snapshot.ref.child("delivered").setValue(true)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                logger.error("Message listener cancelled", error.toException())
                close(error.toException())
            }
        }
        query.addChildEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    override fun getConnectionStatus(): Flow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private suspend fun signInAnonymously() = suspendCancellableCoroutine { continuation ->
        auth.signInAnonymously()
            .addOnSuccessListener { result -> continuation.resume(result.user) }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
    }

    private fun attachConnectivityListener() {
        if (connectedListener != null) return
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                _connectionStatus.value =
                    if (isConnected) ConnectionStatus.Connected else ConnectionStatus.Reconnecting
            }
            override fun onCancelled(error: DatabaseError) {
                logger.error("Connectivity listener cancelled", error.toException())
                _connectionStatus.value = ConnectionStatus.Error(error.message)
            }
        }
        connectedListener = listener
        database.getReference(".info/connected").addValueEventListener(listener)
    }

    private fun messagesRef(): DatabaseReference? {
        val code = channelCode ?: return null
        return channelRef(code).child("messages")
    }

    private fun channelRef(code: String): DatabaseReference =
        database.getReference(CHANNELS_PATH).child(code)

    private fun generateCode(): String = Random.nextInt(100_000, 1_000_000).toString()

    private suspend fun writeValue(
        ref: DatabaseReference,
        value: String
    ): EmptyResult<DataError.Connection> {
        return try {
            suspendCancellableCoroutine { continuation ->
                ref.setValue(value)
                    .addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(
                            Result.Success(
                                Unit
                            )
                        )
                    }
                    .addOnFailureListener {
                        logger.warn("Failed to write relay channel value", it)
                        if (continuation.isActive) continuation.resume(Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE))
                    }
            }
        } catch (e: Exception) {
            Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
        }
    }

    private suspend fun readValue(ref: DatabaseReference): String? {
        return try {
            suspendCancellableCoroutine { continuation ->
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (continuation.isActive) continuation.resume(snapshot.getValue(String::class.java))
                    }

                    override fun onCancelled(error: DatabaseError) {
                        logger.warn("Failed to read relay channel value", error.toException())
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val CHANNELS_PATH = "relay_channels"
    }
}
