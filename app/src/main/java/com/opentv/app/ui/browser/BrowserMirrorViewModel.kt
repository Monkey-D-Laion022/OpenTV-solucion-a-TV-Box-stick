package com.opentv.app.ui.browser

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opentv.app.OpenTvApp
import com.opentv.app.stream.BrowserMirrorState
import com.opentv.app.stream.MirrorRequest
import com.opentv.app.stream.MjpegQuality
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BrowserMirrorViewModel(application: Application) : AndroidViewModel(application) {
    private val mirror = (application as OpenTvApp).container.browserMirror

    val state: StateFlow<BrowserMirrorState> get() = mirror.state
    val quality: StateFlow<MjpegQuality> get() = mirror.quality

    fun start(resultCode: Int, data: Intent) {
        viewModelScope.launch { mirror.start(MirrorRequest(resultCode, data)) }
    }

    val shortUrl: StateFlow<Boolean> get() = mirror.shortUrl

    fun cycleQuality() = mirror.cycleQuality()
    fun toggleShortUrl() = mirror.toggleShortUrl()
    fun stop() = mirror.stop()
}
