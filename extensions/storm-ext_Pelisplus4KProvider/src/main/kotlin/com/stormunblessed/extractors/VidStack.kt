package com.stormunblessed.extractors

import com.lagradost.cloudstream3.extractors.VidStack

class PelisplusUpnsPro : VidStack() {
    override var mainUrl = "https://pelisplus.upns.pro"
    override var name = "PelisplusUpns"
}

class PelisplusUpnsPro2 : VidStack() {
    override var mainUrl = "https://pelisplus.strp2p.com"
    override var name = "PelisplusStrp2p"
}

class PelisplusUpnsPro3 : VidStack() {
    override var mainUrl = "https://pelisplusto.4meplayer.pro"
    override var name = "Pelisplus4meplayer"
}

class Anime4meplayer : VidStack() {
    override var mainUrl = "https://anime.4meplayer.com"
    override var name = "Anime4meplayer"
}

class AnimeUpnsPro : VidStack() {
    override var mainUrl = "https://anime.upns.pro"
    override var name = "AnimeUpnsPro"
}

class AnimeP2pstreamVip : VidStack() {
    override var mainUrl = "https://anime.p2pstream.vip"
    override var name = "AnimeP2pstreamVip"
}
