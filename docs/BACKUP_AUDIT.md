# Backup audit (pre-avatar branch)

This audit covers the independent `RivoPhoneApp-PreAvatar` repository. It does not change or restore data on the phone.

| Feature | Export | Restore | Deliberate limit |
| --- | --- | --- | --- |
| App settings | User-selected JSON, format v2 | Replaces restorable settings | Security codes, device-specific contact/SIM IDs and ringtone/storage URIs are excluded. Version 1 files remain readable. |
| Unknown-number identification | Online, Google Places and business-search choices in settings JSON | Choices restored; Google stays off without a locally verified key | API key, verification status and provider cache are excluded. |
| Custom number names | Included in settings JSON | Replaces existing custom names | JSON contains phone numbers and names in clear text; keep the file private. |
| Quick responses, navigation layout, recorder source/codec/bitrate/minimum duration, call and appearance switches | Included when stored in `rivo_prefs` | Restored through settings JSON | The current default is used for a setting never explicitly saved. Hardware permissions and Shizuku authorization are not portable. |
| Android contacts | Manual VCF | Creates public contacts | Only public contact names, phone numbers and email addresses; no photos, addresses, notes, favorites or account assignment. Re-import can duplicate contacts. Private contacts have a separate export flow. |
| Blocked numbers | Separate CSV export/import on the Blocked Numbers screen | Separate explicit import | Stored in Android's blocked-number provider, not in settings JSON or contact VCF. |
| System call history | Manual JSON of visible entries | Adds missing entries to Android's system log | Requires default-dialer role and call-log permissions. Recordings and call notes are separate. Existing entries are matched by number, type, date and duration. |
| Call notes, callback reminders and private contacts | No combined manual backup | No combined manual restore | Kept in Rivo's Room database. Private contacts have their own export/import. Platform backup may include the database, but this must not be treated as a portable migration file. |
| Simulated-call schedules | No combined manual backup | No combined manual restore | Kept in separate `rivo_fake_call_prefs`; do not infer that settings JSON includes schedules. |
| Call recordings | Local file plus optional OAuth Google Drive copy | Downloads verified Drive recordings without overwriting different local files | Drive does not back up settings, contact data, notes or the system call log. Local files remain primary. |
| Drive connection | No portable export | Reconnect on each device | OAuth access and account state are excluded; automatic/Wi-Fi backup switches must be set again. |
| Android Auto Backup / device transfer | Platform-managed according to XML rules | Platform-managed | Device-protected preferences containing security codes, caller-identification preferences, Drive state, and internal/legacy app-external recording directories are excluded. Room data may be included if the platform performs backup. Cloud backup requires encryption capability. |

On 29 September 2026 a read-only USB ADB check found OPPO `CPH2765` with Rivo Personal 2.4.6 (`it.stivy.rivo.personal.debug`) installed as the default dialer. Read/write contacts and call-log permissions were granted. Google identification and business search were enabled; Drive was connected with automatic backup enabled and the Wi-Fi-only switch off. The app had 24 local recording files; its local Drive state reported `DONE`, zero pending and 30 historical `COPIED` file markers. This is evidence of the app's bookkeeping, **not** independent proof that every remote file is still present or restorable. No personal values, API keys, contact names, phone numbers, recordings, or Drive account identifiers were exported during this check. The installed APK is newer than this pre-avatar source snapshot, so these source changes were **not installed over the phone's data**.

Verification: `:app:testCloudDebugUnitTest`, `:app:assembleCloudDebug`, and `:app:lintCloudDebug` passed locally. A real settings round-trip, VCF/JSON import into the system providers, Drive upload/download, and Android platform restore were not run on the user's data. Test those with disposable contacts and calls before relying on them for migration.

Android backup-rule domains and encryption semantics: <https://developer.android.com/identity/data/autobackup>.
