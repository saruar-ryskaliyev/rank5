package io.rank5.app.audio

import androidx.annotation.RawRes
import io.rank5.app.R

const val MusicScopeLobby = "lobby"
const val MusicScopeLobbyAndGame = "lobby_and_game"

data class LobbyMusicTrack(
    val id: String,
    val name: String,
    val originalTitle: String,
    val artist: String,
    val duration: String,
    @RawRes val resourceId: Int,
)

object LobbyMusicCatalog {
    val tracks = listOf(
        LobbyMusicTrack(
            id = "five_alive",
            name = "Five Alive",
            originalTitle = "Game Music Loop 6",
            artist = "XtremeFreddy",
            duration = "0:33",
            resourceId = R.raw.r5_music_five_alive,
        ),
        LobbyMusicTrack(
            id = "dancehall_shuffle",
            name = "Dancehall Shuffle",
            originalTitle = "Afro Dancehall Drum Loop",
            artist = "West Tunes",
            duration = "0:09",
            resourceId = R.raw.r5_music_dancehall_shuffle,
        ),
        LobbyMusicTrack(
            id = "easy_glow",
            name = "Easy Glow",
            originalTitle = "Happy Relaxing Loop",
            artist = "SergeQuadrado",
            duration = "0:55",
            resourceId = R.raw.r5_music_easy_glow,
        ),
        LobbyMusicTrack(
            id = "t8_bounce",
            name = "T8 Bounce",
            originalTitle = "T8 Drum Loop",
            artist = "Liecio",
            duration = "0:05",
            resourceId = R.raw.r5_music_t8_bounce,
        ),
    )

    fun find(id: String?): LobbyMusicTrack? = tracks.firstOrNull { it.id == id }
}
