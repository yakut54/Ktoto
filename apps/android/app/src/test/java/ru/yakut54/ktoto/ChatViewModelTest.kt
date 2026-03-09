@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package ru.yakut54.ktoto

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.Conversation
import ru.yakut54.ktoto.data.model.Message
import ru.yakut54.ktoto.data.model.MessagesReadEvent
import ru.yakut54.ktoto.data.model.Sender
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.ui.chat.ChatViewModel
import ru.yakut54.ktoto.ui.chat.VoiceState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ChatViewModel] — messages flow.
 *
 * Coverage: send / delete / deleteForMe / edit / forward / reply / socket events /
 * partnerOnline / voice state / state helpers.
 */
class ChatViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val api: ApiService = mockk(relaxed = true)
    private val tokenStore: TokenStore = mockk(relaxed = true)
    private val socketManager: SocketManager = mockk(relaxed = true)

    // ── Socket emission pipes ─────────────────────────────────────────────────

    private val _msgsFlow = MutableSharedFlow<Message>()
    private val _typingFlow = MutableSharedFlow<Pair<String, String>>()
    private val _editedFlow = MutableSharedFlow<Message>()
    private val _deletedFlow = MutableSharedFlow<Pair<String, String>>()
    private val _readFlow = MutableSharedFlow<MessagesReadEvent>()
    private val _onlineFlow = MutableStateFlow<Set<String>>(emptySet())

    private lateinit var viewModel: ChatViewModel

    // ── Setup / teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { socketManager.messages } returns _msgsFlow
        every { socketManager.typing } returns _typingFlow
        every { socketManager.editedMessages } returns _editedFlow
        every { socketManager.deletedMessageIds } returns _deletedFlow
        every { socketManager.messagesRead } returns _readFlow
        every { socketManager.onlineUsers } returns _onlineFlow

        every { tokenStore.accessToken } returns flowOf("test-token")
        every { tokenStore.userId } returns flowOf("user-1")
        every { tokenStore.username } returns flowOf("alice")

        coEvery { api.getMessages(any(), any()) } returns emptyList()
        coEvery { api.getConversations(any()) } returns emptyList()
        coEvery { api.markConversationRead(any(), any()) } returns emptyMap()

        viewModel = ChatViewModel(api, tokenStore, socketManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun msg(
        id: String = "msg-1",
        content: String = "Hello",
        type: String = "text",
        senderId: String = "user-1",
        senderName: String = "alice",
        convId: String = "conv-1",
        readByOthers: Boolean = false,
        createdAt: String = "2024-01-01T00:00:00Z",
    ) = Message(
        id = id,
        content = content,
        type = type,
        createdAt = createdAt,
        editedAt = null,
        replyToId = null,
        sender = Sender(id = senderId, username = senderName, avatarUrl = null),
        conversationId = convId,
        readByOthers = readByOthers,
    )

    private fun conv(id: String = "c-1") = Conversation(
        id = id, name = null, type = "direct", avatarUrl = null,
        updatedAt = "2024-01-01T00:00:00Z", lastMessage = null, unreadCount = 0,
    )

    // ══════════════════════════════════════════════════════════════════════════
    // Init / loadHistory
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `init - calls getMessages API`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        coVerify { api.getMessages(any(), "conv-1") }
    }

    @Test
    fun `init - sets messages from API response`() = runTest(testDispatcher) {
        val m = msg()
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        assertEquals(listOf(m), viewModel.messages.value)
    }

    @Test
    fun `init - discovers otherUserId from history messages`() = runTest(testDispatcher) {
        val bobMsg = msg(senderId = "user-2", senderName = "bob")
        coEvery { api.getMessages(any(), any()) } returns listOf(bobMsg)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _onlineFlow.value = setOf("user-2")
        advanceUntilIdle()
        assertTrue(viewModel.partnerOnline.value)
    }

    @Test
    fun `init with explicit otherUserId - partnerOnline reacts immediately`() = runTest(testDispatcher) {
        viewModel.init("conv-1", "user-2")
        advanceUntilIdle()
        _onlineFlow.value = setOf("user-2")
        advanceUntilIdle()
        assertTrue(viewModel.partnerOnline.value)
    }

    @Test
    fun `init - empty API response - messages remain empty`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `init - API failure - does not crash, messages stay empty`() = runTest(testDispatcher) {
        coEvery { api.getMessages(any(), any()) } throws RuntimeException("network error")
        viewModel.init("conv-1")
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // sendMessage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `sendMessage - blank content - no API call`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        viewModel.sendMessage("   ")
        advanceUntilIdle()
        coVerify(exactly = 0) { api.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `sendMessage - empty string - no API call`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        viewModel.sendMessage("")
        advanceUntilIdle()
        coVerify(exactly = 0) { api.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `sendMessage - optimistic message added synchronously before API returns`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.sendMessage("Hello")
        // Do NOT advance — optimistic added in synchronous section before launch{}
        val msgs = viewModel.messages.value
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].id.startsWith("temp_"))
        assertFalse(msgs[0].isDelivered)
        assertEquals("Hello", msgs[0].content)
    }

    @Test
    fun `sendMessage - success replaces optimistic with server message`() = runTest(testDispatcher) {
        val confirmed = msg(id = "server-1", content = "Hello")
        coEvery { api.sendMessage(any(), any(), any()) } returns confirmed
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.sendMessage("Hello")
        advanceUntilIdle()
        val msgs = viewModel.messages.value
        assertEquals(1, msgs.size)
        assertEquals("server-1", msgs[0].id)
        assertTrue(msgs[0].isDelivered)
    }

    @Test
    fun `sendMessage - API failure removes optimistic message`() = runTest(testDispatcher) {
        coEvery { api.sendMessage(any(), any(), any()) } throws RuntimeException("500")
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.sendMessage("Hello")
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `sendMessage - clears replyTo before coroutine launch`() = runTest(testDispatcher) {
        val reply = msg(id = "original")
        coEvery { api.sendMessage(any(), any(), any()) } returns msg(id = "s1")
        viewModel.init("conv-1")
        viewModel.setReplyTo(reply)
        assertEquals(reply, viewModel.replyTo.value)
        viewModel.sendMessage("Reply text")
        // replyTo cleared synchronously BEFORE launch{}
        assertNull(viewModel.replyTo.value)
    }

    @Test
    fun `sendMessage - sends reply_to_id from active replyTo`() = runTest(testDispatcher) {
        val reply = msg(id = "original")
        coEvery { api.sendMessage(any(), any(), any()) } returns msg(id = "s1")
        viewModel.init("conv-1")
        viewModel.setReplyTo(reply)
        viewModel.sendMessage("Reply!")
        advanceUntilIdle()
        coVerify { api.sendMessage(any(), any(), match { it.reply_to_id == "original" }) }
    }

    @Test
    fun `sendMessage - socket delivers first - no duplicate in list`() = runTest(testDispatcher) {
        val confirmed = msg(id = "server-1", convId = "conv-1")
        coEvery { api.sendMessage(any(), any(), any()) } returns confirmed
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.sendMessage("Hello")
        // socket delivers the confirmed message before API coroutine runs
        _msgsFlow.emit(confirmed)
        advanceUntilIdle()
        assertEquals(1, viewModel.messages.value.filter { it.id == "server-1" }.size)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteMessage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `deleteMessage - adds msgId to deletingIds immediately`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        viewModel.deleteMessage("msg-1")
        assertTrue(viewModel.deletingIds.value.contains("msg-1"))
    }

    @Test
    fun `deleteMessage - calls deleteMessage API`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.deleteMessage("msg-1")
        advanceUntilIdle()
        coVerify { api.deleteMessage(any(), "conv-1", "msg-1") }
    }

    @Test
    fun `deleteMessage - removes message from list after 280ms delay`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        assertEquals(1, viewModel.messages.value.size)
        viewModel.deleteMessage("msg-1")
        advanceTimeBy(100)
        assertEquals(1, viewModel.messages.value.size) // still animating
        advanceTimeBy(200) // 300ms total
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `deleteMessage - removes msgId from deletingIds after removal`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.deleteMessage("msg-1")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertFalse(viewModel.deletingIds.value.contains("msg-1"))
    }

    @Test
    fun `deleteMessage - API failure keeps message and clears deletingIds`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        coEvery { api.deleteMessage(any(), any(), any()) } throws RuntimeException("403")
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.deleteMessage("msg-1")
        advanceUntilIdle()
        assertEquals(1, viewModel.messages.value.size)
        assertFalse(viewModel.deletingIds.value.contains("msg-1"))
    }

    @Test
    fun `deleteMessage - clears selectedMessage`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1")
        viewModel.selectMessage(m)
        assertEquals(m, viewModel.selectedMessage.value)
        viewModel.deleteMessage("msg-1")
        assertNull(viewModel.selectedMessage.value)
    }

    @Test
    fun `deleteMessage - multiple concurrent deletes all land in deletingIds`() = runTest(testDispatcher) {
        val msgs = listOf(msg("m1"), msg("m2"), msg("m3"))
        coEvery { api.getMessages(any(), any()) } returns msgs
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.deleteMessage("m1")
        viewModel.deleteMessage("m2")
        assertTrue(viewModel.deletingIds.value.containsAll(listOf("m1", "m2")))
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(1, viewModel.messages.value.size)
        assertEquals("m3", viewModel.messages.value[0].id)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteMessageForMe
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `deleteMessageForMe - does NOT call API`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        viewModel.deleteMessageForMe("msg-1")
        advanceUntilIdle()
        coVerify(exactly = 0) { api.deleteMessage(any(), any(), any()) }
    }

    @Test
    fun `deleteMessageForMe - adds msgId to deletingIds`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        viewModel.deleteMessageForMe("msg-1")
        assertTrue(viewModel.deletingIds.value.contains("msg-1"))
    }

    @Test
    fun `deleteMessageForMe - removes from list after 280ms`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.deleteMessageForMe("msg-1")
        advanceTimeBy(100)
        assertEquals(1, viewModel.messages.value.size)
        advanceTimeBy(200)
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `deleteMessageForMe - clears selectedMessage`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1")
        viewModel.selectMessage(m)
        viewModel.deleteMessageForMe("msg-1")
        assertNull(viewModel.selectedMessage.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // saveEdit
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `saveEdit - no editing message - no API call`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        viewModel.saveEdit("New content")
        advanceUntilIdle()
        coVerify(exactly = 0) { api.editMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `saveEdit - blank content - no API call`() = runTest(testDispatcher) {
        viewModel.startEditing(msg())
        viewModel.init("conv-1")
        viewModel.saveEdit("   ")
        advanceUntilIdle()
        coVerify(exactly = 0) { api.editMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `saveEdit - calls editMessage API with correct args`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1")
        val updated = m.copy(content = "Edited", editedAt = "2024-01-01T00:01:00Z")
        coEvery { api.editMessage(any(), any(), any(), any()) } returns updated
        viewModel.init("conv-1")
        viewModel.startEditing(m)
        viewModel.saveEdit("Edited")
        advanceUntilIdle()
        coVerify { api.editMessage(any(), "conv-1", "msg-1", match { it.content == "Edited" }) }
    }

    @Test
    fun `saveEdit - updates content and editedAt in messages list`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", content = "Original")
        val updated = m.copy(content = "Edited", editedAt = "2024-01-01T00:01:00Z")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        coEvery { api.editMessage(any(), any(), any(), any()) } returns updated
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.startEditing(m)
        viewModel.saveEdit("Edited")
        advanceUntilIdle()
        val found = viewModel.messages.value.first { it.id == "msg-1" }
        assertEquals("Edited", found.content)
        assertEquals("2024-01-01T00:01:00Z", found.editedAt)
    }

    @Test
    fun `saveEdit - clears editingMessage synchronously`() = runTest(testDispatcher) {
        val m = msg()
        coEvery { api.editMessage(any(), any(), any(), any()) } returns m.copy(content = "E")
        viewModel.startEditing(m)
        assertEquals(m, viewModel.editingMessage.value)
        viewModel.saveEdit("E")
        assertNull(viewModel.editingMessage.value)
    }

    @Test
    fun `saveEdit - API failure does not crash, message content unchanged`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", content = "Original")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        coEvery { api.editMessage(any(), any(), any(), any()) } throws RuntimeException("403")
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.startEditing(m)
        viewModel.saveEdit("New")
        advanceUntilIdle()
        assertEquals("Original", viewModel.messages.value.first { it.id == "msg-1" }.content)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // forwardMessage
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `forwardMessage - same conv - appended to messages`() = runTest(testDispatcher) {
        val forwarded = msg(id = "fwd-1", convId = "conv-1")
        coEvery { api.sendMessage(any(), "conv-1", any()) } returns forwarded
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.forwardMessage(msg(id = "orig"), "conv-1")
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.any { it.id == "fwd-1" })
    }

    @Test
    fun `forwardMessage - different conv - NOT added to current list`() = runTest(testDispatcher) {
        val forwarded = msg(id = "fwd-1", convId = "conv-2")
        coEvery { api.sendMessage(any(), "conv-2", any()) } returns forwarded
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.forwardMessage(msg(id = "orig"), "conv-2")
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.none { it.id == "fwd-1" })
    }

    @Test
    fun `forwardMessage - sends forward_message_id`() = runTest(testDispatcher) {
        coEvery { api.sendMessage(any(), any(), any()) } returns msg(id = "fwd-1")
        viewModel.init("conv-1")
        viewModel.forwardMessage(msg(id = "orig-99"), "conv-1")
        advanceUntilIdle()
        coVerify { api.sendMessage(any(), any(), match { it.forward_message_id == "orig-99" }) }
    }

    @Test
    fun `forwardMessage - does not duplicate if already in list`() = runTest(testDispatcher) {
        val forwarded = msg(id = "fwd-1", convId = "conv-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(forwarded)
        coEvery { api.sendMessage(any(), "conv-1", any()) } returns forwarded
        viewModel.init("conv-1")
        advanceUntilIdle()
        viewModel.forwardMessage(msg(id = "orig"), "conv-1")
        advanceUntilIdle()
        assertEquals(1, viewModel.messages.value.filter { it.id == "fwd-1" }.size)
    }

    @Test
    fun `forwardMessage - API failure does not crash`() = runTest(testDispatcher) {
        coEvery { api.sendMessage(any(), any(), any()) } throws RuntimeException("error")
        viewModel.init("conv-1")
        viewModel.forwardMessage(msg(), "conv-1")
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Socket — new_message
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `socket new_message - appended to messages`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _msgsFlow.emit(msg(id = "socket-1", convId = "conv-1"))
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.any { it.id == "socket-1" })
    }

    @Test
    fun `socket new_message - duplicate id NOT added twice`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", convId = "conv-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _msgsFlow.emit(m)
        advanceUntilIdle()
        assertEquals(1, viewModel.messages.value.size)
    }

    @Test
    fun `socket new_message - from other conversation ignored`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _msgsFlow.emit(msg(id = "m-other", convId = "conv-OTHER"))
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `socket new_message - calls markConversationRead`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _msgsFlow.emit(msg(id = "m1", convId = "conv-1"))
        advanceUntilIdle()
        coVerify { api.markConversationRead(any(), "conv-1") }
    }

    @Test
    fun `socket delivers multiple messages - all appended in order`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _msgsFlow.emit(msg(id = "m1", convId = "conv-1"))
        _msgsFlow.emit(msg(id = "m2", convId = "conv-1"))
        _msgsFlow.emit(msg(id = "m3", convId = "conv-1"))
        advanceUntilIdle()
        assertEquals(listOf("m1", "m2", "m3"), viewModel.messages.value.map { it.id })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Socket — typing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `socket typing - sets isTyping true`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _typingFlow.emit("conv-1" to "user-2")
        advanceUntilIdle()
        assertTrue(viewModel.isTyping.value)
    }

    @Test
    fun `socket typing - resets to false after 3000ms`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _typingFlow.emit("conv-1" to "user-2")
        advanceUntilIdle()
        assertTrue(viewModel.isTyping.value)
        advanceTimeBy(3001)
        advanceUntilIdle()
        assertFalse(viewModel.isTyping.value)
    }

    @Test
    fun `socket typing - still true at 2999ms`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _typingFlow.emit("conv-1" to "user-2")
        advanceUntilIdle()
        advanceTimeBy(2999)
        advanceUntilIdle()
        assertTrue(viewModel.isTyping.value)
    }

    @Test
    fun `socket typing - other conv does not trigger isTyping`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _typingFlow.emit("conv-OTHER" to "user-2")
        advanceUntilIdle()
        assertFalse(viewModel.isTyping.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Socket — editedMessages
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `socket editedMessages - updates content and editedAt`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", content = "Original", convId = "conv-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        val edited = m.copy(content = "Edited", editedAt = "2024-01-01T00:01:00Z")
        _editedFlow.emit(edited)
        advanceUntilIdle()
        val found = viewModel.messages.value.first { it.id == "msg-1" }
        assertEquals("Edited", found.content)
        assertEquals("2024-01-01T00:01:00Z", found.editedAt)
    }

    @Test
    fun `socket editedMessages - unknown message id is no-op`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _editedFlow.emit(msg(id = "unknown", content = "Edited", convId = "conv-1"))
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `socket editedMessages - other conv filtered out`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", content = "Original", convId = "conv-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _editedFlow.emit(m.copy(content = "Hacked", conversationId = "conv-OTHER"))
        advanceUntilIdle()
        assertEquals("Original", viewModel.messages.value.first { it.id == "msg-1" }.content)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Socket — deletedMessageIds
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `socket deletedMessageIds - immediately added to deletingIds`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        _deletedFlow.emit("msg-1" to "conv-1")
        assertTrue(viewModel.deletingIds.value.contains("msg-1"))
    }

    @Test
    fun `socket deletedMessageIds - removes message after 280ms`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", convId = "conv-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _deletedFlow.emit("msg-1" to "conv-1")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.isEmpty())
        assertFalse(viewModel.deletingIds.value.contains("msg-1"))
    }

    @Test
    fun `socket deletedMessageIds - still in list at 100ms`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", convId = "conv-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _deletedFlow.emit("msg-1" to "conv-1")
        advanceTimeBy(100)
        advanceUntilIdle()
        assertEquals(1, viewModel.messages.value.size)
    }

    @Test
    fun `socket deletedMessageIds - other conv is ignored`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", convId = "conv-1")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _deletedFlow.emit("msg-1" to "conv-OTHER")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(1, viewModel.messages.value.size) // not removed
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Socket — messagesRead
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `socket messagesRead - marks own messages as readByOthers`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", senderId = "user-1", readByOthers = false,
            createdAt = "2024-01-01T00:00:00Z")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle() // wait for currentUserId to be set
        _readFlow.emit(MessagesReadEvent("conv-1", "user-2", "2024-01-01T01:00:00Z"))
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.first { it.id == "msg-1" }.readByOthers)
    }

    @Test
    fun `socket messagesRead - event from self (readerId==currentUser) is ignored`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", senderId = "user-1", readByOthers = false,
            createdAt = "2024-01-01T00:00:00Z")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _readFlow.emit(MessagesReadEvent("conv-1", "user-1", "2024-01-01T01:00:00Z"))
        advanceUntilIdle()
        assertFalse(viewModel.messages.value.first { it.id == "msg-1" }.readByOthers)
    }

    @Test
    fun `socket messagesRead - other conv event is ignored`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", senderId = "user-1", readByOthers = false,
            createdAt = "2024-01-01T00:00:00Z")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _readFlow.emit(MessagesReadEvent("conv-OTHER", "user-2", "2024-01-01T01:00:00Z"))
        advanceUntilIdle()
        assertFalse(viewModel.messages.value.first { it.id == "msg-1" }.readByOthers)
    }

    @Test
    fun `socket messagesRead - messages after readAt not marked`() = runTest(testDispatcher) {
        // Message created AFTER readAt — should NOT be marked
        val m = msg(id = "msg-1", senderId = "user-1", readByOthers = false,
            createdAt = "2024-01-02T00:00:00Z")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _readFlow.emit(MessagesReadEvent("conv-1", "user-2", "2024-01-01T00:00:00Z"))
        advanceUntilIdle()
        assertFalse(viewModel.messages.value.first { it.id == "msg-1" }.readByOthers)
    }

    @Test
    fun `socket messagesRead - partner messages not affected (only sender==self)`() = runTest(testDispatcher) {
        val m = msg(id = "msg-1", senderId = "user-2", readByOthers = false,
            createdAt = "2024-01-01T00:00:00Z")
        coEvery { api.getMessages(any(), any()) } returns listOf(m)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _readFlow.emit(MessagesReadEvent("conv-1", "user-3", "2024-01-01T01:00:00Z"))
        advanceUntilIdle()
        assertFalse(viewModel.messages.value.first { it.id == "msg-1" }.readByOthers)
    }

    @Test
    fun `socket messagesRead - multiple own messages all marked`() = runTest(testDispatcher) {
        val m1 = msg(id = "m1", senderId = "user-1", createdAt = "2024-01-01T00:00:00Z")
        val m2 = msg(id = "m2", senderId = "user-1", createdAt = "2024-01-01T00:01:00Z")
        coEvery { api.getMessages(any(), any()) } returns listOf(m1, m2)
        viewModel.init("conv-1")
        advanceUntilIdle()
        _readFlow.emit(MessagesReadEvent("conv-1", "user-2", "2024-01-01T01:00:00Z"))
        advanceUntilIdle()
        assertTrue(viewModel.messages.value.all { it.readByOthers })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // partnerOnline
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `partnerOnline - initially false`() = runTest(testDispatcher) {
        viewModel.init("conv-1")
        advanceUntilIdle()
        assertFalse(viewModel.partnerOnline.value)
    }

    @Test
    fun `partnerOnline - true when otherId in onlineUsers`() = runTest(testDispatcher) {
        viewModel.init("conv-1", "user-2")
        advanceUntilIdle()
        _onlineFlow.value = setOf("user-2")
        advanceUntilIdle()
        assertTrue(viewModel.partnerOnline.value)
    }

    @Test
    fun `partnerOnline - false when otherId absent from onlineUsers`() = runTest(testDispatcher) {
        viewModel.init("conv-1", "user-2")
        advanceUntilIdle()
        _onlineFlow.value = setOf("user-3", "user-4")
        advanceUntilIdle()
        assertFalse(viewModel.partnerOnline.value)
    }

    @Test
    fun `partnerOnline - updates dynamically on online set change`() = runTest(testDispatcher) {
        viewModel.init("conv-1", "user-2")
        advanceUntilIdle()
        _onlineFlow.value = setOf("user-2")
        advanceUntilIdle()
        assertTrue(viewModel.partnerOnline.value)
        _onlineFlow.value = emptySet()
        advanceUntilIdle()
        assertFalse(viewModel.partnerOnline.value)
    }

    @Test
    fun `partnerOnline - blank otherId is never online`() = runTest(testDispatcher) {
        viewModel.init("conv-1") // no otherId
        advanceUntilIdle()
        _onlineFlow.value = setOf("", "user-2", "user-3")
        advanceUntilIdle()
        assertFalse(viewModel.partnerOnline.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // State helpers
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `initial state - messages is empty`() {
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `initial state - voiceState is IDLE`() {
        assertEquals(VoiceState.IDLE, viewModel.voiceState.value)
    }

    @Test
    fun `initial state - sending is false`() {
        assertFalse(viewModel.sending.value)
    }

    @Test
    fun `initial state - isTyping is false`() {
        assertFalse(viewModel.isTyping.value)
    }

    @Test
    fun `initial state - uploadProgress is null`() {
        assertNull(viewModel.uploadProgress.value)
    }

    @Test
    fun `initial state - deletingIds is empty`() {
        assertTrue(viewModel.deletingIds.value.isEmpty())
    }

    @Test
    fun `setReplyTo - sets replyTo`() {
        val m = msg()
        viewModel.setReplyTo(m)
        assertEquals(m, viewModel.replyTo.value)
    }

    @Test
    fun `clearReplyTo - clears replyTo`() {
        viewModel.setReplyTo(msg())
        viewModel.clearReplyTo()
        assertNull(viewModel.replyTo.value)
    }

    @Test
    fun `startEditing - sets editingMessage`() {
        val m = msg()
        viewModel.startEditing(m)
        assertEquals(m, viewModel.editingMessage.value)
    }

    @Test
    fun `cancelEditing - clears editingMessage`() {
        viewModel.startEditing(msg())
        viewModel.cancelEditing()
        assertNull(viewModel.editingMessage.value)
    }

    @Test
    fun `selectMessage - sets selectedMessage`() {
        val m = msg()
        viewModel.selectMessage(m)
        assertEquals(m, viewModel.selectedMessage.value)
    }

    @Test
    fun `clearSelection - clears selectedMessage`() {
        viewModel.selectMessage(msg())
        viewModel.clearSelection()
        assertNull(viewModel.selectedMessage.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Voice state (no Android context needed)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `lockRecording - no effect when state is IDLE`() {
        assertEquals(VoiceState.IDLE, viewModel.voiceState.value)
        viewModel.lockRecording()
        assertEquals(VoiceState.IDLE, viewModel.voiceState.value)
    }

    @Test
    fun `cancelRecording - from IDLE stays IDLE`() {
        viewModel.cancelRecording()
        assertEquals(VoiceState.IDLE, viewModel.voiceState.value)
    }

    @Test
    fun `cancelRecording - resets recordingSeconds to 0`() {
        viewModel.cancelRecording()
        assertEquals(0, viewModel.recordingSeconds.value)
    }

    @Test
    fun `cancelRecording - resets previewDuration to 0`() {
        viewModel.cancelRecording()
        assertEquals(0, viewModel.previewDuration.value)
    }

    @Test
    fun `cancelRecording - resets previewPlaying to false`() {
        viewModel.cancelRecording()
        assertFalse(viewModel.previewPlaying.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // notifyTyping / loadConversationsForPicker
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `notifyTyping - delegates to socketManager sendTyping`() {
        viewModel.init("conv-1")
        viewModel.notifyTyping()
        verify { socketManager.sendTyping("conv-1") }
    }

    @Test
    fun `loadConversationsForPicker - calls getConversations and populates state`() = runTest(testDispatcher) {
        val convs = listOf(conv("c1"), conv("c2"))
        coEvery { api.getConversations(any()) } returns convs
        viewModel.init("conv-1")
        viewModel.loadConversationsForPicker()
        advanceUntilIdle()
        assertEquals(convs, viewModel.conversationsForPicker.value)
    }

    @Test
    fun `loadConversationsForPicker - API failure does not crash`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } throws RuntimeException("network error")
        viewModel.init("conv-1")
        viewModel.loadConversationsForPicker()
        advanceUntilIdle()
        assertTrue(viewModel.conversationsForPicker.value.isEmpty())
    }
}
