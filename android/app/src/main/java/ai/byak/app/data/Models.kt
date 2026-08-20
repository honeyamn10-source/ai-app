package ai.byak.app.data

data class Session(val accessToken: String, val refreshToken: String, val name: String, val email: String)
data class Provider(val id: String, val provider: String, val name: String, val maskedKey: String, val defaultModel: String)
data class Conversation(val id: String, val title: String, val providerId: String? = null, val model: String = "")
data class ChatMessage(val id: String, val role: String, val content: String, val pending: Boolean = false)
data class Project(val id: String, val name: String, val description: String = "")
data class ResearchResult(val title: String, val url: String, val summary: String)
data class UserFile(val id: String, val name: String, val mimeType: String, val chunkCount: Int)

