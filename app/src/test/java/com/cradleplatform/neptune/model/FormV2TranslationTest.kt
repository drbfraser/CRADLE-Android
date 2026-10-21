package com.cradleplatform.neptune.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class FormV2TranslationTest {
    @Test
    fun `exact language tag takes precedence over a differently cased key`() {
        val translations = linkedMapOf("pt-br" to "Other", "pt-BR" to "Exact", "english" to "Fallback")

        assertEquals("Exact", translations.getTranslation("pt-BR"))
    }

    @Test
    fun `language lookup accepts case differences including regional tags`() {
        val translations = mapOf("english" to "Fallback", "french" to "French text", "pt-BR" to "Portuguese text")

        assertEquals("French text", translations.getTranslation("French"))
        assertEquals("Portuguese text", translations.getTranslation("PT-br"))
    }

    @Test
    fun `missing language falls back to English then first translation then null`() {
        assertEquals("English text", mapOf("french" to "French text", "English" to "English text")
            .getTranslation("missing"))
        assertEquals("French text", mapOf("french" to "French text").getTranslation("missing"))
        assertNull(emptyMap<String, String>().getTranslation("missing"))
    }
}
