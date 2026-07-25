package com.stormunblessed

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.stormunblessed.extractors.*

@CloudstreamPlugin
class AnimeJlProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AnimeJlProvider())
//         VidHidepro
        registerExtractorAPI(DhtpreCom())
        registerExtractorAPI(DingtezuniCom())
        registerExtractorAPI(Minochinos())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(VidHideHub())
        registerExtractorAPI(VidHidePro1())
        registerExtractorAPI(VidHidePro2())
        registerExtractorAPI(VidHidePro3())
        registerExtractorAPI(VidHidePro4())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(Dhtpre())
        registerExtractorAPI(Peytonepre())
        registerExtractorAPI(VidHidePro())
//        streamwish
        registerExtractorAPI(SaveFiles())
        registerExtractorAPI(Mwish())
        registerExtractorAPI(Dwish())
        registerExtractorAPI(Ewish())
        registerExtractorAPI(WishembedPro())
        registerExtractorAPI(Kswplayer())
        registerExtractorAPI(Wishfast())
        registerExtractorAPI(Streamwish2())
        registerExtractorAPI(SfastwishCom())
        registerExtractorAPI(Strwish())
        registerExtractorAPI(Strwish2())
        registerExtractorAPI(FlaswishCom())
        registerExtractorAPI(Awish())
        registerExtractorAPI(Obeywish())
        registerExtractorAPI(Jodwish())
        registerExtractorAPI(Swhoi())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(UqloadsXyz())
        registerExtractorAPI(Doodporn())
        registerExtractorAPI(CdnwishCom())
        registerExtractorAPI(Asnwish())
        registerExtractorAPI(Nekowish())
        registerExtractorAPI(Nekostream())
        registerExtractorAPI(Swdyu())
        registerExtractorAPI(Wishonly())
        registerExtractorAPI(Playerwish())
        registerExtractorAPI(StreamHLS())
        registerExtractorAPI(HlsWish())
        registerExtractorAPI(StreamWishExtractor())
//        VidStack
        registerExtractorAPI(PelisplusUpnsPro())
        registerExtractorAPI(PelisplusUpnsPro2())
        registerExtractorAPI(PelisplusUpnsPro3())
        registerExtractorAPI(Anime4meplayer())
        registerExtractorAPI(AnimeUpnsPro())
        registerExtractorAPI(AnimeP2pstreamVip())
//        ByseSX
        registerExtractorAPI(BysezoxexeCom())
        registerExtractorAPI(Bysezejataos())
        registerExtractorAPI(ByseBuho())
        registerExtractorAPI(ByseVepoin())
        registerExtractorAPI(ByseQekaho())
        registerExtractorAPI(ByseSX())
//        Filesim
        registerExtractorAPI(EmturbovidCom())

    }
}