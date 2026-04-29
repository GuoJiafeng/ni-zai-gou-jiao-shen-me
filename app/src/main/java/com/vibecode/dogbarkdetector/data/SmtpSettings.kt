package com.vibecode.dogbarkdetector.data

enum class EmailProvider(
    val displayName: String,
    val host: String,
    val port: String,
    val useTls: Boolean,
    val domain: String
) {
    CUSTOM("自定义", "", "587", true, ""),
    QQ("QQ邮箱", "smtp.qq.com", "465", true, "qq.com"),
    NETEASE_163("163邮箱", "smtp.163.com", "465", true, "163.com"),
    NETEASE_126("126邮箱", "smtp.126.com", "465", true, "126.com"),
    GMAIL("Gmail", "smtp.gmail.com", "587", true, "gmail.com"),
    OUTLOOK("Outlook", "smtp.office365.com", "587", true, "outlook.com"),
    SINA("新浪邮箱", "smtp.sina.com", "465", true, "sina.com"),
    SOHU("搜狐邮箱", "smtp.sohu.com", "465", true, "sohu.com");

    companion object {
        fun detectFromAddress(address: String): EmailProvider {
            if (address.isBlank()) return CUSTOM
            val domain = address.substringAfter("@", "").lowercase()
            return entries.firstOrNull { it.domain.isNotBlank() && domain.endsWith(it.domain) } ?: CUSTOM
        }
    }
}

data class SmtpSettings(
    val host: String = "",
    val port: String = "587",
    val username: String = "",
    val password: String = "",
    val senderAddress: String = "",
    val recipientAddress: String = "",
    val useTls: Boolean = true
) {
    fun detectedProvider(): EmailProvider = EmailProvider.detectFromAddress(senderAddress)

    fun isComplete(): Boolean {
        return host.isNotBlank() &&
            port.toIntOrNull() != null &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            senderAddress.isNotBlank() &&
            recipientAddress.isNotBlank()
    }

    fun normalizedPort(): Int = port.toIntOrNull() ?: if (useTls) 587 else 25
}
