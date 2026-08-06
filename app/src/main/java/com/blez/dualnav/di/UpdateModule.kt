package com.blez.dualnav.di

import com.blez.dualnav.core.data.repository.GithubUpdateRepository
import com.blez.dualnav.core.data.update.AndroidUpdateInstaller
import com.blez.dualnav.core.domain.repository.UpdateInstaller
import com.blez.dualnav.core.domain.repository.UpdateRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val updateModule = module {
    singleOf(::GithubUpdateRepository) { bind<UpdateRepository>() }
    singleOf(::AndroidUpdateInstaller) { bind<UpdateInstaller>() }
}
