package com.nimegami

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NimegamiPlugin : Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list
        // directly.
        registerMainAPI(Nimegami())
        registerExtractorAPI(Mitedrive())
        registerExtractorAPI(Berkasdrive())
        registerExtractorAPI(Videogami())
    }
}
