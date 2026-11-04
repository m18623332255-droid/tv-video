package com.home.tvvideo

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.hierynomus.smbj.share.File

/** smb://path 数据源（基于 smbj） */
@UnstableApi
class SmbDataSource : BaseDataSource(false) {
    private var file: File? = null; private var pos = 0L; private var remain = 0L; private var uri: Uri? = null
    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri; transferInitializing(dataSpec)
        val path = Uri.decode(dataSpec.uri.toString().removePrefix("smb://")).replace('/', '\\')
        val f = Smb.open(path); file = f
        val size = f.fileInformation.standardInformation.endOfFile
        pos = dataSpec.position
        remain = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - pos
        transferStarted(dataSpec); return remain
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remain == 0L) return C.RESULT_END_OF_INPUT
        val n = file!!.read(buffer, pos, offset, minOf(length.toLong(), remain).toInt())
        if (n <= 0) return C.RESULT_END_OF_INPUT
        pos += n; remain -= n; bytesTransferred(n); return n
    }
    override fun getUri() = uri
    override fun close() { runCatching { file?.close() }; file = null; if (uri != null) { uri = null; transferEnded() } }
}

@UnstableApi
object PlayerKit {
    fun build(ctx: Context, headers: Map<String, String> = emptyMap(), live: Boolean = false): ExoPlayer {
        val okf = OkHttpDataSource.Factory(http).setUserAgent(UA).setDefaultRequestProperties(headers)
        val base = DefaultDataSource.Factory(ctx, okf)
        val smbAware = DataSource.Factory {
            val d = base.createDataSource(); val s = SmbDataSource()
            object : DataSource by d {
                var cur: DataSource = d
                override fun open(dataSpec: DataSpec): Long { cur = if (dataSpec.uri.scheme == "smb") s else d; return cur.open(dataSpec) }
                override fun read(buffer: ByteArray, offset: Int, length: Int) = cur.read(buffer, offset, length)
                override fun getUri() = cur.uri
                override fun close() = cur.close()
                override fun getResponseHeaders() = cur.responseHeaders
            }
        }
        val ext = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
        val load = if (live) DefaultLoadControl.Builder().setBufferDurationsMs(3000, 20000, 1500, 2000).build() else DefaultLoadControl()
        return ExoPlayer.Builder(ctx, DefaultRenderersFactory(ctx).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(smbAware, ext)).setLoadControl(load).build()
    }
    fun item(url: String): MediaItem {
        val b = MediaItem.Builder().setUri(url)
        val l = url.lowercase().substringBefore('?')
        if (l.endsWith(".m3u8") || url.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        return b.build()
    }
}
