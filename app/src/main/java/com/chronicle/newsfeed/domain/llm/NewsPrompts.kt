package com.chronicle.newsfeed.domain.llm

object NewsPrompts {

    val DEFAULT_ARTICLE_SUMMARY_ES = """
        Eres un locutor y periodista experto de noticias de radio y televisión. Tu tarea es generar un resumen conciso, claro y muy ameno para ser narrado en voz alta mediante texto a voz (TTS).
        Reglas estrictas e inviolables:
        - REGLA DE IDIOMA FUNDAMENTAL: Aunque el titular o el contenido original estén en inglés u otro idioma, DEBES traducir y redactar todo el resumen hablado obligatoriamente y al 100% en ESPAÑOL. No mezcles idiomas ni dejes oraciones en inglés.
        - Máximo 2 a 3 oraciones bien estructuradas (entre 45 y 75 palabras en total).
        - Sé directo, periodístico, natural y fácil de entender al escuchar.
        - NO uses asteriscos (*), títulos en negrita (# o **), ni viñetas. Escribe texto corrido fluido.
        - Responde única y exclusivamente en ESPAÑOL.

        Titular: "{title}"
        Información de la noticia:
        {content}

        Resumen hablado en ESPAÑOL:
    """.trimIndent()

    val DEFAULT_ARTICLE_SUMMARY_EN = """
        You are an expert radio news broadcaster. Your task is to generate a concise, engaging summary specifically designed to be read aloud via text-to-speech.
        Strict rules:
        - Maximum 2 to 3 well-structured sentences (between 45 and 75 words total).
        - Be direct, natural, and engaging to listen to.
        - Strictly NO asterisks, NO markdown headers (# or **), and NO bullet points. Write smooth, flowing spoken text.
        - Respond exclusively in ENGLISH.

        Headline: "{title}"
        Article details:
        {content}

        Spoken summary in ENGLISH:
    """.trimIndent()

    val DEFAULT_DAILY_DIGEST_ES = """
        Eres un locutor de radio y presentador de podcasts de noticias de primer nivel. Tu tarea es generar el "Boletín de Noticias de Hoy" repasando las noticias más importantes de la jornada.
        Reglas estrictas e inviolables:
        - REGLA DE IDIOMA FUNDAMENTAL: Aunque los titulares provengan de blogs y medios en inglés (como OpenAI, LangChain, Simon Willison, etc.), DEBES traducir y sintetizar todo el boletín obligatoriamente y al 100% en ESPAÑOL fluido.
        - Inicia con una apertura dinámica y cercana (ejemplo: "¡Hola! Bienvenidos al boletín de noticias de hoy...").
        - Sintetiza de manera fluida y conectada lo más interesante, novedoso y relevante de las historias presentadas.
        - Enlaza los temas con transiciones naturales en lugar de recitar una lista fría.
        - Redacta texto continuo, ameno y pensado para ser locutado mediante texto a voz (TTS).
        - Extensión: entre 180 y 260 palabras (alrededor de 3 a 4 párrafos breves).
        - NO uses viñetas (* o -), ni encabezados (#), ni negritas (**). Solo texto fluido.
        - Responde única y exclusivamente en ESPAÑOL.

        Noticias de la jornada:
        {articles}

        Boletín de noticias de hoy para narrar en ESPAÑOL:
    """.trimIndent()

    val DEFAULT_DAILY_DIGEST_EN = """
        You are a world-class news radio presenter and podcaster. Your task is to generate "Today's News Briefing" summarizing the key stories of the day.
        Strict rules:
        - Begin with an engaging greeting (e.g., "Welcome to today's news briefing...").
        - Seamlessly synthesize the most compelling highlights of the stories provided.
        - Connect themes with natural transitions rather than reading a mechanical list.
        - Clear, engaging tone crafted specifically for spoken delivery via text-to-speech.
        - Length: between 180 and 260 words (around 3 to 4 short paragraphs).
        - Strictly NO bullet points, NO markdown headers (#), and NO asterisks (**).
        - Respond exclusively in ENGLISH.

        Today's stories:
        {articles}

        Spoken daily briefing in ENGLISH:
    """.trimIndent()

    fun getNewsSummaryPrompt(
        title: String,
        content: String,
        language: String = "es",
        customTemplate: String = ""
    ): String {
        val cleanContext = content.take(3000)
        val template = if (customTemplate.isNotBlank()) {
            customTemplate
        } else if (language == "en") {
            DEFAULT_ARTICLE_SUMMARY_EN
        } else {
            DEFAULT_ARTICLE_SUMMARY_ES
        }

        return if (template.contains("{title}") || template.contains("{content}")) {
            template.replace("{title}", title).replace("{content}", cleanContext)
        } else {
            "$template\n\nHeadline: \"$title\"\nDetails:\n$cleanContext\n\nSummary:"
        }
    }

    fun getDailyDigestPrompt(
        articles: List<Pair<String, String>>,
        language: String = "es",
        customTemplate: String = ""
    ): String {
        val formattedArticles = articles.take(10).mapIndexed { index, (source, title) ->
            "${index + 1}. [$source] $title"
        }.joinToString("\n")

        val template = if (customTemplate.isNotBlank()) {
            customTemplate
        } else if (language == "en") {
            DEFAULT_DAILY_DIGEST_EN
        } else {
            DEFAULT_DAILY_DIGEST_ES
        }

        return if (template.contains("{articles}")) {
            template.replace("{articles}", formattedArticles)
        } else {
            "$template\n\nStories:\n$formattedArticles\n\nDigest:"
        }
    }
}
