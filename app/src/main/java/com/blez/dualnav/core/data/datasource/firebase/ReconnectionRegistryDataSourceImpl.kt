package com.blez.dualnav.core.data.datasource.firebase

import com.blez.dualnav.core.data.datasource.ReconnectionRegistryDataSource
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ReconnectionSession
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.core.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import kotlin.coroutines.resume

class ReconnectionRegistryDataSourceImpl(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val logger: Logger
) : ReconnectionRegistryDataSource {

    override suspend fun upsertSession(session: ReconnectionSession): EmptyResult<DataError> {
        if (!ensureSignedIn()) return Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
        val ref = sessionRef(session.controlDeviceId, session.companionDeviceId)
        val value = mapOf(
            "controlDeviceId" to session.controlDeviceId,
            "companionDeviceId" to session.companionDeviceId,
            "status" to session.status.name,
            "endedByRole" to session.endedByRole?.name,
            "updatedAt" to session.updatedAt
        )
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
                        logger.warn("Failed to upsert reconnection session", it)
                        if (continuation.isActive) continuation.resume(Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE))
                    }
            }
        } catch (e: Exception) {
            Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
        }
    }

    override suspend fun getSession(
        controlDeviceId: String,
        companionDeviceId: String
    ): Result<ReconnectionSession?, DataError> {
        if (!ensureSignedIn()) return Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
        val ref = sessionRef(controlDeviceId, companionDeviceId)
        return try {
            suspendCancellableCoroutine { continuation ->
                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (continuation.isActive) continuation.resume(Result.Success(snapshot.toSession()))
                    }

                    override fun onCancelled(error: DatabaseError) {
                        logger.warn("Failed to read reconnection session", error.toException())
                        if (continuation.isActive) {
                            continuation.resume(Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE))
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Result.Error(DataError.Connection.FIREBASE_UNAVAILABLE)
        }
    }

    override fun observeSession(
        controlDeviceId: String,
        companionDeviceId: String
    ): Flow<ReconnectionSession?> = callbackFlow {
        if (!ensureSignedIn()) {
            // A no-argument close() completes the flow normally rather than as a failure, which
            // would make the repository's retryWhen() never fire - so a single transient sign-in
            // hiccup would silently and permanently end this listener instead of being retried.
            close(IllegalStateException("Anonymous sign-in failed"))
            return@callbackFlow
        }
        val ref = sessionRef(controlDeviceId, companionDeviceId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toSession())
            }

            override fun onCancelled(error: DatabaseError) {
                logger.warn("Reconnection session listener cancelled", error.toException())
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun DataSnapshot.toSession(): ReconnectionSession? {
        if (!exists()) return null
        val controlDeviceId = child("controlDeviceId").getValue(String::class.java) ?: return null
        val companionDeviceId =
            child("companionDeviceId").getValue(String::class.java) ?: return null
        val status = child("status").getValue(String::class.java)
            ?.let { runCatching { ReconnectionSession.Status.valueOf(it) }.getOrNull() }
            ?: return null
        val endedByRole = child("endedByRole").getValue(String::class.java)
            ?.let { runCatching { AppRole.valueOf(it) }.getOrNull() }
        val updatedAt = child("updatedAt").getValue(Long::class.java) ?: 0L
        return ReconnectionSession(
            controlDeviceId,
            companionDeviceId,
            status,
            endedByRole,
            updatedAt
        )
    }

    private suspend fun ensureSignedIn(): Boolean {
        if (auth.currentUser != null) return true
        return suspendCancellableCoroutine { continuation ->
            auth.signInAnonymously()
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(true) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(false) }
        }
    }

    private fun sessionRef(controlDeviceId: String, companionDeviceId: String) =
        database.getReference(SESSIONS_PATH).child(sessionKey(controlDeviceId, companionDeviceId))

    private fun sessionKey(controlDeviceId: String, companionDeviceId: String): String {
        val raw = "$controlDeviceId|$companionDeviceId"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SESSIONS_PATH = "reconnection_sessions"
    }
}
