package com.cradle.neptune.utilities

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.MutableLiveData
import com.cradle.neptune.utilitiles.LiveDataDynamicModelBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException
import kotlin.reflect.full.createType
import kotlin.reflect.full.defaultType

class LiveDataDynamicModelBuilderTests {

    @BeforeEach
    fun beforeEach() {
        // This is needed to handle the Looper that is used by LiveData.
        // Code taken from InstantTaskExecutorRule. See InstantTaskExecutor.java inside of
        // lifecycle/lifecycle-livedata/src/test/java/androidx/lifecycle/util/ at
        // https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-master-dev/
        //
        // Using
        //      @Rule @JvmField
        //      val rule = InstantTaskExecutorRule()
        // with testImplementation androidx.arch.core:core-testing:2.1.0
        // and import androidx.arch.core.executor.testing.InstantTaskExecutorRule isn't working;
        // maybe some Kotlin-related issue.
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) {
                runnable.run()
            }

            override fun postToMainThread(runnable: Runnable) {
                runnable.run()
            }

            override fun isMainThread(): Boolean {
                return true
            }
        })
    }

    @AfterEach
    fun afterEach() {
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun liveDataDynamicModelBuilder_setWithStringKey_populatesInternalMap() {
        // Should do the exact same things as dynamicModelBuilder
        val person = with(LiveDataDynamicModelBuilder()) {
            set("name", "Maya")
            set("age", 20)
            build<Person>()
        }
        assertEquals("Maya", person.name)
        assertEquals(20, person.age)
        assertNull(person.email)
    }

    @Test
    fun liveDataDynamicModelBuilder_setWithRefectionField_populatesInternalMap() {
        // Should do the exact same things as dynamicModelBuilder
        val person = with(LiveDataDynamicModelBuilder()) {
            set(Person::name, "Maya")
            set(Person::age, 20)
            build<Person>()
        }
        assertEquals("Maya", person.name)
        assertEquals(20, person.age)
        assertNull(person.email)
    }

    @Test
    fun liveDataDynamicModelBuilder_setWithLiveData_populatesInternalMap() {
        // Test default values
        val personBuilder = LiveDataDynamicModelBuilder()
        val nameLiveData = personBuilder.get(Person::name, "A")
        assertEquals("A", nameLiveData.value)

        nameLiveData.value = "B"
        assertEquals("B", nameLiveData.value)
        assertEquals("B", personBuilder.get(Person::name, "A").value)

        val ageLiveData = personBuilder.get(Person::age, 35)
        assertEquals(35, ageLiveData.value)
        ageLiveData.value = 56
        assertEquals(56, ageLiveData.value!!)
        assertEquals(56, personBuilder.get(Person::age, 1).value)


        val person = personBuilder.build<Person>()
        assertEquals("B", person.name)
        assertEquals(56, person.age)
        assertNull(person.email)
    }

    @Test
    fun liveDataDynamicModelBuilder_ifMissingField_returnNull() {
        // Should do the exact same things as dynamicModelBuilder
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            with(LiveDataDynamicModelBuilder()) {
                set(Person::name, "Maya")
                build<Person>()
            }
        }
    }

    @Test
    fun liveDataDynamicModelBuilder_setWithRefectionField_getBackLiveData() {
        val personBuilder = with(LiveDataDynamicModelBuilder()) {
            set(Person::name, "Jack")
            set(Person::age, 25)
        }

        assertEquals("Jack", personBuilder.get(Person::name).value)
        assertEquals(25, personBuilder.get(Person::age).value)

        // We expect that this should behave like LiveData.
        val emailLiveData = personBuilder.get<String?>(Person::email)
        assertNull(emailLiveData.value)
        emailLiveData.value = "some@email.com"
        // Should be automatically updated, because the LiveData in emailLiveData and the one
        // in personBuilder should be the same.
        assertEquals("some@email.com", emailLiveData.value)
        assertEquals("some@email.com", personBuilder.get(Person::email).value)

        personBuilder.get(Person::email).value = "some_other@email.com"
        assertEquals("some_other@email.com", personBuilder.get(Person::email).value)
        assertEquals("some_other@email.com", emailLiveData.value)

        // We never replace any LiveData stored in personBuilder we only update the values in them.
        // This way, anything observing it can update itself accordingly
        personBuilder.set(Person::email, "this_is_same@email.com")
        assertEquals("this_is_same@email.com", personBuilder.get(Person::email).value)
        assertEquals("this_is_same@email.com", emailLiveData.value)
    }

    @Test
    fun liveDataDynamicModelBuilder_setWithRefectionFieldWorkerThread_getBackLiveData() {
        // The Looper has been overridden to immediately run any tasks posted to the main thread,
        // effectively making postValue the same as setValue. We should test this anyway for
        // completeness.
        val personBuilder = with(LiveDataDynamicModelBuilder()) {
            setWorkerThread(Person::name, "Jack")
            setWorkerThread(Person::age, 25)
        }

        assertEquals("Jack", personBuilder.get(Person::name).value)
        assertEquals(25, personBuilder.get(Person::age).value)

        // We expect that this should behave like LiveData.
        val emailLiveData = personBuilder.get<String?>(Person::email)
        assertNull(emailLiveData.value)
        emailLiveData.postValue("some@email.com")
        // Should be automatically updated, because the LiveData in emailLiveData and the one
        // in personBuilder should be the same.
        assertEquals("some@email.com", emailLiveData.value)
        assertEquals("some@email.com", personBuilder.get(Person::email).value)

        personBuilder.get(Person::email).value = "some_other@email.com"
        assertEquals("some_other@email.com", personBuilder.get(Person::email).value)
        assertEquals("some_other@email.com", emailLiveData.value)

        // We never replace any LiveData stored in personBuilder we only update the values in them.
        // This way, anything observing it can update itself accordingly
        personBuilder.setWorkerThread(Person::email, "this_is_same@email.com")
        assertEquals("this_is_same@email.com", personBuilder.get(Person::email).value)
        assertEquals("this_is_same@email.com", emailLiveData.value)
    }

    @Test
    fun liveDataDynamicModelBuilder_decompose() {
        val person = Person(name = "Someone", age = 50, email = null)
        val personBuilder = with(LiveDataDynamicModelBuilder()) {
            // If we use [LiveDataDynamicModelBuilder.decompose], it's not compatible with the
            // builder way of working due to it being an inline function and its the return type
            // being the super class, [DynamicModelBuilder]. We have to define a new inline fun that
            // uses the overrided [decompose(KProperty<*>, Any?)] method.
            decomposeToLiveData(person)
        }

        assertEquals("Someone", personBuilder.get(Person::name).value)
        assertEquals(50, personBuilder.get(Person::age).value)
        assertNull(personBuilder.get(Person::email).value)

        val emailLiveData = personBuilder.get<String?>(Person::email)
        personBuilder.set(Person::email, "this_is_same@email.com")
        assertEquals("this_is_same@email.com", personBuilder.get(Person::email).value)
        assertEquals("this_is_same@email.com", emailLiveData.value)
    }

}
