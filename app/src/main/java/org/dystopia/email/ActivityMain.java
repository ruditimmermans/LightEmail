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
*/

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;

import java.util.List;

public class ActivityMain extends AppCompatActivity
    implements FragmentManager.OnBackStackChangedListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        super.onCreate(savedInstanceState);
        DB.getInstance(this)
            .account()
            .liveAccounts(true)
            .observe(
                this,
                new Observer<List<EntityAccount>>() {
                    @Override
                    public void onChanged(@Nullable List<EntityAccount> accounts) {
                        if (accounts == null || accounts.size() == 0) {
                            startActivity(new Intent(ActivityMain.this, ActivitySetup.class));
                        } else {
                            startActivity(new Intent(ActivityMain.this, ActivityView.class));
                            ServiceSynchronize.init(ActivityMain.this);
                        }
                        finish();
                    }
                });
    }

    @Override
    public void onBackStackChanged() {
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count == 0) {
            finish();
        }
    }
}
