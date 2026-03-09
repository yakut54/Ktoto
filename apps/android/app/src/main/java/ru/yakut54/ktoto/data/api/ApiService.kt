package ru.yakut54.ktoto.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.DELETE
import ru.yakut54.ktoto.data.model.AddMemberRequest
import ru.yakut54.ktoto.data.model.AuthResponse
import ru.yakut54.ktoto.data.model.BlockedUser
import ru.yakut54.ktoto.data.model.ChangePasswordRequest
import ru.yakut54.ktoto.data.model.ChangeRoleRequest
import ru.yakut54.ktoto.data.model.Conversation
import ru.yakut54.ktoto.data.model.FcmTokenRequest
import ru.yakut54.ktoto.data.model.CreateConversationRequest
import ru.yakut54.ktoto.data.model.EditMessageRequest
import ru.yakut54.ktoto.data.model.GroupMember
import ru.yakut54.ktoto.data.model.LoginRequest
import ru.yakut54.ktoto.data.model.Message
import ru.yakut54.ktoto.data.model.RegisterRequest
import ru.yakut54.ktoto.data.model.RenameGroupRequest
import ru.yakut54.ktoto.data.model.SendMessageRequest
import ru.yakut54.ktoto.data.model.CallRecord
import ru.yakut54.ktoto.data.model.TurnCredentials
import ru.yakut54.ktoto.data.model.OkResponse
import ru.yakut54.ktoto.data.model.User
import ru.yakut54.ktoto.data.model.UserItem
import ru.yakut54.ktoto.data.model.UserResponse
import ru.yakut54.ktoto.data.store.TokenStore

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun getMe(@Header("Authorization") token: String): UserResponse

    @POST("api/auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>,
    )

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

    @Multipart
    @POST("api/conversations/{id}/messages")
    suspend fun uploadMessage(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Part file: MultipartBody.Part,
        @Part("meta") meta: RequestBody,
    ): Message

    @PATCH("api/conversations/{convId}/messages/{msgId}")
    suspend fun editMessage(
        @Header("Authorization") token: String,
        @Path("convId") conversationId: String,
        @Path("msgId") messageId: String,
        @Body body: EditMessageRequest,
    ): Message

    @HTTP(method = "DELETE", path = "api/conversations/{convId}/messages/{msgId}", hasBody = false)
    suspend fun deleteMessage(
        @Header("Authorization") token: String,
        @Path("convId") conversationId: String,
        @Path("msgId") messageId: String,
    )

    @HTTP(method = "DELETE", path = "api/conversations/{id}", hasBody = false)
    suspend fun deleteConversation(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
    )

    @POST("api/conversations/{id}/block")
    suspend fun blockConversationPartner(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
    )

    @GET("api/calls/turn-credentials")
    suspend fun getTurnCredentials(@Header("Authorization") token: String): TurnCredentials

    @GET("api/calls/history")
    suspend fun getCallHistory(@Header("Authorization") token: String): List<CallRecord>

    @POST("api/conversations/{id}/read")
    suspend fun markConversationRead(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
    ): Map<String, Any>

    @PUT("api/users/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") token: String,
        @Body body: FcmTokenRequest,
    )

    @GET("api/conversations/{id}/members")
    suspend fun getGroupMembers(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
    ): List<GroupMember>

    @POST("api/conversations/{id}/members")
    suspend fun addGroupMember(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Body body: AddMemberRequest,
    )

    @HTTP(method = "DELETE", path = "api/conversations/{id}/members/{userId}", hasBody = false)
    suspend fun removeGroupMember(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Path("userId") userId: String,
    )

    @PATCH("api/conversations/{id}")
    suspend fun renameGroup(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Body body: RenameGroupRequest,
    ): Map<String, Any>

    @Multipart
    @POST("api/conversations/{id}/avatar")
    suspend fun uploadGroupAvatar(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Part file: MultipartBody.Part,
    ): Map<String, Any>

    @PATCH("api/conversations/{id}/members/{userId}/role")
    suspend fun changeGroupMemberRole(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String,
        @Path("userId") userId: String,
        @Body body: ChangeRoleRequest,
    ): Map<String, Any>

    @Multipart
    @PATCH("api/users/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part?,
        @Part("username") username: RequestBody?,
    ): UserResponse

    @POST("api/users/change-password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body body: ChangePasswordRequest,
    ): OkResponse

    @GET("api/users/blocked")
    suspend fun getBlockedUsers(@Header("Authorization") token: String): List<BlockedUser>

    @DELETE("api/users/blocked/{userId}")
    suspend fun unblockUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: String,
    ): OkResponse
}

fun buildApiService(baseUrl: String, tokenStore: TokenStore): ApiService {
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .authenticator(TokenAuthenticator(tokenStore, baseUrl))
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
