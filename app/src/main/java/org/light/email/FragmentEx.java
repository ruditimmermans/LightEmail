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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.light.email.util.CompatibilityHelper;

public class FragmentEx extends Fragment {
    private String title = "";
    private String subtitle = " ";
    protected boolean finish = false;

    protected String authorized = null;
    protected String authorized_refresh = null;
    protected Long authorized_expiry = null;
    protected String authorized_code_verifier = null;

    private BroadcastReceiver oauthReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String code = intent.getStringExtra("code");
            String error = intent.getStringExtra("error");
            if (code != null) {
                exchangeCode(code);
            } else if (error != null) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(Helper.TAG, "Create " + this + " saved=" + (savedInstanceState != null));
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            subtitle = savedInstanceState.getString("subtitle");
        }
        LocalBroadcastManager.getInstance(getContext())
            .registerReceiver(oauthReceiver, new IntentFilter(ActivitySetup.ACTION_OAUTH_RESULT));
    }

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(Helper.TAG, "Create view " + this);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(Helper.TAG, "Activity " + this + " saved=" + (savedInstanceState != null));
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        Log.i(Helper.TAG, "Resume " + this);
        super.onResume();
        updateSubtitle();
        if (finish) {
            getFragmentManager().popBackStack();
            finish = false;
        }
    }

    @Override
    public void onPause() {
        Log.i(Helper.TAG, "Pause " + this);
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.i(Helper.TAG, "Save instance " + this);
        super.onSaveInstanceState(outState);
        outState.putString("subtitle", subtitle);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        InputMethodManager inputMethodManager = CompatibilityHelper.getInputMethodManager(getContext());
        View focused = getActivity().getCurrentFocus();
        if (focused != null) {
            inputMethodManager.hideSoftInputFromWindow(focused.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(Helper.TAG, "Config " + this);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDestroy() {
        Log.i(Helper.TAG, "Destroy " + this);
        super.onDestroy();
        if (getContext() != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(oauthReceiver);
        }
    }

    protected void exchangeCode(final String code) {
        if (authorized_code_verifier == null) {
            Log.w(Helper.TAG, "No OAuth verifier available");
            return;
        }
        final Bundle args = new Bundle();
        args.putString("code", code);
        args.putString("verifier", authorized_code_verifier);
        new SimpleTask<OutlookOAuthHelper.TokenResponse>() {
            @Override
            protected OutlookOAuthHelper.TokenResponse onLoad(Context context, Bundle args) throws Throwable {
                return OutlookOAuthHelper.exchangeCode(args.getString("code"), args.getString("verifier"));
            }

            @Override
            protected void onLoaded(Bundle args, OutlookOAuthHelper.TokenResponse response) {
                authorized = response.accessToken;
                authorized_refresh = response.refreshToken;
                authorized_expiry = response.expiry;
                onAuthorized(response);
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Helper.unexpectedError(getContext(), ex);
            }
        }.load(this, args);
    }

    protected void onAuthorized(OutlookOAuthHelper.TokenResponse response) {
    }

    protected void setTitle(int resid) {
        setTitle(getString(resid));
    }

    protected void setTitle(String title) {
        this.title = title;
        updateTitle();
    }

    protected void setSubtitle(int resid) {
        setSubtitle(getString(resid));
    }

    protected void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        updateSubtitle();
    }

    protected void finish() {
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            getFragmentManager().popBackStack();
        } else {
            finish = true;
        }
    }

    private void updateTitle() {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            ActionBar actionbar = activity.getSupportActionBar();
            if (actionbar != null) {
                actionbar.setTitle(title);
            }
        }
    }

    private void updateSubtitle() {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            ActionBar actionbar = activity.getSupportActionBar();
            if (actionbar != null) {
                actionbar.setSubtitle(subtitle);
            }
        }
    }
}
