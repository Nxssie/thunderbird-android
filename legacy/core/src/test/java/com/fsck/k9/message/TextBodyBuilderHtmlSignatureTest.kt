package com.fsck.k9.message

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.fsck.k9.notification.FakePlatformConfigProvider
import net.thunderbird.core.logging.legacy.Log
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.core.preference.GeneralSettings
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Tests for HTML signature support in TextBodyBuilder.
 */
class TextBodyBuilderHtmlSignatureTest {

    private val generalSettingsManager = mock<net.thunderbird.core.preference.GeneralSettingsManager> {
        on { getConfig() } doReturn GeneralSettings(platformConfigProvider = FakePlatformConfigProvider())
    }

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `buildTextHtml with HTML signature should include HTML unchanged`() {
        val htmlSignature = "<div style='color:blue'><strong>John Doe</strong><br>Developer</div>"
        val builder = TextBodyBuilder("Hello", generalSettingsManager).apply {
            setAppendSignature(true)
            setIncludeQuotedText(false)
            setSignature(htmlSignature)
        }

        val result = builder.buildTextHtml()

        // Should contain the signature HTML wrapped in k9mail-signature div
        assertThat(result.rawText).contains("<div class='k9mail-signature'>")
        assertThat(result.rawText).contains("<strong>John Doe</strong>")
        assertThat(result.rawText).contains("<br>Developer")
    }

    @Test
    fun `buildTextHtml with plain text signature should escape HTML characters`() {
        val plainSignature = "<john@example.com>"
        val builder = TextBodyBuilder("Hello", generalSettingsManager).apply {
            setAppendSignature(true)
            setIncludeQuotedText(false)
            setSignature(plainSignature)
        }

        val result = builder.buildTextHtml()

        // Should escape the < and > characters
        assertThat(result.rawText).contains("&lt;john@example.com&gt;")
        assertThat(result.rawText).doesNotContain("<john@example.com>")
    }

    @Test
    fun `buildTextPlain with HTML signature should convert to plain text`() {
        val htmlSignature = "<div><strong>John Doe</strong><br>Developer</div>"
        val builder = TextBodyBuilder("Hello", generalSettingsManager).apply {
            setAppendSignature(true)
            setIncludeQuotedText(false)
            setSignature(htmlSignature)
        }

        val result = builder.buildTextPlain()

        // Should convert HTML to plain text
        assertThat(result.rawText).contains("John Doe")
        assertThat(result.rawText).contains("Developer")
        assertThat(result.rawText).doesNotContain("<div>")
        assertThat(result.rawText).doesNotContain("<strong>")
    }

    @Test
    fun `buildTextPlain with plain text signature should include as-is`() {
        val plainSignature = "-- \nJohn Doe\nDeveloper"
        val builder = TextBodyBuilder("Hello", generalSettingsManager).apply {
            setAppendSignature(true)
            setIncludeQuotedText(false)
            setSignature(plainSignature)
        }

        val result = builder.buildTextPlain()

        assertThat(result.rawText).isEqualTo("Hello\r\n-- \nJohn Doe\nDeveloper")
    }

    @Test
    fun `buildTextHtml with signature containing links should preserve links`() {
        val htmlSignature = "<a href='https://example.com'>My Website</a>"
        val builder = TextBodyBuilder("Hello", generalSettingsManager).apply {
            setAppendSignature(true)
            setIncludeQuotedText(false)
            setSignature(htmlSignature)
        }

        val result = builder.buildTextHtml()

        assertThat(result.rawText).contains("<a href='https://example.com'>My Website</a>")
    }

    @Test
    fun `buildTextHtml with signature containing image should preserve image tag`() {
        val htmlSignature = "<img src='logo.png' alt='Logo'>"
        val builder = TextBodyBuilder("Hello", generalSettingsManager).apply {
            setAppendSignature(true)
            setIncludeQuotedText(false)
            setSignature(htmlSignature)
        }

        val result = builder.buildTextHtml()

        assertThat(result.rawText).contains("<img src='logo.png' alt='Logo'>")
    }

    @Test
    fun `buildTextHtml with signature with void tags should be detected as HTML`() {
        val htmlSignature = "Line 1<br>Line 2<hr>Line 3"
        val builder = TextBodyBuilder("Hello", generalSettingsManager).apply {
            setAppendSignature(true)
            setIncludeQuotedText(false)
            setSignature(htmlSignature)
        }

        val result = builder.buildTextHtml()

        // Should be treated as HTML, not escaped
        assertThat(result.rawText).contains("<br>")
        assertThat(result.rawText).contains("<hr>")
        assertThat(result.rawText).doesNotContain("&lt;br&gt;")
    }
}
