package com.vibecode.dogbarkdetector.mail

import com.vibecode.dogbarkdetector.data.SmtpSettings
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

data class EmailSendRequest(
    val subject: String,
    val body: String,
    val attachmentFile: File? = null
)

class SmtpAlertMailer {
    suspend fun send(settings: SmtpSettings, request: EmailSendRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(settings.isComplete()) {
                    "SMTP settings are incomplete."
                }

                val port = settings.normalizedPort()
                val useSsl = port == 465

                val properties = Properties().apply {
                    put("mail.transport.protocol", "smtp")
                    put("mail.smtp.host", settings.host.trim())
                    put("mail.smtp.port", port.toString())
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.ssl.enable", useSsl.toString())
                    put("mail.smtp.starttls.enable", (!useSsl).toString())
                    put("mail.smtp.ssl.trust", settings.host.trim())
                    put("mail.smtp.connectiontimeout", "15000")
                    put("mail.smtp.timeout", "15000")
                    put("mail.smtp.writetimeout", "15000")
                    if (useSsl) {
                        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                        put("mail.smtp.socketFactory.port", port.toString())
                        put("mail.smtp.socketFactory.fallback", "false")
                    }
                }

                val session = Session.getInstance(properties, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(settings.username, settings.password)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(settings.senderAddress.trim()))
                    setRecipients(
                        Message.RecipientType.TO,
                        arrayOf(InternetAddress(settings.recipientAddress.trim()))
                    )
                    subject = request.subject

                    if (request.attachmentFile != null && request.attachmentFile.exists()) {
                        val multipart = MimeMultipart()
                        val textPart = MimeBodyPart().apply {
                            setText(request.body, "utf-8")
                        }
                        multipart.addBodyPart(textPart)

                        val attachPart = MimeBodyPart().apply {
                            attachFile(request.attachmentFile)
                            fileName = request.attachmentFile.name
                        }
                        multipart.addBodyPart(attachPart)
                        setContent(multipart)
                    } else {
                        setText(request.body, "utf-8")
                    }
                }

                Transport.send(message)
            }
        }
    }

    companion object {
        private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

        fun buildAlertRequest(
            detectorSummary: String,
            featureSummary: String,
            occurredAtMillis: Long,
            burstCount: Int,
            audioFile: File? = null
        ): EmailSendRequest {
            val timestamp = timeFormatter.format(Instant.ofEpochMilli(occurredAtMillis))
            return EmailSendRequest(
                subject = "🐶 狗叫提醒 $timestamp",
                body = buildString {
                    appendLine("狗叫检测器检测到狗叫事件。")
                    appendLine()
                    appendLine("检测方式：YAMNet 设备端音频分类")
                    appendLine("检测时间：$timestamp")
                    appendLine("连续帧数：$burstCount")
                    appendLine("检测摘要：$detectorSummary")
                    appendLine("特征信息：$featureSummary")
                    if (audioFile != null) {
                        appendLine("音频附件：${audioFile.name}（检测前后各约5秒）")
                    }
                    appendLine()
                    appendLine("— 来自「你在狗叫什么？」")
                },
                attachmentFile = audioFile
            )
        }

        fun buildTestRequest(): EmailSendRequest {
            val timestamp = timeFormatter.format(Instant.now())
            return EmailSendRequest(
                subject = "你在狗叫什么？ - 测试邮件",
                body = "这是一封测试邮件，发送于 $timestamp，来自「你在狗叫什么？」。"
            )
        }
    }
}
