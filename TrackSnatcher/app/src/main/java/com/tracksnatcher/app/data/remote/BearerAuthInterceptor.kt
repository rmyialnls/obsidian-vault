package com.tracksnatcher.app.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <token>` to every request, refreshing the token first if
 * needed. [tokenProvider] delegates to the relevant OAuth manager's silent-refresh logic.
 *
 * OkHttp interceptors are blocking, so we bridge the suspending refresh with [runBlocking];
 * calls already run on OkHttp's background dispatcher, never the main thread.
 */
class BearerAuthInterceptor(
    private val tokenProvider: suspend () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
