package com.cradle.neptune.model

import java.util.Locale

/**
 * Keeps track of the checked state of the symptoms. We do not deal with direct strings aside from
 * the [otherSymptoms] property, since we want to send English strings for the default strings to
 * the server only. We use a [BooleanArray] instead of a Map for efficiency.
 *
 * Why: Because the server sends and receives all default symptoms in English. We want to support
 * showing localized default symptoms to the user.
 *
 */
class SymptomsState(val numberOfDefaultSymptoms: Int) {
    /**
     * Holds the state of all default symptoms, where a symptom is present if and only if its
     * value in the array is true. The index corresponds to the symptoms in the string array
     * R.array.reading_symptoms in res/values.
     *
     * The [SymptomsState] ensures that this BooleanArray has the following property:
     *
     *     defaultSymptomsState[0] (the No Symptoms checkbox) is true if and only if
     *     all the others are false and otherSymptoms == "".
     */
    private val defaultSymptomsState = BooleanArray(numberOfDefaultSymptoms)

    init {
        check(numberOfDefaultSymptoms > 0)
        defaultSymptomsState[0] = true
    }

    var otherSymptoms: String = ""
        private set

    /**
     * Initializes the [SymptomsState] using the user-provided [symptomStrings] and the
     * [defaultEnglishSymptoms] from R.array.reading_symptoms in res/values.
     */
    constructor(
        symptomStrings: Collection<String>,
        defaultEnglishSymptoms: Array<String>
    ) : this(defaultEnglishSymptoms.size) {
        if (symptomStrings.isEmpty()) {
            // At this point of the code, the SymptomsState already has all other booleans as false.
            defaultSymptomsState[0] = true
            return
        }
        val otherSymptomsBuilder = StringBuilder("")
        symptomStrings.forEach { symptomFromList ->
            if (isNoneSymptom(symptomFromList, defaultEnglishSymptoms)) {
                // At this point of the code, the SymptomsState may have other booleans as true
                // and strings in `otherSymptoms`, so we need to clear them all.
                clearSymptoms()
                return
            }
            // Find this symptom in the list of default English symptoms.
            // If it's not there, find will return null; that means it'll be added to the
            // other symptoms.
            defaultEnglishSymptoms
                .find { trimAndLowercase(it) == trimAndLowercase(symptomFromList) }
                ?.apply {
                    val indexOfEnglishSymptom = defaultEnglishSymptoms.indexOf(this)
                    set(indexOfEnglishSymptom, true)
                }
                ?: otherSymptomsBuilder.append(symptomFromList).append(", ")
        }
        setOtherSymptoms(otherSymptomsBuilder.removeSuffix(", ").toString())
    }

    /**
     * Builds a list of symptoms given a String array from R.array.reading_symptoms.
     * [defaultSymptoms] can be in any language.
     *
     * If [listForDisplayingInUi] is true, then no symptoms is represented by a single element
     * list of the first element in [defaultSymptoms]. If it is false, then no symptoms will
     * return an empty list.
     */
    fun buildSymptomsList(
        defaultSymptoms: Array<String>,
        listForDisplayingInUi: Boolean = false
    ): List<String> {
        if (defaultSymptomsState[0]) {
            return if (listForDisplayingInUi) {
                listOf(defaultSymptoms[0])
            } else {
                emptyList()
            }
        }

        val list = defaultSymptoms
            .filterIndexed { index, _ -> defaultSymptomsState[index] }
            .toMutableList()
        if (areThereOtherSymptoms()) list.add(otherSymptoms)
        return list
    }

    fun areThereAnySymptoms() = areThereOtherSymptoms() || areThereDefaultSymptoms()

    fun areThereOtherSymptoms() = otherSymptoms.isNotBlank()

    fun areThereDefaultSymptoms(): Boolean {
        if (defaultSymptomsState[0]) return false
        for (i in 1 until defaultSymptomsState.size) {
            if (defaultSymptomsState[i]) {
                return true
            }
        }
        return false
    }

    operator fun get(index: Int) = defaultSymptomsState[index]

    fun clearSymptoms() {
        defaultSymptomsState.forEachIndexed { index, _ -> defaultSymptomsState[index] = false }
        otherSymptoms = ""
        defaultSymptomsState[0] = true
    }

    fun clearOtherSymptoms() {
        otherSymptoms = ""
        onNewInput(inputAddedSymptom = false)
    }

    operator fun set(index: Int, newValue: Boolean) {
        if (index == 0) {
            // This makes it so that defaultSymptomsState[0] is true, and it cannot be turned off
            // by toggling it again.
            if (newValue && !defaultSymptomsState[0]) {
                clearSymptoms()
            }
        } else {
            defaultSymptomsState[index] = newValue
            onNewInput(inputAddedSymptom = newValue)
        }
    }

    /**
     * When new input is added to this [SymptomsState] object, run this to update
     * the state of the first checkbox depending on the others.
     */
    private fun onNewInput(inputAddedSymptom: Boolean) {
        if (inputAddedSymptom) {
            defaultSymptomsState[0] = false
        } else {
            // Worst case: O(n) check, but acceptable because this is meant to be used
            // with a small number of checkboxes. For maximum efficiency, we could use
            // bits and do an bitwise AND or something, but that's unneeded right now.
            if (!areThereAnySymptoms()) {
                defaultSymptomsState[0] = true
            }
        }
    }

    fun setOtherSymptoms(otherSymptoms: String) {
        this.otherSymptoms = otherSymptoms
        onNewInput(inputAddedSymptom = areThereOtherSymptoms())
    }

    /**
     * Lowercases and removes all whitespaces for comparison. The frontend/backend may use symptoms
     * of all caps.
     */
    private fun trimAndLowercase(symptomString: String) =
        symptomString.toLowerCase(Locale.getDefault()).replace("\\s".toRegex(), "")

    /**
     * When we see these symptoms, the [SymptomsState] has to change so that all the other symptoms
     * are cleared. The frontend uses "none" apparently, so we check that here too.
     */
    private fun isNoneSymptom(stringToBeTrimmed: String, defaultEnglishSymptoms: Array<String>) =
        trimAndLowercase(stringToBeTrimmed) == "none" ||
            trimAndLowercase(stringToBeTrimmed) == trimAndLowercase(defaultEnglishSymptoms[0])

    /**
     * Android Studio-generated hash code function.
     */
    override fun hashCode(): Int {
        var result = defaultSymptomsState.contentHashCode()
        result = 31 * result + otherSymptoms.hashCode()
        return result
    }

    /**
     * Android Studio-generated equals function.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SymptomsState

        if (!defaultSymptomsState.contentEquals(other.defaultSymptomsState)) return false
        if (otherSymptoms != other.otherSymptoms) return false

        return true
    }

    override fun toString(): String {
        return "${SymptomsState::class.java.simpleName}(" +
            "numberOfDefaultSymptoms=$numberOfDefaultSymptoms, " +
            "defaultSymptomsState=[${defaultSymptomsState.joinToString(", ")}], " +
            "otherSymptoms=\"$otherSymptoms\"" +
            ")"
    }
}
