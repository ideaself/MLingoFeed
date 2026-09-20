package com.mlingofeed.data.api

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/** Streams word pronunciations from Youdao's public dictvoice endpoint. */
class PronunciationPlayer {
    private var player: MediaPlayer? = null

    fun play(
        word: String,
        uk: Boolean = false,
        onStart: () -> Unit = {},
        onFinish: () -> Unit = {}
    ) {
        val spoken = word.trim()
        if (spoken.isBlank()) {
            onFinish()
            return
        }
        try {
            val mp = player ?: MediaPlayer().also { player = it }
            mp.reset()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(
                "https://dict.youdao.com/dictvoice?audio=${Uri.encode(spoken)}&type=${if (uk) 1 else 2}"
            )
            mp.setOnPreparedListener {
                it.start()
                onStart()
            }
            mp.setOnCompletionListener { onFinish() }
            mp.setOnErrorListener { _, _, _ ->
                onFinish()
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            onFinish()
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
