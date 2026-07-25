package com.layarkaca

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class LayarKacaPlugin : Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list
        // directly.
        registerMainAPI(LayarKaca())
        registerExtractorAPI(Emturbovid())
        registerExtractorAPI(Furher())
        registerExtractorAPI(Hownetwork())
    }
}
