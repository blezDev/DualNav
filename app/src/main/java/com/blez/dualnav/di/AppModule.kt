package com.blez.dualnav.di

import com.blez.dualnav.core.data.logging.AndroidLogger
import com.blez.dualnav.core.domain.util.Logger
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    singleOf(::AndroidLogger) { bind<Logger>() }
}
