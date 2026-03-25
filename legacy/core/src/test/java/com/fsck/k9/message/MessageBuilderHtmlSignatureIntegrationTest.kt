package com.fsck.k9.message

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.fsck.k9.CoreResourceProvider
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.BoundaryGenerator
import com.fsck.k9.mail.internet.MessageIdGenerator
import com.fsck.k9.mail.internet.MimeMultipart
import com.fsck.k9.mail.internet.TextBody
import com.fsck.k9.notification.FakePlatformConfigProvider
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.QuoteStyle
import net.thunderbird.core.preference.GeneralSettings
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Date

/**
 * Integration test to verify that HTML signatures are correctly included in sent messages.
 */
class MessageBuilderHtmlSignatureIntegrationTest {

    private lateinit var messageBuilder: SimpleMessageBuilder

    private val resourceProvider = mock<CoreResourceProvider> {
        on { userAgent() } doReturn "Thunderbird Test"
    }

    private val generalSettingsManager = mock<net.thunderbird.core.preference.GeneralSettingsManager> {
        on { getConfig() } doReturn GeneralSettings(platformConfigProvider = FakePlatformConfigProvider())
        on { getSettings() } doReturn GeneralSettings(platformConfigProvider = FakePlatformConfigProvider())
    }

    private val boundaryGenerator = BoundaryGenerator.getInstance()
    private val messageIdGenerator = MessageIdGenerator.getInstance()

    private val identity = Identity(
        description = "Test",
        name = "Test User",
        email = "test@example.com",
        signature = "<div style='color:blue'><strong>John Doe</strong><br>Developer</div>",
        signatureUse = true,
    )

    @Before
    fun setUp() {
        messageBuilder = SimpleMessageBuilder(
            messageIdGenerator,
            boundaryGenerator,
            resourceProvider,
            generalSettingsManager
        )
    }

    @Test
    fun `build message with HTML signature should include HTML in text-html part`() {
        val htmlSignature = "<div style='color:blue'><strong>John Doe</strong><br>Developer</div>"
        val messageContent = "Hello, this is a test message."

        val message = messageBuilder
            .setSubject("Test Subject")
            .setSentDate(Date())
            .setTo(listOf(Address("recipient@example.com")))
            .setIdentity(identity)
            .setMessageFormat(SimpleMessageFormat.HTML)
            .setText(messageContent)
            .setSignature(htmlSignature)
            .setQuoteStyle(QuoteStyle.HEADER)
            .setQuotedTextMode(QuotedTextMode.HIDE)
            .setAttachments(emptyList())
            .build()

        // The message should be multipart/alternative
        assertThat(message.getMimeType()).isEqualTo("multipart/alternative")

        val multipart = message.body as MimeMultipart

        // Find the HTML part
        var htmlPart: com.fsck.k9.mail.BodyPart? = null
        var plainPart: com.fsck.k9.mail.BodyPart? = null

        for (i in 0 until multipart.getCount()) {
            val part = multipart.getBodyPart(i)
            when (part.getMimeType()) {
                "text/html" -> htmlPart = part
                "text/plain" -> plainPart = part
            }
        }

        // Verify HTML part exists and contains the signature HTML
        assertThat(htmlPart).isNotNull()
        val htmlBody = htmlPart!!.body as TextBody
        val htmlContent = htmlBody.rawText

        // The HTML signature should be present (not escaped)
        assertThat(htmlContent).contains("<strong>John Doe</strong>")
        assertThat(htmlContent).contains("<br>Developer")
        assertThat(htmlContent).contains("style='color:blue'")

        // Verify plain text part exists
        assertThat(plainPart).isNotNull()
        val plainBody = plainPart!!.body as TextBody
        val plainContent = plainBody.rawText

        // The plain text should contain the signature text (HTML converted to text)
        assertThat(plainContent).contains("John Doe")
        assertThat(plainContent).contains("Developer")
    }

    @Test
    fun `build message with plain text signature should escape HTML characters`() {
        val plainSignature = "<john@example.com>"
        val messageContent = "Hello"

        val message = messageBuilder
            .setSubject("Test")
            .setSentDate(Date())
            .setTo(listOf(Address("recipient@example.com")))
            .setIdentity(identity.copy(signature = plainSignature))
            .setMessageFormat(SimpleMessageFormat.HTML)
            .setText(messageContent)
            .setSignature(plainSignature)
            .setQuoteStyle(QuoteStyle.HEADER)
            .setQuotedTextMode(QuotedTextMode.HIDE)
            .setAttachments(emptyList())
            .build()

        val multipart = message.body as MimeMultipart

        // Find the HTML part
        var htmlPart: com.fsck.k9.mail.BodyPart? = null
        for (i in 0 until multipart.getCount()) {
            val part = multipart.getBodyPart(i)
            if (part.getMimeType() == "text/html") {
                htmlPart = part
                break
            }
        }

        assertThat(htmlPart).isNotNull()
        val htmlBody = htmlPart!!.body as TextBody
        val htmlContent = htmlBody.rawText

        // HTML special characters should be escaped
        assertThat(htmlContent).contains("&lt;john@example.com&gt;")
        assertThat(htmlContent).doesNotContain("<john@example.com>")
    }

    @Test
    fun `build text-only message with HTML signature should convert to text`() {
        val htmlSignature = "<div><strong>John Doe</strong><br>Developer</div>"
        val messageContent = "Hello"

        val message = messageBuilder
            .setSubject("Test")
            .setSentDate(Date())
            .setTo(listOf(Address("recipient@example.com")))
            .setIdentity(identity)
            .setMessageFormat(SimpleMessageFormat.TEXT)
            .setText(messageContent)
            .setSignature(htmlSignature)
            .setQuoteStyle(QuoteStyle.HEADER)
            .setQuotedTextMode(QuotedTextMode.HIDE)
            .setAttachments(emptyList())
            .build()

        // For text-only messages, the body should be text/plain
        assertThat(message.getMimeType()).isEqualTo("text/plain")

        val body = message.body as TextBody
        val content = body.rawText

        // Should contain the text content of the signature
        assertThat(content).contains("John Doe")
        assertThat(content).contains("Developer")
        // Should not contain HTML tags
        assertThat(content).doesNotContain("<div>")
        assertThat(content).doesNotContain("<strong>")
    }
}
