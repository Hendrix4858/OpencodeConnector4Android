package com.opencode.remote.ui.chat

import com.opencode.remote.data.api.dto.MessagePart
import com.opencode.remote.data.api.dto.ToolState
import com.opencode.remote.ui.strings.AppLocale
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The question tool bubble renders questions/options plus the user's submitted answer,
 * which opencode persists in the tool part's metadata.answers.
 */
class ToolSummarizerQuestionTest {

    private fun questionPart(answers: List<List<String>>? = null): MessagePart {
        val input = buildJsonObject {
            put("questions", buildJsonArray {
                add(buildJsonObject {
                    put("question", JsonPrimitive("Which approach?"))
                    put("header", JsonPrimitive("Method"))
                    put("options", buildJsonArray {
                        add(buildJsonObject {
                            put("label", JsonPrimitive("A"))
                            put("description", JsonPrimitive("Use A"))
                        })
                        add(buildJsonObject {
                            put("label", JsonPrimitive("B"))
                            put("description", JsonPrimitive("Use B"))
                        })
                    })
                })
            })
        }
        val metadata = answers?.let {
            buildJsonObject {
                put("answers", buildJsonArray {
                    it.forEach { group ->
                        add(buildJsonArray { group.forEach { ans -> add(JsonPrimitive(ans)) } })
                    }
                })
            }
        }
        return MessagePart(type = "tool", tool = "question", state = ToolState(input = input, metadata = metadata))
    }

    @Test
    fun `question without answer renders questions and options`() {
        val text = ToolSummarizer.summarize(questionPart())
        assertTrue(text.contains("❓ Method: Which approach?"))
        assertTrue(text.contains("• A — Use A"))
        assertTrue(text.contains("• B — Use B"))
        assertFalse(text.contains(AppLocale.strings.questionYourAnswer))
    }

    @Test
    fun `answered question renders answer inline from metadata`() {
        val text = ToolSummarizer.summarize(questionPart(listOf(listOf("my custom answer"))))
        assertTrue(text.contains("❓ Method: Which approach?"))
        assertTrue(text.contains(AppLocale.strings.questionYourAnswer))
        assertTrue(text.contains("my custom answer"))
    }

    @Test
    fun `multi-value answer joins with comma`() {
        val text = ToolSummarizer.summarize(questionPart(listOf(listOf("A", "B"))))
        assertTrue(text.contains("A, B"))
    }
}