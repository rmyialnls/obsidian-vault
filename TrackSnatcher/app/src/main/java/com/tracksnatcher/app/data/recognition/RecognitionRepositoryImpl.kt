package com.tracksnatcher.app.data.recognition

import com.tracksnatcher.app.data.remote.RecognitionApi
import com.tracksnatcher.app.domain.model.AppError
import com.tracksnatcher.app.domain.model.Track
import com.tracksnatcher.app.domain.repository.RecognitionRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

class RecognitionRepositoryImpl @Inject constructor(
    private val recognitionApi: RecognitionApi,
) : RecognitionRepository {

    override suspend fun identify(pcmWav: ByteArray): Result<Track> = try {
        val part = MultipartBody.Part.createFormData(
            name = "audio",
            filename = "capture.wav",
            body = pcmWav.toRequestBody("audio/wav".toMediaType()),
        )
        val response = recognitionApi.recognize(part)
        if (response.isMatch) {
            Result.success(response.match!!.toDomain())
        } else {
            Result.failure(AppError.NoMatch)
        }
    } catch (e: IOException) {
        Result.failure(AppError.Network(e))
    } catch (t: Throwable) {
        Result.failure(AppError.Unknown(t))
    }
}
