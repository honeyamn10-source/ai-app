package ai.byak.app.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class PublicResearchSource(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
)

/** Keyless, read-only discovery for evidence supplied to autonomous research. */
@Singleton
class PublicResearchRepository @Inject constructor(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun search(goal: String, includeCode: Boolean): List<PublicResearchSource> = supervisorScope {
        val query = goal.replace(Regex("\\s+"), " ").trim().take(MAX_QUERY)
        if (query.isBlank()) return@supervisorScope emptyList()
        val github = async {
            if (!includeCode) emptyList() else runCatching { searchGitHub(query) }.getOrDefault(emptyList())
        }
        val wikipedia = async { runCatching { searchWikipedia(query) }.getOrDefault(emptyList()) }
        (github.await() + wikipedia.await()).distinctBy { it.url }.take(MAX_RESULTS)
    }

    fun evidenceLedger(sources: List<PublicResearchSource>): String = if (sources.isEmpty()) {
        "No public search result was available. State that limitation and do not invent citations."
    } else {
        sources.mapIndexed { index, source ->
            "[${index + 1}] ${source.title} (${source.source})\nURL: ${source.url}\nSummary: ${source.summary}"
        }.joinToString("\n\n")
    }

    private suspend fun searchGitHub(query: String): List<PublicResearchSource> {
        val response = client.get(
            "https://api.github.com/search/repositories?q=${encode(query)}&sort=stars&order=desc&per_page=4",
        ) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "BYAK-AI-Android")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) return emptyList()
        val root = json.parseToJsonElement(response.bodyAsText().take(MAX_BODY)) as? JsonObject ?: return emptyList()
        return (root["items"] as? JsonArray).orEmpty().mapNotNull { item ->
            val value = item as? JsonObject ?: return@mapNotNull null
            val title = value.string("full_name") ?: return@mapNotNull null
            val url = value.string("html_url") ?: return@mapNotNull null
            val stars = value.int("stargazers_count")
            val description = value.string("description").orEmpty().cleanText()
            PublicResearchSource(
                title = title,
                summary = listOfNotNull(description.takeIf(String::isNotBlank), stars?.let { "$it GitHub stars" })
                    .joinToString(" · ").take(MAX_SUMMARY),
                url = url,
                source = "GitHub",
            )
        }
    }

    private suspend fun searchWikipedia(query: String): List<PublicResearchSource> {
        val response = client.get(
            "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encode(query)}&srlimit=4&format=json&utf8=1",
        ) { header(HttpHeaders.UserAgent, "BYAK-AI-Android/2.4") }
        if (!response.status.isSuccess()) return emptyList()
        val root = json.parseToJsonElement(response.bodyAsText().take(MAX_BODY)) as? JsonObject ?: return emptyList()
        val queryObject = root["query"] as? JsonObject ?: return emptyList()
        return (queryObject["search"] as? JsonArray).orEmpty().mapNotNull { item ->
            val value = item as? JsonObject ?: return@mapNotNull null
            val title = value.string("title") ?: return@mapNotNull null
            PublicResearchSource(
                title = title,
                summary = value.string("snippet").orEmpty().cleanText().take(MAX_SUMMARY),
                url = "https://en.wikipedia.org/wiki/${encode(title).replace("+", "%20")}",
                source = "Wikipedia",
            )
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private companion object {
        const val MAX_QUERY = 240
        const val MAX_BODY = 1_500_000
        const val MAX_RESULTS = 8
        const val MAX_SUMMARY = 500
    }
}

private fun JsonObject.string(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

private fun JsonObject.int(key: String): Int? =
    runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

private fun String.cleanText(): String = replace(Regex("<[^>]+>"), " ")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
    .replace(Regex("\\s+"), " ")
    .trim()
