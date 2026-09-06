package com.tracksnatcher.app.data.remote

import com.tracksnatcher.app.data.remote.dto.RecognitionResponseDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Ambient-audio recognition endpoint (backend fronts AcoustID / MusicBrainz). */
interface RecognitionApi {

    /** @param audio 5–7s of 16-bit PCM/WAV; backend fingerprints and resolves provider ids. */
    @Multipart
    @POST("v1/recognize")
    suspend fun recognize(
        @Part audio: MultipartBody.Part,
    ): RecognitionResponseDto
}
