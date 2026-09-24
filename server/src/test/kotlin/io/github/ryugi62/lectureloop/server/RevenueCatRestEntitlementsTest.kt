package io.github.ryugi62.lectureloop.server

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Shape from https://www.revenuecat.com/docs/api-v1/customers (GET /v1/subscribers/{app_user_id}). */
class RevenueCatRestEntitlementsTest {
    private val server = MockWebServer().apply { start() }
    private val now = Instant.parse("2026-09-24T03:00:00Z")
    private val rc = RevenueCatRestEntitlements("sk_test", server.url("/"), OkHttpClient()) { now }

    @AfterTest fun stop() = server.shutdown()

    private fun subscriber(entitlements: String) =
        MockResponse().setBody("""{"request_date":"2026-09-24T03:00:00Z","subscriber":{"entitlements":{$entitlements},"subscriptions":{}}}""")

    @Test fun activeProIsPro() = runTest {
        server.enqueue(subscriber(""""pro":{"expires_date":"2026-10-24T03:00:00Z","grace_period_expires_date":null,"product_identifier":"semester_pass"}"""))
        assertEquals(true, rc.isPro("\$RCAnonymousID:abc"))
        val r = server.takeRequest()
        assertEquals("/v1/subscribers/%24RCAnonymousID%3Aabc", r.path)
        assertEquals("Bearer sk_test", r.getHeader("Authorization"))
    }

    @Test fun expiredOrMissingIsNotPro_graceAndLifetimeAre() = runTest {
        server.enqueue(subscriber(""""pro":{"expires_date":"2026-09-01T00:00:00Z","grace_period_expires_date":null}"""))
        assertEquals(false, rc.isPro("u"))
        server.enqueue(subscriber(""))
        assertEquals(false, rc.isPro("u"))
        server.enqueue(subscriber(""""pro":{"expires_date":"2026-09-01T00:00:00Z","grace_period_expires_date":"2026-09-30T00:00:00Z"}"""))
        assertEquals(true, rc.isPro("u"))
        server.enqueue(subscriber(""""pro":{"expires_date":null}"""))
        assertEquals(true, rc.isPro("u"))
    }

    @Test fun revenueCatDownFailsClosedToFree() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(false, rc.isPro("u"))
    }
}
