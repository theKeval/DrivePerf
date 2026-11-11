package com.driveperf.data.local

import app.cash.sqldelight.db.SqlDriver
import com.driveperf.data.local.db.DrivePerfDatabase

/** The SQLite file name (data-model.md §2.1). */
public const val DATABASE_NAME: String = "driveperf.db"

/**
 * Creates the platform SQLite driver with foreign keys on (data-model.md §2.2). Android needs a `Context`; iOS uses
 * the app's default protection class (`completeUntilFirstUserAuthentication`, ADR-003).
 */
public fun interface DatabaseDriverFactory {
    public fun create(): SqlDriver
}

/** Opens the database on [driver]. */
public fun openDatabase(driver: SqlDriver): DrivePerfDatabase = DrivePerfDatabase(driver)
