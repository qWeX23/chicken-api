package co.qwex.chickenapi

import co.qwex.chickenapi.model.Breed
import java.time.Instant

object TestFixtures {
    val silkie = Breed(1, "Silkie", "China", "White", "Small", "Docile", "Fluffy", "img", 200)
    val orpington = Breed(2, "Orpington", "UK", "Brown", "Large", "Friendly", "Big", "img2", 180)

    fun breedList() = listOf(silkie, orpington)

    // Breeds with various updatedAt states for testing selection logic
    val breedNeverUpdated1 = Breed(1, "Rhode Island Red", "USA", "Brown", "Large", "Friendly", "Hardy dual-purpose breed", "img1", 250, updatedAt = null)
    val breedNeverUpdated2 = Breed(2, "Plymouth Rock", "USA", "Brown", "Large", "Docile", "Excellent layer", "img2", 280, updatedAt = null)
    val breedOldUpdate = Breed(3, "Leghorn", "Italy", "White", "Large", "Active", "Top layer", "img3", 300, updatedAt = Instant.parse("2024-01-01T00:00:00Z"))
    val breedRecentUpdate = Breed(4, "Sussex", "England", "Cream", "Large", "Calm", "Traditional breed", "img4", 260, updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
    val breedWithSources = Breed(5, "Silkie", "China", "Cream", "Small", "Docile", "Fluffy ornamental", "img5", 120, updatedAt = Instant.parse("2024-06-15T00:00:00Z"), sources = listOf("https://example.com/silkie"))
}
