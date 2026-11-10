package com.driveperf.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.driveperf.data.local.db.DrivePerfDatabase

/** Android: credential-encrypted storage under `Context.getDatabasePath`, never Direct Boot (data-model.md §2.1). */
public class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun create(): SqlDriver = AndroidSqliteDriver(
        schema = DrivePerfDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
        callback = object : AndroidSqliteDriver.Callback(DrivePerfDatabase.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
                db.enableWriteAheadLogging()
            }
        },
    )
}
