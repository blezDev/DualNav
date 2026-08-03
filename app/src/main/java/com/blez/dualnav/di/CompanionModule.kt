package com.blez.dualnav.di

import com.blez.dualnav.feature.companion.data.AndroidMapsIntentHandler
import com.blez.dualnav.feature.companion.domain.MapsIntentHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val companionModule = module {
    singleOf(::AndroidMapsIntentHandler) { bind<MapsIntentHandler>() }
}
