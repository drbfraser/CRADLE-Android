package com.cradleVSA.neptune.binding

import androidx.databinding.DataBindingComponent

/**
 * Set up a DataBindingComponent that's compatible with Fragments.
 * src: https://github.com/android/architecture-components-samples/tree/master/GithubBrowserSampl
 * /app/src/main/java/com/android/example/github/binding
 */
class FragmentDataBindingComponent : DataBindingComponent {
    private val adapters = ReadingBindingAdapters()
    override fun getReadingBindingAdapters(): ReadingBindingAdapters = adapters
}
