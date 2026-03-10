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
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.ui.conversations.ConversationsState
import ru.yakut54.ktoto.ui.conversations.ConversationsViewModel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ConversationsViewModel].
 *
 * Coverage: load / refresh / delete / logout / userId / username /
 * onlineUsers / socket typing / socket newConversation.
 */
class ConversationsViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    // ── Mocks ─────────────────────────────────────────────────────────────────

    private val api: ApiService = mockk(relaxed = true)
    private val tokenStore: TokenStore = mockk(relaxed = true)
    private val socketManager: SocketManager = mockk(relaxed = true)

    // ── Socket pipes ──────────────────────────────────────────────────────────

    // extraBufferCapacity=1 prevents emit() from blocking when the collector
    // has a delay() in a nested launch (same pattern as ChatViewModelTest)
    private val _typingFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    private val _newConvFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val _onlineFlow = MutableStateFlow<Set<String>>(emptySet())

    private lateinit var viewModel: ConversationsViewModel

    // ── Setup / teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { socketManager.typing } returns _typingFlow
        every { socketManager.newConversation } returns _newConvFlow
        every { socketManager.onlineUsers } returns _onlineFlow

        every { tokenStore.accessToken } returns flowOf("test-token")
        every { tokenStore.userId } returns flowOf("user-1")
        every { tokenStore.username } returns flowOf("alice")

        coEvery { api.getConversations(any()) } returns emptyList()

        viewModel = ConversationsViewModel(api, tokenStore, socketManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun conv(
        id: String = "c-1",
        type: String = "direct",
        name: String? = null,
        otherId: String? = null,
        otherStatus: String? = null,
        unreadCount: Int = 0,
    ) = Conversation(
        id = id, name = name, type = type, avatarUrl = null,
        updatedAt = "2024-01-01T00:00:00Z", lastMessage = null,
        unreadCount = unreadCount, otherId = otherId, otherStatus = otherStatus,
    )

    // ══════════════════════════════════════════════════════════════════════════
    // Initial state
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `initial state is Loading`() {
        assertIs<ConversationsState.Loading>(viewModel.state.value)
    }

    @Test
    fun `initial refreshing is false`() {
        assertFalse(viewModel.refreshing.value)
    }

    @Test
    fun `initial typingConvIds is empty`() {
        assertTrue(viewModel.typingConvIds.value.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // load()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `load - success sets conversations`() = runTest(testDispatcher) {
        val convs = listOf(conv("c-1"), conv("c-2"))
        coEvery { api.getConversations(any()) } returns convs
        viewModel.load()
        val state = viewModel.state.value
        assertIs<ConversationsState.Success>(state)
        assertEquals(convs, state.items)
    }

    @Test
    fun `load - calls API with bearer token`() = runTest(testDispatcher) {
        viewModel.load()
        coVerify { api.getConversations("Bearer test-token") }
    }

    @Test
    fun `load - failure sets Error when no prior data`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } throws RuntimeException("Network error")
        viewModel.load()
        val state = viewModel.state.value
        assertIs<ConversationsState.Error>(state)
        assertEquals("Network error", state.message)
    }

    @Test
    fun `load - failure keeps Success when data already loaded`() = runTest(testDispatcher) {
        val convs = listOf(conv("c-1"))
        coEvery { api.getConversations(any()) } returns convs
        viewModel.load()
        coEvery { api.getConversations(any()) } throws RuntimeException("error")
        viewModel.load()
        val state = viewModel.state.value
        assertIs<ConversationsState.Success>(state)
        assertEquals(convs, state.items)
    }

    @Test
    fun `load - does not set Loading when data already present`() = runTest(testDispatcher) {
        val convs = listOf(conv("c-1"))
        coEvery { api.getConversations(any()) } returns convs
        viewModel.load() // first load → Success

        val newConvs = listOf(conv("c-2"))
        coEvery { api.getConversations(any()) } returns newConvs
        viewModel.load() // second load — must skip Loading intermediate state
        // still Success (not reverted to Loading)
        assertIs<ConversationsState.Success>(viewModel.state.value)
    }

    @Test
    fun `load - seeds online users from REST otherStatus`() = runTest(testDispatcher) {
        val convs = listOf(
            conv("c-1", otherId = "user-2", otherStatus = "online"),
            conv("c-2", otherId = "user-3", otherStatus = "offline"),
            conv("c-3", otherId = null),
        )
        coEvery { api.getConversations(any()) } returns convs
        viewModel.load()
        verify { socketManager.initOnlineUsers(setOf("user-2")) }
    }

    @Test
    fun `load - empty list with no online users`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } returns emptyList()
        viewModel.load()
        verify { socketManager.initOnlineUsers(emptySet()) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // refresh()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `refresh - updates state to Success`() = runTest(testDispatcher) {
        val convs = listOf(conv("c-1"))
        coEvery { api.getConversations(any()) } returns convs
        viewModel.refresh()
        val state = viewModel.state.value
        assertIs<ConversationsState.Success>(state)
        assertEquals(convs, state.items)
    }

    @Test
    fun `refresh - refreshing is false after completion`() = runTest(testDispatcher) {
        viewModel.refresh()
        assertFalse(viewModel.refreshing.value)
    }

    @Test
    fun `refresh - swallows error, does not emit Error state`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } throws RuntimeException("error")
        viewModel.refresh()
        // refresh() catches all exceptions — state must not become Error
        assertFalse(viewModel.state.value is ConversationsState.Error)
    }

    @Test
    fun `refresh - refreshing is false even after error`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } throws RuntimeException("error")
        viewModel.refresh()
        assertFalse(viewModel.refreshing.value)
    }

    @Test
    fun `refresh - overwrites previous Success with new list`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } returns listOf(conv("c-1"))
        viewModel.load()
        val newConvs = listOf(conv("c-2"), conv("c-3"))
        coEvery { api.getConversations(any()) } returns newConvs
        viewModel.refresh()
        assertEquals(newConvs, (viewModel.state.value as ConversationsState.Success).items)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteConversation()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `deleteConversation - removes conversation from list`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } returns listOf(conv("c-1"), conv("c-2"))
        viewModel.load()
        viewModel.deleteConversation("c-1")
        val state = viewModel.state.value as ConversationsState.Success
        assertEquals(listOf(conv("c-2")), state.items)
    }

    @Test
    fun `deleteConversation - calls deleteConversation API`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } returns listOf(conv("c-1"))
        viewModel.load()
        viewModel.deleteConversation("c-1")
        coVerify { api.deleteConversation(any(), "c-1") }
    }

    @Test
    fun `deleteConversation - reloads list on API failure`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } returns listOf(conv("c-1"))
        viewModel.load()
        coEvery { api.deleteConversation(any(), any()) } throws RuntimeException("403")
        val reloaded = listOf(conv("c-1"), conv("c-2"))
        coEvery { api.getConversations(any()) } returns reloaded
        viewModel.deleteConversation("c-1")
        assertEquals(reloaded, (viewModel.state.value as ConversationsState.Success).items)
    }

    @Test
    fun `deleteConversation - no-op when state is not Success`() = runTest(testDispatcher) {
        // state is Loading by default
        viewModel.deleteConversation("c-1")
        assertIs<ConversationsState.Loading>(viewModel.state.value)
    }

    @Test
    fun `deleteConversation - unknown id leaves list unchanged`() = runTest(testDispatcher) {
        val convs = listOf(conv("c-1"), conv("c-2"))
        coEvery { api.getConversations(any()) } returns convs
        viewModel.load()
        viewModel.deleteConversation("unknown-id")
        assertEquals(convs, (viewModel.state.value as ConversationsState.Success).items)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // logout()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `logout - calls tokenStore clear`() = runTest(testDispatcher) {
        viewModel.logout {}
        coVerify { tokenStore.clear() }
    }

    @Test
    fun `logout - invokes onDone callback`() = runTest(testDispatcher) {
        var called = false
        viewModel.logout { called = true }
        assertTrue(called)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // userId / username / onlineUsers
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `userId - exposed from tokenStore`() {
        assertEquals("user-1", viewModel.userId.value)
    }

    @Test
    fun `username - exposed from tokenStore`() {
        assertEquals("alice", viewModel.username.value)
    }

    @Test
    fun `onlineUsers - reflects socketManager state`() {
        _onlineFlow.value = setOf("user-2", "user-3")
        assertEquals(setOf("user-2", "user-3"), viewModel.onlineUsers.value)
    }

    @Test
    fun `onlineUsers - updates dynamically`() {
        _onlineFlow.value = setOf("user-2")
        assertTrue(viewModel.onlineUsers.value.contains("user-2"))
        _onlineFlow.value = emptySet()
        assertTrue(viewModel.onlineUsers.value.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Socket — typing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `typing - adds convId to typingConvIds`() = runTest(testDispatcher) {
        _typingFlow.emit("conv-1" to "user-2")
        assertTrue(viewModel.typingConvIds.value.contains("conv-1"))
    }

    @Test
    fun `typing - resets after 3000ms`() = runTest(testDispatcher) {
        _typingFlow.emit("conv-1" to "user-2")
        assertTrue(viewModel.typingConvIds.value.contains("conv-1"))
        advanceTimeBy(3001)
        assertFalse(viewModel.typingConvIds.value.contains("conv-1"))
    }

    @Test
    fun `typing - still present at 2999ms`() = runTest(testDispatcher) {
        _typingFlow.emit("conv-1" to "user-2")
        advanceTimeBy(2999) // delay(3000) not fired yet — no advanceUntilIdle()
        assertTrue(viewModel.typingConvIds.value.contains("conv-1"))
    }

    @Test
    fun `typing - debounce resets timer on repeat event`() = runTest(testDispatcher) {
        _typingFlow.emit("conv-1" to "user-2")
        advanceTimeBy(2000)                       // 2s into first 3s window
        _typingFlow.emit("conv-1" to "user-2")    // resets the timer
        advanceTimeBy(2000)                       // 2s into new 3s window (4s from start) — first would have fired
        assertTrue(viewModel.typingConvIds.value.contains("conv-1"))
        advanceTimeBy(1001)                       // 3001ms from second emit → fires
        assertFalse(viewModel.typingConvIds.value.contains("conv-1"))
    }

    @Test
    fun `typing - multiple convIds tracked independently`() = runTest(testDispatcher) {
        _typingFlow.emit("conv-1" to "user-2")
        _typingFlow.emit("conv-2" to "user-3")
        assertTrue(viewModel.typingConvIds.value.containsAll(listOf("conv-1", "conv-2")))
    }

    @Test
    fun `typing - conv-1 expiry does not remove conv-2`() = runTest(testDispatcher) {
        _typingFlow.emit("conv-1" to "user-2")
        advanceTimeBy(1000)
        _typingFlow.emit("conv-2" to "user-3")    // timer for conv-2 starts 1s later
        advanceTimeBy(2001)                       // conv-1 expires (3001ms total), conv-2 still has 999ms
        assertFalse(viewModel.typingConvIds.value.contains("conv-1"))
        assertTrue(viewModel.typingConvIds.value.contains("conv-2"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Socket — newConversation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `newConversation socket event triggers load`() = runTest(testDispatcher) {
        val convs = listOf(conv("c-1"))
        coEvery { api.getConversations(any()) } returns convs
        _newConvFlow.emit("c-1")
        val state = viewModel.state.value
        assertIs<ConversationsState.Success>(state)
        assertEquals(convs, state.items)
    }

    @Test
    fun `newConversation - multiple events each reload conversations`() = runTest(testDispatcher) {
        coEvery { api.getConversations(any()) } returns listOf(conv("c-1"))
        _newConvFlow.emit("c-1")
        coEvery { api.getConversations(any()) } returns listOf(conv("c-1"), conv("c-2"))
        _newConvFlow.emit("c-2")
        assertEquals(2, (viewModel.state.value as ConversationsState.Success).items.size)
    }
}
