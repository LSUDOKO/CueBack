package com.cueback.app.ai

/**
 * Removes likely secrets before anything could leave the device. Runs on every cloud AI request.
 * Conservative by design: false positives only cost a little context.
 */
object Redactor {
    private val RULES: List<Pair<Regex, String>> = listOf(
        Regex("(?i)authorization\\s*[:=]\\s*\\S+(\\s+\\S+)?") to "authorization: [REDACTED]",
        Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}") to "Bearer [REDACTED]",
        Regex("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}") to "[JWT]",
        Regex("(?i)\\b(sk|pk|rk)[-_](live|test|proj|ant)?[-_]?[A-Za-z0-9]{16,}") to "[API_KEY]",
        Regex("\\bAKIA[0-9A-Z]{16}\\b") to "[AWS_KEY]",
        Regex("\\bgh[pousr]_[A-Za-z0-9]{30,}\\b") to "[GITHUB_TOKEN]",
        Regex("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b") to "[SLACK_TOKEN]",
        Regex("\\bAIza[0-9A-Za-z_-]{35}\\b") to "[GOOGLE_KEY]",
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----") to "[PRIVATE_KEY]",
        Regex("(?i)\\b(password|passwd|pwd|secret|api[_-]?key|token)\\s*[:=]\\s*\\S+") to "$1=[REDACTED]",
        Regex("(?i)([?&](token|key|sig|signature|access_token|auth|code)=)[^&\\s]+") to "$1[REDACTED]",
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "[EMAIL]",
        Regex("\\b(?:\\d[ -]?){13,19}\\b") to "[NUMBER]",
    )

    fun redact(text: String): String = RULES.fold(text) { acc, (re, rep) -> re.replace(acc, rep) }

    fun containsSecret(text: String) = redact(text) != text
}
