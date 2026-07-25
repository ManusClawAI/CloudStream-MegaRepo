package it.dogior.hadEnough

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class CB01Plugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CB01())
    }
}