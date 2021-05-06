package com.cradleVSA.neptune.model

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.ext.getEnglishResources
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

private const val DEFAULT_SIZE = 6

class SymptomsStateTests {
    @Test
    fun `size is correct`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)
        assertEquals(DEFAULT_SIZE, symptomsState.numberOfDefaultSymptoms)
    }

    @Test
    fun `setSymptomIndexState when index 0 is checked, it cannot be unchecked again`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)
        assertEquals(DEFAULT_SIZE, symptomsState.numberOfDefaultSymptoms)

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState.areThereOtherSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }

        // Setting index 0 to true should not do anything.
        symptomsState[0] = true
        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState.areThereOtherSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }

        // Setting index 0 to false should reject that change, and not do anything.
        symptomsState[0] = false
        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState.areThereOtherSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
    }

    @Test
    fun `checking a checkbox makes first false and clearing symptoms turns all others off`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }

        // Now, set all the other symptoms to true.
        for (i in 1 until DEFAULT_SIZE) {
            symptomsState[i] = true
        }

        // Verify that they're all true
        /*
        Expected:
        [ ] No symptoms (index 0)
        [x] Symptom at index 1
        [x] Symptom at index 2
        [x] Symptom at index 3
        [x] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: (empty)
         */
        for (i in 1 until DEFAULT_SIZE) {
            assert(symptomsState[i])
        }
        // Verify that the first is false now
        assert(symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState[0])

        // Now, clear the symptoms
        symptomsState.clearSymptoms()

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        // Check that the first is true again, and that all the others are false
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
    }

    @Test
    fun `checking a checkbox makes first false, manually clearing symptoms turns all others off`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }

        // Now, set all the other symptoms to true.
        for (i in 1 until DEFAULT_SIZE) {
            symptomsState[i] = true
        }

        // Verify that they're all true
        /*
        Expected:
        [ ] No symptoms (index 0)
        [x] Symptom at index 1
        [x] Symptom at index 2
        [x] Symptom at index 3
        [x] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: (empty)
         */
        for (i in 1 until DEFAULT_SIZE) {
            assert(symptomsState[i])
        }
        // Verify that the first is false now
        assert(symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState[0])

        // Now, manually clear the symptoms
        for (i in 1 until DEFAULT_SIZE) {
            symptomsState[i] = false
        }

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        // Check that the first is true again, and that all the others are false
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
    }

    @Test
    fun `checking other checkboxes makes first box false, and index 0 turns all others off`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }

        // Now, set all the other symptoms to true.
        for (i in 1 until DEFAULT_SIZE) {
            symptomsState[i] = true
        }

        // Verify that they're all true
        /*
        Expected:
        [ ] No symptoms (index 0)
        [x] Symptom at index 1
        [x] Symptom at index 2
        [x] Symptom at index 3
        [x] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: (empty)
         */
        for (i in 1 until DEFAULT_SIZE) {
            assert(symptomsState[i])
        }

        // Verify that the first is false now
        assert(symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState[0])

        // Now, set index 0
        symptomsState[0] = true

        // Check that the first is true again, and that all the others are false
        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
    }

    /**
     * This makes sure that if all the checkboxes are unchecked, the first symptom is
     * checked again
     */
    @Test
    fun `if all the checkboxes are unchecked, first symptom is checked again`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        assert(!symptomsState.areThereOtherSymptoms())
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }

        // Now, set the last symptom to true
        symptomsState[DEFAULT_SIZE - 1] = true

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: (empty)
         */
        // Verify that the first is false now
        assert(symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState[0])

        // Verify that they're all false except for the last
        for (i in 0 until DEFAULT_SIZE - 1) {
            assert(!symptomsState[i])
        }
        assert(!symptomsState.areThereOtherSymptoms())
        // Verify the last symptom is true
        assert(symptomsState[DEFAULT_SIZE - 1])

        // Now, set the second last symptom to true
        symptomsState[DEFAULT_SIZE - 2] = true

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [x] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: (empty)
         */
        // Verify that the first is still false
        assert(!symptomsState[0])

        // Verify that they're all false except for the last two
        for (i in 0 until DEFAULT_SIZE - 2) {
            assert(!symptomsState[i])
        }
        assert(symptomsState.areThereDefaultSymptoms())
        // Verify that no custom symptom is set
        assert(!symptomsState.areThereOtherSymptoms())
        // Verify the second last and last symptoms are true
        assert(symptomsState[DEFAULT_SIZE - 2])
        assert(symptomsState[DEFAULT_SIZE - 1])

        // Now, uncheck the second last symptom
        symptomsState[DEFAULT_SIZE - 2] = false

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: (empty)
         */
        // Verify that the first is still false
        assert(!symptomsState[0])

        // Verify that they're all false except for the last
        for (i in 0 until DEFAULT_SIZE - 1) {
            assert(!symptomsState[i])
        }
        assert(symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState.areThereOtherSymptoms())
        // Verify the last symptom is true
        assert(symptomsState[DEFAULT_SIZE - 1])

        // Now, uncheck the last symptom
        symptomsState[DEFAULT_SIZE - 1] = false

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        // Verify custom symptoms not set
        assert(!symptomsState.areThereOtherSymptoms())

        // Verify that the first is true now
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(symptomsState[0])

        // Verify all others are false
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
    }

    /**
     * This makes sure that if all the checkboxes are unchecked, the first symptom is
     * checked again
     */
    @Test
    fun `if all the checkboxes are unchecked but using custom symptom, custom symptom stays`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)
        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        assert(!symptomsState.areThereOtherSymptoms())
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState.areThereAnySymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }

        // Now, set the last symptom to true
        symptomsState[DEFAULT_SIZE - 1] = true

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: (empty)
         */
        // Verify that the first is false now
        assert(symptomsState.areThereAnySymptoms())
        assert(symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState[0])

        // Verify that they're all false except for the last
        for (i in 0 until DEFAULT_SIZE - 1) {
            assert(!symptomsState[i])
        }
        assert(!symptomsState.areThereOtherSymptoms())
        // Verify the last symptom is true
        assert(symptomsState[DEFAULT_SIZE - 1])

        // Now, set the second last symptom to true
        symptomsState[DEFAULT_SIZE - 2] = true

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [x] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: (empty)
         */
        // Verify that the first is still false
        assert(!symptomsState[0])

        // Verify that they're all false except for the last two
        for (i in 0 until DEFAULT_SIZE - 2) {
            assert(!symptomsState[i])
        }
        assert(symptomsState.areThereDefaultSymptoms())
        // Verify that no custom symptom is set
        assert(!symptomsState.areThereOtherSymptoms())
        // Verify the second last and last symptoms are true
        assert(symptomsState[DEFAULT_SIZE - 2])
        assert(symptomsState[DEFAULT_SIZE - 1])

        // Now, add a custom symptom
        symptomsState.setOtherSymptoms("this is my custom symptom")

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [x] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: "this is my custom symptom"
         */
        // Verify that the first is still false
        assert(!symptomsState[0])

        // Verify that they're all false except for the last two
        for (i in 0 until DEFAULT_SIZE - 2) {
            assert(!symptomsState[i])
        }
        assert(symptomsState.areThereDefaultSymptoms())
        // Verify that the custom symptom is set
        assert(symptomsState.areThereOtherSymptoms())
        assertEquals("this is my custom symptom", symptomsState.otherSymptoms)

        // Now, uncheck the second last symptom
        symptomsState[DEFAULT_SIZE - 2] = false

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: "this is my custom symptom"
         */
        // Verify that the first is still false
        assert(!symptomsState[0])

        // Verify that they're all false except for the last
        for (i in 0 until DEFAULT_SIZE - 1) {
            assert(!symptomsState[i])
        }
        assert(symptomsState.areThereDefaultSymptoms())
        // Verify that the custom symptom is set
        assert(symptomsState.areThereOtherSymptoms())
        assertEquals("this is my custom symptom", symptomsState.otherSymptoms)
        // Verify the last symptom is true
        assert(symptomsState[DEFAULT_SIZE - 1])

        // Now, uncheck the last symptom
        symptomsState[DEFAULT_SIZE - 1] = false

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: "this is my custom symptom"
         */
        // Verify that the first is still false
        assert(!symptomsState[0])

        // Verify that all checkboxes are false
        for (i in 0 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
        assert(!symptomsState.areThereDefaultSymptoms())
        // Verify that the custom symptom is set
        assert(symptomsState.areThereOtherSymptoms())
        assertEquals("this is my custom symptom", symptomsState.otherSymptoms)

        // Now, clear the custom symptom
        symptomsState.setOtherSymptoms("")

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        // Verify custom symptoms not set
        assert(!symptomsState.areThereOtherSymptoms())

        // Verify that the first is true now
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(symptomsState[0])

        // Verify all others are false
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
    }

    @Test
    fun `setOtherSymptoms doesn't toggle anything else`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)
        symptomsState.setOtherSymptoms("other symptom")

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: "other symptom"
         */
        assertEquals("other symptom", symptomsState.otherSymptoms)
        assert(symptomsState.areThereAnySymptoms())
        assert(symptomsState.areThereOtherSymptoms())
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
    }

    @Test
    fun `clearOtherSymptoms doesnt clear anything else`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)
        symptomsState.setOtherSymptoms("other symptom")
        for (i in 1 until DEFAULT_SIZE) {
            symptomsState[i] = true
        }

        // Verify that they're all true
        /*
        Expected:
        [ ] No symptoms (index 0)
        [x] Symptom at index 1
        [x] Symptom at index 2
        [x] Symptom at index 3
        [x] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: "other symptom"
         */
        for (i in 1 until DEFAULT_SIZE) {
            assert(symptomsState[i])
        }

        // Verify that the first is false now
        assert(symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState[0])

        // Now, clear the user-entered symptoms
        symptomsState.clearOtherSymptoms()

        // Verify that they're all still true
        /*
        Expected:
        [ ] No symptoms (index 0)
        [x] Symptom at index 1
        [x] Symptom at index 2
        [x] Symptom at index 3
        [x] Symptom at index 4
        [x] Symptom at index 5
        Other symptoms: (empty)
         */
        for (i in 1 until DEFAULT_SIZE) {
            assert(symptomsState[i])
        }

        // Verify that the first is still false
        assert(symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState[0])
    }

    @Test
    fun `entering other symptoms and then erasing it results in first checkbox checked`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)
        symptomsState.setOtherSymptoms("other symptom")
        // Verify that they're all false
        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: "other symptom"
         */
        for (i in 0 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
        assert(symptomsState.areThereOtherSymptoms())
        assert(!symptomsState.areThereDefaultSymptoms())

        // Now, clear the user-entered symptoms
        symptomsState.setOtherSymptoms("")

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (empty)
         */
        // Verify that the first one is now true
        assert(symptomsState[0])
        // Verify that the other checkboxes are false
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
        assert(!symptomsState.areThereOtherSymptoms())
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState.areThereAnySymptoms())
    }

    @Test
    fun `entering other symptoms and then checking first checkbox erases it`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)
        symptomsState.setOtherSymptoms("other symptom")

        /*
        Expected:
        [ ] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: "other symptom"
         */
        // Verify that they're all false
        for (i in 0 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
        assert(symptomsState.areThereOtherSymptoms())
        assert(!symptomsState.areThereDefaultSymptoms())

        // Now, check the first checkbox
        symptomsState[0] = true

        /*
        Expected:
        [x] No symptoms (index 0)
        [ ] Symptom at index 1
        [ ] Symptom at index 2
        [ ] Symptom at index 3
        [ ] Symptom at index 4
        [ ] Symptom at index 5
        Other symptoms: (none)
         */
        // Verify that the first one is now true
        assert(symptomsState[0])
        // Verify that the other checkboxes are false
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }
        assert(!symptomsState.areThereOtherSymptoms())
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState.areThereAnySymptoms())
    }

    @Test
    fun `constructor setup from empty user-supplied symptoms has no symptoms`() {
        val symptomsState = SymptomsState(
            symptomStrings = emptyList(),
            context = createMockContextWithArrayForEnglishSymptoms(Array(DEFAULT_SIZE) { "" })
        )

        /*
        Expected:
        [x] (empty string)(index 0)
        Other symptoms: (none)
         */
        assert(!symptomsState.areThereDefaultSymptoms()) {"failed symptomsState: $symptomsState"}
        assert(!symptomsState.areThereOtherSymptoms()) {"failed symptomsState: $symptomsState"}
        assert(symptomsState[0]) {"failed symptomsState: $symptomsState"}
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i]) {"failed symptomsState: $symptomsState"}
        }
    }

    @Test
    fun `constructor setup user-supplied symptoms has none string`() {
        var symptomsState = SymptomsState(
            symptomStrings = listOf("some useless symptom", "nOnE"),
            context = createMockContextWithArrayForEnglishSymptoms(arrayOf("none", "hello"))
        )

        /*
        Expected:
        [x] none (index 0)
        [x] hello (index 0)
        Other symptoms: "some useless symptom"
         */
        assert(!symptomsState.areThereDefaultSymptoms()) {"failed symptomsState: $symptomsState"}
        assert(!symptomsState.areThereOtherSymptoms()) {"failed symptomsState: $symptomsState"}
        assert(symptomsState[0]) {"failed symptomsState: $symptomsState"}
        assert(!symptomsState[1]) {"failed symptomsState: $symptomsState"}

        /*
        Expected:
        [x] No symptoms (patient healthy) (index 0)
        [ ] hello (index 0)
        Other symptoms: "some useless symptom"
         */
        symptomsState = SymptomsState(
            symptomStrings = listOf("nOnE", "some useless symptom"),
            context = createMockContextWithArrayForEnglishSymptoms(
                arrayOf("No symptoms (patient healthy)", "hello")
            )
        )
        assert(!symptomsState.areThereDefaultSymptoms()) {"failed symptomsState: $symptomsState"}
        assert(!symptomsState.areThereOtherSymptoms()) {"failed symptomsState: $symptomsState"}
        assert(symptomsState[0]) {"failed symptomsState: $symptomsState"}
        assert(!symptomsState[1]) {"failed symptomsState: $symptomsState"}

        symptomsState = SymptomsState(
            symptomStrings = listOf("No symptoms (patient healthy)"),
            context = createMockContextWithArrayForEnglishSymptoms(
                arrayOf("No symptoms (patient healthy)", "hello")
            )
        )
        /*
        Expected:
        [x] No symptoms (patient healthy) (index 0)
        [ ] hello (index 0)
        Other symptoms: "some useless symptom"
         */
        assert(!symptomsState.areThereDefaultSymptoms()) {"failed symptomsState: $symptomsState"}
        assert(!symptomsState.areThereOtherSymptoms()) {"failed symptomsState: $symptomsState"}
        assert(symptomsState[0]) {"failed symptomsState: $symptomsState"}
        assert(!symptomsState[1]) {"failed symptomsState: $symptomsState"}
    }

    @Test
    fun `constructor setup from user-supplied symptoms and build`() {
        val englishSymptoms = arrayOf(
            "No symptoms (patient healthy)", "Headache", "Blurred vision", "Abdominal pain",
            "Bleeding", "Feverish", "Unwell", "Cough", "Sneezing"
        )
        val englishStringToIndexMap = englishSymptoms.mapIndexed { index, s ->
            s to index
        }.toMap()

        val symptomsFromList = listOf(
            // These symptoms should be matched with the ones in the default English symptoms,
            // as whitespace is stripped.
            "FEVERISH", "HeAdA che", "B l urred visi    on", "Unwell ", " Bleeding"
        )
        val symptomsNotFromList = listOf(
            // These symptoms do not match, so they should be in the other symptoms
            "Delusions of grandeur", "Too happy", "w H a t?", "Nothing."
        )
        val symptomsState = SymptomsState(
            symptomStrings = symptomsFromList union symptomsNotFromList,
            context = createMockContextWithArrayForEnglishSymptoms(englishSymptoms)
        )

        val expectedTrueSymptomsIndexes = arrayOf(
            englishStringToIndexMap["Headache"],
            englishStringToIndexMap["Blurred vision"],
            englishStringToIndexMap["Bleeding"],
            englishStringToIndexMap["Feverish"],
            englishStringToIndexMap["Unwell"]
        )
        /*
        Expected:
        [ ] No symptoms (patient healthy) (index 0)
        [x] Headache (index 1)
        [x] Blurred vision (index 2)
        [ ] Abdominal pain (index 3)
        [x] Bleeding (index 4)
        [x] Feverish (index 5)
        [x] Unwell (index 6)
        [ ] Cough (index 7)
        [ ] Sneezing
        Other symptoms: "Delusions of grandeur, Too happy, w H a t?, Nothing."
         */
        assert(symptomsState.areThereDefaultSymptoms())
        assert(symptomsState.areThereOtherSymptoms())
        assert(!symptomsState[0])

        englishSymptoms.forEachIndexed { index, currentSymptom ->
            if (index in expectedTrueSymptomsIndexes) {
                assert(symptomsState[index]) {
                    "expected $currentSymptom to be true but it was false"
                }
            } else {
                assert(!symptomsState[index]) {
                    "expected $currentSymptom to be false but it was true"
                }
            }
        }

        // We expect the format to be: "Delusions of grandeur, Too happy, w H a t?, Nothing."
        val expectedOtherSymptoms = symptomsNotFromList.joinToString(", ")
        assertEquals(expectedOtherSymptoms, symptomsState.otherSymptoms)

        val listOfSymptoms = symptomsState.buildSymptomsList(englishSymptoms)

        val expected = mutableListOf(
            "Headache", "Blurred vision", "Bleeding", "Feverish", "Unwell"
        ).apply { addAll(listOfSymptoms) }

        expected.forEach { expectedSymptom ->
            listOfSymptoms.find { it == expectedSymptom } ?: fail {
                "The string <$expectedSymptom]> not found in resulting list of symptoms: " +
                    "$listOfSymptoms"
            }
        }
    }

    @Test
    fun `building a no-symptoms SymptomsState results in empty list`() {
        val symptomsState = SymptomsState(DEFAULT_SIZE)
        assert(!symptomsState.areThereAnySymptoms())
        assert(!symptomsState.areThereDefaultSymptoms())
        assert(!symptomsState.areThereOtherSymptoms())
        assert(symptomsState[0])
        for (i in 1 until DEFAULT_SIZE) {
            assert(!symptomsState[i])
        }

        val fakeArray = arrayOf("none", "2", "3", "4", "5", "6")

        assert(symptomsState.buildSymptomsList(fakeArray).isEmpty())

        symptomsState.setOtherSymptoms("this is my custom symptom")
        assertEquals(listOf("this is my custom symptom"), symptomsState.buildSymptomsList(fakeArray))

        symptomsState.setOtherSymptoms("")
        assert(symptomsState.buildSymptomsList(fakeArray).isEmpty())
    }

    @Test
    fun `equals works`() {
        val listOfSymptomsState = mutableListOf<SymptomsState>()
        for (i in 1..2) {
            val englishSymptoms = arrayOf(
                "No symptoms (patient healthy)", "Headache", "Blurred vision", "Abdominal pain",
                "Bleeding", "Feverish", "Unwell", "Cough", "Sneezing"
            )
            val mockedContext = createMockContextWithArrayForEnglishSymptoms(englishSymptoms)

            val symptomsFromList = listOf(
                "FEVERISH", "HeAdA che", "B l urred visi    on", "Unwell ", " Bleeding"
            )
            val symptomsNotFromList = listOf(
                "Delusions of grandeur", "Too happy", "w H a t?", "Nothing."
            )
            listOfSymptomsState.add(
                SymptomsState(symptomsFromList union symptomsNotFromList, mockedContext)
            )
        }
        assertEquals(listOfSymptomsState[0], listOfSymptomsState[1])
    }

    private fun createMockContextWithArrayForEnglishSymptoms(
        englishSymptoms: Array<String>
    ): Context {
        val mockResources = mockk<Resources> {
            every { getStringArray(R.array.reading_symptoms) } answers {
                englishSymptoms
            }
            every { configuration } returns mockk<Configuration> {
                every { setLocale(any()) } answers {
                }
            }
        }
        mockkStatic("com.cradleVSA.neptune.ext.ContextExtensionsKt")
        every { any<Context>().getEnglishResources() } answers { mockResources }

        return mockk()
    }
}