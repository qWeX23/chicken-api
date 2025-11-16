package co.qwex.chickenapi.controller

import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.db.ChickenFactsSheetRepository
import mu.KotlinLogging
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

private val log = KotlinLogging.logger {}
private val markdownLinkRegex = Regex("\\[([^\\]]+?)\\]\\((https?://[^)]+)\\)")
private val urlRegex = Regex("https?://\\S+")
private val sourceMarkerRegex = Regex("(?i)(?:\\*+|_+)?sources?[:\\-]")
private val fallbackDailyFact = DailyFact(
    fact = "A hen's earlobe color often predicts the shade of the eggs she lays.",
    sourceUrl = "https://www.britannica.com/animal/chicken",
)

data class DailyFact(val fact: String, val sourceUrl: String? = null)

@Controller
class LandingController(
    private val chickenFactsSheetRepository: ChickenFactsSheetRepository,
) {
    @GetMapping("/")
    fun landing(model: Model): String {
        log.debug { "Rendering landing page" }
        val latestFact = chickenFactsSheetRepository.fetchLatestChickenFact()
            ?.let(::toDailyFact)
            ?: fallbackDailyFact

        model.addAttribute("dailyFact", latestFact)
        return "index"
    }

    @GetMapping("/about")
    fun about(): String {
        log.debug { "Rendering about page" }
        return "about"
    }

    private fun toDailyFact(record: ChickenFactsRecord): DailyFact? {
        val markdown = record.factsMarkdown?.trim() ?: return null
        val firstLine = markdown.lineSequence().firstOrNull { it.isNotBlank() } ?: return null

        val sourceUrl = markdownLinkRegex.find(markdown)?.groupValues?.getOrNull(2)
            ?: urlRegex.find(markdown)?.value

        val cleanedLine = markdownLinkRegex.replace(firstLine) { it.groupValues[1] }
        val factText = cleanedLine
            .replace(Regex("^[-*]\\s+"), "")
            .replace("**", "")
            .replace("__", "")
            .trim()

        val sanitizedFact = factText.removeSourceCitation()
        return sanitizedFact.takeIf { it.isNotBlank() }?.let { DailyFact(fact = it, sourceUrl = sourceUrl) }
    }
}

private fun String.removeSourceCitation(): String {
    val match = sourceMarkerRegex.find(this)
    return if (match != null) {
        substring(0, match.range.first).trim()
    } else {
        this
    }
}
