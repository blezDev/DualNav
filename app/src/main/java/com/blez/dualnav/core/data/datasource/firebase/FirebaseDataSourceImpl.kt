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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cloud relay fallback: Firebase Realtime Database under `users/{uid}/messages`, with anonymous
 * auth providing the per-pair `uid` and `.info/connected` driving live connection status.
 * No device discovery here — pairing for this transport happens out of band (shared uid/room),
 * so [com.blez.dualnav.core.domain.repository.ConnectionRepository] never routes discovery here.
 */
class FirebaseDataSourceImpl(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val logger: Logger
) : FirebaseDataSource {

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    private var uid: String? = null
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
        _connectionStatus.value = ConnectionStatus.Disconnected
        return Result.Success(Unit)
    }

    override suspend fun sendMessage(message: String): EmptyResult<DataError.Connection> {
        val ref = messagesRef() ?: return Result.Error(DataError.Connection.NOT_CONNECTED)
        return try {
            val payload = mapOf(
                "payload" to message,
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
        val currentUid = uid ?: return null
        return database.getReference("users").child(currentUid).child("messages")
    }
}
