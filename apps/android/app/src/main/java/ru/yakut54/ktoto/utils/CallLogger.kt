package ru.yakut54.ktoto.utils

import android.os.Build
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Remote call logger — writes to Android Logcat AND fires a POST to the backend
 * so all call flow events from both devices land in one server log file.
 */
object CallLogger {

    private const val LOG_URL = "http://31.128.39.216:3000/api/calls/log"

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    val deviceId: String = "${Build.MANUFACTURER}_${Build.MODEL}".replace(" ", "_")

    fun i(tag: String, msg: String, vararg pairs: Pair<String, Any?>) {
        Log.i(tag, msg)
        send("info", tag, msg, pairs)
    }

    fun w(tag: String, msg: String, vararg pairs: Pair<String, Any?>) {
        Log.w(tag, msg)
        send("warn", tag, msg, pairs)
    }

    fun e(tag: String, msg: String, vararg pairs: Pair<String, Any?>) {
        Log.e(tag, msg)
        send("error", tag, msg, pairs)
    }

    private fun send(level: String, tag: String, msg: String, pairs: Array<out Pair<String, Any?>>) {
        val body = JSONObject().apply {
            put("level", level)
            put("tag", tag)
            put("message", msg)
            put("deviceId", deviceId)
            put("ts", System.currentTimeMillis())
            if (pairs.isNotEmpty()) {
                val data = JSONObject()
                pairs.forEach { (k, v) -> data.put(k, v?.toString() ?: "null") }
                put("data", data)
            }
        }.toString()

        val request = Request.Builder()
            .url(LOG_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* silent — don't crash if server unavailable */ }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
