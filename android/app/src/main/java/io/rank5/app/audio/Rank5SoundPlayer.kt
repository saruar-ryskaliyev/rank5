package io.rank5.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import io.rank5.app.BuildConfig
import io.rank5.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class AudioSettings(
    val gameSoundsEnabled: Boolean = true,
    val gameSoundsVolume: Float = 0.7f,
    val musicEnabled: Boolean = false,
    val musicVolume: Float = 0.5f,
    val allowInSilentMode: Boolean = false,
)

/**
 * Lifecycle-owned, no-audio-focus short-cue player. SoundPool handles decoded
 * samples while this class owns brand-level policies: variation bags,
 * category gain, cooldowns, concurrency, silent mode, and TalkBack restraint.
 */
class Rank5SoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val accessibilityManager =
        appContext.getSystemService(AccessibilityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AudioSettings> = _settings.asStateFlow()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private data class Sample(val durationMs: Long, val priority: Int, val gain: Float)
    private data class Active(
        val streamId: Int,
        val priority: Int,
        val gain: Float,
        val endsAtMs: Long,
    )

    private val samples = buildMap {
        fun sample(resId: Int, duration: Long, priority: Int, gain: Float) {
            put(resId, Sample(duration, priority, gain))
        }
        listOf(R.raw.r5_player_join_01, R.raw.r5_player_join_02, R.raw.r5_player_join_03)
            .forEach { sample(it, 180, P2, SOCIAL_GAIN) }
        sample(R.raw.r5_player_leave, 180, P2, SOCIAL_GAIN)
        sample(R.raw.r5_code_copy, 70, P1, UTILITY_GAIN)
        listOf(R.raw.r5_selection_tick_01, R.raw.r5_selection_tick_02, R.raw.r5_selection_tick_03)
            .forEach { sample(it, 48, P1, UTILITY_GAIN) }
        sample(R.raw.r5_game_start, 650, P4, PHASE_GAIN)
        listOf(R.raw.r5_drag_lift_01, R.raw.r5_drag_lift_02, R.raw.r5_drag_lift_03)
            .forEach { sample(it, 110, P1, UTILITY_GAIN) }
        listOf(
            R.raw.r5_rank_cross_01,
            R.raw.r5_rank_cross_02,
            R.raw.r5_rank_cross_03,
            R.raw.r5_rank_cross_04,
        ).forEach { sample(it, 30, P1, POSITION_GAIN) }
        listOf(R.raw.r5_lock_in_01, R.raw.r5_lock_in_02)
            .forEach { sample(it, 180, P4, PHASE_GAIN) }
        listOf(R.raw.r5_remote_lock_01, R.raw.r5_remote_lock_02)
            .forEach { sample(it, 120, P2, SOCIAL_GAIN) }
        sample(R.raw.r5_countdown_3, 95, P3, OUTCOME_GAIN)
        sample(R.raw.r5_countdown_2, 95, P3, OUTCOME_GAIN)
        sample(R.raw.r5_countdown_1, 95, P3, OUTCOME_GAIN)
        sample(R.raw.r5_time_up, 260, P4, PHASE_GAIN)
        listOf(
            R.raw.r5_reveal_card_01,
            R.raw.r5_reveal_card_02,
            R.raw.r5_reveal_card_03,
            R.raw.r5_reveal_card_04,
            R.raw.r5_reveal_card_05,
        ).forEach { sample(it, 160, P3, OUTCOME_GAIN) }
        sample(R.raw.r5_score_countup, 780, P3, OUTCOME_GAIN)
        sample(R.raw.r5_score_way_off, 260, P3, OUTCOME_GAIN)
        sample(R.raw.r5_score_not_bad, 320, P3, OUTCOME_GAIN)
        sample(R.raw.r5_score_so_close, 400, P3, OUTCOME_GAIN)
        sample(R.raw.r5_score_perfect, 620, P4, PHASE_GAIN)
        listOf(R.raw.r5_round_ready_01, R.raw.r5_round_ready_02)
            .forEach { sample(it, 120, P2, SOCIAL_GAIN) }
        sample(R.raw.r5_everyone_ready, 430, P3, OUTCOME_GAIN)
        sample(R.raw.r5_final_results_coop, 1_520, P4, PHASE_GAIN)
        sample(R.raw.r5_final_results_versus, 1_560, P4, PHASE_GAIN)
        listOf(R.raw.r5_error_01, R.raw.r5_error_02)
            .forEach { sample(it, 170, P3, OUTCOME_GAIN) }
        sample(R.raw.r5_connection_lost, 420, P3, OUTCOME_GAIN)
        sample(R.raw.r5_reconnect_success, 340, P3, OUTCOME_GAIN)
    }

    private val sampleIds = mutableMapOf<Int, Int>()
    private val loadedSampleIds = mutableSetOf<Int>()
    private val active = mutableListOf<Active>()
    private val lastPlayedAt = mutableMapOf<String, Long>()
    private val crossingTimes = ArrayDeque<Long>()
    private var released = false
    private var foreground = true
    private var desiredMusicTrack = ""
    private var desiredMusicScope = MusicScopeLobbyAndGame
    private var desiredMusicInRoom = false
    private var desiredMusicInLobby = false
    private var activeMusicTrack = ""
    private var musicPlayer: MediaPlayer? = null
    private var musicDuck = 1f
    private var duckUntilMs = 0L

    private val restoreMusicVolume = object : Runnable {
        override fun run() {
            synchronized(this@Rank5SoundPlayer) {
                val remaining = duckUntilMs - SystemClock.elapsedRealtime()
                if (remaining > 0) {
                    mainHandler.postDelayed(this, remaining)
                } else {
                    musicDuck = 1f
                    applyMusicVolume()
                }
            }
        }
    }
    private val ringerModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reconcileMusic()
        }
    }
    private val touchExplorationListener =
        AccessibilityManager.TouchExplorationStateChangeListener { reconcileMusic() }

    private val joinBag = VariantBag(
        listOf(R.raw.r5_player_join_01, R.raw.r5_player_join_02, R.raw.r5_player_join_03),
    )
    private val selectionBag = VariantBag(
        listOf(R.raw.r5_selection_tick_01, R.raw.r5_selection_tick_02, R.raw.r5_selection_tick_03),
    )
    private val dragBag = VariantBag(
        listOf(R.raw.r5_drag_lift_01, R.raw.r5_drag_lift_02, R.raw.r5_drag_lift_03),
    )
    private val crossBag = VariantBag(
        listOf(
            R.raw.r5_rank_cross_01,
            R.raw.r5_rank_cross_02,
            R.raw.r5_rank_cross_03,
            R.raw.r5_rank_cross_04,
        ),
    )
    private val lockBag = VariantBag(listOf(R.raw.r5_lock_in_01, R.raw.r5_lock_in_02))
    private val remoteLockBag = VariantBag(
        listOf(R.raw.r5_remote_lock_01, R.raw.r5_remote_lock_02),
    )
    private val readyBag = VariantBag(listOf(R.raw.r5_round_ready_01, R.raw.r5_round_ready_02))
    private val errorBag = VariantBag(listOf(R.raw.r5_error_01, R.raw.r5_error_02))

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            synchronized(this) {
                if (!released && status == 0) loadedSampleIds += sampleId
                if (BuildConfig.DEBUG && loadedSampleIds.size == samples.size) {
                    Log.d(TAG, "Loaded ${loadedSampleIds.size} Rank5 sound cues")
                }
            }
        }
        samples.forEach { (resId, sample) ->
            sampleIds[resId] = soundPool.load(appContext, resId, sample.priority)
        }
        accessibilityManager?.addTouchExplorationStateChangeListener(touchExplorationListener)
        ContextCompat.registerReceiver(
            appContext,
            ringerModeReceiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun setGameSoundsEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(gameSoundsEnabled = enabled))
        if (!enabled) stopSoundEffects()
    }

    fun setGameSoundsVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        updateSettings(_settings.value.copy(gameSoundsVolume = clamped))
        synchronized(this) {
            active.forEach {
                val volume = clamped * it.gain
                soundPool.setVolume(it.streamId, volume, volume)
            }
        }
    }

    fun setMusicEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(musicEnabled = enabled))
        reconcileMusic()
    }

    fun setMusicVolume(volume: Float) {
        updateSettings(_settings.value.copy(musicVolume = volume.coerceIn(0f, 1f)))
        applyMusicVolume()
        reconcileMusic()
    }

    fun setAllowInSilentMode(allowed: Boolean) {
        updateSettings(_settings.value.copy(allowInSilentMode = allowed))
        reconcileMusic()
    }

    /** Updates the host-selected soundtrack without restarting it at phase changes. */
    @Synchronized
    fun updateMusicState(track: String, scope: String, inRoom: Boolean, inLobby: Boolean) {
        desiredMusicTrack = track
        desiredMusicScope = scope
        desiredMusicInRoom = inRoom
        desiredMusicInLobby = inLobby
        reconcileMusic()
    }

    @Synchronized
    fun onForeground() {
        foreground = true
        reconcileMusic()
    }

    @Synchronized
    fun onBackground() {
        foreground = false
        stopSoundEffects()
        stopMusic()
    }

    fun playPlayerJoin() = play(joinBag.next(), "player_join", 180, accessibilitySensitive = true)
    fun playPlayerLeave() = play(
        R.raw.r5_player_leave,
        "player_leave",
        180,
        accessibilitySensitive = true,
    )
    fun playCodeConfirmed() = play(
        R.raw.r5_code_copy,
        "code_confirmed",
        300,
        accessibilitySensitive = true,
    )
    fun playSelectionTick() = play(
        selectionBag.next(),
        "selection",
        70,
        accessibilitySensitive = true,
    )
    fun playGameStart() = play(R.raw.r5_game_start, "game_start", 700)
    fun playDragLift() = play(
        dragBag.next(),
        "drag_lift",
        120,
        accessibilitySensitive = true,
    )

    fun playRankCross() {
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            while (crossingTimes.isNotEmpty() && now - crossingTimes.first() >= 1_000) {
                crossingTimes.removeFirst()
            }
            if (crossingTimes.size >= MAX_CROSSINGS_PER_SECOND) return
            crossingTimes.addLast(now)
        }
        play(
            crossBag.next(),
            "rank_cross",
            45,
            accessibilitySensitive = true,
        )
    }

    fun playLockIn() = play(lockBag.next(), "lock_in", 250)
    fun playRemoteLock() = play(
        remoteLockBag.next(),
        "remote_lock",
        160,
        accessibilitySensitive = true,
    )
    fun playCountdown(second: Int) {
        val resId = when (second) {
            3 -> R.raw.r5_countdown_3
            2 -> R.raw.r5_countdown_2
            1 -> R.raw.r5_countdown_1
            else -> return
        }
        play(resId, "countdown_$second", 500, allowHighOverlap = true)
    }
    fun playTimeUp() = play(R.raw.r5_time_up, "time_up", 1_000)

    fun playRevealCard(index: Int) {
        val resources = listOf(
            R.raw.r5_reveal_card_01,
            R.raw.r5_reveal_card_02,
            R.raw.r5_reveal_card_03,
            R.raw.r5_reveal_card_04,
            R.raw.r5_reveal_card_05,
        )
        val resId = resources.getOrNull(index) ?: return
        play(resId, "reveal_$index", 500, allowHighOverlap = true)
    }

    fun playScoreCountUp() = play(R.raw.r5_score_countup, "score_countup", 900)
    fun playScoreOutcome(points: Int) {
        val resId = when {
            points >= 2_000 -> R.raw.r5_score_perfect
            points >= 1_800 -> R.raw.r5_score_so_close
            points >= 1_400 -> R.raw.r5_score_not_bad
            else -> R.raw.r5_score_way_off
        }
        play(resId, "score_outcome", 900)
    }

    fun playRoundReady() = play(
        readyBag.next(),
        "round_ready",
        160,
        accessibilitySensitive = true,
    )
    fun playEveryoneReady() = play(R.raw.r5_everyone_ready, "everyone_ready", 500)
    fun playFinalResults(mode: String) = play(
        if (mode == "versus") R.raw.r5_final_results_versus else R.raw.r5_final_results_coop,
        "final_results",
        2_000,
    )
    fun playError() = play(errorBag.next(), "error", 500)
    fun playConnectionLost() = play(R.raw.r5_connection_lost, "connection_lost", 1_000)
    fun playReconnectSuccess() = play(R.raw.r5_reconnect_success, "reconnect_success", 1_000)

    @Synchronized
    private fun play(
        resId: Int,
        eventKey: String,
        cooldownMs: Long,
        accessibilitySensitive: Boolean = false,
        allowHighOverlap: Boolean = false,
    ) {
        if (released) return
        val settings = _settings.value
        if (!settings.gameSoundsEnabled || settings.gameSoundsVolume <= 0f) return
        if (!settings.allowInSilentMode && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            return
        }
        val touchExploration = accessibilityManager?.isTouchExplorationEnabled == true
        if (touchExploration && accessibilitySensitive) return

        val now = SystemClock.elapsedRealtime()
        val previous = lastPlayedAt[eventKey]
        if (previous != null && now - previous < cooldownMs) return
        val sampleId = sampleIds[resId] ?: return
        if (sampleId !in loadedSampleIds) return
        val sample = samples.getValue(resId)

        active.removeAll { it.endsAtMs <= now }
        if (!allowHighOverlap && sample.priority >= P3) {
            val obsolete = active.filter { it.priority >= P3 || it.priority < sample.priority }
            obsolete.forEach { soundPool.stop(it.streamId) }
            active.removeAll(obsolete.toSet())
        } else if (sample.priority >= P4) {
            val lower = active.filter { it.priority < P4 }
            lower.forEach { soundPool.stop(it.streamId) }
            active.removeAll(lower.toSet())
        }
        if (active.size >= MAX_STREAMS && sample.priority == P1) return
        if (active.size >= MAX_STREAMS) {
            val replace = active.minByOrNull { it.priority }
            if (replace != null && replace.priority <= sample.priority) {
                soundPool.stop(replace.streamId)
                active.remove(replace)
            } else {
                return
            }
        }

        val talkBackTrim = if (touchExploration) 0.6f else 1f
        val volume = settings.gameSoundsVolume * sample.gain * talkBackTrim
        val streamId = soundPool.play(
            sampleId,
            volume,
            volume,
            sample.priority,
            0,
            1f,
        )
        if (streamId == 0) return
        lastPlayedAt[eventKey] = now
        active += Active(streamId, sample.priority, sample.gain * talkBackTrim, now + sample.durationMs)
        if (sample.priority >= P3) {
            duckMusic(
                factor = if (sample.priority >= P4) 0.28f else 0.45f,
                durationMs = sample.durationMs + MUSIC_DUCK_TAIL_MS,
            )
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "play $eventKey stream=$streamId")
    }

    @Synchronized
    private fun stopSoundEffects() {
        active.forEach { soundPool.stop(it.streamId) }
        active.clear()
    }

    @Synchronized
    private fun reconcileMusic() {
        if (released) return
        val settings = _settings.value
        val selected = LobbyMusicCatalog.find(desiredMusicTrack)
        val scopeAllowsPlayback = desiredMusicInLobby ||
            (desiredMusicInRoom && desiredMusicScope == MusicScopeLobbyAndGame)
        val deviceAllowsPlayback = settings.musicEnabled && settings.musicVolume > 0f &&
            (settings.allowInSilentMode || audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) &&
            accessibilityManager?.isTouchExplorationEnabled != true
        if (!foreground || selected == null || !scopeAllowsPlayback || !deviceAllowsPlayback) {
            stopMusic()
            return
        }
        if (activeMusicTrack == selected.id && musicPlayer != null) {
            applyMusicVolume()
            return
        }
        stopMusic()
        musicPlayer = MediaPlayer.create(appContext, selected.resourceId)?.apply {
            isLooping = true
            setOnErrorListener { _, what, extra ->
                if (BuildConfig.DEBUG) Log.w(TAG, "music error what=$what extra=$extra")
                synchronized(this@Rank5SoundPlayer) { stopMusic() }
                true
            }
        }
        activeMusicTrack = if (musicPlayer != null) selected.id else ""
        applyMusicVolume()
        musicPlayer?.start()
        if (BuildConfig.DEBUG && activeMusicTrack.isNotEmpty()) {
            Log.d(TAG, "music start ${selected.id} scope=$desiredMusicScope")
        }
    }

    @Synchronized
    private fun applyMusicVolume() {
        val volume = (_settings.value.musicVolume * MUSIC_GAIN * musicDuck).coerceIn(0f, 1f)
        musicPlayer?.setVolume(volume, volume)
    }

    @Synchronized
    private fun duckMusic(factor: Float, durationMs: Long) {
        if (musicPlayer == null) return
        musicDuck = minOf(musicDuck, factor)
        duckUntilMs = maxOf(duckUntilMs, SystemClock.elapsedRealtime() + durationMs)
        applyMusicVolume()
        mainHandler.removeCallbacks(restoreMusicVolume)
        mainHandler.postDelayed(restoreMusicVolume, durationMs)
    }

    @Synchronized
    private fun stopMusic() {
        mainHandler.removeCallbacks(restoreMusicVolume)
        musicDuck = 1f
        duckUntilMs = 0L
        musicPlayer?.runCatching { stop() }
        musicPlayer?.release()
        musicPlayer = null
        if (BuildConfig.DEBUG && activeMusicTrack.isNotEmpty()) Log.d(TAG, "music stop $activeMusicTrack")
        activeMusicTrack = ""
    }

    @Synchronized
    fun release() {
        if (released) return
        stopSoundEffects()
        stopMusic()
        released = true
        accessibilityManager?.removeTouchExplorationStateChangeListener(touchExplorationListener)
        runCatching { appContext.unregisterReceiver(ringerModeReceiver) }
        soundPool.release()
        loadedSampleIds.clear()
    }

    private fun loadSettings(): AudioSettings = AudioSettings(
        gameSoundsEnabled = preferences.getBoolean(KEY_GAME_SOUNDS, true),
        gameSoundsVolume = preferences.getFloat(KEY_GAME_VOLUME, 0.7f),
        musicEnabled = preferences.getBoolean(KEY_MUSIC, false),
        musicVolume = preferences.getFloat(KEY_MUSIC_VOLUME, 0.5f),
        allowInSilentMode = preferences.getBoolean(KEY_ALLOW_SILENT, false),
    )

    private fun updateSettings(value: AudioSettings) {
        _settings.value = value
        preferences.edit()
            .putBoolean(KEY_GAME_SOUNDS, value.gameSoundsEnabled)
            .putFloat(KEY_GAME_VOLUME, value.gameSoundsVolume)
            .putBoolean(KEY_MUSIC, value.musicEnabled)
            .putFloat(KEY_MUSIC_VOLUME, value.musicVolume)
            .putBoolean(KEY_ALLOW_SILENT, value.allowInSilentMode)
            .apply()
    }

    private class VariantBag(private val variants: List<Int>) {
        private var remaining = emptyList<Int>()
        private var last: Int? = null

        @Synchronized
        fun next(): Int {
            if (remaining.isEmpty()) {
                remaining = variants.shuffled(Random.Default).toMutableList().also { shuffled ->
                    if (shuffled.size > 1 && shuffled.first() == last) {
                        val swap = shuffled[0]
                        shuffled[0] = shuffled[1]
                        shuffled[1] = swap
                    }
                }
            }
            return remaining.first().also { next ->
                remaining = remaining.drop(1)
                last = next
            }
        }
    }

    companion object {
        private const val TAG = "Rank5Sound"
        private const val PREFS_NAME = "rank5_audio"
        private const val KEY_GAME_SOUNDS = "game_sounds"
        private const val KEY_GAME_VOLUME = "game_volume"
        private const val KEY_MUSIC = "music"
        private const val KEY_MUSIC_VOLUME = "music_volume"
        private const val KEY_ALLOW_SILENT = "allow_in_silent_mode"
        private const val MAX_STREAMS = 3
        private const val MAX_CROSSINGS_PER_SECOND = 8
        private const val MUSIC_GAIN = 0.70f
        private const val MUSIC_DUCK_TAIL_MS = 180L
        private const val P1 = 1
        private const val P2 = 2
        private const val P3 = 3
        private const val P4 = 4
        private const val PHASE_GAIN = 1f
        private const val OUTCOME_GAIN = 0.79f
        private const val SOCIAL_GAIN = 0.63f
        private const val UTILITY_GAIN = 0.50f
        private const val POSITION_GAIN = 0.40f
    }
}
