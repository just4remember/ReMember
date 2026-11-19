package dev.chaik.remember.core.editor

import dev.chaik.remember.appContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.chaik.remember.core.model.formatVideoDuration
import java.io.File

/** Ролик рядом с кадром-обложкой (`<id>.webp` → `<id>.mp4`); у фото его нет. */
fun File.videoSibling(): File? = File(parentFile, "$nameWithoutExtension.mp4").takeIf { it.exists() }

/**
 * Значок видео поверх кадра: кнопка ▶ по центру и длительность в углу.
 * [compact] — для мелких ячеек сетки и галереи.
 */
@Composable
fun BoxScope.VideoBadge(durationMs: Long?, compact: Boolean = false) {
    val play: Dp = if (compact) 32.dp else 52.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.Center)
            .size(play)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(play * 0.62f))
    }
    if (durationMs != null) {
        Text(
            formatVideoDuration(durationMs),
            color = Color.White,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(if (compact) 4.dp else 8.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = if (compact) 5.dp else 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Плеер на странице просмотра: играет, пока страница открыта ([active]), со звуком и
 * обычными кнопками плеера. Ушли со страницы — пауза; закрыли — плеер освобождается.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPage(file: File, active: Boolean) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(active) { player.playWhenReady = active }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                setShowNextButton(false)
                setShowPreviousButton(false)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Значок на кнопке добавления, пока идёт вставка: у видео — сколько уже сжато (кольцо
 * заполняется), у фото — просто крутится.
 */
@Composable
fun MediaImportProgress() {
    val progress by LocalContext.current.appContainer.photoStorage.videoProgress.collectAsStateWithLifecycle()
    val value = progress
    if (value != null) {
        CircularProgressIndicator(progress = { value }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    } else {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
