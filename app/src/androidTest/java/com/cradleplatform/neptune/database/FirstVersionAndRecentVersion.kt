package com.cradleplatform.neptune.database

data class FirstVersionAndRecentVersion<T, R> (
    val firstVerObj: T,
    val expectedRecentVerObj: R
)