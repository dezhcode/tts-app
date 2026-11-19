package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioPlayer(private val scope: CoroutineScope) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _playingClipId = MutableStateFlow<String?>(null)
    val playingClipId: StateFlow<String?> = _playingClipId.asStateFlow()

    private var playJob: Job? = null
    private var currentTrack: AudioTrack? = null

    fun play(clipId: String, samples: FloatArray, sampleRate: Int = 24_000) {
        stop()
        if (samples.isEmpty()) return

        _playingClipId.value = clipId
        _isPlaying.value = true
        _progress.value = 0f

        playJob = scope.launch(Dispatchers.IO) {
            val pcm16 = ShortArray(samples.size) { i ->
                (samples[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            }
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            currentTrack = track
            try {
                track.play()
                val chunkSize = 2048
                var offset = 0
                while (isActive && offset < pcm16.size) {
                    val count = minOf(chunkSize, pcm16.size - offset)
                    val written = track.write(pcm16, offset, count)
                    if (written <= 0) break
                    offset += written
                    _progress.value = offset.toFloat() / pcm16.size.toFloat()
                }
                if (isActive) {
                    val remainingMs = ((minBuf.toFloat() / (sampleRate * 2)) * 1000).toLong().coerceIn(80L, 400L)
                    delay(remainingMs)
                }
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
                if (currentTrack === track) currentTrack = null
                _isPlaying.value = false
                _progress.value = 0f
                _playingClipId.value = null
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        runCatching { currentTrack?.pause() }
        runCatching { currentTrack?.flush() }
        runCatching { currentTrack?.release() }
        currentTrack = null
        _isPlaying.value = false
        _progress.value = 0f
        _playingClipId.value = null
    }
}

class VoicePromptRecorder(private val scope: CoroutineScope) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0f)
    val elapsedSeconds: StateFlow<Float> = _elapsedSeconds.asStateFlow()

    private var recordJob: Job? = null
    private val recordedSamples = mutableListOf<Float>()

    @SuppressLint("MissingPermission")
    fun startRecording(sampleRate: Int = 24_000, maxSeconds: Int = 10, onAutoStopped: (FloatArray) -> Unit) {
        if (_isRecording.value) return
        recordedSamples.clear()
        _isRecording.value = true
        _elapsedSeconds.value = 0f
        _micLevel.value = 0f

        recordJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf,
            )

            val buffer = ShortArray(1024)
            val maxSamples = sampleRate * maxSeconds
            try {
                recorder.startRecording()
                while (isActive && recordedSamples.size < maxSamples) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sq = 0f
                        synchronized(recordedSamples) {
                            for (i in 0 until read) {
                                val f = buffer[i] / 32768f
                                recordedSamples.add(f)
                                sq += f * f
                            }
                        }
                        val rms = sqrt(sq / read)
                        _micLevel.value = (rms * 4.5f).coerceIn(0f, 1f)
                        _elapsedSeconds.value = recordedSamples.size.toFloat() / sampleRate.toFloat()
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                val captured = synchronized(recordedSamples) { recordedSamples.toFloatArray() }
                _isRecording.value = false
                _micLevel.value = 0f
                if (captured.isNotEmpty()) {
                    onAutoStopped(captured)
                }
            }
        }
    }

    fun stopRecording() {
        recordJob?.cancel()
        recordJob = null
    }
}
