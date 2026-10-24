package com.betterappmafia.apophenia.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** The app's database file, opened with the same bundled SQLite the JVM tests use. */
fun openApopheniaDatabase(context: Context): ApopheniaDatabase {
    val app = context.applicationContext
    return Room.databaseBuilder<ApopheniaDatabase>(app, app.getDatabasePath(DATABASE_NAME).absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
