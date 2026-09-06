package com.tracksnatcher.app.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.tracksnatcher.app.domain.model.AppError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import javax.inject.Inject

/**
 * Captures a short mono PCM window from the mic and returns it as a WAV byte array ready
 * to POST to the recognition backend. 16 kHz / 16-bit mono is plenty for fingerprinting
 * and keeps the upload small for a fast round-trip.
 */
class AudioRecorder @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher,
) {
    @SuppressLint("MissingPermission") // enforced by @RequiresPermission on the public entry point
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(durationMs: Long = DEFAULT_DURATION_MS): Result<ByteArray> =
        withContext(ioDispatcher) {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            if (minBuffer <= 0) return@withContext Result.failure(AppError.Unknown())

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                minBuffer * 2,
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@withContext Result.failure(AppError.MicPermissionDenied)
            }

            val pcm = ByteArrayOutputStream()
            val buffer = ByteArray(minBuffer)
            val deadline = System.currentTimeMillis() + durationMs
            try {
                recorder.startRecording()
                while (System.currentTimeMillis() < deadline) {
                    coroutineContext.ensureActive() // honour cancellation (e.g. user dismisses)
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) pcm.write(buffer, 0, read)
                }
            } catch (t: Throwable) {
                return@withContext Result.failure(AppError.Unknown(t))
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }

            Result.success(wrapAsWav(pcm.toByteArray()))
        }

    /** Prepend a 44-byte PCM WAV header so the backend can decode without extra metadata. */
    private fun wrapAsWav(pcm: ByteArray): ByteArray {
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                       // PCM chunk size
            putShort(1)                      // audio format = PCM
            putShort(CHANNELS.toShort())
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }.array()
        return header + pcm
    }

    private companion object {
        const val DEFAULT_DURATION_MS = 6_000L
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
