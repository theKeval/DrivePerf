package com.driveperf.server.application

internal const val ACCOUNT_ID_CHARS: String = "[A-Za-z0-9_-]"

/**
 * Account IDs are opaque strings (ADR-004 rule 2). The only thing the server assumes is that one is safe to put into
 * an object-key prefix or a document path: URL-safe characters, at most 128, never a `/` or `..`.
 */
internal object AccountIds {
    private val valid = Regex("^$ACCOUNT_ID_CHARS{1,128}$")

    fun isValid(accountId: String): Boolean = valid.matches(accountId)

    /** Prefixes of every object that belongs to the account. The trailing slash keeps `ab` apart from `abc`. */
    fun objectPrefixes(accountId: String): List<String> {
        require(isValid(accountId)) { "not a valid account id" }
        return listOf("u/$accountId/", "x/$accountId/")
    }
}
