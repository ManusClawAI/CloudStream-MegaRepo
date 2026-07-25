package com.bnyro

import com.lagradost.cloudstream3.extractors.Supervideo

class SupervideoCom: Supervideo() {
    override var name = "SupervideoCom"
    override var mainUrl = "https://supervideo.com"
}

class SupervideoTv: Supervideo() {
    override var name = "SupervideoTv"
    override var mainUrl = "https://supervideo.tv"
}

class DroploadIo: Supervideo() {
    override var name = "DroploadIO"
    override var mainUrl = "https://dropload.io"
}