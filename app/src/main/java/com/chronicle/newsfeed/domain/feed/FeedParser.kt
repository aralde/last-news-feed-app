package com.chronicle.newsfeed.domain.feed

import android.util.Log
import android.util.Xml
import com.chronicle.newsfeed.data.local.entity.ArticleEntity
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class FeedParser {

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    )

    fun parse(xmlContent: String, sourceId: String, sourceName: String, category: String): List<ArticleEntity> {
        val articles = mutableListOf<ArticleEntity>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var isAtom = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name?.lowercase(Locale.ROOT)
                    if (tagName == "feed") {
                        isAtom = true
                    } else if (tagName == "item" && !isAtom) {
                        parseRssItem(parser, sourceId, sourceName, category)?.let { articles.add(it) }
                    } else if (tagName == "entry" && isAtom) {
                        parseAtomEntry(parser, sourceId, sourceName, category)?.let { articles.add(it) }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("FeedParser", "Error parsing feed XML for source: $sourceName", e)
        }
        return articles
    }

    private fun parseRssItem(parser: XmlPullParser, sourceId: String, sourceName: String, category: String): ArticleEntity? {
        var title = ""
        var link = ""
        var description = ""
        var content = ""
        var author = ""
        var pubDateStr = ""
        var imageUrl: String? = null

        while (!(parser.next() == XmlPullParser.END_TAG && parser.name?.equals("item", ignoreCase = true) == true)) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name?.lowercase(Locale.ROOT)) {
                "title" -> title = readText(parser)
                "link" -> link = readText(parser).trim()
                "description" -> {
                    description = readText(parser)
                    if (imageUrl == null) imageUrl = extractImageFromHtml(description)
                }
                "content:encoded", "content" -> {
                    content = readText(parser)
                    if (imageUrl == null) imageUrl = extractImageFromHtml(content)
                }
                "author", "dc:creator" -> author = readText(parser)
                "pubdate" -> pubDateStr = readText(parser)
                "enclosure" -> {
                    val type = parser.getAttributeValue(null, "type")
                    val url = parser.getAttributeValue(null, "url")
                    if (type?.startsWith("image") == true && !url.isNullOrBlank()) {
                        imageUrl = url
                    }
                    parser.next()
                }
                "media:content", "media:thumbnail" -> {
                    val medium = parser.getAttributeValue(null, "medium")
                    val url = parser.getAttributeValue(null, "url")
                    if ((medium == "image" || url?.matches(Regex(".*\\.(jpg|jpeg|png|webp).*")) == true) && !url.isNullOrBlank()) {
                        imageUrl = url
                    }
                    parser.next()
                }
                else -> skip(parser)
            }
        }

        if (title.isBlank() && link.isBlank()) return null
        val pubTimestamp = parseDate(pubDateStr)
        val cleanDesc = cleanHtml(description)
        val cleanContent = cleanHtml(content)
        val id = generateHash("$sourceId:${link.ifBlank { title }}")

        return ArticleEntity(
            id = id,
            sourceId = sourceId,
            sourceName = sourceName,
            title = cleanHtml(title),
            link = link,
            description = cleanDesc,
            content = cleanContent.ifBlank { cleanDesc },
            author = cleanHtml(author),
            pubDate = pubTimestamp,
            imageUrl = imageUrl,
            isRead = false,
            isFavorite = false,
            category = category
        )
    }

    private fun parseAtomEntry(parser: XmlPullParser, sourceId: String, sourceName: String, category: String): ArticleEntity? {
        var title = ""
        var link = ""
        var summary = ""
        var content = ""
        var author = ""
        var updatedStr = ""
        var imageUrl: String? = null

        while (!(parser.next() == XmlPullParser.END_TAG && parser.name?.equals("entry", ignoreCase = true) == true)) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name?.lowercase(Locale.ROOT)) {
                "title" -> title = readText(parser)
                "link" -> {
                    val rel = parser.getAttributeValue(null, "rel")
                    val href = parser.getAttributeValue(null, "href")
                    if ((rel == null || rel == "alternate") && !href.isNullOrBlank()) {
                        link = href
                    }
                    parser.next()
                }
                "summary" -> {
                    summary = readText(parser)
                    if (imageUrl == null) imageUrl = extractImageFromHtml(summary)
                }
                "content" -> {
                    content = readText(parser)
                    if (imageUrl == null) imageUrl = extractImageFromHtml(content)
                }
                "author" -> author = parseAtomAuthor(parser)
                "published", "updated" -> updatedStr = readText(parser)
                else -> skip(parser)
            }
        }

        if (title.isBlank() && link.isBlank()) return null
        val pubTimestamp = parseDate(updatedStr)
        val cleanDesc = cleanHtml(summary)
        val cleanContent = cleanHtml(content)
        val id = generateHash("$sourceId:${link.ifBlank { title }}")

        return ArticleEntity(
            id = id,
            sourceId = sourceId,
            sourceName = sourceName,
            title = cleanHtml(title),
            link = link,
            description = cleanDesc,
            content = cleanContent.ifBlank { cleanDesc },
            author = cleanHtml(author),
            pubDate = pubTimestamp,
            imageUrl = imageUrl,
            isRead = false,
            isFavorite = false,
            category = category
        )
    }

    private fun parseAtomAuthor(parser: XmlPullParser): String {
        var name = ""
        while (!(parser.next() == XmlPullParser.END_TAG && parser.name?.equals("author", ignoreCase = true) == true)) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name?.lowercase(Locale.ROOT) == "name") {
                name = readText(parser)
            } else if (parser.eventType == XmlPullParser.START_TAG) {
                skip(parser)
            }
        }
        return name
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val trimmed = dateStr.trim()
        for (format in dateFormats) {
            try {
                return format.parse(trimmed)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    private fun cleanHtml(html: String): String {
        if (html.isBlank()) return ""
        return try {
            Jsoup.parse(html).text().trim()
        } catch (_: Exception) {
            html.replace(Regex("<[^>]*>"), " ").trim()
        }
    }

    private fun extractImageFromHtml(html: String): String? {
        if (html.isBlank()) return null
        return try {
            val doc = Jsoup.parse(html)
            val img = doc.select("img").first()
            img?.attr("src")?.takeIf { it.startsWith("http") }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
