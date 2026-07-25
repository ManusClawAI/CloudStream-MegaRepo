
package com.bnyro

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Urochsunloath
import com.lagradost.cloudstream3.extractors.Voe1

@CloudstreamPlugin
class SerienstreamPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Serienstream())
        registerExtractorAPI(Voe1())
        registerExtractorAPI(Urochsunloath())
    }
}