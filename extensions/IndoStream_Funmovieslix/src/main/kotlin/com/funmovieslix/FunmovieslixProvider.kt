package com.funmovieslix

import android.content.Context
import com.lagradost.cloudstream3.extractors.FileMoonIn
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FunmovieslixProvider : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Funmovieslix())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(FilemoonV2())
        registerExtractorAPI(Dhtpre())
        registerExtractorAPI(FileMoonIn())
        registerExtractorAPI(Vidhideplus())
    }
}
