package com.bnyro

import com.lagradost.cloudstream3.extractors.Supervideo
import com.lagradost.cloudstream3.extractors.VidHidePro

class Ryderjet: VidHidePro() {
    override var name = "Ryderjet"
    override var mainUrl = "https://ryderjet.com"
}

class AbstreamTo : Supervideo() {
    override var name = "Abstream"
    override var mainUrl = "https://abstream.to"
}