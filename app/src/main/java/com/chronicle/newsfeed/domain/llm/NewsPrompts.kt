package com.chronicle.newsfeed.domain.llm

object NewsPrompts {

    val DEFAULT_ARTICLE_SUMMARY_ES = """
        Eres un locutor y periodista experto de noticias de radio y televisión. Tu tarea es generar una crónica o resumen periodístico completo, profundo y riguroso pero a la vez muy ameno para ser locutado mediante texto a voz (TTS).
        Reglas estrictas e inviolables:
        - REGLA DE IDIOMA FUNDAMENTAL: Aunque el titular o el contenido original estén en inglés u otro idioma, DEBES traducir, adaptar y redactar todo obligatoriamente y al 100% en ESPAÑOL. No dejes palabras ni oraciones en inglés.
        - ESTRUCTURA DE RESPUESTA OBLIGATORIA (exactamente 2 líneas):
          Línea 1: Titular traducido y adaptado al ESPAÑOL (un titular claro, directo, periodístico y natural de locutar en voz alta, sin etiquetas como 'Titular:').
          Línea 2: Crónica o resumen periodístico completo, fluido y profundo en ESPAÑOL de entre 100 y 160 palabras explicando los hechos clave, contexto de fondo y repercusiones.
        - Tono: Periodístico, analítico, claro, natural y cautivador para el oyente.
        - Estilo TTS: NO uses asteriscos (*), títulos ni encabezados Markdown (# o **), ni viñetas. Solo texto limpio para voz.
        - Responde única y exclusivamente en ESPAÑOL.

        Titular original: "{title}"
        Información de la noticia:
        {content}

        Respuesta en ESPAÑOL (Línea 1: titular traducido, Línea 2: resumen):
    """.trimIndent()

    val DEFAULT_ARTICLE_SUMMARY_EN = """
        You are an expert news broadcaster and investigative journalist. Your task is to produce a thorough, insightful, and engaging journalistic summary crafted specifically to be read aloud via text-to-speech (TTS).
        Strict rules:
        - EXACT RESPONSE STRUCTURE (2 lines):
          Line 1: Headline translated and adapted into natural spoken ENGLISH (concise, clear, without labels like 'Headline:').
          Line 2: A thorough, smooth, in-depth journalistic spoken summary in ENGLISH (100 to 160 words) explaining key events, essential context, and implications.
        - Tone: Professional, journalistic, authoritative, yet natural and captivating to listen to.
        - Spoken delivery: Strictly NO asterisks, NO markdown headers (# or **), and NO bullet points. Write smooth, fluid spoken prose without formatting symbols.
        - Respond exclusively in ENGLISH.

        Original headline: "{title}"
        Article details:
        {content}

        Response in ENGLISH (Line 1: translated headline, Line 2: summary):
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

    fun getCustomDigestPrompt(
        articles: List<Pair<String, String>>,
        language: String = "es",
        customTemplate: String = ""
    ): String {
        val formattedArticles = articles.mapIndexed { index, (source, title) ->
            "${index + 1}. [$source] $title"
        }.joinToString("\n")

        val baseTemplate = if (language == "en") {
            """
            You are an expert news presenter. Generate a dedicated "Custom News Bulletin" synthesizing the following hand-picked stories into a cohesive, fluid broadcast.
            Strict rules:
            - Start with a dynamic greeting (e.g., "Welcome to your custom news briefing...").
            - Seamlessly connect the selected stories using natural journalistic transitions.
            - Provide clear insights, significance, and context for each story.
            - Spoken audio style: Strictly NO asterisks, NO markdown headers, and NO bullet points.
            - Respond exclusively in ENGLISH.

            Selected stories:
            {articles}

            Custom spoken bulletin in ENGLISH:
            """.trimIndent()
        } else {
            """
            Eres un locutor de radio y presentador de noticias de primer nivel. Tu tarea es generar un "Boletín de Noticias Personalizado" que sintetice de forma conectada, amena y profunda las noticias seleccionadas a continuación.
            Reglas estrictas e inviolables:
            - REGLA DE IDIOMA FUNDAMENTAL: Todo el boletín debe redactarse obligatoriamente y al 100% en ESPAÑOL fluido, traduciendo los títulos que vengan en inglés.
            - Inicia con una apertura dinámica y cercana (ejemplo: "¡Hola! Bienvenidos a vuestro boletín personalizado con las historias seleccionadas...").
            - Sintetiza cada una de las noticias elegidas enlazándolas con transiciones periodísticas naturales y coherentes.
            - Destaca lo más relevante, el contexto y sus implicaciones.
            - Texto continuo pensado para voz alta (TTS): NO uses viñetas (* o -), ni encabezados (#), ni negritas (**).
            - Responde única y exclusivamente en ESPAÑOL.

            Noticias seleccionadas:
            {articles}

            Boletín personalizado para narrar en ESPAÑOL:
            """.trimIndent()
        }

        val template = if (customTemplate.isNotBlank()) customTemplate else baseTemplate
        return if (template.contains("{articles}")) {
            template.replace("{articles}", formattedArticles)
        } else {
            "$template\n\nStories:\n$formattedArticles\n\nBulletin:"
        }
    }
}

