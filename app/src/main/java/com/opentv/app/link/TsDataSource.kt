package com.opentv.app.link

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.InterruptedIOException

/** Entrega al reproductor los bytes TS que llegan por OpenTV Link (flujo sin fin: largo desconocido, sin saltos). */
@UnstableApi
class TsDataSource(private val supplier: () -> TsStreamSource) : BaseDataSource(true) {
    private var source: TsStreamSource? = null
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        source = supplier()
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val s = source ?: return C.RESULT_END_OF_INPUT
        val n = try {
            s.read(buffer, offset, length)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException()
        }
        if (n < 0) return C.RESULT_END_OF_INPUT
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (source != null) {
            source = null
            transferEnded()
        }
    }
}
