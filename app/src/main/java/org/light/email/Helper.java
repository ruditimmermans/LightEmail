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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.sun.mail.imap.IMAPStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadFactory;

import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

public class Helper {
    static final String TAG = "LightEmail";

    static final int JOB_DAILY = 1001;

    static final int AUTH_TYPE_PASSWORD = 1;
    static final int AUTH_TYPE_GMAIL = 2;
    static final int AUTH_TYPE_OUTLOOK = 3;

    static ThreadFactory backgroundThreadFactory =
        new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setPriority(THREAD_PRIORITY_BACKGROUND);
                return thread;
            }
        };

    static void view(Context context, Intent intent) {
        Uri uri = intent.getData();
        if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
            view(context, intent.getData());
        } else {
            context.startActivity(intent);
        }
    }

    static void view(Context context, Uri uri) {
        Log.i(Helper.TAG, "Custom tab=" + uri);

        // https://developer.chrome.com/multidevice/android/customtabs
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(Helper.resolveColor(context, androidx.appcompat.R.attr.colorPrimary));

        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(context, uri);
    }

    static Intent getIntentOpenKeychain() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://f-droid.org/en/packages/org.sufficientlysecure.keychain/"));
        return intent;
    }

    /**
     * Get color by attr from theme
     *
     * @param context android context intance
     * @param attr    R attribute to get styled
     * @return integer color value
     */
    static int resolveColor(Context context, int attr) {
        int[] attrs = new int[] {attr};
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs);
        int color = a.getColor(0, 0xFF0000);
        a.recycle();
        return color;
    }

    /**
     * Get drawable resource from theme
     *
     * @param context android context intance
     * @param attr    R attribute to get drawable
     * @return drawable resource
     */
    static Drawable resolveDrawable(Context context, int attr) {
        int[] attrs = new int[] {attr};
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs);
        Drawable drawable = a.getDrawable(0);
        a.recycle();
        return drawable;
    }

    static void setViewsEnabled(ViewGroup view, boolean enabled) {
        for (int i = 0; i < view.getChildCount(); i++) {
            View child = view.getChildAt(i);
            if (child instanceof Spinner
                || child instanceof EditText
                || child instanceof CheckBox
                || child instanceof ImageView /* =ImageButton */) {
                child.setEnabled(enabled);
            }
            if (child instanceof BottomNavigationView) {
                Menu menu = ((BottomNavigationView) child).getMenu();
                menu.setGroupEnabled(0, enabled);
            } else if (child instanceof ViewGroup) {
                setViewsEnabled((ViewGroup) child, enabled);
            }
        }
    }

    static String localizeFolderName(Context context, String name) {
        if ("INBOX".equals(name)) {
            return context.getString(R.string.title_folder_inbox);
        } else if ("OUTBOX".equals(name)) {
            return context.getString(R.string.title_folder_outbox);
        } else {
            return name;
        }
    }

    static String formatThrowable(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
        Throwable cause = ex.getCause();
        while (cause != null) {
            sb.append(" ")
                .append(cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage());
            cause = cause.getCause();
        }
        return sb.toString();
    }

    static void unexpectedError(Context context, Throwable ex) {
        new AlertDialog.Builder(context)
            .setTitle(R.string.title_unexpected_error)
            .setMessage(ex.toString())
            .setPositiveButton(android.R.string.cancel, null)
            .show();
    }

    static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return new DecimalFormat("@@").format(bytes / Math.pow(unit, exp)) + " " + pre + "B";
    }

    static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    static Address myAddress() throws UnsupportedEncodingException {
        return new InternetAddress("rudi.timmer@posteo.be", "Rudi Timmermans");
    }

    static String canonicalAddress(String address) {
        String[] a = address.split("\\@");
        if (a.length > 0) {
            a[0] = a[0].split("\\+")[0];
        }
        return TextUtils.join("@", a);
    }

    static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    static Notification.Builder getNotificationBuilder(Context context, String channelId) {
        Notification.Builder pbuilder;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return new Notification.Builder(context);
        } else {
            return new Notification.Builder(context, channelId);
        }
    }

    static String getExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int index = filename.lastIndexOf(".");
        if (index < 0) {
            return null;
        }
        return filename.substring(index + 1);
    }

    static void connect(Context context, Store store, EntityAccount account)
        throws MessagingException {
        try {
            store.connect(account.host, account.port, account.user, account.password);
        } catch (AuthenticationFailedException ex) {
            String type = getAccountType(account.auth_type);
            if (type != null) {
                account.password =
                    Helper.refreshToken(context, type, account.user, account.password);
                DB.getInstance(context).account().setAccountPassword(account.id, account.password);
                store.connect(account.host, account.port, account.user, account.password);
            } else {
                throw ex;
            }
        }
    }

    static String refreshToken(Context context, String type, String name, String current) {
        try {
            AccountManager am = AccountManager.get(context);
            List<String> types = new ArrayList<>();
            types.add(type);

            // Handle Gmail OAuth types
            if ("com.google".equals(type)) {
                types.add("com.google.android.gm.exchange");
                types.add("com.android.exchange");
            }

            // Handle Outlook/Microsoft OAuth types
            if ("com.microsoft".equals(type) ||
                "com.microsoft.office.outlook".equals(type) ||
                "com.microsoft.azure.authenticator".equals(type) ||
                "office365".equals(type) ||
                "office365pcke".equals(type) ||
                "outlookgraph".equals(type) ||
                "outlook".equals(type)) {
                if (!types.contains("com.microsoft")) {
                    types.add("com.microsoft");
                }
                if (!types.contains("com.microsoft.office.outlook")) {
                    types.add("com.microsoft.office.outlook");
                }
                if (!types.contains("com.microsoft.azure.authenticator")) {
                    types.add("com.microsoft.azure.authenticator");
                }
                if (!types.contains("office365")) {
                    types.add("office365");
                }
                if (!types.contains("office365pcke")) {
                    types.add("office365pcke");
                }
                if (!types.contains("outlookgraph")) {
                    types.add("outlookgraph");
                }
                if (!types.contains("outlook")) {
                    types.add("outlook");
                }
                if (!types.contains("com.google.android.gm.exchange")) {
                    types.add("com.google.android.gm.exchange");
                }
                if (!types.contains("com.android.exchange")) {
                    types.add("com.android.exchange");
                }
            }

            for (String t : types) {
                Account[] accounts = am.getAccountsByType(t);
                Log.i(Helper.TAG, "Looking for accounts with type=" + t + ", found " + accounts.length + " accounts");
                for (Account account : accounts) {
                    if (name.equalsIgnoreCase(account.name)) {
                        Log.i(Helper.TAG, "Found matching account: " + account.name + " for type=" + t);
                        Log.i(Helper.TAG, "Refreshing token type=" + t);
                        am.invalidateAuthToken(t, current);

                        // Use modern getAuthToken API for Android 14 compatibility
                        Bundle options = new Bundle();
                        String authTokenType = getAuthTokenType(t);

                        // Try non-blocking approach first for better Android 14 compatibility
                        try {
                            AccountManagerFuture<Bundle> future = am.getAuthToken(
                                account,
                                authTokenType,
                                options,
                                false,  // notifyAuthFailure
                                null,   // callback
                                null    // handler
                            );

                            Bundle result = future.getResult();
                            if (result != null) {
                                String refreshed = result.getString(AccountManager.KEY_AUTHTOKEN);
                                Log.i(Helper.TAG, "Token refresh result: " + (refreshed != null ? "success" : "null token"));
                                if (refreshed != null && !refreshed.equals(current)) {
                                    Log.i(Helper.TAG, "Refreshed token successfully, length=" + refreshed.length());
                                    return refreshed;
                                } else if (refreshed != null) {
                                    Log.i(Helper.TAG, "Token refreshed but same as current");
                                }
                            } else {
                                Log.i(Helper.TAG, "Token refresh result is null");
                            }
                        } catch (Exception ex) {
                            Log.w(Helper.TAG, "Modern getAuthToken failed, falling back: " + ex);

                            // Fallback to deprecated method for older Android versions
                            try {
                                String refreshed = am.blockingGetAuthToken(account, authTokenType, true);
                                if (refreshed != null && !refreshed.equals(current)) {
                                    Log.i(Helper.TAG, "Refreshed token successfully (fallback)");
                                    return refreshed;
                                }
                            } catch (Exception fallbackEx) {
                                Log.w(Helper.TAG, "Fallback getAuthToken also failed: " + fallbackEx);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            Log.w(TAG, ex + "\n" + Log.getStackTraceString(ex));
        }
        return current;
    }

    static String getAuthTokenType(String type) {
        if ("com.google".equals(type)) {
            return "oauth2:https://mail.google.com/";
        }
        if ("com.microsoft".equals(type) ||
            "com.microsoft.office.outlook".equals(type) ||
            "com.microsoft.azure.authenticator".equals(type) ||
            "office365".equals(type) ||
            "office365pcke".equals(type) ||
            "outlookgraph".equals(type) ||
            "outlook".equals(type)) {
            return "oauth2:https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send offline_access";
        }
        if ("com.google.android.gm.exchange".equals(type) ||
            "com.android.email".equals(type) ||
            "com.android.exchange".equals(type)) {
            return "mail";
        }
        return null;
    }

    static String getAccountType(int auth_type) {
        if (auth_type == AUTH_TYPE_GMAIL) {
            return "com.google";
        }
        if (auth_type == AUTH_TYPE_OUTLOOK) {
            return "outlook";
        }
        return null;
    }

    static String sha256(String data) throws NoSuchAlgorithmException {
        return sha256(data.getBytes());
    }

    static String sha256(byte[] data) throws NoSuchAlgorithmException {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String getFingerprint(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            byte[] cert = info.signatures[0].toByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            byte[] bytes = digest.digest(cert);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Integer.toString(b & 0xff, 16).toUpperCase(Locale.US));
            }
            return sb.toString();
        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            return null;
        }
    }

    public static boolean hasValidFingerprint(Context context) {
        String signed = getFingerprint(context);
        String expected = context.getString(R.string.fingerprint);
        return (signed != null && signed.equals(expected));
    }

    static long[] toLongArray(List<Long> list) {
        long[] result = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    static List<Long> fromLongArray(long[] array) {
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < array.length; i++) {
            result.add(array[i]);
        }
        return result;
    }

    public static void onAddContact(final Context context, InternetAddress ia) {
        final String name = ia.getPersonal();
        final String email = ia.getAddress();

        if (context instanceof LifecycleOwner) {
            new SimpleTask<Void>() {
                @Override
                protected Void onLoad(Context context, Bundle args) {
                    DB db = DB.getInstance(context);
                    EntityContact contact = db.contact().getContactByEmail(email);
                    if (contact == null) {
                        contact = new EntityContact();
                        contact.name = (TextUtils.isEmpty(name) ? email : name);
                        contact.email = email;
                        db.contact().insertContact(contact);
                    } else if (!TextUtils.isEmpty(name)) {
                        contact.name = name;
                        db.contact().updateContact(contact);
                    }
                    return null;
                }

                @Override
                protected void onLoaded(Bundle args, Void data) {
                    Toast.makeText(context, R.string.title_contact_saved, Toast.LENGTH_LONG).show();
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Toast.makeText(context, ex.getMessage(), Toast.LENGTH_LONG).show();
                }
            }.load((LifecycleOwner) context, new Bundle());
        }
    }

    public static void onAddAddresses(final Context context, Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return;
        }

        final List<InternetAddress> ias = new ArrayList<>();
        for (Address address : addresses) {
            if (address instanceof InternetAddress) {
                ias.add((InternetAddress) address);
            }
        }

        if (ias.isEmpty()) {
            return;
        }

        if (ias.size() == 1) {
            onAddContact(context, ias.get(0));
        } else {
            String[] names = new String[ias.size()];
            for (int i = 0; i < ias.size(); i++) {
                names[i] = ias.get(i).toString();
            }

            new AlertDialog.Builder(context)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onAddContact(context, ias.get(which));
                    }
                })
                .show();
        }
    }
}
