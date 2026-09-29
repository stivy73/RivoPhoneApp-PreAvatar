package com.grinch.rivo4.controller

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grinch.rivo4.R
import com.grinch.rivo4.modal.`interface`.ICallLogRepository
import com.grinch.rivo4.modal.`interface`.IContactsRepository
import com.grinch.rivo4.modal.data.CallLogEntry
import com.grinch.rivo4.modal.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupViewModel(
    private val contactsRepo: IContactsRepository,
    private val callLogRepo: ICallLogRepository
) : ViewModel() {

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun escapeVCard(value: String): String = value.replace("\\", "\\\\")
        .replace("\r", "").replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")

    private fun unescapeVCard(value: String): String {
        val output = StringBuilder()
        var index = 0
        while (index < value.length) {
            if (value[index] == '\\' && index + 1 < value.length) {
                output.append(when (value[++index]) { 'n', 'N' -> '\n'; else -> value[index] })
            } else output.append(value[index])
            index++
        }
        return output.toString()
    }

    fun exportContacts(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // The generic VCF action is public-only. Private contacts have their own
                // explicit export flow and must never leak into a regular contact file.
                val contacts = contactsRepo.getContacts(includePrivate = false, includeHidden = false)
                val vCard = buildString {
                    contacts.forEach { contact ->
                        append("BEGIN:VCARD\n")
                        append("VERSION:3.0\n")
                        append("FN:${escapeVCard(contact.name)}\n")
                        contact.phoneNumbers.forEach { number ->
                            append("TEL;TYPE=CELL:${escapeVCard(number)}\n")
                        }
                        contact.emails.forEach { email ->
                            append("EMAIL;TYPE=INTERNET:${escapeVCard(email)}\n")
                        }
                        append("END:VCARD\n")
                    }
                }
                (context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")).use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(vCard)
                    }
                }
                _status.value = context.getString(R.string.backup_contacts_export_success)
            } catch (e: Exception) {
                _status.value = context.getString(R.string.backup_export_failed, e.message)
            }
        }
    }

    fun importContacts(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                (context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String?
                        var currentName = ""
                        val currentNumbers = mutableListOf<String>()
                        val currentEmails = mutableListOf<String>()
                        
                        while (reader.readLine().also { line = it } != null) {
                            when {
                                line!!.startsWith("FN:") -> currentName = unescapeVCard(line!!.substring(3))
                                line!!.startsWith("TEL") -> {
                                    if (line!!.contains(':')) currentNumbers.add(unescapeVCard(line!!.substringAfter(':')))
                                }
                                line!!.startsWith("EMAIL") -> {
                                    if (line!!.contains(':')) currentEmails.add(unescapeVCard(line!!.substringAfter(':')))
                                }
                                line!!.startsWith("END:VCARD") -> {
                                    if (currentName.isNotEmpty()) {
                                        check(contactsRepo.saveContact(
                                            Contact(
                                                id = "0",
                                                name = currentName,
                                                phoneNumbers = currentNumbers.toList(),
                                                emails = currentEmails.toList()
                                            )
                                        )) { "Contact provider rejected import" }
                                    }
                                    currentName = ""
                                    currentNumbers.clear()
                                    currentEmails.clear()
                                }
                            }
                        }
                    }
                }
                _status.value = context.getString(R.string.backup_contacts_import_success)
            } catch (e: Exception) {
                _status.value = context.getString(R.string.backup_import_failed, e.message)
            }
        }
    }

    fun exportCallLogs(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = callLogRepo.getCallLogs()
                val jsonString = json.encodeToString(logs)
                (context.contentResolver.openOutputStream(uri) ?: error("Cannot open destination")).use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonString)
                    }
                }
                _status.value = context.getString(R.string.backup_call_logs_export_success)
            } catch (e: Exception) {
                _status.value = context.getString(R.string.backup_export_failed, e.message)
            }
        }
    }

    fun importCallLogs(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                (context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")).use { inputStream ->
                    val jsonString = BufferedReader(InputStreamReader(inputStream)).readText()
                    val logs = json.decodeFromString<List<CallLogEntry>>(jsonString)
                    val existing = callLogRepo.getCallLogs()
                        .map { listOf(it.number, it.type.toString(), it.date.toString(), it.duration.toString()) }
                        .toMutableSet()
                    logs.forEach { log ->
                        val identity = listOf(log.number, log.type.toString(), log.date.toString(), log.duration.toString())
                        if (existing.add(identity)) callLogRepo.saveCallLog(log)
                    }
                }
                _status.value = context.getString(R.string.backup_call_logs_import_success)
            } catch (e: Exception) {
                _status.value = context.getString(R.string.backup_import_failed, e.message)
            }
        }
    }

    fun clearStatus() {
        _status.value = null
    }
}
