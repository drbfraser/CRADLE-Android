package com.cradle.neptune.view.ui.settings.ui.settingnamedpairs;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import androidx.core.app.DialogFragment;
import androidx.core.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.view.ui.settings.SettingNamedPairsActivity;
import com.cradle.neptune.viewmodel.NamedPairViewAdapter;

import java.util.List;

import javax.inject.Inject;


public class SettingNamedPairsFragment extends Fragment {
    private static final String TAG = "SettingNamedPairsFrag";

    // Data Model
    @Inject
    SharedPreferences sharedPreferences;
    @Inject
    Settings settings;

    // UI Components
    private RecyclerView recyclerView;
    private NamedPairViewAdapter listAdapter;


    // fields
    private SettingNamedPairsActivity.SelectPair selectedPair;


    public static SettingNamedPairsFragment newInstance() {
        return new SettingNamedPairsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.setting_named_pairs_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // inject:
        ((MyApp) getActivity().getApplication()).getAppComponent().inject(this);
        selectedPair = ((SettingNamedPairsActivity) getActivity()).getSelectedPair();

        // floating action bar: create new currentReading
        FloatingActionButton fab = getView().findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogFragment newFragment = EditNamedPairDialogFragment.makeInstanceForNew(
                        new EditNamedPairDialogFragment.DoneCallback() {
                            @Override
                            public void ok(Settings.NamedPair pair) {
                                writePairToPrefs(pair);
                            }

                            @Override
                            public void delete(Settings.NamedPair pair) {
                            }
                        }
                );
                newFragment.show(getFragmentManager(), "Settings");
            }

        });

        updateUI();

    }

    private void updateUI() {

        setupReadingsRecyclerView();

        // empty state
        TextView tvEmpty = getView().findViewById(R.id.webViewEmptyState);
        if (settings.getHealthCentres().size() == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }

    }

    /**
     * List View
     */
    private void setupReadingsRecyclerView() {
        recyclerView = getView().findViewById(R.id.list_pair_items);

        // Improve performance: size of each entry does not change.
        recyclerView.setHasFixedSize(true);

        // use linear layout
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        // get content
        List<Settings.NamedPair> data = null;
        switch (selectedPair) {
            case SELECT_PAIR_HEALTH_CENTRES:
                data = settings.getHealthCentres();
                break;
            case SELECT_PAIR_UPLOAD_SERVERS:
            default:
                Util.ensure(false);
        }

        // set adapter
        listAdapter = new NamedPairViewAdapter(data);
        listAdapter.setOnClickElementListener(new NamedPairViewAdapter.OnClickElement() {

            @Override
            public void onClick(Settings.NamedPair pair, int position) {
                DialogFragment newFragment = EditNamedPairDialogFragment.makeInstanceForEdit(pair,
                        new EditNamedPairDialogFragment.DoneCallback() {
                            @Override
                            public void ok(Settings.NamedPair pair) {
                                writePairToPrefs(pair, position);
                            }

                            @Override
                            public void delete(Settings.NamedPair pair) {
                                removePairFromPrefs(position);
                            }
                        }
                );
                newFragment.show(getFragmentManager(), "Settings");
            }

            @Override
            public boolean onLongClick(Settings.NamedPair pair) {
                return false;
            }
        });

        recyclerView.setAdapter(listAdapter);
    }


    /**
     * Access Shared Prefs
     */
    private void writePairToPrefs(Settings.NamedPair pair) {
        int numValues = settings.getHealthCentreNames().size();
        writePairToPrefs(pair, numValues);
    }

    private void writePairToPrefs(Settings.NamedPair pair, int position) {
        String spNumVals = Settings.PREF_KEY_NUM_HEALTH_CENTRES;
        String spNameBase = Settings.PREF_KEY_HEALTH_CENTRE_NAME_;
        String spValueBase = Settings.PREF_KEY_HEALTH_CENTRE_CELL_;

        int numValues = sharedPreferences.getInt(spNumVals, 0);

        // add it
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putString(spNameBase + position, pair.name);
        edit.putString(spValueBase + position, pair.value);

        // .. update #, if needed
        if (position + 1 > numValues) {
            edit.putInt(spNumVals, numValues + 1);
        }
        edit.commit();

        // trigger settings to reload
        settings.loadFromSharedPrefs();

        // reload
        updateUI();
    }

    private void removePairFromPrefs(int position) {
        String spNumVals = Settings.PREF_KEY_NUM_HEALTH_CENTRES;
        String spNameBase = Settings.PREF_KEY_HEALTH_CENTRE_NAME_;
        String spValueBase = Settings.PREF_KEY_HEALTH_CENTRE_CELL_;

        int numValues = sharedPreferences.getInt(spNumVals, 0);

        SharedPreferences.Editor edit = sharedPreferences.edit();

        // shift ones after:
        for (int targetIdx = position; targetIdx < numValues - 1; targetIdx++) {
            int sourceIdx = targetIdx + 1;
            String sourceName = sharedPreferences.getString(spNameBase + sourceIdx, "");
            String sourceValue = sharedPreferences.getString(spValueBase + sourceIdx, "");

            edit.putString(spNameBase + targetIdx, sourceName);
            edit.putString(spValueBase + targetIdx, sourceValue);
        }

        // delete final ones
        edit.remove(spNameBase + (numValues - 1));
        edit.remove(spValueBase + (numValues - 1));

        // update #
        edit.putInt(spNumVals, numValues - 1);

        edit.commit();

        // trigger settings to reload
        settings.loadFromSharedPrefs();

        // reload
        updateUI();
    }

}
