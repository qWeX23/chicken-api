package co.qwex.chickenapi.ai

import co.qwex.chickenapi.repository.ChickenFactsRepository
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.math.sqrt

interface ChickenFactDuplicateCheckService {
    suspend fun checkFactForDuplicate(fact: String): FactDuplicateCheckResult
}

@Serializable
data class SimilarFactMatch(
    val runId: String,
    val fact: String,
    val sourceUrl: String?,
    val similarity: Double,
)

@Serializable
data class FactDuplicateCheckResult(
    val hasHit: Boolean,
    val threshold: Double,
    val topSimilarity: Double?,
    val matches: List<SimilarFactMatch>,
)

@Service
class ChickenFactDuplicateChecker(
    private val embeddingService: OllamaEmbeddingService,
    private val chickenFactsRepository: ChickenFactsRepository,
    @Value("\${koog.agent.fact-dedup-threshold:0.88}")
    private val similarityThreshold: Double,
) : ChickenFactDuplicateCheckService {
    private val log = KotlinLogging.logger {}

    override suspend fun checkFactForDuplicate(fact: String): FactDuplicateCheckResult {
        if (!embeddingService.isReady()) {
            log.error { "Fact duplicate check failed because embedding service is unavailable." }
            throw IllegalStateException("Embedding service unavailable for duplicate check")
        }

        val candidateEmbedding = embeddingService.embedFact(fact.trim())
        if (candidateEmbedding.isNullOrEmpty()) {
            log.error { "Fact duplicate check failed because candidate embedding could not be created." }
            throw IllegalStateException("Unable to create embedding for candidate fact")
        }

        val matches = chickenFactsRepository.fetchAllSuccessfulChickenFacts()
            .asSequence()
            .mapNotNull { record ->
                val existingFact = record.fact?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val existingEmbedding = record.factEmbedding ?: return@mapNotNull null
                val similarity = cosineSimilarity(candidateEmbedding, existingEmbedding) ?: return@mapNotNull null
                SimilarFactMatch(
                    runId = record.runId,
                    fact = existingFact,
                    sourceUrl = record.sourceUrl,
                    similarity = similarity,
                )
            }
            .filter { it.similarity >= similarityThreshold }
            .sortedByDescending { it.similarity }
            .toList()

        return FactDuplicateCheckResult(
            hasHit = matches.isNotEmpty(),
            threshold = similarityThreshold,
            topSimilarity = matches.firstOrNull()?.similarity,
            matches = matches,
        )
    }

    private fun cosineSimilarity(
        left: List<Double>,
        right: List<Double>,
    ): Double? {
        if (left.isEmpty() || right.isEmpty() || left.size != right.size) {
            return null
        }

        var dotProduct = 0.0
        var leftNormSquared = 0.0
        var rightNormSquared = 0.0

        left.indices.forEach { index ->
            val leftValue = left[index]
            val rightValue = right[index]
            dotProduct += leftValue * rightValue
            leftNormSquared += leftValue * leftValue
            rightNormSquared += rightValue * rightValue
        }

        val denominator = sqrt(leftNormSquared) * sqrt(rightNormSquared)
        if (denominator == 0.0) {
            return null
        }

        return dotProduct / denominator
    }
}
