package com.cradle.neptune.view.ui.settings

import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference
import com.cradle.neptune.utilitiles.functional.Either
import com.cradle.neptune.utilitiles.functional.coalesce

/**
 * Tells this preference to use a dynamically generated summary by pulling
 * data from [SharedPreferences] using its key.
 *
 * @param transform Transforms the value retrieved from shared preferences
 * before setting it as this preference's summary. If not supplied, no
 * transformation is applied.
 */
fun Preference.useDynamicSummary(transform: ((String?) -> String)? = null): Preference {
    summaryProvider = Preference.SummaryProvider<Preference> { _ ->
        val value = sharedPreferences.getString(key, null)
        if (transform == null) {
            value
        } else {
            transform(value)
        }.also {
            Log.d(this::class.simpleName, "Preference $key changed, updating summary to: '$it'")
        }
    }
    return this
}

/**
 * Registers a validator with this preference which is called to validate
 * any new values set by the user.
 *
 * If an invalid value is found (i.e., [validator] returns false) then a
 * toast is shown to the user containing the error message returned from
 * the validator.
 *
 * @param validator A predicate returning `Right(Unit)` if the new value is
 * valid or a Left variant containing an error message otherwise.
 */
inline fun <reified T> Preference.withValidator(crossinline validator: (T) -> Either<String, Unit>): Preference {
    this.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, obj ->
        if (obj !is T) {
            Log.wtf(
                this::class.simpleName,
                "Preference $key has a and unexpected value type: ${obj.javaClass.canonicalName}"
            )
            throw IllegalArgumentException("Preference value is not a string")
        }
        validator(obj)
            .mapRight { true }
            .mapLeft { value ->
                Toast.makeText(context, value, Toast.LENGTH_SHORT).show()
                false
            }
            .coalesce()
    }
    return this
}
