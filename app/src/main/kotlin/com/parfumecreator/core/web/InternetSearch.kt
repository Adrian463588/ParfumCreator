package com.parfumecreator.core.web

import android.net.Uri
import android.text.Html
import android.util.Log
import com.google.gson.JsonParser
import com.parfumecreator.core.llm.PerfumeEvidenceSelector
import com.parfumecreator.domain.model.WebSearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InternetSearch"

/**
 * Removes data that does not belong in an outbound knowledge query.
 * Uploaded document text and conversation history never enter this boundary.
 */
object InternetQuerySanitizer {
    private const val MAX_QUERY_CHARS = 240
    private val urlPattern = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val emailPattern = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val phonePattern = Regex("(?<!\\d)(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\d)")
    private val instructionPattern = Regex(
        "(?i)\\b(?:ignore|abaikan|disregard|override|bypass)\\b.{0,100}\\b(?:instruction|aturan|system|prompt|policy|kebijakan)\\b"
    )
    private val credentialPattern = Regex(
        "(?i)\\b(?:api[_-]?key|access[_-]?token|token|password|authorization|bearer)\\s*[:=]?\\s*[^\\s,;]+"
    )

    fun sanitize(query: String): String = query
        .replace('\u0000', ' ')
        .replace(urlPattern, " ")
        .replace(emailPattern, " ")
        .replace(phonePattern, " ")
        .replace(credentialPattern, " ")
        .replace(instructionPattern, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_QUERY_CHARS)

    /** Search content words rather than treating a conversational sentence as an AND query. */
    fun searchTerms(query: String): String = PerfumeEvidenceSelector.queryTokens(sanitize(query))
        .take(12).joinToString(" ")
}

/** Public pages that can be searched/fetched by the no-key companion fallback. */
object CuratedWebSourceRegistry {
    data class Source(
        val title: String,
        val url: String,
        val description: String,
        val keywords: Set<String>
    )

    val sources: List<Source> = listOf(
        Source(
            title = "IFRA Standards — Safe Use of Fragrance Science",
            url = "https://ifrafragrance.org/initiatives-positions/safe-use-fragrance-science/ifra-standards",
            description = "Sumber resmi tentang standar dan penggunaan aman fragrance.",
            keywords = setOf("ifra", "standar", "standards", "safety", "regulasi")
        ),
        Source(
            title = "FDA — Fragrances in Cosmetics",
            url = "https://www.fda.gov/cosmetics/cosmetic-ingredients/fragrances-cosmetics",
            description = "Sumber resmi FDA tentang fragrance dan kosmetik.",
            keywords = setOf("fda", "kosmetik", "cosmetic", "alergi", "allergy", "safety")
        ),
        Source(
            title = "EU CosIng — Cosmetic Ingredients",
            url = "https://single-market-economy.ec.europa.eu/sectors/cosmetics/cosmetic-ingredient-database_en",
            description = "Database informasi bahan kosmetik Uni Eropa.",
            keywords = setOf("eu", "cosing", "kosmetik", "regulasi")
        )
    )

    fun matching(query: String): List<Source> {
        val queryTokens = Regex("[\\p{L}\\p{N}]+")
            .findAll(query.lowercase(Locale.ROOT))
            .map { it.value }
            .toSet()
        return sources.filter { source -> source.keywords.any(queryTokens::contains) }
    }
}

/**
 * Bounded public internet adapter for the companion fallback.
 *
 * Search uses the official MediaWiki Action API and a small registry of
 * official fragrance/cosmetic pages. Page fetching is HTTPS-only and limited
 * to the same allowlist; redirects and arbitrary user URLs are rejected.
 */
@Singleton
class WikimediaInternetClient @Inject constructor() {
    private val requestMutex = Mutex()
    private val searchCache = object : LinkedHashMap<String, List<WebSearchHit>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<WebSearchHit>>?): Boolean =
            size > CACHE_SIZE
    }
    private val pageCache = object : LinkedHashMap<String, WebSearchHit>(PAGE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WebSearchHit>?): Boolean =
            size > PAGE_CACHE_SIZE
    }
    private var lastRequestAt = 0L

    suspend fun search(query: String): Result<List<WebSearchHit>> = withContext(Dispatchers.IO) {
        val sanitized = InternetQuerySanitizer.sanitize(query)
        if (sanitized.isBlank()) return@withContext Result.success(emptyList())

        requestMutex.withLock {
            synchronized(searchCache) {
                searchCache[sanitized]?.let { cached ->
                    if (cached.isNotEmpty() && cached.all { isFresh(it.retrievedAt, SEARCH_CACHE_TTL_MS) }) {
                        return@withLock Result.success(cached)
                    }
                    searchCache.remove(sanitized)
                }
            }

            throttle()
            val terms = InternetQuerySanitizer.searchTerms(sanitized).ifBlank { sanitized }
            val outcomes = listOf("id.wikipedia.org", "en.wikipedia.org").map { host ->
                currentCoroutineContext().ensureActive()
                val domainTerm = if (host.startsWith("id.")) "parfum" else "perfume"
                runCatching { searchHost(host, "$terms $domainTerm") }.also { outcome ->
                    outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                }
            }
            val wikiHits = outcomes.flatMap { it.getOrElse { emptyList() } }
                .distinctBy { it.url }
                .sortedByDescending { PerfumeEvidenceSelector.lexicalScore(
                    PerfumeEvidenceSelector.queryTokens(sanitized), it.title + " " + it.snippet
                ) }
                .take(MAX_RESULTS)
            val curatedHits = CuratedWebSourceRegistry.matching(sanitized).map { source ->
                WebSearchHit(
                    title = source.title,
                    snippet = source.description,
                    url = source.url,
                    sourceName = "Internet • Official",
                    retrievedAt = System.currentTimeMillis()
                )
            }
            // Official matches must not be discarded behind the first three
            // discovery snippets when the use case applies its page budget.
            val hits = (curatedHits + wikiHits)
                .distinctBy { it.url }
                .take(MAX_RESULTS + MAX_CURATED_RESULTS)

            if (hits.isEmpty() && outcomes.all { it.isFailure }) {
                val error = outcomes.firstNotNullOfOrNull { it.exceptionOrNull() }
                    ?: IllegalStateException("Internet search failed")
                Log.w(TAG, "Internet knowledge lookup failed", error)
                return@withLock Result.failure(error)
            }

            synchronized(searchCache) { searchCache[sanitized] = hits }
            Result.success(hits)
        }
    }

    suspend fun fetchPage(url: String): Result<WebSearchHit> = withContext(Dispatchers.IO) {
        val canonicalUrl = canonicalizeAllowedUrl(url)
            ?: return@withContext Result.failure(IllegalArgumentException("Sumber web tidak diizinkan"))

        requestMutex.withLock {
            synchronized(pageCache) {
                pageCache[canonicalUrl]?.let { cached ->
                    if (isFresh(cached.retrievedAt, PAGE_CACHE_TTL_MS)) {
                        return@withLock Result.success(cached)
                    }
                    pageCache.remove(canonicalUrl)
                }
            }
            throttle()
            currentCoroutineContext().ensureActive()
            runCatching {
                if (URI(canonicalUrl).host in WIKI_HOSTS) {
                    return@runCatching fetchWikiArticle(canonicalUrl)
                }
                val connection = (URL(canonicalUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    useCaches = true
                    setRequestProperty("Accept", "text/html,application/xhtml+xml")
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                try {
                    val status = connection.responseCode
                    require(status in 200..299) { "Web page HTTP status $status" }
                    val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
                    require(contentType.isBlank() || contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                        "Web page content type is not HTML"
                    }
                    val html = connection.inputStream.use { input -> readBounded(input, MAX_PAGE_BYTES) }
                    val text = cleanPageText(html)
                    require(text.isNotBlank()) { "Web page contains no readable text" }
                    val title = extractHtmlTitle(html).ifBlank { Uri.parse(canonicalUrl).lastPathSegment.orEmpty() }
                    WebSearchHit(
                        title = title.take(MAX_TITLE_CHARS),
                        snippet = text.take(MAX_SNIPPET_CHARS),
                        url = canonicalUrl,
                        sourceName = sourceNameFor(canonicalUrl),
                        retrievedAt = System.currentTimeMillis(),
                        pageText = text,
                        contentHash = sha256(text)
                    )
                } finally {
                    connection.disconnect()
                }
            }.onSuccess { page ->
                synchronized(pageCache) { pageCache[canonicalUrl] = page }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Allowlisted web page fetch failed", error)
            }
        }
    }

    fun isAllowedPublicUrl(url: String): Boolean = canonicalizeAllowedUrl(url) != null

    private fun searchHost(host: String, query: String): List<WebSearchHit> {
        val encodedQuery = Uri.encode(query)
        val endpoint = "https://$host/w/api.php?action=query&format=json&list=search&srsearch=$encodedQuery&srlimit=$MAX_RESULTS&utf8=1"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            useCaches = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Api-User-Agent", USER_AGENT)
        }

        return try {
            val status = connection.responseCode
            if (status !in 200..299) error("MediaWiki HTTP status $status")
            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            require(contentType.isBlank() || contentType.contains("json")) { "MediaWiki response is not JSON" }
            val response = connection.inputStream.use { input -> readBounded(input, MAX_RESPONSE_BYTES) }
            parseSearchResponse(response, host)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSearchResponse(response: String, host: String): List<WebSearchHit> {
        val root = JsonParser.parseString(response).asJsonObject
        require(!root.has("error")) { "MediaWiki returned an API error" }
        val actionResults = root.getAsJsonObject("query")?.getAsJsonArray("search")
        val restResults = root.getAsJsonArray("pages")
        val records = actionResults ?: restResults ?: return emptyList()

        return buildList {
            for (record in records) {
                val page = record.asJsonObject
                val key = page.optString("title").ifBlank { page.optString("key") }
                val title = key.take(MAX_TITLE_CHARS)
                val snippet = cleanSnippet(
                    page.optString("snippet")
                        .ifBlank { page.optString("excerpt") }
                        .ifBlank { page.optString("description") }
                ).take(MAX_SNIPPET_CHARS)
                if (title.isBlank() || snippet.isBlank()) continue
                add(
                    WebSearchHit(
                        title = title,
                        snippet = snippet,
                        url = pageUrl(host, key),
                        sourceName = "Internet • Wikipedia",
                        retrievedAt = System.currentTimeMillis()
                    )
                )
                if (size >= MAX_RESULTS) break
            }
        }
    }

    private fun canonicalizeAllowedUrl(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (
            scheme != "https" ||
            host !in ALLOWED_HOSTS ||
            (uri.port != -1 && uri.port != 443) ||
            uri.rawUserInfo != null
        ) return null
        return buildString {
            append("https://").append(host)
            uri.rawPath?.takeIf { it.isNotBlank() }?.let(::append)
            uri.rawQuery?.takeIf { it.isNotBlank() }?.let { append('?').append(it) }
        }
    }

    private fun sourceNameFor(url: String): String {
        val host = Uri.parse(url).host.orEmpty()
        return if (host.contains("wikipedia.org")) "Internet • Wikipedia" else "Internet • Official"
    }

    private fun cleanSnippet(value: String): String = Html.fromHtml(
        value,
        Html.FROM_HTML_MODE_LEGACY
    ).toString()
        .replace(
            Regex("(?i)\\b(ignore|disregard|override|follow)\\b.{0,100}\\b(instruction|prompt|system|rule)\\b"),
            "[web instruction removed]"
        )
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun cleanPageText(html: String): String = Html.fromHtml(
        html
            .replace(Regex("(?is)<(script|style|noscript|svg|nav|header|footer)[^>]*>.*?</\\1>"), " "),
        Html.FROM_HTML_MODE_LEGACY
    ).toString()
        .let(::cleanSnippet)
        .take(MAX_PAGE_TEXT_CHARS)

    private fun extractHtmlTitle(html: String): String = Regex(
        "(?is)<title[^>]*>(.*?)</title>"
    ).find(html)?.groupValues?.getOrNull(1)?.let(::cleanSnippet).orEmpty()

    private fun pageUrl(host: String, key: String): String =
        "https://$host/wiki/${Uri.encode(key.replace(' ', '_'))}"

    /** Action=parse returns article content without the site's navigation layout. */
    private fun fetchWikiArticle(canonicalUrl: String): WebSearchHit {
        val uri = URI(canonicalUrl)
        require(uri.path.startsWith("/wiki/")) { "Wikipedia source must identify an article" }
        val title = uri.path.removePrefix("/wiki/").replace('_', ' ')
        val endpoint = "https://${uri.host}/w/api.php?action=parse&format=json&formatversion=2" +
            "&prop=text&disableeditsection=1&redirects=1&page=${Uri.encode(title)}"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Api-User-Agent", USER_AGENT)
        }
        return try {
            require(connection.responseCode in 200..299) { "MediaWiki article HTTP status ${connection.responseCode}" }
            require(connection.contentType.orEmpty().contains("json", ignoreCase = true)) { "MediaWiki article is not JSON" }
            val response = connection.inputStream.use { readBounded(it, MAX_PAGE_BYTES) }
            val root = JsonParser.parseString(response).asJsonObject
            require(!root.has("error")) { "MediaWiki article returned an API error" }
            val page = root.getAsJsonObject("parse") ?: error("MediaWiki response contains no article")
            val text = cleanPageText(page.optString("text"))
            require(text.isNotBlank()) { "Wikipedia article has no readable content" }
            val resolvedTitle = page.optString("title").ifBlank { title }
            WebSearchHit(
                title = resolvedTitle.take(MAX_TITLE_CHARS), snippet = text.take(MAX_SNIPPET_CHARS),
                url = pageUrl(uri.host, resolvedTitle), sourceName = "Internet • Wikipedia",
                retrievedAt = System.currentTimeMillis(), pageText = text, contentHash = sha256(text)
            )
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun throttle() {
        val waitMs = MIN_REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt)
        if (waitMs > 0L) delay(waitMs)
        lastRequestAt = System.currentTimeMillis()
    }

    private fun isFresh(retrievedAt: Long, ttlMs: Long): Boolean =
        retrievedAt > 0L && System.currentTimeMillis() - retrievedAt in 0..ttlMs

    private fun readBounded(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) error("Internet response exceeds safety limit")
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun com.google.gson.JsonObject.optString(name: String): String =
        get(name)?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()

    private companion object {
        val WIKI_HOSTS = setOf("id.wikipedia.org", "en.wikipedia.org")
        val ALLOWED_HOSTS = setOf(
            "id.wikipedia.org",
            "en.wikipedia.org",
            "ifrafragrance.org",
            "www.ifrafragrance.org",
            "fda.gov",
            "www.fda.gov",
            "single-market-economy.ec.europa.eu",
            "ec.europa.eu"
        )
        const val CACHE_SIZE = 16
        const val PAGE_CACHE_SIZE = 16
        const val MAX_RESULTS = 3
        const val MAX_CURATED_RESULTS = 3
        const val MAX_TITLE_CHARS = 140
        const val MAX_SNIPPET_CHARS = 600
        const val MAX_PAGE_TEXT_CHARS = 100_000
        const val MAX_RESPONSE_BYTES = 256 * 1024
        const val MAX_PAGE_BYTES = 512 * 1024
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 8_000
        const val MIN_REQUEST_INTERVAL_MS = 1_500L
        const val SEARCH_CACHE_TTL_MS = 24L * 60L * 60L * 1_000L
        const val PAGE_CACHE_TTL_MS = 24L * 60L * 60L * 1_000L
        const val USER_AGENT = "ParfumeCreator/1.1 (https://github.com/Adrian463588/ParfumCreator)"
    }
}
