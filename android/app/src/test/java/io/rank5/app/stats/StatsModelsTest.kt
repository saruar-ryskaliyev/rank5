package io.rank5.app.stats

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes complete stats response`() {
        val stats = json.decodeFromString<StatsResponse>(
            """
            {
              "gamesPlayed": 18,
              "versus": {"played": 10, "wins": 4, "tiesForFirst": 1, "winRatePct": 40},
              "coop": {
                "played": 8,
                "bestByRounds": [{"rounds": 5, "score": 9120, "maxScore": 10000}]
              },
              "guessing": {"accuracyPct": 84, "predictionsTracked": 42},
              "superlatives": {
                "bestGuesser": 6,
                "mostPredictable": 3,
                "detailedGamesTracked": 12
              }
            }
            """.trimIndent(),
        )

        assertEquals(18, stats.gamesPlayed)
        assertEquals(40, stats.versus.winRatePct)
        assertEquals(9120, stats.coop.bestByRounds.single().score)
        assertEquals(84, stats.guessing?.accuracyPct)
        assertEquals(12, stats.superlatives.detailedGamesTracked)
    }

    @Test
    fun `supports legacy history without detailed guessing stats`() {
        val stats = json.decodeFromString<StatsResponse>(
            """
            {
              "gamesPlayed": 2,
              "versus": {"played": 1, "wins": 1, "tiesForFirst": 0, "winRatePct": 100},
              "coop": {"played": 1, "bestByRounds": []},
              "guessing": null,
              "superlatives": {
                "bestGuesser": 0,
                "mostPredictable": 0,
                "detailedGamesTracked": 0
              }
            }
            """.trimIndent(),
        )

        assertNull(stats.guessing)
        assertEquals(2, stats.gamesPlayed)
    }
}
