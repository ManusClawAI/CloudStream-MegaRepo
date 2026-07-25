
package com.bnyro

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.FileMoonSx

@CloudstreamPlugin
class EinschaltenInPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(EinschaltenInProvider())
        registerExtractorAPI(D000dCom())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Vide0Net())
        registerExtractorAPI(DSVPlay())
    }
}