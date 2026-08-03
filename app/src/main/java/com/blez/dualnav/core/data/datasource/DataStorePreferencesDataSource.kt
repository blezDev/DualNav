package com.blez.dualnav.core.data.datasource

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.DeviceInfo
import com.blez.dualnav.core.domain.util.DataError
import com.blez.dualnav.core.domain.util.EmptyResult
import com.blez.dualnav.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "dualnav_preferences")

class DataStorePreferencesDataSource(
    private val context: Context,
    private val json: Json
) : PreferencesDataSource {

    private object Keys {
        val DEVICE_ROLE = stringPreferencesKey("device_role")
        val CONNECTION_TYPE = stringPreferencesKey("connection_type")
        val DEVICE_INFO = stringPreferencesKey("device_info")
        val PAIRED_DEVICES = stringPreferencesKey("paired_devices")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    override suspend fun saveDeviceRole(role: AppRole): EmptyResult<DataError.Local> {
        return writeSafely { it[Keys.DEVICE_ROLE] = role.name }
    }

    override fun getDeviceRole(): Flow<AppRole?> {
        return context.dataStore.data
            .catchIoErrors()
            .map { prefs ->
                prefs[Keys.DEVICE_ROLE]?.let { name ->
                    runCatching { AppRole.valueOf(name) }.getOrNull()
                }
            }
    }

    override suspend fun saveConnectionType(connectionType: ConnectionType): EmptyResult<DataError.Local> {
        return writeSafely { it[Keys.CONNECTION_TYPE] = connectionType.name }
    }

    override fun getConnectionType(): Flow<ConnectionType?> {
        return context.dataStore.data
            .catchIoErrors()
            .map { prefs ->
                prefs[Keys.CONNECTION_TYPE]?.let { name ->
                    runCatching { ConnectionType.valueOf(name) }.getOrNull()
                }
            }
    }

    override suspend fun saveDeviceInfo(deviceInfo: DeviceInfo): EmptyResult<DataError.Local> {
        return writeSafely { it[Keys.DEVICE_INFO] = json.encodeToString(deviceInfo) }
    }

    override fun getDeviceInfo(): Flow<DeviceInfo?> {
        return context.dataStore.data
            .catchIoErrors()
            .map { prefs ->
                prefs[Keys.DEVICE_INFO]?.let { raw ->
                    runCatching { json.decodeFromString<DeviceInfo>(raw) }.getOrNull()
                }
            }
    }

    override suspend fun savePairedDevices(devices: List<DeviceInfo>): EmptyResult<DataError.Local> {
        return writeSafely { it[Keys.PAIRED_DEVICES] = json.encodeToString(devices) }
    }

    override fun getPairedDevices(): Flow<List<DeviceInfo>> {
        return context.dataStore.data
            .catchIoErrors()
            .map { prefs ->
                prefs[Keys.PAIRED_DEVICES]?.let { raw ->
                    runCatching { json.decodeFromString<List<DeviceInfo>>(raw) }.getOrNull()
                } ?: emptyList()
            }
    }

    override suspend fun saveThemeMode(themeMode: AppThemeMode): EmptyResult<DataError.Local> {
        return writeSafely { it[Keys.THEME_MODE] = themeMode.name }
    }

    override fun getThemeMode(): Flow<AppThemeMode?> {
        return context.dataStore.data
            .catchIoErrors()
            .map { prefs ->
                prefs[Keys.THEME_MODE]?.let { name ->
                    runCatching { AppThemeMode.valueOf(name) }.getOrNull()
                }
            }
    }

    override suspend fun clearAll(): EmptyResult<DataError.Local> {
        return writeSafely { it.clear() }
    }

    private suspend inline fun writeSafely(
        crossinline transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit
    ): EmptyResult<DataError.Local> {
        return try {
            context.dataStore.edit { transform(it) }
            Result.Success(Unit)
        } catch (e: IOException) {
            Result.Error(DataError.Local.DISK_FULL)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    private fun Flow<androidx.datastore.preferences.core.Preferences>.catchIoErrors() = catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }
}
