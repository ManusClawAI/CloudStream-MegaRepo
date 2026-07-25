package com.egydead

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.Episode as CS3Episode
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.net.URL
import java.net.URI
import com.lagradost.cloudstream3.network.CloudflareKiller
import kotlinx.coroutines.delay

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
class EgyDead : MainAPI() {
    override var mainUrl = "https://egydead.beer"
    override var name = "ايجي ديد"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )
    @Volatile
    private var dynamicUA: String? = null

    private fun getUA(): String {

        return dynamicUA ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }


        private val androidUA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

        private val cloudflareKiller by lazy { CloudflareKiller() }
        private val bypassMutex = Mutex()

        @Volatile
        private var cfBypassed = false

        private fun log(tag: String, msg: String) {
            println("EgyDeadDebug | [$tag] -> $msg")
        }

    private val imageHeaders: Map<String, String>
        get() {
            val headers = mutableMapOf(
                "User-Agent" to getUA(),
                "Referer" to "$mainUrl/"
            )

            cloudflareKiller.savedCookies["egydead.beer"]?.let { cookies ->
                if (cookies.isNotEmpty()) {
                    headers["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                }
            }
            return headers
        }

    private suspend fun httpGet(url: String, referer: String? = null): Document {
        val headers = mapOf(
            "User-Agent" to getUA(),
            "Referer" to (referer ?: mainUrl),
            "Accept-Language" to "ar,en-US;q=0.9"
        )

        log("GET-REQUEST", "Fetching: $url")


        var res = app.get(url, headers = headers, interceptor = cloudflareKiller, timeout = 30)

        if (res.code == 403 && !cfBypassed) {
            log("GET-REQUEST", "403 detected. Running bypass...")
            runCloudflareBypass() // سيقوم بالحل والانتظار

            res = app.get(url, headers = headers, interceptor = cloudflareKiller, timeout = 30)
        }

        return res.document
    }

    private suspend fun httpPost(url: String, data: Map<String, String>, referer: String? = null): Document {
        val headers = mapOf(
            "User-Agent" to getUA(),
            "Referer" to (referer ?: mainUrl),
            "X-Requested-With" to "XMLHttpRequest",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
        )

        log("POST-REQUEST", "Sending to: $url")
        var res = app.post(url, data = data, headers = headers, interceptor = cloudflareKiller, timeout = 30)

        if (res.code == 403 && !cfBypassed) {
            log("POST-REQUEST", "403 detected. Running bypass...")
            runCloudflareBypass()
            res = app.post(url, data = data, headers = headers, interceptor = cloudflareKiller, timeout = 30)
        }

        return res.document
    }

    private suspend fun runCloudflareBypass() {
        val host = "egydead.beer"

        if (cloudflareKiller.savedCookies.containsKey(host)) {
            cfBypassed = true
            return
        }

        bypassMutex.withLock {
            if (cfBypassed) return@withLock

            log("BYPASS", "Initiating WebView solver...")
            val solveJob = GlobalScope.launch(Dispatchers.IO) {
                try {
                    val response = app.get(mainUrl, interceptor = cloudflareKiller, timeout = 60)

                    if (response.code == 200) {
                        val capturedUA = response.okhttpResponse.request.header("User-Agent")
                        if (!capturedUA.isNullOrBlank()) dynamicUA = capturedUA
                    }
                } catch (e: Exception) {
                    log("WEBVIEW", "Error: ${e.message}")
                }
            }

            for (i in 1..15) {
                if (cloudflareKiller.savedCookies.containsKey(host) || solveJob.isCompleted) {
                    log("POLLING", "Ready to proceed at second $i")
                    cfBypassed = true
                    break
                }
                delay(1000)
            }
            cfBypassed = true // حتى لو فشل، نفتح المجال للطلب الرئيسي ليحاول
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val logTag = "MAIN-PAGE"
        log(logTag, "Starting getMainPage | Page: $page | Request: ${request.name}")

        val document = try {
            log(logTag, "Attempting to fetch HTML from: $mainUrl")
            httpGet(mainUrl)
        } catch (e: Exception) {
            log(logTag, "CRITICAL ERROR: Failed to fetch main page -> ${e.message}")
            return HomePageResponse(emptyList())
        }

        val homePageList = ArrayList<HomePageList>()

        log(logTag, "Parsing Pinned Section (div.pin-posts-list)...")
        val pinnedSection = document.selectFirst("div.pin-posts-list")
        if (pinnedSection != null) {
            val sectionTitle = pinnedSection.selectFirst("h1.TitleMaster em")?.text()?.trim() ?: "المميز"
            log(logTag, "Pinned Section found! Title: '$sectionTitle'")

            val items = pinnedSection.select("li.movieItem").mapNotNull {
                it.toSearchResponse("PINNED")
            }

            if (items.isNotEmpty()) {
                log(logTag, "Pinned Section: Successfully added ${items.size} items.")
                homePageList.add(HomePageList(sectionTitle, items, isHorizontalImages = true))
            } else {
                log(logTag, "Pinned Section: FOUND, but contains 0 valid items.")
            }
        } else {
            log(logTag, "Pinned Section (div.pin-posts-list) NOT FOUND in HTML.")
        }

        log(logTag, "Parsing Main Sections (section.main-section)...")
        val mainSections = document.select("section.main-section")
        log(logTag, "Found ${mainSections.size} main-section containers.")

        mainSections.forEachIndexed { index, section ->
            val sectionTitle = section.selectFirst("h1.TitleMaster em")?.text()?.trim() ?: "قسم ${index + 1}"
            log(logTag, "Processing Section [$index]: '$sectionTitle'")

            val items = section.select("li.movieItem").mapNotNull {
                it.toSearchResponse("SECTION-$index")
            }

            if (items.isNotEmpty()) {
                log(logTag, "Section '$sectionTitle': Successfully added ${items.size} items.")
                homePageList.add(HomePageList(sectionTitle, items))
            } else {
                log(logTag, "Section '$sectionTitle': Contains 0 valid items.")
            }
        }

        val finalCount = homePageList.filter { it.list.isNotEmpty() }.size
        log(logTag, "getMainPage finished. Total valid sections found: $finalCount")

        if (homePageList.isEmpty()) {
            log(logTag, "WARNING: homePageList is empty. Possible selector mismatch or empty page.")
        }

        return HomePageResponse(homePageList.filter { it.list.isNotEmpty() })
    }

    private fun Element.toSearchResponse(parentTag: String): SearchResponse? {
        try {
            val linkEl = this.selectFirst("a") ?: run {
                log("PARSER-$parentTag", "Item failed: No anchor (<a>) tag found.")
                return null
            }

            val href = linkEl.attr("href")
            val fullUrl = fixUrlNull(href) ?: run {
                log("PARSER-$parentTag", "Item failed: Could not fix URL from '$href'")
                return null
            }

            val title = this.selectFirst("h1.BottomTitle")?.text()?.trim() ?: run {
                log("PARSER-$parentTag", "Item failed: No title found for URL: $fullUrl")
                return null
            }

            val posterUrl = this.selectFirst("img")?.attr("src")

            return newMovieSearchResponse(title, fullUrl) {
                this.posterUrl = posterUrl
                this.posterHeaders = imageHeaders
            }

        } catch (e: Exception) {
            log("PARSER-$parentTag", "CRITICAL Item Error: ${e.message}")
            return null
        }
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = httpGet(url)
        return document.select("ul.posts-list li.movieItem").mapNotNull {

            it.toSearchResponse("SEARCH")
        }
    }



    private val seasonNumRegex = Regex(
        """(?ix)
        (?:الموسم[\s:\-_.]*0*(\d+))
        |
        (?:S(?:eason)?[\s:\-_.]*0*(\d+))
        """.trimIndent().replace("\n", "")
    )

    private val episodeNumRegex = Regex(
        """(?ix)
        (?:حلقة[\s:\-_.]*0*(\d+))|
        (?:Episode[\s:\-_.]*0*(\d+))|
        (?:EP[\s:\-_.]*0*(\d+))|
        (?:\d+[xX]0*(\d+))|
        (?:S(?:eason)?[\s:\-_.]*\d+[\s\-_.,]*E(?:p(?:isode)?)?[\s:\-_.]*0*(\d+))
        """.trimIndent().replace("\n", "")
    )

    private fun getSeasonNum(title: String?): Int {
        if (title == null) return 9999
        val match = seasonNumRegex.find(title) ?: return 9999
        return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: 9999
    }

    private fun getEpisodeNum(title: String?): Int {
        if (title == null) return 9999
        val match = episodeNumRegex.find(title) ?: return 9999
        return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: 9999
    }

    private fun normalizeUrl(link: String?, base: String): String? {
        if (link.isNullOrBlank()) return null
        val t = link.trim()
        if (t.startsWith("#") || t.lowercase().startsWith("javascript:")) return null
        return try {
            val resolved = if (t.startsWith("http")) t else URL(URL(base), t).toString()
            fixUrl(resolved)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun batchFetch(
        urls: List<String>,
        concurrency: Int = 8
    ): Map<String, Document?> {
        val sem = Semaphore(concurrency)
        val out = mutableMapOf<String, Document?>()
        coroutineScope {
            val jobs = urls.map { u ->
                async {
                    sem.withPermit {
                        try {

                            val res = httpGet(u)
                            out[u] = res
                        } catch (e: Exception) {
                            e.printStackTrace()
                            out[u] = null
                        }
                    }
                }
            }
            jobs.awaitAll()
        }
        return out
    }


    private suspend fun discoverSeasonsPreserveOrder(
        startUrl: String,
        concurrency: Int = 8
    ): List<Triple<Int, String, String>> {
        val discovered = mutableListOf<Triple<Int, String, String>>()
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(startUrl); seen.add(startUrl)
        var nextIndex = 0

        while (queue.isNotEmpty()) {
            val batch = mutableListOf<String>()
            repeat(minOf(queue.size, concurrency)) { batch.add(queue.poll()) }
            if (batch.isEmpty()) break

            val docs = batchFetch(batch, concurrency)
            for (u in batch) {
                val doc = docs[u] ?: continue
                val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                    ?: "موسم غير معروف"
                discovered.add(Triple(nextIndex++, title, u))

                val seasonsCont =
                    doc.selectFirst("div.seasons-list") ?: doc.selectFirst("div.seasons")
                seasonsCont?.select("li.movieItem a, a")?.forEach { a ->
                    val href = normalizeUrl(a.attr("href"), u) ?: return@forEach
                    if (href !in seen && href.contains("/season/")) {
                        seen.add(href)
                        queue.add(href)
                    }
                }
            }
        }

        return discovered.distinctBy { it.third }
    }

    private fun extractEpisodesFromSeasonDoc(seasonUrl: String, doc: Document): List<CS3Episode> {
        val episodes = mutableListOf<CS3Episode>()
        val epsContainer = doc.selectFirst("div.EpsList") ?: doc.selectFirst("div.episodes-list")
        ?: doc.selectFirst("ul")
        ?: return emptyList()

        val items = epsContainer.select("li, a")
        for (el in items) {
            val a: Element = if (el.tagName() == "a") el else el.selectFirst("a") ?: continue
            val rawTitle = (a.attr("title").takeIf { it.isNotBlank() } ?: a.text()).trim()
            val href = normalizeUrl(a.attr("href"), seasonUrl) ?: continue

            if (href.contains("/season/")) continue
            if (href.contains("/film/")) continue

            val epNum = getEpisodeNum(rawTitle)
            val ep: CS3Episode = newEpisode(href) {
                this.name = rawTitle
                this.episode = if (epNum != 9999) epNum else null
                this.data = href
            }
            episodes.add(ep)
        }

        return episodes.sortedBy { it.episode ?: 9999 }
    }

    private fun parseRecommendations(doc: Document, base: String): List<SearchResponse> {
        val out = mutableListOf<SearchResponse>()
        val nodes = doc.select(".related-posts li.movieItem, .related-posts a, .related-posts li")
        for (li in nodes) {
            val a = li.selectFirst("a") ?: continue
            val href = normalizeUrl(a.attr("href"), base) ?: continue
            val title = a.selectFirst("h1, span, .title")?.text() ?: a.attr("title")
                .takeIf { it.isNotBlank() } ?: a.text()
            val poster = a.selectFirst("img")?.attr("src")
            val sr = when {
                href.contains("/film/") -> newMovieSearchResponse(title, href) {
                    this.posterUrl = poster
                    this.posterHeaders = imageHeaders
                }

                href.contains("/season/") || href.contains("/series/") || href.contains("/show/") || href.contains(
                    "/serie/"
                ) || href.contains("/assembly/") -> newTvSeriesSearchResponse(
                    title,
                    href
                ) { this.posterUrl = poster
                    this.posterHeaders = imageHeaders
                }

                else -> null
            }
            sr?.let { out.add(it) }
        }
        return out
    }

    override suspend fun load(url: String): LoadResponse? {
        fun log(msg: String) = println("EgyDead.load | $msg")

        log("START load() for url=$url")
        val document = try {
            httpGet(url).also { log("Fetched initial URL (length=${it.html().length})") }
        } catch (e: Exception) {
            e.printStackTrace()
            log("ERROR: failed to fetch initial url -> ${e.message}")
            return null
        }

        val movieCollectionList = document.selectFirst("div.salery-list ul")
        if (movieCollectionList != null) {
            log("Detected Movie Collection page (salery-list)")
            val seriesTitle =
                document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                    ?: "Movie Collection"
            val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            val plot = document.selectFirst("div.singleStory")?.text()?.trim()

            val moviesAsEpisodes =
                movieCollectionList.select("li.movieItem").mapIndexedNotNull { index, item ->
                    val a = item.selectFirst("a") ?: return@mapIndexedNotNull null
                    val href = normalizeUrl(a.attr("href"), url) ?: return@mapIndexedNotNull null

                    if (!href.contains("/film/")) return@mapIndexedNotNull null

                    val movieTitle =
                        item.selectFirst("h1.BottomTitle")?.text() ?: "Movie ${index + 1}"
                    val moviePoster = item.selectFirst("img")?.attr("src")

                    newEpisode(href) {
                        this.name = movieTitle
                        this.posterUrl = moviePoster
                        this.season = 1 // Treat all movies as season 1
                        this.episode = index + 1
                        this.data = href // Pass the movie URL to loadLinks
                    }
                }

            if (moviesAsEpisodes.isNotEmpty()) {
                log("Found ${moviesAsEpisodes.size} movies in the collection.")
                return newTvSeriesLoadResponse(
                    seriesTitle,
                    url,
                    TvType.TvSeries,
                    moviesAsEpisodes
                ) {
                    this.posterUrl = poster
                    this.posterHeaders = imageHeaders
                    this.plot = plot
                    this.recommendations = parseRecommendations(document, url)
                }
            }
        }

        if (url.contains("/film/")) {
            log("Detected /film/ page")
            val title =
                document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: run {
                    log("Movie: no og:title found")
                    return null
                }
            val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            val plot = document.selectFirst("div.singleStory")?.text()?.trim()
            val year =
                document.select("div.LeftBox li:has(span:contains(السنه)) a").text().toIntOrNull()
            val tags =
                document.select("div.LeftBox li:has(span:contains(النوع)) a").map { it.text() }
            val recommendations = parseRecommendations(document, url)
            log("Movie parsed: title='$title', year=$year, tags=${tags.size}, recs=${recommendations.size}")
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.posterHeaders = imageHeaders
                this.plot = plot
                this.year = year
                this.tags = tags
                this.recommendations = recommendations
            }
        }

        val isEpisode = url.contains("/episode/")
        val isSeason = url.contains("/season/")
        val isSeriesPage = url.contains("/serie/") // Helper for main series page
        val hasSeasonsList =
            document.selectFirst("div.seasons-list") != null // Helper for main series page

        log("isEpisode=$isEpisode, isSeason=$isSeason, isSeriesPage=$isSeriesPage, hasSeasonsList=$hasSeasonsList")

        var startSeasonUrl: String? = null

        if (isEpisode) {
            log("Page is an episode, try to find season link from breadcrumbs or page")
            val bc = document.selectFirst("div.breadcrumbs-single, div.breadcrumbs")
            bc?.select("a")?.forEach { a ->
                val href = a.attr("href")
                if (href.contains("/season/") || href.contains("/serie/")) {
                    startSeasonUrl = normalizeUrl(href, url)
                    log("Found parent link in breadcrumbs -> $startSeasonUrl")
                }
            }
            if (startSeasonUrl == null) {
                val linkInPage =
                    document.selectFirst("div.seasons-list li.movieItem a, div.seasons-list a")
                        ?.attr("href")
                startSeasonUrl = normalizeUrl(linkInPage, url)
                log("Candidate parent link from page container -> $startSeasonUrl")
            }
        } else if (isSeason) {
            startSeasonUrl = url
            log("Page itself is a season page -> $startSeasonUrl")
        } else {
            val maybeEps = document.selectFirst("div.EpsList, div.episodes-list, ul.episodes")

            if (isSeriesPage || hasSeasonsList) {
                startSeasonUrl = url
                log("Detected Main Series Page. Setting start point to current URL.")
            } else if (maybeEps != null) {
                log("Page contains episodes list directly (no /season/ or /episode/ in URL). Treating as series page.")
                val eps = extractEpisodesFromSeasonDoc(url, document)
                val pageImage = document.selectFirst("meta[property=og:image]")?.attr("content")
                    ?.let { normalizeUrl(it, url) }
                val epsWithImage = eps.map { ep ->
                    if (pageImage != null) {
                        try {
                            ep.posterUrl = pageImage

                        } catch (_: Exception) { /* ignore if property missing */
                        }
                    }
                    ep
                }
                val seriesTitle =
                    document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                        ?: "TV Series"
                val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
                val plot = document.selectFirst("div.singleStory")?.text()?.trim()
                log("Direct series page: title='$seriesTitle', episodes=${epsWithImage.size}, pageImage=${pageImage != null}")
                return newTvSeriesLoadResponse(seriesTitle, url, TvType.TvSeries, epsWithImage) {
                    this.posterUrl = poster
                    this.posterHeaders = imageHeaders
                    this.plot = plot
                }
            } else {
                log("Page is neither season nor episode nor episodes-list -> treating as movie page fallback")
                val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                    ?: return null
                val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
                val plot = document.selectFirst("div.singleStory")?.text()?.trim()
                val recommendations = parseRecommendations(document, url)
                log("Fallback movie: title='$title', recs=${recommendations.size}")
                return newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.posterHeaders = imageHeaders
                    this.plot = plot
                    this.recommendations = recommendations
                }
            }
        }

        if (startSeasonUrl == null) {
            log("ERROR: could not determine startSeasonUrl; aborting.")
            return null
        }

        log("Start season URL determined: $startSeasonUrl")

        val startDoc = try {
            httpGet(startSeasonUrl).also { log("Fetched startSeasonUrl doc (length=${it.html().length})") }
        } catch (e: Exception) {
            e.printStackTrace()
            log("ERROR: failed to fetch startSeasonUrl doc -> ${e.message}")
            return null
        }

        val seasonAnchors =
            startDoc.select("div.seasons-list li.movieItem a, div.seasons-list a, div.seasons-list ul li a")
        val candidateSeasonUrls = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (a in seasonAnchors) {
            val raw = a.attr("href")
            val href = normalizeUrl(raw, startSeasonUrl)
            if (href != null && href !in seen) {
                seen.add(href)
                candidateSeasonUrls.add(href)
            }
        }
        log("Season anchors found on start page = ${seasonAnchors.size}, unique candidate urls = ${candidateSeasonUrls.size}")

        if (candidateSeasonUrls.isNotEmpty()) {
            candidateSeasonUrls.reverse()
            log(
                "Reversed season links order. New order (first 5 shown): ${
                    candidateSeasonUrls.take(
                        5
                    )
                }"
            )
        }

        if (candidateSeasonUrls.isNotEmpty()) {
            log("Using season links from start page (will NOT extract episodes from start page).")
            val fetched = batchFetch(candidateSeasonUrls, concurrency = 8)
            val seasonsResults =
                mutableListOf<Pair<Int, List<CS3Episode>>>() // Pair(orderIndex, episodes)

            for ((idx, sUrl) in candidateSeasonUrls.withIndex()) {
                val doc = fetched[sUrl]
                val seasonTitle =
                    doc?.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                        ?: run {
                            log("Season $idx: no meta title in page, fallback to anchor text")
                            null
                        }
                val titleForSeason = seasonTitle ?: "Season ${idx + 1}"

                val rawImg = doc?.selectFirst("meta[property=og:image]")?.attr("content")
                val seasonImage = rawImg?.let { normalizeUrl(it, sUrl) }
                if (seasonImage != null) log("Season $idx image -> $seasonImage") else log("Season $idx image -> (none)")

                val eps = if (doc != null) extractEpisodesFromSeasonDoc(sUrl, doc) else emptyList()
                val seasonNumber = getSeasonNum(titleForSeason).takeIf { it != 9999 } ?: (idx + 1)

                val epsWithSeason = eps.mapIndexed { epIdx, ep ->
                    ep.season = seasonNumber
                    ep.episode = ep.episode ?: (epIdx + 1)
                    if (seasonImage != null) {
                        try {
                            ep.posterUrl = seasonImage
                        } catch (_: Exception) { /* ignore if property missing */
                        }
                    }
                    log("SeasonLink[order=${idx}] -> Season=$seasonNumber Episode=${ep.episode} Title='${ep.name}' poster=${seasonImage != null}")
                    ep
                }

                seasonsResults.add(Pair(idx, epsWithSeason))
                log("SeasonLink[order=${idx}] title='$titleForSeason' url=$sUrl -> extracted ${epsWithSeason.size} episodes")
            }

            val allEpisodes = seasonsResults.flatMap { it.second }
            log("Total episodes aggregated from season links = ${allEpisodes.size}")

            val seriesTitle =
                startDoc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                    ?.replace(Regex("""\s*(الموسم|الحلقة)\s+.*"""), "")?.trim() ?: "TV Series"
            val poster = startDoc.selectFirst("meta[property=og:image]")?.attr("content")
            val plot = startDoc.selectFirst("div.singleStory")?.text()?.trim()

            log("Returning newTvSeriesLoadResponse (from season links): title='$seriesTitle', seasons=${seasonsResults.size}")
            return newTvSeriesLoadResponse(
                seriesTitle,
                startSeasonUrl,
                TvType.TvSeries,
                allEpisodes
            ) {
                this.posterUrl = poster
                this.posterHeaders = imageHeaders
                this.plot = plot
            }
        }

        log("No explicit season links found on start page -> fallback to extracting episodes from start page")
        val fallbackEpisodes = extractEpisodesFromSeasonDoc(startSeasonUrl, startDoc)
        val seriesTitleFallback =
            startDoc.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "TV Series"
        val posterFallback = startDoc.selectFirst("meta[property=og:image]")?.attr("content")
            ?.let { normalizeUrl(it, startSeasonUrl) }
        val plotFallback = startDoc.selectFirst("div.singleStory")?.text()?.trim()
        log("Fallback: extracted ${fallbackEpisodes.size} episodes from start page, pageImage=${posterFallback != null}")

        val epsFixed = fallbackEpisodes.mapIndexed { idx, ep ->
            ep.season = ep.season ?: 1
            ep.episode = ep.episode ?: (idx + 1)
            if (posterFallback != null) {
                try {
                    ep.posterUrl = posterFallback
                } catch (_: Exception) { /* ignore */
                }
            }
            log("Fallback Episode -> Season=${ep.season} Episode=${ep.episode} Title='${ep.name}' poster=${posterFallback != null}")
            ep
        }

        log("Returning newTvSeriesLoadResponse (fallback page episodes): title='$seriesTitleFallback', episodes=${epsFixed.size}")
        return newTvSeriesLoadResponse(
            seriesTitleFallback,
            startSeasonUrl,
            TvType.TvSeries,
            epsFixed
        ) {
            this.posterUrl = posterFallback
            this.posterHeaders = imageHeaders
            this.plot = plotFallback
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val originalUrl = data
            val watchPageUrl = if (!data.contains("?view=watch")) "$data?view=watch" else data

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
                "Referer" to originalUrl
            )

            try {
                println("loadLinks: initial GET (priming) -> $originalUrl")
                httpGet(originalUrl, referer = originalUrl)
            } catch (e: Exception) {
                println("loadLinks: initial GET failed: ${e.message}")
            }

            val document = try {
                println("loadLinks: POST -> $watchPageUrl (View=1)")
                httpPost(watchPageUrl, data = mapOf("View" to "1"), referer = originalUrl)
            } catch (e: Exception) {
                println("loadLinks: POST failed (${e.message}), trying fallback GET on $watchPageUrl")
                try {
                    httpGet(watchPageUrl, referer = originalUrl)
                } catch (e2: Exception) {
                    println("loadLinks: fallback GET failed: ${e2.message}")
                    return false
                }
            }

            val allSeenLinks = java.util.Collections.synchronizedSet(mutableSetOf<String>())

            val candidates = mutableListOf<Pair<String?, String?>>()

            fun normalizeCandidate(raw: String?): String? {
                if (raw.isNullOrBlank()) return null
                return try {

                    normalizeUrl(raw, watchPageUrl)
                } catch (e: Exception) {
                    println("loadLinks: normalizeCandidate failed for $raw -> ${e.message}")
                    null
                }
            }

            val downloadSelectors = listOf(
                "ul.donwload-servers-list li",
                "ul.download-servers-list li",
                "ul.donwload-servers-list > li",
                "div.donwload-servers-list li"
            )
            for (sel in downloadSelectors) {
                val nodes = document.select(sel)
                if (nodes.isEmpty()) continue
                for (li in nodes) {
                    val serverName =
                        li.selectFirst("span.ser-name")?.text()?.trim() ?: li.selectFirst("p")
                            ?.text()?.trim()
                    val href = li.selectFirst("a.ser-link")?.attr("href")
                        ?: li.selectFirst("a")?.attr("href")
                        ?: li.attr("data-link")
                    val normalized = normalizeCandidate(href)
                    if (normalized != null) candidates += Pair(normalized, serverName)
                }
            }

            val watchSelectors = listOf(
                "ul.serversList li",
                "ul.servers-list li",
                "div.serversList li",
                "div.servers-list li"
            )
            for (sel in watchSelectors) {
                val nodes = document.select(sel)
                if (nodes.isEmpty()) continue
                for (li in nodes) {
                    val serverName = li.selectFirst("p")?.text()?.trim()
                        ?: li.selectFirst(".ser-name")?.text()?.trim()
                        ?: li.selectFirst("span.ser-name")?.text()?.trim()

                    val dataLink = li.attr("data-link").takeIf { it.isNotBlank() }
                    val childDataLink = li.selectFirst("[data-link]")?.attr("data-link")
                    val hrefFromA = li.selectFirst("a")?.attr("href")
                    val hrefFromBtn = li.selectFirst("button[data-link]")?.attr("data-link")

                    val candidate = dataLink ?: childDataLink ?: hrefFromBtn ?: hrefFromA
                    val normalized = normalizeCandidate(candidate)
                    if (normalized != null) candidates += Pair(normalized, serverName)
                }
            }

            for (el in document.select("[data-link]")) {
                val serverName =
                    el.attr("data-name").takeIf { it.isNotBlank() } ?: el.attr("data-provider")
                val dl = el.attr("data-link")
                val normalized = normalizeCandidate(dl)
                if (normalized != null) candidates += Pair(normalized, serverName)
            }

            for (a in document.select("a")) {
                val href = a.attr("href")
                if (href.contains("player") || href.contains("embed") || href.contains("download") ||
                    href.contains("drive") || href.contains("mp4")
                ) {
                    val serverName = a.attr("title").takeIf { it.isNotBlank() } ?: a.text()
                        .takeIf { it.isNotBlank() }
                    val normalized = normalizeCandidate(href)
                    if (normalized != null) candidates += Pair(normalized, serverName)
                }
            }

            println("loadLinks: collected candidate count = ${candidates.size}")
            println("===== السيرفرات التي تم العثور عليها =====")

            candidates.forEachIndexed { index, pair ->
                val link = pair.first ?: "null"
                val name = pair.second ?: "Unknown"
                println("${index + 1}- Server: $name")
                println("   Link: $link")
            }

            println("=========================================")


            val maxConcurrency = 8
            val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrency)

            suspend fun prepareAndSendParallel(linkRaw: String?, serverName: String?): Boolean {
                if (linkRaw.isNullOrBlank()) return false
                val normalized = try {
                    normalizeUrl(linkRaw, watchPageUrl) ?: return false
                } catch (e: Exception) {
                    println("loadLinks: normalizeUrl failed in prepareAndSendParallel: ${e.message}")
                    return false
                }

                if (!allSeenLinks.add(normalized)) {
                    println("loadLinks: skipping duplicate -> $normalized")
                    return false
                }

                println("loadLinks: calling loadExtractor for -> $normalized (serverName=$serverName)")
                try {

                    loadExtractor(normalized, data, subtitleCallback, callback)
                } catch (ex: Exception) {
                    println("loadLinks: loadExtractor failed for $normalized : ${ex.message}")
                }

                try {
                    if (serverName != null && (serverName.equals(
                            "EarnVids",
                            true
                        ) || serverName.equals("StreamHG", true))
                    ) {
                        println("loadLinks: trying ExternalEarnVidsExtractor for server=$serverName url=$normalized")
                        val customLink: String? = try {
                            withContext(Dispatchers.IO) {
                                ExternalEarnVidsExtractor.extract(normalized, this@EgyDead.mainUrl)
                            }
                        } catch (ee: Exception) {
                            println("loadLinks: ExternalEarnVidsExtractor threw: ${ee.message}")
                            null
                        }

                        if (!customLink.isNullOrBlank()) {
                            val finalLink = customLink.toString()
                            println("loadLinks: got custom link for $serverName -> $finalLink")
                            try {
                                callback.invoke(
                                    newExtractorLink(
                                        source = this@EgyDead.name,
                                        name = "${serverName} (Custom)",
                                        url = finalLink,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = this@EgyDead.mainUrl
                                    }
                                )
                            } catch (cbEx: Exception) {
                                println("loadLinks: callback invoke failed for custom link: ${cbEx.message}")
                            }
                        } else {
                            println("loadLinks: ExternalEarnVidsExtractor returned null/empty for $normalized")
                        }
                    }
                } catch (outer: Exception) {
                    println("loadLinks: error in custom-extract step: ${outer.message}")
                }

                return true
            }

            coroutineScope {
                candidates.map { pair ->
                    async {
                        semaphore.acquire()
                        try {
                            val link: String? = pair.first
                            val serverName: String? = pair.second
                            prepareAndSendParallel(link, serverName)
                        } finally {
                            semaphore.release()
                        }
                    }
                }.awaitAll()
            }


            println("loadLinks: finished, total unique links sent = ${allSeenLinks.size}")
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}