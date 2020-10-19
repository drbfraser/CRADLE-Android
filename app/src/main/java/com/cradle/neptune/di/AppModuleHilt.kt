package com.cradle.neptune.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module(includes = [
    DataModule::class,
    ViewModelModule::class
])
interface AppModuleHilt
