package com.cradleVSA.neptune.testutils

import android.database.sqlite.SQLiteConstraintException

fun assertForeignKeyConstraintException(sqLiteException: SQLiteConstraintException) {
    assert(sqLiteException.message != null) { "missing message" }
    assert(
        sqLiteException.message!!.contains("foreign key", ignoreCase = true)
            && sqLiteException.message!!.contains("constraint failed", ignoreCase = true)
    ) { "got \"${sqLiteException.message}\" instead of foreign key constraint failed" }
}
