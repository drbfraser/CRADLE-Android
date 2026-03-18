package com.cradleplatform.neptune.activities.forms

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A base activity for form-based screens, providing common functionality
 * such as a back button in the toolbar and handling of unsaved changes.
 */
abstract class BaseFormActivity : AppCompatActivity() {

    /**
     * Checks whether the form has unsaved changes.
     * Subclasses should override this method to provide custom logic,
     * typically by querying the ViewModel state.
     *
     * Default implementation returns false (no unsaved changes).
     */
    protected open fun hasUnsavedChanges(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActionBar()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    /**
     * Sets up the activity's action bar to display a back button.
     */
    private fun setupActionBar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleBackPress()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Handles the back press action. If there are unsaved changes, it shows a confirmation
     * dialog. Otherwise, it finishes the activity.
     */
    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    /**
     * Displays a dialog to confirm leaving the screen when there are unsaved changes.
     */
    private fun showUnsavedChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsaved_changes_dialog_title)
            .setMessage(R.string.unsaved_changes_dialog_message)
            .setPositiveButton(R.string.action_leave) { _, _ -> finish() }
            .setNegativeButton(R.string.action_stay, null)
            .show()
    }
}
