package com.parfumecreator

import android.app.Application
import android.content.ComponentCallbacks2
import com.parfumecreator.core.llm.LiteRtLmRunner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ParfumeApp : Application() {
    @Inject
    lateinit var liteRtLmRunner: LiteRtLmRunner

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            liteRtLmRunner.releaseForMemoryPressure()
        }
    }
}
