package com.driveperf.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.driveperf.data.local.db.DrivePerfDatabase

/** iOS: the app's Application Support database with the default file protection (ADR-003). */
public class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(): SqlDriver = NativeSqliteDriver(
        schema = DrivePerfDatabase.Schema,
        name = DATABASE_NAME,
        onConfiguration = { config ->
            config.copy(extendedConfig = config.extendedConfig.copy(foreignKeyConstraints = true))
        },
    )
}
