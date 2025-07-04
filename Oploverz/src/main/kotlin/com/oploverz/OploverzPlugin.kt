package com.oploverz

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class OploverzPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Oploverz())
        registerExtractorAPI(Qiwi())
        registerExtractorAPI(Blogger())
    }
}
