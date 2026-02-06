package com.theveloper.pixelplay.presentation.viewmodel

import androidx.media3.session.MediaController
import androidx.media3.common.Player
import androidx.media3.common.C
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.theveloper.pixelplay.data.model.Song
import com.google.android.gms.cast.MediaStatus
import timber.log.Timber
import com.theveloper.pixelplay.utils.QueueUtils
import com.theveloper.pixelplay.utils.MediaItemBuilder
import kotlin.math.abs

@Singleton
class PlaybackStateHolder @Inject constructor(
    private val dualPlayerEngine: DualPlayerEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val castStateHolder: CastStateHolder,
    private val queueStateHolder: QueueStateHolder,
    private val listeningStatsTracker: ListeningStatsTracker
) {
    companion object {
        private const val TAG = "PlaybackStateHolder"
        private const val DURATION_MISMATCH_TOLERANCE_MS = 1500L
    }

    private var scope: CoroutineScope? = null
    
    // MediaController
    var mediaController: MediaController? = null
        private set

    // Player State
    private val _stablePlayerState = MutableStateFlow(StablePlayerState())
    val stablePlayerState: StateFlow<StablePlayerState> = _stablePlayerState.asStateFlow()

    // Internal State
    private var isSeeking = false
    private var remoteSeekUnlockJob: Job? = null

    fun initialize(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
    }

    fun setMediaController(controller: MediaController?) {
        this.mediaController = controller
    }
    
    fun updateStablePlayerState(update: (StablePlayerState) -> StablePlayerState) {
        _stablePlayerState.update(update)
    }
    
    /* -------------------------------------------------------------------------- */
    /*                               Playback Controls                            */
    /* -------------------------------------------------------------------------- */

    fun playPause() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            if (remoteMediaClient.isPlaying) {
                castStateHolder.castPlayer?.pause()
                _stablePlayerState.update { it.copy(isPlaying = false) }
            } else {
                if (remoteMediaClient.mediaQueue != null && remoteMediaClient.mediaQueue.itemCount > 0) {
                    castStateHolder.castPlayer?.play()
                    _stablePlayerState.update { it.copy(isPlaying = true) }
                } else {
                    Timber.w("Remote queue empty, cannot resume.")
                }
            }
        } else {
            val controller = mediaController ?: return
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun seekTo(position: Long) {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val targetPosition = position.coerceAtLeast(0L)
            castStateHolder.setRemotelySeeking(true)
            castStateHolder.setRemotePosition(targetPosition)
            _stablePlayerState.update { it.copy(currentPosition = targetPosition) }
            castStateHolder.castPlayer?.seek(targetPosition)

            remoteSeekUnlockJob?.cancel()
            remoteSeekUnlockJob = scope?.launch {
                // Fail-safe: never keep remote seeking lock indefinitely.
                delay(1800)
                castStateHolder.setRemotelySeeking(false)
                castSession.remoteMediaClient?.requestStatus()
            }
        } else {
            remoteSeekUnlockJob?.cancel()
            castStateHolder.setRemotelySeeking(false)
            mediaController?.seekTo(position)
            _stablePlayerState.update { it.copy(currentPosition = position) }
        }
    }

    fun previousSong() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castStateHolder.castPlayer?.previous()
        } else {
            val controller = mediaController ?: return
             if (controller.currentPosition > 10000) { // 10 seconds
                 controller.seekTo(0)
            } else {
                 controller.seekToPrevious()
            }
        }
    }

    fun nextSong() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castStateHolder.castPlayer?.next()
        } else {
             mediaController?.seekToNext()
        }
    }

    fun cycleRepeatMode() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            val currentRepeatMode = remoteMediaClient.mediaStatus?.getQueueRepeatMode() ?: MediaStatus.REPEAT_MODE_REPEAT_OFF
            val newMode = when (currentRepeatMode) {
                MediaStatus.REPEAT_MODE_REPEAT_OFF -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                MediaStatus.REPEAT_MODE_REPEAT_ALL -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            castStateHolder.castPlayer?.setRepeatMode(newMode)
            
            // Map remote mode back to local constant for persistence/UI
            val mappedLocalMode = when (newMode) {
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            scope?.launch { userPreferencesRepository.setRepeatMode(mappedLocalMode) }
            _stablePlayerState.update { it.copy(repeatMode = mappedLocalMode) }
        } else {
            val currentMode = _stablePlayerState.value.repeatMode
            val newMode = when (currentMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            mediaController?.repeatMode = newMode
            scope?.launch { userPreferencesRepository.setRepeatMode(newMode) }
            _stablePlayerState.update { it.copy(repeatMode = newMode) }
        }
    }

    fun setRepeatMode(mode: Int) {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            val remoteMode = when (mode) {
                Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            castStateHolder.castPlayer?.setRepeatMode(remoteMode)
        } else {
             mediaController?.repeatMode = mode
        }
        
        scope?.launch { userPreferencesRepository.setRepeatMode(mode) }
        _stablePlayerState.update { it.copy(repeatMode = mode) }
    }

    /* -------------------------------------------------------------------------- */
    /*                               Progress Updates                             */
    /* -------------------------------------------------------------------------- */
    
    private var progressJob: kotlinx.coroutines.Job? = null

    /**
     * Reconciles duration reported by the player with the current song metadata duration.
     *
     * Why:
     * - During some transitions (notably crossfade player swaps), the reported duration can lag
     *   behind the currently visible track for a short period.
     * - Relying only on one source can make progress run too slow/fast.
     */
    private fun resolveEffectiveDuration(
        reportedDurationMs: Long,
        songDurationHintMs: Long,
        currentPositionMs: Long
    ): Long {
        val reported = when {
            reportedDurationMs == C.TIME_UNSET -> 0L
            reportedDurationMs < 0L -> 0L
            else -> reportedDurationMs
        }
        val hint = songDurationHintMs.coerceAtLeast(0L)
        val position = currentPositionMs.coerceAtLeast(0L)

        if (reported <= 0L) return hint
        if (hint <= 0L) return reported

        val diff = abs(reported - hint)
        if (diff <= DURATION_MISMATCH_TOLERANCE_MS) return reported

        // If playback already passed the metadata hint, trust the reported duration to avoid clipping.
        if (position > hint + DURATION_MISMATCH_TOLERANCE_MS && reported >= position) {
            return reported
        }

        // Otherwise prefer the shorter duration to avoid stale longer values after swaps.
        val resolved = minOf(reported, hint)
        if (diff > 10_000L) {
            Timber.tag(TAG).w(
                "Duration mismatch resolved (reported=%dms, hint=%dms, pos=%dms, resolved=%dms)",
                reported, hint, position, resolved
            )
        }
        return resolved
    }

    fun resolveDurationForPlaybackState(
        reportedDurationMs: Long,
        songDurationHintMs: Long,
        currentPositionMs: Long
    ): Long = resolveEffectiveDuration(
        reportedDurationMs = reportedDurationMs,
        songDurationHintMs = songDurationHintMs,
        currentPositionMs = currentPositionMs
    )

    fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope?.launch {
            while (true) {
                val castSession = castStateHolder.castSession.value
                val isRemote = castSession?.remoteMediaClient != null
                
                if (isRemote) {
                    val remoteClient = castSession?.remoteMediaClient
                    if (remoteClient != null) {
                        val isRemotePlaying = remoteClient.isPlaying
                        val currentPosition = remoteClient.approximateStreamPosition.coerceAtLeast(0L)
                        val songDurationHint = _stablePlayerState.value.currentSong?.duration ?: 0L
                        val duration = resolveEffectiveDuration(
                            reportedDurationMs = remoteClient.streamDuration,
                            songDurationHintMs = songDurationHint,
                            currentPositionMs = currentPosition
                        )
                        val isRemotelySeeking = castStateHolder.isRemotelySeeking.value
                        if (!isRemotelySeeking) {
                            castStateHolder.setRemotePosition(currentPosition)
                        }

                        listeningStatsTracker.onProgress(currentPosition, isRemotePlaying)

                        _stablePlayerState.update {
                             it.copy(
                                 currentPosition = if (isRemotelySeeking) it.currentPosition else currentPosition,
                                 totalDuration = duration,
                                 isPlaying = isRemotePlaying
                             )
                        }
                    }
                } else {
                     val controller = mediaController
                     // Media3: Check isPlaying or playbackState == READY/BUFFERING
                     if (controller != null && controller.isPlaying && !isSeeking) {
                         val visibleSong = _stablePlayerState.value.currentSong
                         val currentMediaId = controller.currentMediaItem?.mediaId
                         val hasMediaMismatch = visibleSong?.id != null &&
                             currentMediaId != null &&
                             visibleSong.id != currentMediaId

                         if (hasMediaMismatch) {
                             Timber.tag(TAG).v(
                                 "Skipping local progress tick due media mismatch (visible=%s, player=%s)",
                                 visibleSong?.id,
                                 currentMediaId
                             )
                             delay(500)
                             continue
                         }

                         val currentPosition = controller.currentPosition.coerceAtLeast(0L)
                         val songDurationHint = visibleSong?.duration ?: 0L
                         val duration = resolveEffectiveDuration(
                             reportedDurationMs = controller.duration,
                             songDurationHintMs = songDurationHint,
                             currentPositionMs = currentPosition
                         )
                         
                         listeningStatsTracker.onProgress(currentPosition, true)
                         
                         _stablePlayerState.update {
                             it.copy(currentPosition = currentPosition, totalDuration = duration)
                        }
                     }
                }
                delay(500) // 500ms ticker
            }
        }
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    /* -------------------------------------------------------------------------- */
    /*                               Shuffle & Repeat                             */
    /* -------------------------------------------------------------------------- */

    private fun reorderQueueInPlace(player: Player, desiredQueue: List<Song>): Boolean {
        if (desiredQueue.isEmpty()) return false

        val currentCount = player.mediaItemCount
        if (currentCount != desiredQueue.size) {
            Timber.tag(TAG).w(
                "Cannot reorder queue in place: size mismatch (player=%d, desired=%d)",
                currentCount,
                desiredQueue.size
            )
            return false
        }

        val currentIds = MutableList(currentCount) { index ->
            player.getMediaItemAt(index).mediaId
        }
        val desiredIds = desiredQueue.map { it.id }

        val currentCounts = currentIds.groupingBy { it }.eachCount()
        val desiredCounts = desiredIds.groupingBy { it }.eachCount()
        if (currentCounts != desiredCounts) {
            Timber.tag(TAG).w("Cannot reorder queue in place: mediaId mismatch")
            return false
        }

        for (targetIndex in desiredIds.indices) {
            val desiredId = desiredIds[targetIndex]
            if (currentIds[targetIndex] == desiredId) continue

            var fromIndex = -1
            for (searchIndex in targetIndex + 1 until currentIds.size) {
                if (currentIds[searchIndex] == desiredId) {
                    fromIndex = searchIndex
                    break
                }
            }

            if (fromIndex == -1) {
                Timber.tag(TAG).w(
                    "Cannot reorder queue in place: target mediaId '%s' not found",
                    desiredId
                )
                return false
            }

            player.moveMediaItem(fromIndex, targetIndex)
            val movedId = currentIds.removeAt(fromIndex)
            currentIds.add(targetIndex, movedId)
        }

        return true
    }

    fun toggleShuffle(
        currentSongs: List<Song>,
        currentSong: Song?,
        currentQueueSourceName: String,
        updateQueueCallback: (List<Song>) -> Unit
    ) {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient
            val newRepeatMode = if (remoteMediaClient?.mediaStatus?.getQueueRepeatMode() == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE) {
                MediaStatus.REPEAT_MODE_REPEAT_ALL
            } else {
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
            }
            castStateHolder.castPlayer?.setRepeatMode(newRepeatMode)
        } else {
            scope?.launch {
                val player = mediaController ?: return@launch
                if (currentSongs.isEmpty()) return@launch

                val isCurrentlyShuffled = _stablePlayerState.value.isShuffleEnabled

                if (!isCurrentlyShuffled) {
                    // Enable Shuffle
                    if (!queueStateHolder.hasOriginalQueue()) {
                        queueStateHolder.setOriginalQueueOrder(currentSongs)
                        queueStateHolder.saveOriginalQueueState(currentSongs, currentQueueSourceName)
                    }

                    val currentIndex = player.currentMediaItemIndex.coerceIn(0, (currentSongs.size - 1).coerceAtLeast(0))
                    val currentPosition = player.currentPosition
                    val currentMediaId = player.currentMediaItem?.mediaId

                    val shuffledQueue = QueueUtils.buildAnchoredShuffleQueue(currentSongs, currentIndex)

                    val targetIndex = shuffledQueue.indexOfFirst { it.id == currentMediaId }
                        .takeIf { it != -1 } ?: currentIndex

                    val reordered = reorderQueueInPlace(player, shuffledQueue)
                    if (!reordered) {
                        // Last-resort fallback if local queue snapshot got desynced from player timeline.
                        val wasPlaying = player.isPlaying
                        dualPlayerEngine.masterPlayer.setMediaItems(
                            shuffledQueue.map { MediaItemBuilder.build(it) },
                            targetIndex,
                            currentPosition
                        )
                        if (wasPlaying && !player.isPlaying) {
                            player.play()
                        }
                    }

                    updateQueueCallback(shuffledQueue)
                    _stablePlayerState.update { it.copy(isShuffleEnabled = true) }
                    
                    scope?.launch {
                        if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                            userPreferencesRepository.setShuffleOn(true)
                        }
                    }
                } else {
                    // Disable Shuffle
                   scope?.launch {
                        if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                            userPreferencesRepository.setShuffleOn(false)
                        }
                    }

                    if (!queueStateHolder.hasOriginalQueue()) {
                        _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                        return@launch
                    }

                    val originalQueue = queueStateHolder.originalQueueOrder
                    val currentPosition = player.currentPosition
                    
                    // Find where the current song is in the original queue
                    val currentSongId = currentSong?.id
                    val originalIndex = originalQueue.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 }

                    if (originalIndex == null) {
                        _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                        return@launch
                    }

                    // Preserve playback state during queue rebuild
                    val reordered = reorderQueueInPlace(player, originalQueue)
                    if (!reordered) {
                        val wasPlaying = player.isPlaying
                        dualPlayerEngine.masterPlayer.setMediaItems(
                            originalQueue.map { MediaItemBuilder.build(it) },
                            originalIndex,
                            currentPosition
                        )
                        if (wasPlaying && !player.isPlaying) {
                            player.play()
                        }
                    }

                    updateQueueCallback(originalQueue)
                    _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                }
            }
        }
    }

}
