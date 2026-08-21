package co.qwex.chickenapi.ai

import co.qwex.chickenapi.model.ChickenFactsRecord
import co.qwex.chickenapi.repository.ChickenFactsRepository
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import kotlin.math.sqrt

interface ChickenFactDuplicateCheckService {
    suspend fun checkFactForDuplicate(fact: String, sourceUrl: String? = null): FactDuplicateCheckResult
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
    val coveredTopics: List<String> = emptyList(),
)

@Service
class ChickenFactDuplicateChecker(
    private val embeddingService: OllamaEmbeddingService,
    private val chickenFactsRepository: ChickenFactsRepository,
    @Value("\${koog.agent.fact-dedup-threshold:0.88}")
    private val similarityThreshold: Double,
    @Value("\${koog.agent.topic-cluster-threshold:0.78}")
    private val topicClusterThreshold: Double,
    @Value("\${koog.agent.max-covered-topics:15}")
    private val maxCoveredTopics: Int,
    @Value("\${koog.agent.source-domain-max-repeats:3}")
    private val sourceDomainMaxRepeats: Int,
) : ChickenFactDuplicateCheckService {
    private val log = KotlinLogging.logger {}

    override suspend fun checkFactForDuplicate(fact: String, sourceUrl: String?): FactDuplicateCheckResult {
        if (!embeddingService.isReady()) {
            log.error { "Fact duplicate check failed because embedding service is unavailable." }
            throw IllegalStateException("Embedding service unavailable for duplicate check")
        }

        val existingFacts = chickenFactsRepository.fetchAllSuccessfulChickenFacts()
        val coveredTopics = computeCoveredTopics(existingFacts)
        val normalizedCandidate = fact.normalizeForExactMatch()
        val exactMatches = existingFacts.mapNotNull { record ->
            val existingFact = record.fact?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (existingFact.normalizeForExactMatch() != normalizedCandidate) {
                return@mapNotNull null
            }
            SimilarFactMatch(
                runId = record.runId,
                fact = existingFact,
                sourceUrl = record.sourceUrl,
                similarity = 1.0,
            )
        }
        if (exactMatches.isNotEmpty()) {
            return FactDuplicateCheckResult(
                hasHit = true,
                threshold = similarityThreshold,
                topSimilarity = 1.0,
                matches = exactMatches,
                coveredTopics = coveredTopics,
            )
        }

        val candidateDomain = sourceUrl?.let(::domainOf).orEmpty()
        if (candidateDomain.isNotEmpty()) {
            val overUsedMatches =
                existingFacts
                    .filter { domainOf(it.sourceUrl) == candidateDomain }
                    .take(5)
                    .map {
                        SimilarFactMatch(
                            runId = it.runId,
                            fact = it.fact?.trim().orEmpty(),
                            sourceUrl = it.sourceUrl,
                            similarity = 1.0,
                        )
                    }
            if (overUsedMatches.size >= sourceDomainMaxRepeats) {
                log.warn {
                    "Rejecting fact from over-used source domain $candidateDomain " +
                        "(${overUsedMatches.size} existing facts, max $sourceDomainMaxRepeats)"
                }
                return FactDuplicateCheckResult(
                    hasHit = true,
                    threshold = similarityThreshold,
                    topSimilarity = null,
                    matches = overUsedMatches,
                    coveredTopics = coveredTopics,
                )
            }
        }

        val candidateEmbedding = embeddingService.embedFact(fact.trim())
        if (candidateEmbedding.isNullOrEmpty()) {
            log.error { "Fact duplicate check failed because candidate embedding could not be created." }
            throw IllegalStateException("Unable to create embedding for candidate fact")
        }

        val matches = existingFacts
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
            coveredTopics = coveredTopics,
        )
    }

    private fun computeCoveredTopics(records: List<ChickenFactsRecord>): List<String> {
        val referenceSize =
            records
                .mapNotNull { it.factEmbedding }
                .firstOrNull()?.size
                ?: return emptyList()
        val clusters = mutableListOf<MutableList<Pair<ChickenFactsRecord, List<Double>>>>()
        records.forEach { record ->
            val fact = record.fact?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
            val embedding = record.factEmbedding ?: return@forEach
            if (embedding.size != referenceSize) {
                return@forEach
            }
            val bestCluster =
                clusters
                    .mapIndexed { index, cluster -> index to cosineSimilarity(embedding, centroidOf(cluster)) }
                    .filter { (_, similarity) -> similarity != null }
                    .maxByOrNull { (_, similarity) -> similarity!! }
            if (bestCluster != null && bestCluster.second!! >= topicClusterThreshold) {
                clusters[bestCluster.first].add(record to embedding)
            } else {
                clusters.add(mutableListOf(record to embedding))
            }
        }
        return clusters
            .sortedByDescending { it.size }
            .take(maxCoveredTopics)
            .map { cluster ->
                val centroid = centroidOf(cluster)
                val representative =
                    cluster.minByOrNull { (_, embedding) -> -(cosineSimilarity(embedding, centroid) ?: 0.0) }!!
                        .first
                val label =
                    representative.fact!!
                        .trim()
                        .replace(Regex("\\s+"), " ")
                        .let { if (it.length > 90) it.take(90).trimEnd() + "…" else it }
                "$label (${domainOf(representative.sourceUrl)})"
            }
    }

    private fun centroidOf(pairs: List<Pair<ChickenFactsRecord, List<Double>>>): List<Double> {
        if (pairs.isEmpty()) {
            return emptyList()
        }
        val dimension = pairs.first().second.size
        return List(dimension) { index -> pairs.map { it.second[index] }.average() }
    }

    private fun String.normalizeForExactMatch(): String =
        trim().lowercase().replace(Regex("\\s+"), " ")

    private fun domainOf(url: String?): String {
        if (url.isNullOrBlank()) {
            return ""
        }
        val host = runCatching { URI(url).host }.getOrNull() ?: return ""
        return host.lowercase().removePrefix("www.")
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
