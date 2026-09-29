package com.grinch.rivo4.controller.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class SettingsBackupDocument(
    val applicationId: String,
    val createdAtEpochMillis: Long,
    val settings: Map<String, Any>,
    val callerOptions: Map<String, Boolean>? = null,
    val customNames: Map<String, String>? = null
)

object SettingsBackupCodec {
    private const val FORMAT = "rivo-settings"
    private const val VERSION = 2
    private const val MAX_DOCUMENT_CHARS = 2_000_000
    private const val MAX_SETTINGS = 2_000
    private const val MAX_KEY_LENGTH = 200
    private val json = Json { prettyPrint = true }

    fun encode(
        settings: Map<String, Any>,
        applicationId: String,
        createdAtEpochMillis: Long = System.currentTimeMillis(),
        callerOptions: Map<String, Boolean> = emptyMap(),
        customNames: Map<String, String> = emptyMap()
    ): String {
        require(settings.size <= MAX_SETTINGS) { "Too many settings" }
        require(callerOptions.keys.all { it in CALLER_OPTIONS }) { "Invalid caller option" }
        require(customNames.size <= MAX_SETTINGS && customNames.all { (number, name) ->
            number.matches(E164) && name.isNotBlank() && name.length <= 160 && name.none(Char::isISOControl)
        }) { "Invalid custom names" }
        val encodedSettings = buildJsonObject {
            settings.toSortedMap().forEach { (key, value) ->
                require(key.isNotBlank() && key.length <= MAX_KEY_LENGTH) { "Invalid setting key" }
                put(key, encodeValue(value))
            }
        }
        val document = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("applicationId", applicationId)
            put("createdAtEpochMillis", createdAtEpochMillis)
            put("settings", encodedSettings)
            put("callerOptions", buildJsonObject { callerOptions.toSortedMap().forEach { (key, value) -> put(key, value) } })
            put("customNames", buildJsonObject { customNames.toSortedMap().forEach { (key, value) -> put(key, value) } })
        }
        return json.encodeToString(JsonObject.serializer(), document)
    }

    fun decode(document: String): SettingsBackupDocument {
        require(document.length <= MAX_DOCUMENT_CHARS) { "Backup file is too large" }
        val root = json.parseToJsonElement(document).jsonObject
        require(root["format"]?.jsonPrimitive?.contentOrNull == FORMAT) { "Unsupported backup format" }
        val version = root["version"]?.jsonPrimitive?.intOrNull
        require(version == 1 || version == VERSION) { "Unsupported backup version" }
        val applicationId = root["applicationId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(applicationId.isNotBlank()) { "Missing application id" }
        val createdAt = root["createdAtEpochMillis"]?.jsonPrimitive?.longOrNull
            ?: throw IllegalArgumentException("Missing creation time")
        val encodedSettings = root["settings"]?.jsonObject
            ?: throw IllegalArgumentException("Missing settings")
        require(encodedSettings.size <= MAX_SETTINGS) { "Too many settings" }

        val decoded = linkedMapOf<String, Any>()
        encodedSettings.forEach { (key, value) ->
            require(key.isNotBlank() && key.length <= MAX_KEY_LENGTH) { "Invalid setting key" }
            decoded[key] = decodeValue(value.jsonObject)
        }
        val callerOptions = if (version == 2) root["callerOptions"]?.jsonObject?.mapValues { (key, value) ->
            require(key in CALLER_OPTIONS) { "Invalid caller option" }
            value.jsonPrimitive.booleanOrNull ?: throw IllegalArgumentException("Invalid caller option")
        } ?: throw IllegalArgumentException("Missing caller options") else null
        val customNames = if (version == 2) root["customNames"]?.jsonObject?.also {
            require(it.size <= MAX_SETTINGS) { "Too many custom names" }
        }?.mapValues { (number, value) ->
            val name = value.jsonPrimitive.contentOrNull ?: throw IllegalArgumentException("Invalid custom name")
            require(number.matches(E164) && name.isNotBlank() && name.length <= 160 && name.none(Char::isISOControl)) {
                "Invalid custom name"
            }
            name
        } ?: throw IllegalArgumentException("Missing custom names") else null
        return SettingsBackupDocument(applicationId, createdAt, decoded, callerOptions, customNames)
    }

    private val CALLER_OPTIONS = setOf("online", "google", "business_search")
    private val E164 = Regex("\\+[1-9][0-9]{7,14}")

    private fun encodeValue(value: Any): JsonObject = buildJsonObject {
        when (value) {
            is Boolean -> { put("type", "boolean"); put("value", value) }
            is Int -> { put("type", "int"); put("value", value) }
            is Long -> { put("type", "long"); put("value", value) }
            is Float -> { put("type", "float"); put("value", value) }
            is String -> { put("type", "string"); put("value", value) }
            is Set<*> -> {
                require(value.all { it is String }) { "Unsupported set value" }
                put("type", "string-set")
                put("value", JsonArray(value.filterIsInstance<String>().sorted().map(::JsonPrimitive)))
            }
            else -> throw IllegalArgumentException("Unsupported setting value")
        }
    }

    private fun decodeValue(encoded: JsonObject): Any {
        val type = encoded["type"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing setting type")
        val value = encoded["value"] ?: throw IllegalArgumentException("Missing setting value")
        return when (type) {
            "boolean" -> value.jsonPrimitive.booleanOrNull
            "int" -> value.jsonPrimitive.intOrNull
            "long" -> value.jsonPrimitive.longOrNull
            "float" -> value.jsonPrimitive.floatOrNull
            "string" -> value.jsonPrimitive.contentOrNull
            "string-set" -> value.jsonArray.map { element ->
                element.jsonPrimitive.contentOrNull
                    ?: throw IllegalArgumentException("Invalid string set")
            }.toSet()
            else -> throw IllegalArgumentException("Unsupported setting type")
        } ?: throw IllegalArgumentException("Invalid setting value")
    }
}
