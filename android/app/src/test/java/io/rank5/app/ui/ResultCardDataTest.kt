package io.rank5.app.ui

import io.rank5.app.game.RoundResult
import io.rank5.app.game.UiState
import io.rank5.app.net.DeckInfo
import io.rank5.app.net.PlayerView
import io.rank5.app.net.RoomStateView
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultCardDataTest {
    @Test
    fun `maps coop result with deck and round breakdown`() {
        val state = UiState(
            room = room(mode = "coop", teamScore = 5700),
            roundHistory = listOf(
                RoundResult(index = 0, teamScore = 1800, scores = emptyMap()),
                RoundResult(index = 1, teamScore = 1900, scores = emptyMap()),
                RoundResult(index = 2, teamScore = 2000, scores = emptyMap()),
            ),
        )

        val card = ResultCardData.from(state)

        assertEquals("Food Fight", card.deckName)
        assertEquals("🍕", card.deckEmoji)
        assertEquals(5700, card.teamScore)
        assertEquals(6000, card.maxTeamScore)
        assertEquals(listOf(1800, 1900, 2000), card.roundScores)
    }

    @Test
    fun `versus standings are score ordered and capped at five`() {
        val players = (1..7).map { index ->
            PlayerView(
                id = "p$index",
                nickname = "Player $index",
                isHost = index == 1,
                connected = true,
                score = index * 100,
            )
        }
        val state = UiState(room = room(mode = "versus", players = players))

        val card = ResultCardData.from(state)

        assertEquals("Player 7", card.standings.first().nickname)
        assertEquals(5, card.standings.size)
        assertEquals(2, card.morePlayers)
        assertEquals(listOf(1, 2, 3, 4, 5), card.standings.map { it.rank })
    }

    @Test fun `multi deck result uses mix title and contributing names`() {
        val selected = listOf(
            DeckInfo("food", "Food Fight", "🍕", 6),
            DeckInfo("movies", "Movie Night", "🎬", 6),
        )
        val state = UiState(
            room = room(mode = "coop").copy(
                deckIds = selected.map { it.id },
                selectedDecks = selected,
                decks = selected,
            ),
        )

        val card = ResultCardData.from(state)

        assertEquals("2-deck mix", card.deckName)
        assertEquals(listOf("Food Fight", "Movie Night"), card.deckNames)
    }

    private fun room(
        mode: String,
        teamScore: Int = 0,
        players: List<PlayerView> = listOf(
            PlayerView("p1", "Me", true, true, 100),
            PlayerView("p2", "Friend", false, true, 200),
        ),
    ) = RoomStateView(
        code = "ABCD",
        phase = "GAME_OVER",
        mode = mode,
        deckId = "food",
        totalRounds = 3,
        players = players,
        teamScore = teamScore,
        decks = listOf(DeckInfo("food", "Food Fight", "🍕", 6)),
    )
}
