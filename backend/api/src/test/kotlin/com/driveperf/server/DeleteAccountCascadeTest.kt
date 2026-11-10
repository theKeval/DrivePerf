package com.driveperf.server

import com.driveperf.server.application.AccountDataSources
import com.driveperf.server.application.DeleteAccountCascade
import com.driveperf.server.application.RequestAccountDeletion
import com.driveperf.server.application.RunDeletionJob
import com.driveperf.server.ports.AccountRecord
import com.driveperf.server.ports.AccountStatus
import com.driveperf.server.ports.BackupState
import com.driveperf.server.ports.ObjectInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteAccountCascadeTest {
    private val journal = Journal()
    private val clock = FakeClock()
    private val objects = FakeObjectStore(journal)
    private val userData = FakeUserData(journal)
    private val accounts = FakeAccounts(journal)
    private val requests = FakeRequests(journal)
    private val external = FakeExternal(journal)

    private fun cascade(objectStore: FakeObjectStore = objects) =
        DeleteAccountCascade(AccountDataSources(objectStore, userData, external, external, external), accounts, requests, clock)

    private fun seed(accountId: String, trips: Int = 3) {
        repeat(trips) {
            objects.objects["u/$accountId/t/trip-$it/r1.pb.gz"] = ObjectInfo(1, emptyMap())
        }
        objects.objects["x/$accountId/export-1.zip"] = ObjectInfo(1, emptyMap())
        userData.documents[accountId] = 1200
        accounts.records[accountId] = AccountRecord(accountId, AccountStatus.ACTIVE, BackupState.ACTIVE, null)
    }

    private fun request(accountId: String) = requests.createOrGet(accountId, clock.now)

    @Test
    fun deletesEverythingInTheDocumentedOrderAndTheIdentityLast() {
        seed("alice")
        val outcome = cascade().run(request("alice"), clock.now + 60_000)

        assertEquals(DeleteAccountCascade.Outcome.DONE, outcome)
        assertTrue(objects.objects.isEmpty())
        assertEquals(0, userData.documents["alice"])
        assertEquals(null, accounts.records["alice"])
        val steps = journal.events.filter { it.startsWith("step(") || it.startsWith("auth.") || it.startsWith("account.delete") }
        assertEquals(
            listOf(
                "step(OBJECTS)", "step(USER_DATA)", "step(SUBSCRIBER)", "step(RIDE_ALONG)", "step(VERIFIED)",
                "auth.delete(alice)", "account.delete(alice)", "step(ACCOUNT)",
            ),
            steps,
        )
        assertTrue("request.complete" in journal.events)
    }

    @Test
    fun neverTouchesAnotherAccountEvenWithASharedPrefix() {
        seed("ab")
        seed("abc")
        cascade().run(request("ab"), clock.now + 60_000)

        assertTrue(objects.objects.keys.all { it.startsWith("u/abc/") || it.startsWith("x/abc/") })
        assertEquals(1200, userData.documents["abc"])
        assertTrue(accounts.records.containsKey("abc"))
    }

    @Test
    fun refusesAccountIdsThatCouldEscapeAPrefix() {
        for (bad in listOf("", "a/b", "..", "a b", "x".repeat(129))) {
            val req = requests.createOrGet(bad, clock.now)
            assertFailsWith<IllegalStateException> { cascade().run(req, clock.now + 1000) }
        }
    }

    @Test
    fun resumesAfterTheTimeBoxRunsOutWithoutRepeatingFinishedSteps() {
        seed("bob", trips = 2500)
        val slow = FakeObjectStore(journal, clock, tickPerCall = 30_000).also { it.objects.putAll(objects.objects) }
        userData.documents["bob"] = 1200
        val req = request("bob")

        val first = cascade(slow).run(req, clock.now + 45_000)
        assertEquals(DeleteAccountCascade.Outcome.MORE_WORK, first)
        assertTrue(slow.objects.isNotEmpty())
        assertFalse("request.complete" in journal.events)

        var guard = 0
        while (guard++ < 10 && requests.requests.getValue(req.id).let { it.id !in requests.completed }) {
            cascade(slow).run(requests.requests.getValue(req.id), clock.now + 45_000)
        }
        assertTrue(slow.objects.isEmpty())
        assertEquals(1, journal.events.count { it == "step(SUBSCRIBER)" })
        assertEquals(1, journal.events.count { it == "auth.delete(bob)" })
        assertTrue(req.id in requests.completed)
    }

    @Test
    fun aVerificationSweepThatFindsLeftoversStartsOverAndKeepsTheIdentity() {
        seed("carol")
        val req = request("carol")
        // A push that was mid-flight writes a document after step USER_DATA ran.
        val racing = object : com.driveperf.server.ports.SubscriberDirectory {
            private var done = false
            override fun deleteSubscriber(accountId: String) {
                if (!done) { done = true; userData.documents[accountId] = 2 }
            }
        }
        val c = DeleteAccountCascade(AccountDataSources(objects, userData, racing, external, external), accounts, requests, clock)

        val first = c.run(req, clock.now + 60_000)
        assertEquals(DeleteAccountCascade.Outcome.MORE_WORK, first)
        assertTrue("steps.reset" in journal.events)
        assertFalse(journal.events.any { it.startsWith("auth.delete") })

        val second = c.run(requests.requests.getValue(req.id), clock.now + 60_000)
        assertEquals(DeleteAccountCascade.Outcome.DONE, second)
        assertEquals(0, userData.documents["carol"])
        assertEquals(1, journal.events.count { it == "auth.delete(carol)" })
    }

    @Test
    fun theJobClaimsEachRequestOnceAndReportsMoreWhenWorkRemains() {
        seed("dave")
        seed("erin")
        val job = RunDeletionJob(requests, cascade(), clock)
        request("dave")
        request("erin")

        val result = job()

        assertEquals(false, result.more)
        assertEquals(mapOf("claimed" to 2, "completed" to 2, "unfinished" to 0), result.processed)
        assertEquals(0, job().processed.getValue("claimed"))
    }

    @Test
    fun requestingDeletionMarksTheAccountRevokesTokensAndIsIdempotent() {
        seed("frank")
        val request = RequestAccountDeletion(accounts, requests, external, clock)

        val first = request("frank", authTimeEpochMillis = clock.now - 60_000) as RequestAccountDeletion.Result.Accepted
        val second = request("frank", authTimeEpochMillis = clock.now - 60_000) as RequestAccountDeletion.Result.Accepted

        assertEquals(first.requestId, second.requestId)
        assertEquals(clock.now + 24 * 3600 * 1000L, first.completeByEpochMillis)
        assertEquals(AccountStatus.DELETING, accounts.records.getValue("frank").status)
        assertTrue("auth.revoke(frank)" in journal.events)
    }

    @Test
    fun requestingDeletionNeedsARecentSignIn() {
        seed("gina")
        val result = RequestAccountDeletion(accounts, requests, external, clock)("gina", authTimeEpochMillis = clock.now - 6 * 60_000)

        assertEquals(RequestAccountDeletion.Result.ReauthRequired, result)
        assertEquals(AccountStatus.ACTIVE, accounts.records.getValue("gina").status)
        assertTrue(requests.requests.isEmpty())
    }

    @Test
    fun aFreeAccountWithNoRecordsStillGoesThroughTheCascade() {
        val result = RequestAccountDeletion(accounts, requests, external, clock)("hank", clock.now)
        assertTrue(result is RequestAccountDeletion.Result.Accepted)

        val outcome = cascade().run(requests.requests.values.single(), clock.now + 60_000)

        assertEquals(DeleteAccountCascade.Outcome.DONE, outcome)
        assertTrue("auth.delete(hank)" in journal.events)
    }
}
