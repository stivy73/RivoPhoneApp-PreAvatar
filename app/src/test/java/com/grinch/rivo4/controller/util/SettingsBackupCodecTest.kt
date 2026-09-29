package com.grinch.rivo4.controller.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsBackupCodecTest {
    @Test
    fun roundTripPreservesEverySupportedPreferenceType() {
        val settings = linkedMapOf<String, Any>(
            "boolean" to true,
            "int" to 42,
            "long" to 4_294_967_296L,
            "float" to 1.25f,
            "string" to "voice-call",
            "string_set" to setOf("alpha", "beta")
        )

        val encoded = SettingsBackupCodec.encode(
            settings = settings,
            applicationId = "it.stivy.rivo.personal.debug",
            createdAtEpochMillis = 1234L
        )
        val decoded = SettingsBackupCodec.decode(encoded)

        assertEquals("it.stivy.rivo.personal.debug", decoded.applicationId)
        assertEquals(1234L, decoded.createdAtEpochMillis)
        assertEquals(settings, decoded.settings)
        assertEquals(emptyMap<String, Boolean>(), decoded.callerOptions)
        assertEquals(emptyMap<String, String>(), decoded.customNames)
    }

    @Test
    fun roundTripIncludesCallerChoicesAndPrivateCustomNamesButNoKeys() {
        val encoded = SettingsBackupCodec.encode(
            settings = mapOf("call_recording_auto" to true),
            applicationId = "it.stivy.rivo.personal.debug",
            createdAtEpochMillis = 1234L,
            callerOptions = mapOf("online" to true, "google" to true, "business_search" to false),
            customNames = mapOf("+390438460195" to "Veterinario")
        )
        val decoded = SettingsBackupCodec.decode(encoded)
        assertEquals(true, decoded.callerOptions?.get("google"))
        assertEquals("Veterinario", decoded.customNames?.get("+390438460195"))
        assertEquals(false, encoded.contains("verified:google"))
        assertEquals(false, encoded.contains("apiKey"))
    }

    @Test
    fun acceptsLegacyV1WithoutChangingCallerSettings() {
        val old = """{"format":"rivo-settings","version":1,"applicationId":"it.stivy.rivo.personal.debug","createdAtEpochMillis":1,"settings":{"dynamic_colors":{"type":"boolean","value":true}}}"""
        val decoded = SettingsBackupCodec.decode(old)
        assertEquals(true, decoded.settings["dynamic_colors"])
        assertEquals(null, decoded.callerOptions)
        assertEquals(null, decoded.customNames)
    }

    @Test
    fun rejectsInvalidCustomNumber() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.encode(emptyMap(), "test", customNames = mapOf("not-a-phone" to "Name"))
        }
    }

    @Test
    fun rejectsUnknownFormatBeforeRestore() {
        val invalid = """{"format":"other","version":1,"applicationId":"test","createdAtEpochMillis":1,"settings":{}}"""

        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode(invalid)
        }
    }

    @Test
    fun rejectsUnsupportedPreferenceType() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.encode(
                settings = mapOf("unsupported" to listOf(1, 2, 3)),
                applicationId = "it.stivy.rivo.personal.debug"
            )
        }
    }

    @Test
    fun rejectsTruncatedDocument() {
        assertThrows(Exception::class.java) {
            SettingsBackupCodec.decode("{\"format\":\"rivo-settings\"")
        }
    }
}
