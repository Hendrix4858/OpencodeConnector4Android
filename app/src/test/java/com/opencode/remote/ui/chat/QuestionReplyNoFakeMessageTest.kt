package com.opencode.remote.ui.chat

import android.content.Context
import com.opencode.remote.data.api.dto.QuestionInfoDto
import com.opencode.remote.data.api.dto.QuestionOptionDto
import com.opencode.remote.data.datastore.ConnectionPreferences
import com.opencode.remote.data.repository.OConnectorRepository
import com.opencode.remote.data.sse.SseEventBus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Answering/dismissing a question must NOT inject a fabricated user message into the chat.
 * opencode stores the answer in the question tool part's metadata.answers, which the
 * ToolSummarizer renders inside the tool bubble (see ToolSummarizerQuestionTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuestionReplyNoFakeMessageTest {

    private val eventBus = SseEventBus()
    private val repository = mockk<OConnectorRepository>(relaxed = true)
    private val connectionPreferences = mockk<ConnectionPreferences>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun newViewModel(): ChatViewModel {
        every { repository.currentGeneration } returns 1L
        coEvery { repository.getMessages(any(), any()) } returns emptyList()
        every { repository.activeSessionId } returns ""
        every { repository.activeSessionDirectory } returns null
        return ChatViewModel(repository, eventBus, connectionPreferences, context)
    }

    private fun setPendingQuestion(vm: ChatViewModel, id: String, questions: List<QuestionInfoDto>) {
        val stateFlow = getStateFlow(vm) as MutableStateFlow<ChatUiState>
        stateFlow.value = stateFlow.value.copy(
            sessionMeta = stateFlow.value.sessionMeta.copy(sessionId = "test-session-123"),
            chatDisplay = stateFlow.value.chatDisplay.copy(
                pendingQuestion = QuestionRequestData(id = id, sessionID = "test-session-123", questions = questions),
                isBlocked = true,
            ),
        )
    }

    private fun getStateFlow(vm: ChatViewModel): Any {
        val field = ChatViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        return field.get(vm)!!
    }

    @Test
    fun `reply question with custom answer clears blocking and injects no fake message`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val vm = newViewModel()
            coEvery { repository.replyQuestion("q-1", any(), any()) } returns Unit
            setPendingQuestion(vm, "q-1", listOf(
                QuestionInfoDto(
                    question = "Which approach?",
                    header = "Method",
                    options = listOf(QuestionOptionDto("A", "Use A"), QuestionOptionDto("B", "Use B")),
                ),
            ))
            vm.replyQuestion(listOf(listOf("my custom answer")))

            assertTrue(vm.uiState.value.messages.none { it.id.startsWith("local_q_") })
            assertNull(vm.uiState.value.pendingQuestion)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `reject question clears blocking and injects no fake message`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val vm = newViewModel()
            coEvery { repository.rejectQuestion("q-1", any()) } returns Unit
            setPendingQuestion(vm, "q-1", listOf(QuestionInfoDto(question = "Continue?")))
            vm.rejectQuestion()

            assertTrue(vm.uiState.value.messages.isEmpty())
            assertNull(vm.uiState.value.pendingQuestion)
        } finally {
            Dispatchers.resetMain()
        }
    }
}