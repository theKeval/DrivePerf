package com.driveperf.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.driveperf.data.local.db.DrivePerfDatabase

/**
 * iOS: the app's Application Support database with the default file protection (ADR-003). [name] is only changed
 * by the on-device self-test, which uses a throw-away database.
 */
public class IosDatabaseDriverFactory(private val name: String = DATABASE_NAME) : DatabaseDriverFactory {
    override fun create(): SqlDriver = NativeSqliteDriver(
        schema = DrivePerfDatabase.Schema,
        name = name,
        onConfiguration = { config ->
            config.copy(extendedConfig = config.extendedConfig.copy(foreignKeyConstraints = true))
        },
    )
}
