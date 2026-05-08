package org.light.email;

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
import androidx.appcompat.widget.SwitchCompat;
import android.widget.Spinner;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FragmentOptions extends FragmentEx {
    private SwitchCompat optSyncEnabled;
    private SwitchCompat optAvatars;
    private SwitchCompat optLight;
    private SwitchCompat optBrowse;
    private SwitchCompat optCompact;
    private SwitchCompat optReplyQuote;
    private SwitchCompat optSignatureAtBottom;
    private SwitchCompat optInsecure;
    private SwitchCompat optDebug;
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
        optReplyQuote = view.findViewById(R.id.optReplyQuote);
        optSignatureAtBottom = view.findViewById(R.id.optSignatureAtBottom);
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

        optBrowse.setChecked(prefs.getBoolean("browse", false));
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

        optReplyQuote.setChecked(prefs.getBoolean("reply_quote", true));
        optReplyQuote.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    prefs.edit().putBoolean("reply_quote", checked).apply();
                }
            });

        optSignatureAtBottom.setChecked(prefs.getBoolean("signature_at_bottom", true));
        optSignatureAtBottom.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    prefs.edit().putBoolean("signature_at_bottom", checked).apply();
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
        final String[] sizes = getResources().getStringArray(R.array.body_text_size_entries);
        int storedSp = prefs.getInt("body_text_size_sp", 24);
        int index = 0;
        for (int i = 0; i < sizes.length; i++) {
            if (Integer.parseInt(sizes[i]) == storedSp) {
                index = i;
                break;
            }
        }
        spnBodyTextSize.setSelection(index);

        spnBodyTextSize.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view1, int position, long id) {
                        int sp = Integer.parseInt(sizes[position]);
                        prefs.edit().putInt("body_text_size_sp", sp).apply();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

        optLight.setVisibility(
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
                ? View.VISIBLE
                : View.GONE);

        return view;
    }
}
