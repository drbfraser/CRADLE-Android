package com.cradle.neptune.view.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.utilitiles.functional.Either
import com.cradle.neptune.utilitiles.functional.Left
import com.cradle.neptune.utilitiles.functional.Right
import com.cradle.neptune.utilitiles.functional.coalesce
import javax.inject.Inject

class AdvancedSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(this::class.simpleName, "Created advanced settings activity")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Advanced Settings"

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, AdvancedSettingsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        Log.v(this::class.simpleName, "Navigating away from advanced settings")
        onBackPressed()
        return super.onSupportNavigateUp()
    }
}

class AdvancedSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        (activity?.application as MyApp)
            .appComponent
            .inject(this)

        Log.v(this::class.simpleName, "Loading advanced settings from resource")
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)

        preferenceScreen
            .findPreference<Preference>("setting_server_hostname")
            ?.useDynamicSummary()

        preferenceScreen
            .findPreference<Preference>("setting_server_port")
            ?.useDynamicSummary { v -> if (v.isNullOrEmpty()) "(default)" else v }
            ?.withValidator { value ->
                // Allow black values as they will be treated as the default port number
                if (value.isEmpty()) {
                    return@withValidator Right(Unit)
                }

                // Ensure that the port is actually a number
                val int = value.toIntOrNull() ?: return@withValidator Left("Port must be a number")

                // Check to make sure it is a valid port number
                if (int !in 0..49151) {
                    return@withValidator Left("'$value' is not a valid port number")
                }

                Right(Unit)
            }
    }

    /**
     * Tells this preference to use a dynamically generated summary by pulling
     * data from [SharedPreferences] using its key.
     *
     * @param transform Transforms the value retrieved from shared preferences
     * before setting it as this preference's summary. If not supplied, no
     * transformation is applied.
     */
    private fun Preference.useDynamicSummary(transform: ((String?) -> String)? = null): Preference {
        summaryProvider = Preference.SummaryProvider<Preference> { _ ->
            val value = sharedPreferences.getString(key, null)
            if (transform == null) {
                value
            } else {
                transform(value)
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
     * valid or a [Left] variant containing an error message otherwise.
     */
    private fun Preference.withValidator(validator: (String) -> Either<String, Unit>): Preference {
        this.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, obj ->
            if (obj !is String) {
                Log.wtf(this::class.simpleName, "Preference $key has a non-string value: $obj")
                throw IllegalArgumentException("Preference value is not a string")
            }
            validator(obj)
                .mapRight { _ -> true }
                .mapLeft { value ->
                    Toast.makeText(context, value, Toast.LENGTH_SHORT).show()
                    false
                }
                .coalesce()
        }
        return this
    }
}
