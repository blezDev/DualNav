package com.blez.dualnav.di

import com.blez.dualnav.core.domain.usecase.EnsureLocalDeviceIdentityUseCase
import com.blez.dualnav.feature.connection.domain.SelectConnectionTypeUseCase
import com.blez.dualnav.feature.control.data.HttpMapsLinkResolver
import com.blez.dualnav.feature.control.domain.MapsLinkResolver
import com.blez.dualnav.feature.control.domain.ParseCoordinatesUseCase
import com.blez.dualnav.feature.control.domain.ParseMapsLinkUseCase
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val useCaseModule = module {
    singleOf(::HttpMapsLinkResolver) { bind<MapsLinkResolver>() }

    factoryOf(::SelectConnectionTypeUseCase)
    factoryOf(::ParseMapsLinkUseCase)
    factoryOf(::ParseCoordinatesUseCase)
    factoryOf(::EnsureLocalDeviceIdentityUseCase)
}
