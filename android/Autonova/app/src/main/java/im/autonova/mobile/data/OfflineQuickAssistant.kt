package im.autonova.mobile.data

/** Provides an immediate, on-device planning result when the user has not imported an LLM model yet. */
object OfflineQuickAssistant {
    fun respond(request: String, memory: String, knowledge: String): String {
        val normalized = request.trim().lowercase()
        val workflow = when {
            normalized.startsWith("build") || normalized.startsWith("create") -> "I created a local planning task. Start by defining the outcome, the materials you already have, and the smallest first deliverable."
            normalized.contains("research") || normalized.contains("search") -> "I created a local research task. Open More → Research, search visibly, then add only the public HTTPS sources you want reviewed."
            normalized.contains("plan") || normalized.contains("organize") -> "I created a local planning task. Break the work into a goal, three next actions, and one check for completion."
            normalized.contains("remember") || normalized.contains("preference") -> "Save the important detail in Memory so future local prompts can use it as approved context."
            else -> "I saved this request in your local workspace and prepared it for an on-device model or your optional remote agent."
        }
        return buildString {
            append("Local working response\n\n")
            append(workflow)
            if (knowledge.isNotBlank()) append("\n\nRelevant local document evidence is available for this request.")
            if (memory.isNotBlank()) append("\n\nYour saved local memory was included as context.")
            append("\n\nFor a generated natural-language answer, import a compatible `.task` model in More → Device capabilities, or choose Optional Remote Agent and sign in. No data was sent off this device for this response.")
        }
    }
}
