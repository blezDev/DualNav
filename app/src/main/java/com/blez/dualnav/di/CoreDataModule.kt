package com.blez.dualnav.di

import com.blez.dualnav.core.data.datasource.DataStorePreferencesDataSource
import com.blez.dualnav.core.data.datasource.PreferencesDataSource
import com.blez.dualnav.core.data.repository.DataStorePreferencesRepository
import com.blez.dualnav.core.data.repository.PreferencesDeviceRepository
import com.blez.dualnav.core.domain.repository.DeviceRepository
import com.blez.dualnav.core.domain.repository.PreferencesRepository
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val coreDataModule = module {
    single { Json { ignoreUnknownKeys = true } }

    singleOf(::DataStorePreferencesDataSource) { bind<PreferencesDataSource>() }
    singleOf(::DataStorePreferencesRepository) { bind<PreferencesRepository>() }
    singleOf(::PreferencesDeviceRepository) { bind<DeviceRepository>() }
}
