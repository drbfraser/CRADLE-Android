package com.cradle.neptune.model

import java.util.Locale

/**
 * Keeps track of the symptoms checkbox state. We do not deal with direct strings aside from the
 * [otherSymptoms] property, since we want to send English strings to the server only.
 *
 * Why: Because the server sends and receives all symptoms in English. We want to support showing
 * localized default symptoms to the user. So, we use the symptoms array as the canonical source
 * of the symptoms.
 *
 * @property defaultSymptomsState The default symptoms that are checked, where the index
 * corresponds to the symptoms in the string array R.array.reading_symptoms in res/values.
 */
class SymptomsState(val numberOfDefaultSymptoms: Int) {
    /**
     * Holds the state of all default symptoms, where a symptom is present if and only if its
     * value in the array is true.
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
                    setSymptomIndexState(indexOfEnglishSymptom, true)
                }
                ?: otherSymptomsBuilder.append(symptomFromList).append(", ")
        }
        setOtherSymptoms(otherSymptomsBuilder.removeSuffix(", ").toString())
    }

    fun buildSymptomsList(defaultEnglishSymptoms: Array<String>): List<String> {
        val list = defaultEnglishSymptoms
            .filterIndexed { index, _ -> defaultSymptomsState[index] }
            .toMutableList()
        if (areThereOtherSymptoms()) list.add(otherSymptoms)
        return list
    }

    fun areThereOtherSymptoms() = otherSymptoms.isNotBlank()

    fun areThereDefaultSymptoms() = !defaultSymptomsState[0]

    fun isSymptomIndexChecked(index: Int) = defaultSymptomsState[index]

    fun clearSymptoms() {
        defaultSymptomsState.forEachIndexed { index, _ -> defaultSymptomsState[index] = false }
        otherSymptoms = ""
        defaultSymptomsState[0] = true
    }

    fun clearOtherSymptoms() {
        otherSymptoms = ""
    }

    fun setSymptomIndexState(index: Int, newValue: Boolean) {
        if (index == 0 && newValue) {
            clearSymptoms()
        } else {
            defaultSymptomsState[index] = newValue
            defaultSymptomsState[0] = false
        }
    }

    fun setOtherSymptoms(otherSymptoms: String) {
        this.otherSymptoms = otherSymptoms
        defaultSymptomsState[0] = false
    }

    /**
     * Lowercases and removes all whitespaces for comparison.
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
