package com.cradleplatform.neptune.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.BloodPressure
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionLangVersion
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.model.UrineTest
import com.cradleplatform.neptune.testutils.assertForeignKeyConstraintException
import com.cradleplatform.neptune.testutils.assertThrows
import com.cradleplatform.neptune.utilities.Weeks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID



class DatabaseIntegrityTests {
    companion object {
        private const val DATABASE = "database-integrity-test"
        private const val FORM_CLASS_ID = "test-form-class-001"
        private const val FORM_TEMPLATE_ID = "test-form-template-001"
    }

    @Rule
    @JvmField
    val helper : MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CradleDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

        private fun getDatabase(): CradleDatabase {
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CradleDatabase::class.java,
            DATABASE
        ).fallbackToDestructiveMigration().build()
        helper.closeWhenFinished(db)
        return db
    }

    @Before
    fun setUp() {
        getDatabase().apply { clearAllTables(); close() }
    }

    @After
    fun finish() {
        getDatabase().apply { clearAllTables(); close() }
    }
}