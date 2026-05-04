/*
 * This file is part of FairEmail.
 *
 * FairEmail is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * FairEmail is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with FairEmail. If not,
 * see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2018, Marcel Bokhorst (M66B)
 * Copyright 2018-2023, Distopico (dystopia project) <distopico@riseup.net> and contributors
 */
package org.dystopia.email

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager

internal class ActivitySetup : ActivityBase(), FragmentManager.OnBackStackChangedListener {
    private var hasAccount = false

    companion object {
        const val REQUEST_PERMISSION = 1
        const val REQUEST_CHOOSE_ACCOUNT = 2
        const val REQUEST_EXPORT = 3
        const val REQUEST_IMPORT = 4
        const val ACTION_EDIT_ACCOUNT = BuildConfig.APPLICATION_ID + ".EDIT_ACCOUNT"
        const val ACTION_EDIT_IDENTITY = BuildConfig.APPLICATION_ID + ".EDIT_IDENTITY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager.addOnBackStackChangedListener(this)

        if (supportFragmentManager.fragments.size == 0) {
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.content_frame, FragmentSetup()).addToBackStack("setup")
            fragmentTransaction.commit()
        }

        DB.getInstance(this)
            .account()
            .liveAccounts(true)
            .observe(
                this
            ) { accounts -> hasAccount = accounts != null && accounts.size > 0 }
    }

    override fun onResume() {
        super.onResume()
        val lbm = LocalBroadcastManager.getInstance(this)
        val iff = IntentFilter()
        iff.addAction(ACTION_EDIT_ACCOUNT)
        iff.addAction(ACTION_EDIT_IDENTITY)
        lbm.registerReceiver(receiver, iff)
    }

    override fun onPause() {
        super.onPause()
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.unregisterReceiver(receiver)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    supportFragmentManager.popBackStack()
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackStackChanged() {
        if (supportFragmentManager.backStackEntryCount == 0) {
            if (hasAccount) {
                startActivity(Intent(this, ActivityView::class.java))
            }
            finish()
        }
    }

    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_EDIT_ACCOUNT -> {
                    val fragment = FragmentAccount().apply { arguments = intent.extras }
                    val fragmentTransaction = supportFragmentManager.beginTransaction()
                    fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("account")
                    fragmentTransaction.commit()
                }
                ACTION_EDIT_IDENTITY -> {
                    val fragment = FragmentIdentity().apply { arguments = intent.extras }
                    val fragmentTransaction = supportFragmentManager.beginTransaction()
                    fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("identity")
                    fragmentTransaction.commit()
                }
            }
        }
    }
}
