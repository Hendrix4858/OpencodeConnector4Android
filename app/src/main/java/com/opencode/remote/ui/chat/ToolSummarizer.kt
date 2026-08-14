package com.opencode.remote.ui.chat

import com.opencode.remote.data.api.dto.MessagePart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Shared utility for building compact one-line summaries of tool invocations.
 *  Used by both [ChatViewModel] (streaming) and [ChatScreen] (completed messages). */
object ToolSummarizer {

    /** Build a summary line from a tool part's structured data.
     *  Uses [MessagePart.tool] (name) and [MessagePart.state.input] (filePath/command) to create compact display. */
    fun summarize(part: MessagePart): String {
        val toolName = part.tool ?: return "🔧 tool call"
        val input = part.state?.input

        val filePath = extractJsonString(input, "filePath")
        val command = extractJsonString(input, "command")
        val query = extractJsonString(input, "query")

        return when (toolName) {
            "edit" -> if (filePath != null) "📝 edit ${fileNameFromPath(filePath)}" else "📝 edit"
            "write" -> if (filePath != null) "📝 write ${fileNameFromPath(filePath)}" else "📝 write"
            "read" -> if (filePath != null) "📖 read ${fileNameFromPath(filePath)}" else "📖 read"
            "bash" -> if (command != null) "💻 bash: ${command.take(60)}" else "💻 bash"
            "grep" -> if (query != null) "🔍 grep: ${query.take(40)}" else "🔍 grep"
            "glob" -> if (query != null) "📂 glob: ${query.take(40)}" else "📂 glob"
            "ast_grep_search" -> "🔎 ast-grep"
            "lsp_diagnostics" -> "🩺 diagnostics"
            "look_at" -> "👁 look_at"
            "task" -> "🤖 task"
            "background_output" -> "📤 background"
            "background_cancel" -> "🚫 cancel"
            "todowrite" -> "📋 todo"
            "question" -> summarizeQuestion(part)
            else -> "🔧 $toolName"
        }
    }

    /** Render a question tool's content: each question with its options, plus the user's answer
     *  if present. opencode persists answers in the tool part's `metadata.answers`. */
    private fun summarizeQuestion(part: MessagePart): String {
        val input = part.state?.input
        val questions = try {
            (input as? JsonObject)?.get("questions") as? JsonArray
        } catch (_: Exception) {
            null
        } ?: return "❓ question"
        if (questions.isEmpty()) return "❓ question"

        val answers = parseAnswers(part.state?.metadata)
        val yourAnswer = com.opencode.remote.ui.strings.AppLocale.strings.questionYourAnswer

        val lines = questions.mapIndexedNotNull { index, el ->
            val obj = el as? JsonObject ?: return@mapIndexedNotNull null
            val q = (obj["question"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            val header = (obj["header"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            val title = when {
                q != null && header != null -> "$header: $q"
                header != null -> header
                else -> q
            } ?: return@mapIndexedNotNull null

            val options = (obj["options"] as? JsonArray)?.mapNotNull opt@ { opt ->
                val o = opt as? JsonObject ?: return@opt null
                val label = (o["label"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@opt null
                val desc = (o["description"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                if (desc != null) "  • $label — $desc" else "  • $label"
            } ?: emptyList()

            val answer = answers?.getOrNull(index)?.filter { it.isNotBlank() }

            buildString {
                append("❓ ").append(title)
                options.take(5).forEach { append("\n").append(it) }
                if (options.size > 5) append("\n  … 另有 ${options.size - 5} 个选项")
                if (!answer.isNullOrEmpty()) {
                    append("\n").append(yourAnswer).append(": ").append(answer.joinToString(", "))
                }
            }
        }
        return if (lines.isEmpty()) "❓ question" else lines.joinToString("\n\n")
    }

    /** Parse `{ "answers": [["..."], ...] }` from tool metadata into per-question answer lists. */
    private fun parseAnswers(metadata: JsonElement?): List<List<String>>? {
        val arr = try {
            (metadata as? JsonObject)?.get("answers") as? JsonArray
        } catch (_: Exception) {
            null
        } ?: return null
        if (arr.isEmpty()) return null
        val result = arr.mapNotNull { el ->
            val ans = el as? JsonArray ?: return@mapNotNull null
            ans.mapNotNull { it as? JsonPrimitive }.map { it.content }
        }
        return if (result.isEmpty()) null else result
    }

    /** Extract a one-line summary from raw tool call text for compact display label.
     *  Used as a fallback when tool data arrives as plain text rather than structured JSON. */
    fun summarizeText(text: String): String {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "tool call"
        val first = lines.first()
        val fileRegex = Regex("""[\w./\\-]+\.\w{1,10}""")
        val fileMatch = fileRegex.find(first)
        return when {
            first.contains("edit", ignoreCase = true) && fileMatch != null -> "edit ${fileMatch.value}"
            first.contains("write", ignoreCase = true) && fileMatch != null -> "write ${fileMatch.value}"
            first.contains("read", ignoreCase = true) && fileMatch != null -> "read ${fileMatch.value}"
            first.contains("bash", ignoreCase = true) -> "bash"
            first.contains("grep", ignoreCase = true) || first.contains("search", ignoreCase = true) -> "search"
            first.contains("glob", ignoreCase = true) -> "list files"
            else -> {
                val summary = lines.firstOrNull { it.isNotBlank() && !it.startsWith("{") && !it.startsWith("\"") }?.take(60)
                    ?: "tool call"
                summary
            }
        }
    }

    /** Extract a string field from a JsonElement input object. */
    private fun extractJsonString(element: kotlinx.serialization.json.JsonElement?, key: String): String? {
        return try {
            (element as? JsonObject)?.get(key)?.let {
                if (it is JsonPrimitive && it.isString) it.content else null
            }
        } catch (_: Exception) { null }
    }

    /** Extract just the filename from a full path. */
    private fun fileNameFromPath(path: String): String {
        val normalized = path.replace('\\', '/')
        return normalized.substringAfterLast('/')
    }
}
