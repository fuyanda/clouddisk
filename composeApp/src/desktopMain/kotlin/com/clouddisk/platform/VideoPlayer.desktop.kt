package com.clouddisk.platform

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

private fun fmt(ms: Long): String {
    if (ms <= 0) return "00:00"
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

/**
 * macOS 下 VLCJ 无法把视频嵌入 AWT 组件（黑屏）。改用回调式解码：VLC 把每帧 BGRA 写入缓冲，
 * 转成 Skia Image → Compose ImageBitmap 直接绘制，完全绕开 Swing。
 * ponytail: 每帧拷贝像素并新建 Image，预览够用；高分辨率高帧率有 GC 压力，升级可复用缓冲/位图。
 */
@Composable
actual fun VideoPlayer(url: String, rate: Float, modifier: Modifier) {
    val factory = remember { runCatching { MediaPlayerFactory() }.getOrNull() }

    if (factory == null) {
        Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("未检测到本机 VLC")
                Button(onClick = { openExternal(url) }) { Text("用外部播放器打开") }
            }
        }
        return
    }

    var frame by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var playing by remember(url) { mutableStateOf(false) }
    var position by remember(url) { mutableStateOf(0f) }
    var timeMs by remember(url) { mutableStateOf(0L) }
    var lengthMs by remember(url) { mutableStateOf(0L) }
    var buffering by remember(url) { mutableStateOf(0f) }
    var failed by remember(url) { mutableStateOf(false) }

    val mediaPlayer = remember(url) {
        val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
        val bufferFormatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat =
                RV32BufferFormat(sourceWidth, sourceHeight)

            override fun allocatedBuffers(buffers: Array<ByteBuffer>) {}
        }
        val renderCallback = RenderCallback { _, buffers, bufferFormat ->
            val bb = buffers[0].also { it.rewind() }
            val bytes = ByteArray(bb.remaining())
            bb.get(bytes)
            val info = ImageInfo(bufferFormat.width, bufferFormat.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            frame = Image.makeRaster(info, bytes, bufferFormat.width * 4).toComposeImageBitmap()
        }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true))
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mp: MediaPlayer) { playing = true }
            override fun paused(mp: MediaPlayer) { playing = false }
            override fun finished(mp: MediaPlayer) { playing = false }
            override fun error(mp: MediaPlayer) { failed = true }
            override fun buffering(mp: MediaPlayer, newCache: Float) { buffering = newCache }
            override fun lengthChanged(mp: MediaPlayer, newLength: Long) { lengthMs = newLength }
            override fun timeChanged(mp: MediaPlayer, newTime: Long) { timeMs = newTime }
            override fun positionChanged(mp: MediaPlayer, newPosition: Float) { position = newPosition }
        })
        player
    }

    DisposableEffect(url) {
        // macOS 上 VLC 优先用独立的 videotoolbox 解码模块，输出 GPU 表面(CVPX)，回调式 vmem 无法转成 RV32，
        // vout 每个关键帧重建一次却永不出帧(白屏)。:avcodec-hw=none 管不到它，必须用 :codec 强制 avcodec 软解。
        mediaPlayer.media().play(url, ":codec=avcodec", ":avcodec-hw=none", ":network-caching=3000")
        onDispose {
            mediaPlayer.controls().stop()
            mediaPlayer.release()
        }
    }
    DisposableEffect(Unit) { onDispose { factory.release() } }
    LaunchedEffect(rate) { mediaPlayer.controls().setRate(rate) }

    Box(modifier, contentAlignment = Alignment.Center) {
        frame?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }

        when {
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("播放失败")
                Button(onClick = { openExternal(url) }) { Text("用外部播放器打开") }
            }
            frame == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(if (buffering > 0f) "缓冲 ${buffering.toInt()}%" else "加载中…")
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { mediaPlayer.controls().setPause(playing) }) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Color.White,
                )
            }
            Text(fmt(timeMs), color = Color.White)
            Slider(
                value = position,
                onValueChange = { position = it; mediaPlayer.controls().setPosition(it) },
                modifier = Modifier.weight(1f),
            )
            Text(fmt(lengthMs), color = Color.White)
        }
    }
}
