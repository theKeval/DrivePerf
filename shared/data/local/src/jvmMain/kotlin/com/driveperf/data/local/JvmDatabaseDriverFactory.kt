package com.driveperf.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.driveperf.data.local.db.DrivePerfDatabase
import java.util.Properties

/** JVM, for tests and tools: an SQLite file at [url], in memory by default. */
public class JvmDatabaseDriverFactory(private val url: String = JdbcSqliteDriver.IN_MEMORY) : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        JdbcSqliteDriver(url, Properties().apply { put("foreign_keys", "true") }, DrivePerfDatabase.Schema)
}
