package com.bnyro

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.FileMoonSx
import com.lagradost.cloudstream3.extractors.Voe1

@CloudstreamPlugin
class FilmpalastPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(FilmpalastProvider())

        registerExtractorAPI(Voe1())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(AbstreamTo())
    }
}