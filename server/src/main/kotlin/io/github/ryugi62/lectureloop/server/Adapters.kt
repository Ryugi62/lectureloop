package io.github.ryugi62.lectureloop.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant

/**
 * `GET /v1/subscribers/{app_user_id}` with a secret key. Active = `pro` present and its expiry (or grace period)
 * is in the future, or it has no expiry (lifetime). Any error fails closed: the user is treated as free.
 */
class RevenueCatRestEntitlements(
    private val secretKey: String,
    private val baseUrl: HttpUrl = "https://api.revenuecat.com/".toHttpUrl(),
    private val http: OkHttpClient = OkHttpClient(),
    private val clock: () -> Instant = Instant::now,
) : EntitlementChecker {
    override suspend fun isPro(appUserId: String): Boolean = withContext(Dispatchers.IO) {
        // Anonymous IDs look like "$RCAnonymousID:…"; RevenueCat asks for the ID to be URL-encoded.
        val encoded = java.net.URLEncoder.encode(appUserId, Charsets.UTF_8).replace("+", "%20")
        val url = baseUrl.newBuilder().addPathSegments("v1/subscribers").addEncodedPathSegment(encoded).build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $secretKey").get().build()
        try {
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext false
                val pro = Json.parseToJsonElement(r.body.string()).jsonObject["subscriber"]?.jsonObject
                    ?.get("entitlements")?.jsonObject?.get("pro") as? JsonObject ?: return@withContext false
                val now = clock()
                fun future(key: String) = (pro[key] as? JsonPrimitive)?.contentOrNull?.let { Instant.parse(it).isAfter(now) } ?: false
                val lifetime = pro["expires_date"] == null || pro["expires_date"] is JsonNull
                lifetime || future("expires_date") || future("grace_period_expires_date")
            }
        } catch (e: Exception) {
            System.err.println("revenuecat check failed: ${e.message}")
            false
        }
    }
}

/** Without a RevenueCat secret key everyone is on the free allowance (local development). */
object NoEntitlements : EntitlementChecker {
    override suspend fun isPro(appUserId: String) = false
}

/** Usage in one JSON file (user → epoch seconds of built cards). Fine for one instance; use a database beyond that. */
class FileUsageStore(private val file: File) : UsageStore {
    private val lock = Any()
    private val data: MutableMap<String, MutableList<Long>> = load()

    private fun load(): MutableMap<String, MutableList<Long>> {
        if (!file.exists()) return mutableMapOf()
        val o = Json.parseToJsonElement(file.readText()).jsonObject
        return o.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.long }.toMutableList() }.toMutableMap()
    }

    override fun countSince(appUserId: String, since: Instant): Int = synchronized(lock) {
        data[appUserId].orEmpty().count { it >= since.epochSecond }
    }

    override fun record(appUserId: String, at: Instant) = synchronized(lock) {
        val keepAfter = at.epochSecond - 14 * 86_400
        data.getOrPut(appUserId) { mutableListOf() }.apply { add(at.epochSecond); removeAll { it < keepAfter } }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JsonObject(data.mapValues { (_, v) -> JsonArray(v.map(::JsonPrimitive)) }).toString())
        tmp.renameTo(file)
        Unit
    }
}
