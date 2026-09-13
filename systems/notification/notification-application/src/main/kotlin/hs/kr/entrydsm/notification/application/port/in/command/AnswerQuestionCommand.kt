package hs.kr.entrydsm.notification.application.port.`in`.command

data class AnswerQuestionCommand(
    val questionId: Long,
    val content: String,
    val answeredBy: String,
) {
    init {
        require(questionId > 0) { "questionId must be positive" }
        require(content.isNotBlank()) { "content must not be blank" }
        require(answeredBy.isNotBlank() && answeredBy.length <= 50) { "answeredBy must be 1..50 characters" }
    }
}
