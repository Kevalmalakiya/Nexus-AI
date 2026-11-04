package com.nexus.ai.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nexus.ai.R
import com.nexus.ai.voice.VoicePhase

private val CLIPS = listOf(
    R.raw.avatar_idle,
    R.raw.avatar_listening,
    R.raw.avatar_thinking,
    R.raw.avatar_speaking
)

private fun indexFor(phase: VoicePhase): Int = when (phase) {
    VoicePhase.IDLE -> 0
    VoicePhase.LISTENING -> 1
    VoicePhase.THINKING -> 2
    VoicePhase.SPEAKING -> 3
}

/** Orb ki jagah anime avatar. 4 clips x 10s = 40s, phase ke hisaab se instant switch + loop. */
@Composable
fun NexusAvatar(phase: VoicePhase, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItems(CLIPS.map { MediaItem.fromUri(Uri.parse("android.resource://${ctx.packageName}/$it")) })
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            player.release()
        }
    }

    LaunchedEffect(phase) {
        player.seekTo(indexFor(phase), 0L)
        player.play()
    }

    Box(modifier) {
        Image(
            painterResource(R.drawable.avatar_poster), null,
            Modifier.matchParentSize(), contentScale = ContentScale.Crop
        )
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.player = player
                }
            },
            modifier = Modifier.matchParentSize()
        )
    }
}
