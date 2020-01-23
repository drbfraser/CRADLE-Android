package com.cradle.neptune.view.ui.settings.ui.settingnamedpairs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import androidx.core.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.cradle.neptune.R;
import com.cradle.neptune.model.Settings;

public class EditNamedPairDialogFragment extends DialogFragment {
    private static final String STATE_SHOW_FOR_EDITING = "state_editing";
    private static final String STATE_PAIR_NAME = "state_name";
    private static final String STATE_PAIR_VALUE = "state_value";
    // current state
    private boolean isShownForEditing = true;
    private Settings.NamedPair pair;
    private DoneCallback callback;

    public static EditNamedPairDialogFragment makeInstanceForNew(DoneCallback callback) {
        EditNamedPairDialogFragment dialog = new EditNamedPairDialogFragment();
        dialog.isShownForEditing = false;
        dialog.pair = new Settings.NamedPair("", "");
        dialog.callback = callback;
        return dialog;
    }

    public static EditNamedPairDialogFragment makeInstanceForEdit(Settings.NamedPair pair, DoneCallback callback) {
        EditNamedPairDialogFragment dialog = new EditNamedPairDialogFragment();
        dialog.pair = pair;
        dialog.callback = callback;
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // Get the layout inflater
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        // Restore state (if killed)
        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        }

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(inflater.inflate(R.layout.edit_named_pair_dialog, null))
                .setPositiveButton(android.R.string.ok, null)    // intercepted below
                .setNegativeButton(android.R.string.cancel, null);
        // Create the AlertDialog object and return it
        Dialog dialog = builder.create();

        // intercept OK button
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        extractPairFromUi(dialog);

                        // validate:
                        if (pair.name.length() == 0 || pair.value.length() == 0) {
                            TextView error = dialog.findViewById(R.id.tvError);
                            error.setVisibility(View.VISIBLE);
                        } else {
                            callback.ok(pair);
                            dismiss();
                        }
                    }
                });
            }
        });

        // show it now so that we can call findViewById() w/o getting null
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        setupDialogLabels(dialog);
        setupDeleteButton(dialog);
        updateUI(dialog);

        return dialog;
    }

    /**
     * Save State
     */
    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putBoolean(STATE_SHOW_FOR_EDITING, isShownForEditing);
        savedInstanceState.putString(STATE_PAIR_NAME, pair.name);
        savedInstanceState.putString(STATE_PAIR_VALUE, pair.value);

        // REVISIT: How to save callback?

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Restore state members from saved instance
        isShownForEditing = savedInstanceState.getBoolean(STATE_SHOW_FOR_EDITING);
        pair = new Settings.NamedPair(
                savedInstanceState.getString(STATE_PAIR_NAME),
                savedInstanceState.getString(STATE_PAIR_VALUE));
        isShownForEditing = savedInstanceState.getBoolean(STATE_SHOW_FOR_EDITING);

        // REVISIT: How to restore callback?

    }

    /**
     * Setup UI
     */
    private void extractPairFromUi(Dialog dialog) {
        EditText et;
        et = dialog.findViewById(R.id.etName);
        pair.name = et.getText().toString();

        et = dialog.findViewById(R.id.etValue);
        pair.value = et.getText().toString();
    }

    private void setupDialogLabels(Dialog dialog) {
        // TODO: add code to set for health centre vs upload server
    }

    private void setupDeleteButton(Dialog dialog) {
        ImageView iv = dialog.findViewById(R.id.ivDelete);
        if (!isShownForEditing) {
            iv.setVisibility(View.INVISIBLE);
        }
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                extractPairFromUi(dialog);
                callback.delete(pair);
                dismiss();
            }
        });
    }

    private void updateUI(Dialog dialog) {
        EditText et;
        et = dialog.findViewById(R.id.etName);
        et.setText(pair.name);

        et = dialog.findViewById(R.id.etValue);
        et.setText(pair.value);
    }

    public interface DoneCallback {
        void ok(Settings.NamedPair pair);

        void delete(Settings.NamedPair pair);
    }

}
