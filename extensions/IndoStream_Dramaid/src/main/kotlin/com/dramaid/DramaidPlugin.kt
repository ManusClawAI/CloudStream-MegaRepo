
package com.dramaid

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DramaidPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Dramaid())
        registerMainAPI(Oppadrama())
        registerExtractorAPI(Vanfem())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(Gcam())
    }
}