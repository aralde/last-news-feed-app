package com.chronicle.newsfeed.domain.feed

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit

data class DiscoveredFeed(
    val title: String,
    val siteUrl: String,
    val feedUrl: String
)

class FeedDiscoveryService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    suspend fun discoverFeed(urlInput: String): Result<DiscoveredFeed> = withContext(Dispatchers.IO) {
        try {
            var url = urlInput.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ChronicleNewsReader/1.0 (Android; RSS Reader)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}"))
            }

            val contentType = response.header("Content-Type", "")?.lowercase() ?: ""
            val body = response.body?.string() ?: ""

            // 1. Direct XML feed check
            if (contentType.contains("xml") || contentType.contains("rss") || contentType.contains("atom") ||
                body.contains("<rss") || body.contains("<feed")
            ) {
                val doc = Jsoup.parse(body)
                val title = doc.select("title").first()?.text() ?: "Feed RSS"
                return@withContext Result.success(
                    DiscoveredFeed(
                        title = title,
                        siteUrl = url,
                        feedUrl = url
                    )
                )
            }

            // 2. HTML Auto-discovery
            val doc = Jsoup.parse(body, url)
            val pageTitle = doc.title().ifBlank { URI(url).host }

            // Look for <link rel="alternate" type="application/rss+xml"> or <atom+xml>
            val feedLinks = doc.select("link[rel~=alternate][type*=rss], link[rel~=alternate][type*=atom], link[rel~=alternate][type*=xml]")
            val firstLink = feedLinks.first()

            if (firstLink != null) {
                val absoluteHref = firstLink.attr("abs:href")
                if (absoluteHref.isNotBlank()) {
                    val feedTitle = firstLink.attr("title").ifBlank { pageTitle }
                    return@withContext Result.success(
                        DiscoveredFeed(
                            title = feedTitle,
                            siteUrl = url,
                            feedUrl = absoluteHref
                        )
                    )
                }
            }

            // 3. Fallback common feed paths
            val commonPaths = listOf("/feed", "/rss", "/rss.xml", "/atom.xml", "/feed.xml", "/index.xml")
            val baseUri = URI(url)
            val origin = "${baseUri.scheme}://${baseUri.host}"

            for (path in commonPaths) {
                val candidate = "$origin$path"
                try {
                    val probeReq = Request.Builder().url(candidate).head().build()
                    val probeRes = client.newCall(probeReq).execute()
                    if (probeRes.isSuccessful) {
                        return@withContext Result.success(
                            DiscoveredFeed(
                                title = pageTitle,
                                siteUrl = url,
                                feedUrl = candidate
                            )
                        )
                    }
                } catch (_: Exception) {}
            }

            Result.failure(Exception("No se encontró ningún feed RSS o Atom en la dirección proporcionada."))
        } catch (e: Exception) {
            Log.e("FeedDiscovery", "Error discovering feed for $urlInput", e)
            Result.failure(e)
        }
    }
}
