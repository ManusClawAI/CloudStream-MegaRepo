package com.bnyro

import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Supervideo
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.Voe

class MixDropPs: MixDrop() {
    override var name = "MixdropPs"
    override var mainUrl = "https://mixdrop.ps"
}

class Dooodster: DoodLaExtractor() {
    override var name = "Doodster"
    override var mainUrl = "https://dooodster.com"
}

class DoodsPro: DoodLaExtractor() {
    override var name = "DoodsPro"
    override var mainUrl = "https://doods.pro"
}

class DoodRe: DoodLaExtractor() {
    override var name = "DoodRe"
    override var mainUrl = "https://dood.re"
}

class SupervideoTv: Supervideo() {
    override var name = "SupervideoTv"
    override var mainUrl = "https://supervideo.tv"
}

class DroploadIo: Supervideo() {
    override var name = "DroploadIO"
    override var mainUrl = "https://dropload.io"
}

class GoofyBanana: Voe() {
    override var name = "Goofy Banana"
    override var mainUrl = "https://goofy-banana.com"
}

class Mixdrp:  MixDrop() {
    override var name = "Mixdrp"
    override var mainUrl = "https://mixdrp.to"
}

class Luluvdo: StreamWishExtractor() {
    override val mainUrl = "https://luluvdo.com"
}

class Ryderjet: VidHidePro() {
    override var name = "Ryderjet"
    override var mainUrl = "https://ryderjet.com"
}

class StreamRuby: StreamWishExtractor() {
    override var name = "Streamruby"
    override var mainUrl = "https://streamruby.com"
}

class SaveFiles: StreamWishExtractor() {
    override var name = "SaveFiles"
    override var mainUrl = "https://savefiles.com"
}