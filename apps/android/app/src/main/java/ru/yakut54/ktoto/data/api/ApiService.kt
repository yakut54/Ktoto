package ru.yakut54.ktoto.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.yakut54.ktoto.data.model.AuthResponse
import ru.yakut54.ktoto.data.model.Conversation
import ru.yakut54.ktoto.data.model.CreateConversationRequest
import ru.yakut54.ktoto.data.model.LoginRequest
import ru.yakut54.ktoto.data.model.Message
import ru.yakut54.ktoto.data.model.RegisterRequest
import ru.yakut54.ktoto.data.model.SendMessageRequest
import ru.yakut54.ktoto.data.model.UserItem

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @GET("api/users")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("search") search: String = "",
    ): List<UserItem>

    @GET("api/conversations")
    suspend fun getConversations(@Header("Authorization") token: String): List<Conversation>

    @POST("api/conversations")
    suspend fun createConversation(
        @Header("Authorization") token: String,
        @Body body: CreateConversationRequest,
    ): Map<String, Any>

    @GET("api/conversations/{id}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null,
    ): List<Message>

    @POST("api/conversations/{id}/messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Body body: SendMessageRequest,
    ): Message
}

fun buildApiService(baseUrl: String): ApiService {
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
