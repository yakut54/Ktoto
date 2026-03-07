package ru.yakut54.ktoto.data.model

data class LoginRequest(val username: String, val password: String)

data class RegisterRequest(val username: String, val email: String, val password: String)

data class ErrorResponse(val error: String?)

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

data class ReplyPreview(
    val id: String,
    val content: String?,
    val type: String?,
    val sender: Sender,
)

data class Attachment(
    val fileName: String,
    val fileSize: Long?,
    val mimeType: String?,
    val url: String,
    val thumbnailUrl: String?,
    val duration: Float?,
    val width: Int?,
    val height: Int?,
)

data class Message(
    val id: String,
    val content: String?,
    val type: String,
    val createdAt: String,
    val editedAt: String?,
    val replyToId: String?,
    val replyTo: ReplyPreview? = null,
    val sender: Sender,
    val conversationId: String,
    val attachment: Attachment? = null,
    val readByOthers: Boolean = false,
    /** false = optimistic (sent locally, not yet confirmed by server) */
    val isDelivered: Boolean = true,
)

data class MessagesReadEvent(
    val conversationId: String,
    val readerId: String,
    val readAt: String,
)

data class Sender(
    val id: String,
    val username: String,
    val avatarUrl: String?,
)

data class SendMessageRequest(
    val content: String = "",
    val type: String = "text",
    val reply_to_id: String? = null,
    val forward_message_id: String? = null,
)

data class EditMessageRequest(val content: String)

data class UserItem(val id: String, val username: String, val avatarUrl: String?)

data class CreateConversationRequest(
    val type: String,
    val userId: String? = null,
    val name: String? = null,
    val memberIds: List<String>? = null,
)

data class RefreshResponse(val accessToken: String, val refreshToken: String)
