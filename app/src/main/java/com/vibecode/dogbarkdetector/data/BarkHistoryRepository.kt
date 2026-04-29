package com.vibecode.dogbarkdetector.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.barkHistoryDataStore by preferencesDataStore(name = "bark_history")

class BarkHistoryRepository(private val context: Context) {

    val records: Flow<List<BarkRecord>> = context.barkHistoryDataStore.data.map { prefs ->
        val json = prefs[Keys.RECORDS_JSON].orEmpty()
        parseRecords(json)
    }

    suspend fun addRecord(record: BarkRecord) {
        context.barkHistoryDataStore.edit { prefs ->
            val existing = parseRecords(prefs[Keys.RECORDS_JSON].orEmpty()).toMutableList()
            existing.add(0, record)
            if (existing.size > MAX_RECORDS) {
                val trimmed = existing.subList(0, MAX_RECORDS)
                prefs[Keys.RECORDS_JSON] = serializeRecords(trimmed)
            } else {
                prefs[Keys.RECORDS_JSON] = serializeRecords(existing)
            }
        }
    }

    suspend fun updateAudioPath(timestampMillis: Long, audioFilePath: String) {
        context.barkHistoryDataStore.edit { prefs ->
            val existing = parseRecords(prefs[Keys.RECORDS_JSON].orEmpty()).toMutableList()
            val idx = existing.indexOfFirst { it.timestampMillis == timestampMillis }
            if (idx >= 0) {
                existing[idx] = existing[idx].copy(audioFilePath = audioFilePath)
                prefs[Keys.RECORDS_JSON] = serializeRecords(existing)
            }
        }
    }

    suspend fun clearHistory() {
        context.barkHistoryDataStore.edit { prefs ->
            prefs.remove(Keys.RECORDS_JSON)
        }
    }

    private fun parseRecords(json: String): List<BarkRecord> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                BarkRecord(
                    timestampMillis = obj.getLong("ts"),
                    confidence = obj.getDouble("conf").toFloat(),
                    label = obj.getString("label"),
                    audioFilePath = obj.optString("audio", null)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeRecords(records: List<BarkRecord>): String {
        val arr = JSONArray()
        for (r in records) {
            val obj = org.json.JSONObject()
            obj.put("ts", r.timestampMillis)
            obj.put("conf", r.confidence.toDouble())
            obj.put("label", r.label)
            r.audioFilePath?.let { obj.put("audio", it) }
            arr.put(obj)
        }
        return arr.toString()
    }

    private object Keys {
        val RECORDS_JSON = stringPreferencesKey("records_json")
    }

    companion object {
        private const val MAX_RECORDS = 500
    }
}
