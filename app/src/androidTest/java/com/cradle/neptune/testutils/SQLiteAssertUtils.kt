package com.cradle.neptune.testutils

import android.database.sqlite.SQLiteConstraintException

fun assertForeignKeyCode787Exception(sqLiteException: SQLiteConstraintException) {
    assert(sqLiteException.message != null) { "missing message" }
    assert(
        sqLiteException.message!!.contains("foreign key", ignoreCase = true)
            && sqLiteException.message!!.contains("constraint failed", ignoreCase = true)
            && sqLiteException.message!!.contains("code 787", ignoreCase = true)
    ) { "expect code 787; got ${sqLiteException.message}" }
}
