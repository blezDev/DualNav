package com.blez.dualnav.di

import com.blez.dualnav.core.data.datasource.BluetoothDataSource
import com.blez.dualnav.core.data.datasource.FirebaseDataSource
import com.blez.dualnav.core.data.datasource.WiFiDataSource
import com.blez.dualnav.core.data.datasource.bluetooth.BluetoothDataSourceImpl
import com.blez.dualnav.core.data.datasource.firebase.FirebaseDataSourceImpl
import com.blez.dualnav.core.data.datasource.wifi.WiFiDataSourceImpl
import com.blez.dualnav.core.data.repository.MultiTransportConnectionRepository
import com.blez.dualnav.core.data.repository.MultiTransportMessageRepository
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.MessageRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val connectionModule = module {
    single { FirebaseAuth.getInstance() }
    single { FirebaseDatabase.getInstance() }

    singleOf(::BluetoothDataSourceImpl) { bind<BluetoothDataSource>() }
    singleOf(::WiFiDataSourceImpl) { bind<WiFiDataSource>() }
    singleOf(::FirebaseDataSourceImpl) { bind<FirebaseDataSource>() }

    singleOf(::MultiTransportConnectionRepository) { bind<ConnectionRepository>() }
    singleOf(::MultiTransportMessageRepository) { bind<MessageRepository>() }
}
