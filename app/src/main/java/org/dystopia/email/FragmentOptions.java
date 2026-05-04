package org.dystopia.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018, Marcel Bokhorst (M66B)
    Copyright 2018-2020, Distopico (dystopia project) <distopico@riseup.net> and contributors
*/

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Spinner;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FragmentOptions extends FragmentEx {
    private Switch optSyncEnabled;
    private Switch optAvatars;
    private Switch optLight;
    private Switch optBrowse;
    private Switch optCompact;
    private Switch optInsecure;
    private Switch optDebug;
    private Spinner spnBodyTextSize;

    @Override
    @Nullable
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
        setSubtitle(R.string.title_advanced);

        View view = inflater.inflate(R.layout.fragment_options, container, false);

        // Get controls
        optSyncEnabled = view.findViewById(R.id.optSyncEnabled);
        optAvatars = view.findViewById(R.id.optAvatars);
        optLight = view.findViewById(R.id.optLight);
        optBrowse = view.findViewById(R.id.optBrowse);
        optCompact = view.findViewById(R.id.optCompact);
        optInsecure = view.findViewById(R.id.optInsecure);
        optDebug = view.findViewById(R.id.optDebug);
        spnBodyTextSize = view.findViewById(R.id.spnBodyTextSize);

        // Wire controls

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        optSyncEnabled.setChecked(prefs.getBoolean("enabled", true));
        optSyncEnabled.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    prefs.edit().putBoolean("enabled", checked).apply();
                    if (checked) {
                        ServiceSynchronize.start(getContext());
                    } else {
                        ServiceSynchronize.stop(getContext());
                    }
                }
            });

        optAvatars.setChecked(prefs.getBoolean("avatars", true));
        optAvatars.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    prefs.edit().putBoolean("avatars", checked).apply();
                }
            });

        optLight.setChecked(prefs.getBoolean("light", false));
        optLight.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    prefs.edit().putBoolean("light", checked).apply();
                }
            });

        optBrowse.setChecked(prefs.getBoolean("browse", true));
        optBrowse.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    prefs.edit().putBoolean("browse", checked).apply();
                }
            });


        optCompact.setChecked(prefs.getBoolean("compact", false));
        optCompact.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    prefs.edit().putBoolean("compact", checked).apply();
                }
            });

        optInsecure.setChecked(prefs.getBoolean("insecure", false));
        optInsecure.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    prefs.edit().putBoolean("insecure", checked).apply();
                }
            });

        optDebug.setChecked(prefs.getBoolean("debug", false));
        optDebug.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    prefs.edit().putBoolean("debug", checked).apply();
                    ServiceSynchronize.reload(getContext(), "debug=" + checked);
                }
            });

        // Body text size preference (stored in sp units)
        // Map Spinner index 0..6 to sp values 12..24; default 16sp (index 2)
        int storedSp = prefs.getInt("body_text_size_sp", 16);
        int index = Math.max(0, Math.min(6, (storedSp - 12) / 2));
        spnBodyTextSize.setSelection(index);

        spnBodyTextSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                int sp = 12 + position * 2;
                prefs.edit().putInt("body_text_size_sp", sp).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        optLight.setVisibility(
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
                ? View.VISIBLE
                : View.GONE);

        return view;
    }
}
