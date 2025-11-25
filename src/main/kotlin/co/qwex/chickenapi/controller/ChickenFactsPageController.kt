package co.qwex.chickenapi.controller

import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.db.ChickenFactsSheetRepository
import mu.KotlinLogging
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

data class ChickenFactDisplay(
    val runId: String,
    val fact: String,
    val sourceUrl: String?,
    val completedAt: String,
    val durationSeconds: Long,
)

@Controller
@RequestMapping("/facts")
class ChickenFactsPageController(
    private val chickenFactsSheetRepository: ChickenFactsSheetRepository,
) {

    @GetMapping("", "/")
    fun facts(model: Model): String {
        log.debug { "Rendering chicken facts catalog page" }
        val facts = chickenFactsSheetRepository.fetchAllSuccessfulChickenFacts()
            .map { it.toDisplay() }
        model.addAttribute("facts", facts)
        return "facts"
    }

    private fun ChickenFactsRecord.toDisplay(): ChickenFactDisplay {
        val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")
            .withZone(ZoneId.systemDefault())
        return ChickenFactDisplay(
            runId = runId,
            fact = fact ?: "",
            sourceUrl = sourceUrl,
            completedAt = formatter.format(completedAt),
            durationSeconds = durationMillis / 1000,
        )
    }
}
