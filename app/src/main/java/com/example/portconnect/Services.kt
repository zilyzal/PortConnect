package com.example.portconnect

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** The device the services run on, unless the user saves a different address. */
internal const val DEFAULT_HOST = "192.168.100.4"

data class Service(
    val id: Long,
    val name: String,
    val port: Int,
    val colorIndex: Int,
) {
    val accent: Color get() = TileColors[colorIndex.mod(TileColors.size)]

    fun urlFor(host: String): String = "http://$host:$port"
}

internal val DefaultServices = listOf(
    Service(1, "Blackbox Store", 1453, 0),
    Service(2, "Orbit Store", 34177, 1),
    Service(3, "Payload Manager", 8084, 2),
    Service(4, "CheatRunner", 9999, 3),
    Service(5, "Ps5upload", 9120, 4),
)

/**
 * Turns whatever the user typed into a bare host name or IP address.
 * Returns the default for empty input and null when the text isn't a valid address.
 */
internal fun normalizeHost(raw: String): String? {
    val host = raw.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
        .trim()
    if (host.isEmpty()) return DEFAULT_HOST
    return host.takeIf { HOST_PATTERN.matches(it) }
}

private val HOST_PATTERN = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$")

/** Keeps the device address and the service list on the phone. */
internal class ServiceStore(private val prefs: SharedPreferences) {

    fun loadHost(): String = prefs.getString(KEY_HOST, null) ?: DEFAULT_HOST

    fun saveHost(host: String) {
        prefs.edit().putString(KEY_HOST, host).apply()
    }

    fun loadServices(): List<Service> {
        val raw = prefs.getString(KEY_SERVICES, null) ?: return DefaultServices
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                runCatching {
                    val o = array.getJSONObject(i)
                    Service(o.getLong("id"), o.getString("name"), o.getInt("port"), o.optInt("color", i))
                }.getOrNull()
            }
        } catch (e: Exception) {
            DefaultServices
        }
    }

    fun saveServices(list: List<Service>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("port", it.port)
                    .put("color", it.colorIndex)
            )
        }
        prefs.edit().putString(KEY_SERVICES, array.toString()).apply()
    }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_SERVICES = "services"
    }
}
