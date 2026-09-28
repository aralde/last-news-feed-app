package com.chronicle.newsfeed.domain.feed

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class ArticleWebExtractor(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    private val userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    /**
     * Attempts to fetch and extract the full body text of an article from its web URL.
     * Returns the extracted text if substantial content (> 200 chars) is found, or null on failure (for fallback).
     */
    suspend fun extractContent(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return@withContext null
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP error ${response.code} fetching article web content from: $url")
                return@withContext null
            }

            val html = response.body?.string()
            if (html.isNullOrBlank()) {
                return@withContext null
            }

            val document = Jsoup.parse(html, url)

            // Remove clutter, scripts, advertisements, navigation, comments, and sidebars
            document.select(
                "script, style, noscript, nav, header, footer, aside, form, svg, iframe, " +
                ".advertisement, .ad, .social-share, .comments, .related-posts, .sidebar, " +
                ".cookie-banner, .popup, .newsletter-signup, [role='banner'], [role='navigation'], [role='complementary']"
            ).remove()

            // Hierarchical search for the main article content container
            val contentSelectors = listOf(
                "article",
                "[itemprop='articleBody']",
                ".article-body",
                ".article__body",
                ".story-body",
                ".post-content",
                ".entry-content",
                ".article-content",
                ".content-article",
                ".c-detail__body",
                ".c-article__body",
                ".news-content",
                "main"
            )

            var bestContainer = contentSelectors
                .mapNotNull { selector -> document.selectFirst(selector) }
                .firstOrNull()

            if (bestContainer == null) {
                bestContainer = document.body()
            }

            // Extract text from paragraphs inside the container
            val paragraphs = bestContainer?.select("p")
                ?.map { it.text().trim() }
                ?.filter { it.length > 20 && !isNoise(it) }
                ?: emptyList()

            val fullText = if (paragraphs.isNotEmpty()) {
                paragraphs.joinToString("\n\n")
            } else {
                // Fallback: extract clean text of the container if p tags weren't used
                bestContainer?.text()?.trim() ?: ""
            }

            // Verify minimum length to ensure it's not just a cookie notice or empty page
            if (fullText.length >= 200) {
                Log.d(TAG, "Successfully extracted full web article (${fullText.length} chars) from: $url")
                return@withContext fullText
            } else {
                Log.d(TAG, "Extracted text was too short (${fullText.length} chars), activating fallback for: $url")
                return@withContext null
            }

        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract web content from $url: ${e.message}")
            return@withContext null
        }
    }

    private fun isNoise(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("cookie") ||
               lower.startsWith("suscríbete") ||
               lower.startsWith("subscribe") ||
               lower.contains("todos los derechos reservados") ||
               lower.contains("all rights reserved") ||
               lower.contains("términos y condiciones") ||
               lower.contains("política de privacidad")
    }

    companion object {
        private const val TAG = "ArticleWebExtractor"
    }
}
