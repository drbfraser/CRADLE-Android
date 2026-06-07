package com.cradleplatform.neptune.database

/** tests for all the FormResponseDao methods
 * instrument tests to be run on an emulated device on Firebase Test Lab
 */

class FormResponseDaoTests {

    companion object{
        private const val DATABASE = "form-response-dao-test"
        private const val PATIENT_ID = "test-patient-001"
        private const val FORM_TEMPLATE_ID = "test-form-template-001"
        private const val FORM_CLASS_ID = "test-form-class-001"
    }

    @Rule
    @JvmField
    val helper : MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CradleDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun getDatabase() : CradleDatabase{
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CradleDatabase::class.java,
            DATABASE
        ).fallbackToDestructiveMigration().build()
        helper.closeWhenFinished(db)
        return db    
    }

    @Before
    fun setUp(){
        getDatabase().apply {
            clearAllTables()
            close()
        }        
    }

    @After
    fun finish(){
        getDatabase().apply {
            clearAllTables()
            close()
        } 
    }
}