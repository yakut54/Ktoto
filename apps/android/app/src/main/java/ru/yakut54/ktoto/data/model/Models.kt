package ru.yakut54.ktoto.data.model

data class LoginRequest(val username: String, val password: String)

data class RegisterRequest(val username: String, val email: String, val password: String)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
)

data class User(
    val id: String,
    val username: String,
    val email: String,
    val avatarUrl: String?,
)

data class Conversation(
    val id: String,
    val name: String?,
    val type: String,
    val avatarUrl: String?,
    val updatedAt: String,
    val lastMessage: LastMessage?,
    val unreadCount: Int,
)

data class LastMessage(
    val content: String?,
    val type: String?,
    val sentAt: String,
    val userId: String?,
)

data class Message(
    val id: String,
    val content: String?,
    val type: String,
    val createdAt: String,
    val editedAt: String?,
    val replyToId: String?,
    val sender: Sender,
    val conversationId: String,
)

data class Sender(
    val id: String,
    val username: String,
    val avatarUrl: String?,
)

data class SendMessageRequest(val content: String, val type: String = "text")

data class UserItem(val id: String, val username: String, val avatarUrl: String?)

data class CreateConversationRequest(
    val type: String,
    val userId: String? = null,
    val name: String? = null,
    val memberIds: List<String>? = null,
)
