package ru.yakut54.ktoto.data.api

import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import ru.yakut54.ktoto.data.model.RefreshResponse
import ru.yakut54.ktoto.data.store.TokenStore

class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val baseUrl: String,
) : Authenticator {

    private val gson = Gson()
    private val refreshClient = OkHttpClient()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite loop — if refresh request itself gets 401, stop
        if (response.request.header("X-Retry-After-Refresh") != null) return null

        val refreshToken = runBlocking { tokenStore.refreshToken.first() } ?: return null

        val body = gson.toJson(mapOf("refreshToken" to refreshToken))
            .toRequestBody("application/json".toMediaType())

        val refreshRequest = Request.Builder()
            .url("${baseUrl}api/auth/refresh")
            .post(body)
            .build()

        val refreshResp = try {
            refreshClient.newCall(refreshRequest).execute()
        } catch (e: Exception) {
            return null
        }

        if (!refreshResp.isSuccessful) {
            runBlocking { tokenStore.clear() }
            return null
        }

        val json = refreshResp.body?.string() ?: return null
        val result = runCatching { gson.fromJson(json, RefreshResponse::class.java) }.getOrNull()
            ?: return null

        runBlocking { tokenStore.saveTokens(result.accessToken, result.refreshToken) }

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${result.accessToken}")
            .header("X-Retry-After-Refresh", "true")
            .build()
    }
}
