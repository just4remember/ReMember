package dev.chaik.remember.core.editor

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlin.math.abs
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.formatVideoDuration
import kotlinx.coroutines.delay
import java.io.File

/** Перемотка кнопками и двойным тапом. */
private const val SEEK_MS = 10_000L

/** Через сколько бездействия прячутся кнопки полноэкранного плеера. */
private const val CONTROLS_HIDE_MS = 2_500L

/** Через сколько прячется значок паузы у мини-плеера в тексте. */
private const val INLINE_ICON_HIDE_MS = 1_000L

/** Ролик рядом с кадром-обложкой (`<id>.webp` → `<id>.mp4`); у фото его нет. */
fun File.videoSibling(): File? = File(parentFile, "$nameWithoutExtension.mp4").takeIf { it.exists() }

/** Кнопка ▶ на кадре видео: размер круга. Тот же размер — зона нажатия в тексте. */
internal fun playCircleSize(compact: Boolean): Dp = if (compact) 32.dp else 52.dp

@Composable
private fun BoxScope.PlayCircle(icon: ImageVector, compact: Boolean) {
    val size = playCircleSize(compact)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.Center)
            .size(size)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/**
 * Значок видео поверх кадра: кнопка ▶ по центру и длительность в углу.
 * [compact] — для мелких ячеек сетки и галереи.
 */
@Composable
fun BoxScope.VideoBadge(durationMs: Long?, compact: Boolean = false) {
    PlayCircle(Icons.Filled.PlayArrow, compact)
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
 * Плеер для ролика: освобождается вместе с экраном, а при сворачивании приложения
 * ставится на паузу ([onBackground] — сообщить об этом наружу).
 */
@Composable
private fun rememberVideoPlayer(file: File, onBackground: () -> Unit = {}): ExoPlayer {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(SEEK_MS)
            .setSeekForwardIncrementMs(SEEK_MS)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
            }
    }
    val background by rememberUpdatedState(onBackground)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player.pause()
                background()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    return player
}

/** Где ролик сейчас и сколько он длится — для полосы прогресса; опрашиваем, пока виден. */
@Composable
private fun rememberPlayback(player: ExoPlayer): Pair<Long, Long> {
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            if (player.duration > 0) duration = player.duration
            delay(200)
        }
    }
    return position to duration
}

/** Двойной тап: в какую сторону перемотали, на сколько всего (тапы подряд складываются) и где был тап. */
private data class SeekFlash(val forward: Boolean, val seconds: Long, val at: Offset, val stamp: Long)

/**
 * Видео на странице просмотра. Играет, пока страница открыта ([active]).
 *
 * Свои кнопки вместо стандартных из Media3: прячутся через секунду бездействия, не залезают
 * под системные кнопки и не мешают листать соседние фото свайпом.
 * Тап — показать/спрятать кнопки, двойной тап слева/справа — на 10 секунд назад/вперёд.
 * Показаны ли кнопки — [controls]; это общее с просмотром: вместе с ними прячутся крестик,
 * счётчик и сведения галереи.
 * [bottomPadding] — сколько снизу занято чужим (сведения в галерее), чтобы полоса была выше.
 * [onSwipe] — вертикальный свайп по ролику (true — вверх): так открывают сведения в галерее.
 *
 * Пока страница открыта, экран поворачивается за телефоном (как настроен автоповорот
 * в системе); кнопка поворота переключает книжную/альбомную вручную. Всё остальное
 * приложение — только книжное.
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPage(
    file: File,
    active: Boolean,
    controls: Boolean,
    onControlsChange: (Boolean) -> Unit,
    bottomPadding: Dp = 0.dp,
    onSwipe: ((up: Boolean) -> Unit)? = null,
) {
    val swipe by rememberUpdatedState(onSwipe)
    // Обработчик жестов создаётся один раз — показаны ли кнопки, читаем на момент тапа,
    // иначе он всё время «видел» бы значение с первого показа и только прятал кнопки
    val controlsShown by rememberUpdatedState(controls)
    val player = rememberVideoPlayer(file)
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    // Любое действие с кнопками заново отсчитывает секунду до скрытия
    var touched by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var flash by remember { mutableStateOf<SeekFlash?>(null) }
    val (position, duration) = rememberPlayback(player)
    val isActive by rememberUpdatedState(active)
    val setControls by rememberUpdatedState(onControlsChange)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ended = playbackState == Player.STATE_ENDED
                if (ended && isActive) setControls(true)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(active) {
        player.playWhenReady = active
        if (active) setControls(true)
    }
    LaunchedEffect(active, controls, touched, playing, dragging) {
        if (active && controls && playing && dragging == null) {
            delay(CONTROLS_HIDE_MS)
            setControls(false)
        }
    }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(450)
            flash = null
        }
    }

    // Поворот: null — за телефоном, иначе выбранный кнопкой
    val activity = LocalContext.current.findActivity()
    var manual by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(active, manual, activity) {
        if (active && activity != null) {
            activity.requestedOrientation = manual ?: ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        onDispose {
            if (active && activity != null) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun poke() {
        setControls(true)
        touched++
    }

    fun seekBy(ms: Long) {
        val end = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0L, end))
    }

    fun togglePlay() {
        when {
            ended -> {
                player.seekTo(0)
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
        poke()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Жесты — поверх ролика: тапы ловим, а свайп вбок уходит листанию страниц
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (controlsShown) setControls(false) else poke()
                        },
                        onDoubleTap = { offset ->
                            val forward = offset.x > size.width / 2f
                            seekBy(if (forward) SEEK_MS else -SEEK_MS)
                            val previous = flash?.takeIf { it.forward == forward }
                            flash = SeekFlash(forward, (previous?.seconds ?: 0) + SEEK_MS / 1000, offset, System.nanoTime())
                            if (controlsShown) touched++
                        },
                    )
                }
                .pointerInput(Unit) {
                    // Только явный вертикальный жест: свайп вбок остаётся листанию
                    val threshold = 48.dp.toPx()
                    var total = 0f
                    var fired = false
                    detectVerticalDragGestures(
                        onDragStart = {
                            total = 0f
                            fired = false
                        },
                        onVerticalDrag = { change, dy ->
                            val handler = swipe ?: return@detectVerticalDragGestures
                            change.consume()
                            total += dy
                            if (!fired && abs(total) > threshold) {
                                fired = true
                                handler(total < 0)
                            }
                        },
                    )
                },
        )

        // Последняя перемотка остаётся, пока эффект плавно гаснет
        var lastFlash by remember { mutableStateOf<SeekFlash?>(null) }
        if (flash != null) lastFlash = flash
        AnimatedVisibility(
            visible = flash != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(280)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) { lastFlash?.let { SeekRipple(it) } }
        }

        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                val buttonColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    IconButton(onClick = { seekBy(-SEEK_MS); poke() }, colors = buttonColors, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Replay10, stringResource(R.string.video_back), Modifier.size(36.dp))
                    }
                    IconButton(
                        onClick = ::togglePlay,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.4f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            imageVector = when {
                                ended -> Icons.Filled.Replay
                                playing -> Icons.Filled.Pause
                                else -> Icons.Filled.PlayArrow
                            },
                            contentDescription = stringResource(if (playing) R.string.video_pause else R.string.video_play),
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    IconButton(onClick = { seekBy(SEEK_MS); poke() }, colors = buttonColors, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Forward10, stringResource(R.string.video_forward), Modifier.size(36.dp))
                    }
                }

                // Низ как в YouTube: время слева и кнопка поворота справа, под ними — тонкая
                // полоса с шариком. Всё над системными кнопками (и над сведениями галереи)
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val shown = dragging?.let { (it * duration).toLong() } ?: position
                val accent = MaterialTheme.colorScheme.primary
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = maxOf(navBottom, bottomPadding))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${formatVideoDuration(shown)} / ${formatVideoDuration(duration)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                        )
                        IconButton(
                            onClick = {
                                manual = if (landscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                                poke()
                            },
                            colors = buttonColors,
                        ) {
                            Icon(Icons.Outlined.ScreenRotation, stringResource(R.string.video_rotate), Modifier.size(22.dp))
                        }
                    }
                    Slider(
                        value = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = {
                            dragging = it
                            touched++
                        },
                        onValueChangeFinished = {
                            dragging?.let { player.seekTo((it * duration).toLong()) }
                            dragging = null
                        },
                        thumb = {
                            Box(
                                Modifier
                                    .size(if (dragging != null) 18.dp else 14.dp)
                                    .background(accent, CircleShape),
                            )
                        },
                        track = { state ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(state.value.coerceIn(0f, 1f))
                                        .background(accent, RoundedCornerShape(50)),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                    )
                }
            }
        }
    }
}

/**
 * Перемотка двойным тапом, как в YouTube: светлая полукруглая область у края, от места тапа
 * расходится волна, бегут три стрелки и подпись «10 секунд».
 */
@Composable
private fun BoxScope.SeekRipple(flash: SeekFlash) {
    val forward = flash.forward
    val wave = remember { Animatable(0f) }
    LaunchedEffect(flash.stamp) {
        wave.snapTo(0f)
        wave.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val arrows = rememberInfiniteTransition(label = "seekArrows")
    val phase by arrows.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "seekArrowPhase",
    )
    // Область — край большого овала, центр которого далеко за экраном: 55 % высоты
    // по центру, до 30 % ширины, внутренний край — пологая дуга
    val shape = remember(forward) {
        GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            addOval(if (forward) Rect(0f, 0f, w * 3.4f, h) else Rect(-w * 2.4f, 0f, w, h))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val full = constraints.maxWidth.toFloat()
        val fullHeight = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight(0.55f)
                .fillMaxWidth(0.3f)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.12f))
                .drawBehind {
                    // Тап — в координатах всей страницы; переводим в координаты области
                    val center = Offset(
                        x = if (forward) flash.at.x - (full - size.width) else flash.at.x,
                        y = flash.at.y - (fullHeight - size.height) / 2f,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f * (1f - wave.value)),
                        radius = size.maxDimension * wave.value,
                        center = center,
                    )
                },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                Row {
                    repeat(3) { i ->
                        // Стрелки загораются по очереди в сторону перемотки
                        // Плавно: яркость каждой стрелки нарастает и спадает, а не переключается
                        val order = if (forward) i else 2 - i
                        val near = 1f - minOf(abs(phase - order - 0.5f), 1.5f) / 1.5f
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f + 0.7f * near),
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { if (!forward) scaleX = -1f },
                        )
                    }
                }
                Text(
                    text = pluralStringResource(R.plurals.video_seek_seconds, flash.seconds.toInt(), flash.seconds.toInt()),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Мини-плеер прямо в тексте: ролик играет в своей ячейке поверх кадра-обложки, обрезанный
 * по ячейке, как и обложка. Внизу тонкая полоса прогресса; по центру — значок паузы
 * (виден секунду после запуска и всё время, пока на паузе).
 * [aspect] — соотношение сторон ролика на экране, [paused] — на паузе ли.
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.InlineVideo(
    file: File,
    aspect: Float,
    paused: Boolean,
    compact: Boolean,
    fit: Boolean = false,
    onPause: () -> Unit,
    onEnded: () -> Unit,
) {
    val player = rememberVideoPlayer(file, onBackground = onPause)
    val ended by rememberUpdatedState(onEnded)
    val (position, duration) = rememberPlayback(player)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) ended()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(paused) { player.playWhenReady = !paused }

    var showIcon by remember { mutableStateOf(true) }
    LaunchedEffect(paused) {
        showIcon = true
        if (!paused) {
            delay(INLINE_ICON_HIDE_MS)
            showIcon = false
        }
    }

    val ratio by rememberUpdatedState(aspect)
    val fitInside by rememberUpdatedState(fit)
    AndroidView(
        factory = { ctx ->
            // TextureView, а не SurfaceView: его обрезают скруглённые углы ячейки
            TextureView(ctx).also { view ->
                player.setVideoTextureView(view)
                view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> (v as TextureView).scaleVideo(ratio, fitInside) }
            }
        },
        update = { it.scaleVideo(aspect, fit) },
        modifier = Modifier.matchParentSize(),
    )

    AnimatedVisibility(showIcon, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
        Box(Modifier.size(playCircleSize(compact))) {
            PlayCircle(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, compact)
        }
    }

    if (duration > 0) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.3f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f))
                    .background(Color.White),
            )
        }
    }
}

/**
 * Растянутый по view кадр приводим к его настоящим пропорциям: обрезаем лишнее (как Crop
 * у фото) или, если [fit], вписываем целиком с пустыми полями.
 */
private fun TextureView.scaleVideo(aspect: Float, fit: Boolean) {
    val w = width.toFloat()
    val h = height.toFloat()
    if (w <= 0f || h <= 0f || aspect <= 0f) return
    val viewAspect = w / h
    // Обрезка тянет до краёв ту сторону, что короче; вписывание — сжимает ту, что не помещается
    val stretchWidth = (aspect > viewAspect) != fit
    val sx = if (stretchWidth) aspect / viewAspect else 1f
    val sy = if (stretchWidth) 1f else viewAspect / aspect
    setTransform(Matrix().apply { setScale(sx, sy, w / 2f, h / 2f) })
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
