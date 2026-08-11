package com.opencode.remote.data.api.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionInfoDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `missing custom field defaults to true matching opencode`() {
        val input = """{
            "question": "Which approach?",
            "header": "Method",
            "options": [
                {"label": "A", "description": "Use A"},
                {"label": "B", "description": "Use B"}
            ]
        }"""
        val dto = json.decodeFromString<QuestionInfoDto>(input)
        assertTrue(dto.custom)
    }

    @Test
    fun `explicit custom true is honored`() {
        val input = """{
            "question": "Which approach?",
            "header": "Method",
            "custom": true
        }"""
        val dto = json.decodeFromString<QuestionInfoDto>(input)
        assertTrue(dto.custom)
    }

    @Test
    fun `explicit custom false is honored`() {
        val input = """{
            "question": "Which approach?",
            "header": "Method",
            "options": [{"label": "A", "description": "Use A"}],
            "custom": false
        }"""
        val dto = json.decodeFromString<QuestionInfoDto>(input)
        assertFalse(dto.custom)
    }

    @Test
    fun `full question asked event payload deserializes`() {
        val input = """{
            "directory": "D:\\proj",
            "payload": {
                "type": "question.asked",
                "properties": {
                    "id": "qst_abc",
                    "sessionID": "ses_xyz",
                    "questions": [
                        {
                            "question": "Pick a plan",
                            "header": "Plan",
                            "options": [
                                {"label": "Plan A", "description": "Fast"},
                                {"label": "Plan B", "description": "Safe"}
                            ],
                            "multiple": false,
                            "custom": true
                        }
                    ],
                    "tool": {"messageID": "msg_1", "callID": "call_1"}
                }
            }
        }"""
        val event = json.decodeFromString<ServerEvent>(input)
        val props = event.payload.properties
        assertEquals("question.asked", event.payload.type)
        assertEquals("qst_abc", props.id)
        assertEquals("ses_xyz", props.sessionID)
        assertEquals(1, props.questions?.size)
        assertEquals("Pick a plan", props.questions?.get(0)?.question)
        assertEquals("Plan", props.questions?.get(0)?.header)
        assertEquals(2, props.questions?.get(0)?.options?.size)
        assertEquals("Plan A", props.questions?.get(0)?.options?.get(0)?.label)
        assertFalse(props.questions?.get(0)?.multiple ?: true)
        assertTrue(props.questions?.get(0)?.custom ?: false)
        assertEquals("call_1", props.tool?.callID)
    }
}