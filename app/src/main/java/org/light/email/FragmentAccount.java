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

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Observer;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.pop3.POP3Folder;
import com.sun.mail.pop3.POP3Store;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.Type;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.mail.AuthenticationFailedException;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;

import static android.accounts.AccountManager.newChooseAccountIntent;
import static android.app.Activity.RESULT_OK;

public class FragmentAccount extends FragmentEx {
    private ViewGroup view;

    private Spinner spProvider;

    private EditText etDomain;
    private Button btnAutoConfig;

    private EditText etHost;
    private CheckBox cbStartTls;
    private CheckBox cbInsecure;
    private EditText etPort;
    private EditText etUser;
    private TextInputLayout tilPassword;

    private Button btnAuthorize;
    private TextView tvGmailNote;
    private LinearLayout llAuthorize;
    private Button btnAdvanced;

    private TextView tvName;
    private EditText etName;

    private ViewButtonColor btnColor;
    private LinearLayout llContainerColor;
    private EditText etSignature;

    private CheckBox cbSynchronize;
    private CheckBox cbPrimary;
    private EditText etInterval;

    private Button btnCheck;
    private ProgressBar pbCheck;

    private TextView tvIdle;

    private ArrayAdapter<EntityFolder> adapter;
    private Spinner spDrafts;
    private Spinner spSent;
    private Spinner spAll;
    private Spinner spTrash;
    private Spinner spJunk;

    private Button btnSave;
    private ProgressBar pbSave;
    private ImageButton ibDelete;
    private ProgressBar pbWait;

    private Group grpServer;
    private Group grpAuthorize;
    private Group grpAdvanced;
    private Group grpFolders;

    private RadioButton rbImap;
    private RadioButton rbPop3;

    private long id = -1;
    private int color = Color.TRANSPARENT;
    private String authorized = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get arguments
        Bundle args = getArguments();
        id = (args == null ? -1 : args.getLong("id", -1));

        getParentFragmentManager().setFragmentResultListener(ColorDialogFragment.DIALOG_COLOR, this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
                setAccountColor(result);
            }
        });
    }

    @Override
    @Nullable
    public View onCreateView(
        @NonNull LayoutInflater inflater,
        @Nullable ViewGroup container,
        @Nullable Bundle savedInstanceState) {
        setSubtitle(R.string.title_edit_account);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        final boolean insecure = prefs.getBoolean("insecure", false);

        view = (ViewGroup) inflater.inflate(R.layout.fragment_account, container, false);

        // Get controls
        spProvider = view.findViewById(R.id.spProvider);

        // Protocol selection
        RadioGroup rgProtocol = view.findViewById(R.id.rgProtocol);
        rbImap = view.findViewById(R.id.rbImap);
        rbPop3 = view.findViewById(R.id.rbPop3);

        etDomain = view.findViewById(R.id.etDomain);
        btnAutoConfig = view.findViewById(R.id.btnAutoConfig);

        etHost = view.findViewById(R.id.etHost);
        llContainerColor = view.findViewById(R.id.llContainerColor);
        etPort = view.findViewById(R.id.etPort);
        cbStartTls = view.findViewById(R.id.cbStartTls);
        cbInsecure = view.findViewById(R.id.cbInsecure);
        etUser = view.findViewById(R.id.etUser);
        tilPassword = view.findViewById(R.id.tilPassword);

        btnAuthorize = view.findViewById(R.id.btnAuthorize);
        tvGmailNote = view.findViewById(R.id.tvGmailNote);
        llAuthorize = view.findViewById(R.id.llAuthorize);
        btnAdvanced = view.findViewById(R.id.btnAdvanced);

        etName = view.findViewById(R.id.etName);
        btnColor = view.findViewById(R.id.btnColor);
        tvName = view.findViewById(R.id.tvName);
        etSignature = view.findViewById(R.id.etSignature);

        cbSynchronize = view.findViewById(R.id.cbSynchronize);
        cbPrimary = view.findViewById(R.id.cbPrimary);
        etInterval = view.findViewById(R.id.etInterval);

        btnCheck = view.findViewById(R.id.btnCheck);
        pbCheck = view.findViewById(R.id.pbCheck);

        tvIdle = view.findViewById(R.id.tvIdle);

        spDrafts = view.findViewById(R.id.spDrafts);
        spSent = view.findViewById(R.id.spSent);
        spAll = view.findViewById(R.id.spAll);
        spTrash = view.findViewById(R.id.spTrash);
        spJunk = view.findViewById(R.id.spJunk);

        btnSave = view.findViewById(R.id.btnSave);
        pbSave = view.findViewById(R.id.pbSave);

        ibDelete = view.findViewById(R.id.ibDelete);

        pbWait = view.findViewById(R.id.pbWait);

        grpServer = view.findViewById(R.id.grpServer);
        grpAuthorize = view.findViewById(R.id.grpAuthorize);
        grpAdvanced = view.findViewById(R.id.grpAdvanced);
        grpFolders = view.findViewById(R.id.grpFolders);

        // Wire controls

        // Protocol selection listener
        rgProtocol.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Provider provider = (Provider) spProvider.getSelectedItem();
                if (provider != null) {
                    updateProviderSettings(provider, checkedId == R.id.rbPop3);
                }
            }
        });

        spProvider.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                    Provider provider = (Provider) adapterView.getSelectedItem();
                    grpServer.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
                    cbStartTls.setVisibility(position == 1 && insecure ? View.VISIBLE : View.GONE);
                    cbInsecure.setVisibility(position == 1 && insecure ? View.VISIBLE : View.GONE);
                    grpAuthorize.setVisibility(position > 0 ? View.VISIBLE : View.GONE);

                    // Show protocol selection only for Gmail and Outlook
                    boolean is_google = "Gmail".equalsIgnoreCase(provider.name);
                    boolean is_outlook = "Outlook".equalsIgnoreCase(provider.name);
                    boolean supported = isSupported(provider);

                    // Show/hide protocol selection
                    FragmentAccount.this.view.findViewById(R.id.tvProtocol).setVisibility((is_google || is_outlook) ? View.VISIBLE : View.GONE);
                    rgProtocol.setVisibility((is_google || is_outlook) ? View.VISIBLE : View.GONE);

                    btnAuthorize.setVisibility(supported ? View.VISIBLE : View.GONE);
                    tvGmailNote.setVisibility((is_google || is_outlook) ? View.VISIBLE : View.GONE);
                    llAuthorize.setVisibility(supported || is_google || is_outlook ? View.VISIBLE : View.GONE);
                    if (is_google) {
                        tvGmailNote.setText(Html.fromHtml(getString(R.string.text_gmail_note)));
                    } else if (is_outlook) {
                        tvGmailNote.setText(Html.fromHtml(provider.documentation));
                    }

                    btnAdvanced.setVisibility(position > 0 ? View.VISIBLE : View.GONE);
                    if (position == 0) {
                        grpAdvanced.setVisibility(View.GONE);
                    }

                    btnCheck.setVisibility(position > 0 ? View.VISIBLE : View.GONE);
                    tvIdle.setVisibility(View.GONE);
                    grpFolders.setVisibility(View.GONE);
                    btnSave.setVisibility(View.GONE);

                    Object tag = adapterView.getTag();
                    if (tag != null && (Integer) tag == position) {
                        return;
                    }
                    adapterView.setTag(position);

                    // Update provider settings based on selected protocol
                    updateProviderSettings(provider, rgProtocol.getCheckedRadioButtonId() == R.id.rbPop3);

                    etUser.setText(null);
                    tilPassword.getEditText().setText(null);

                    etName.setText(position > 1 ? provider.name : null);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });

        btnAutoConfig.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    etDomain.setEnabled(false);
                    btnAutoConfig.setEnabled(false);

                    Bundle args = new Bundle();
                    args.putString("domain", etDomain.getText().toString());

                    new SimpleTask<SRVRecord>() {
                        @Override
                        protected SRVRecord onLoad(Context context, Bundle args) throws Throwable {
                            String domain = args.getString("domain");
                            Record[] records = new Lookup("_imaps._tcp." + domain, Type.SRV).run();
                            if (records != null) {
                                for (int i = 0; i < records.length; i++) {
                                    SRVRecord srv = (SRVRecord) records[i];
                                    Log.i(Helper.TAG, "SRV=" + srv);
                                    return srv;
                                }
                            }

                            throw new IllegalArgumentException(getString(R.string.title_no_settings));
                        }

                        @Override
                        protected void onLoaded(Bundle args, SRVRecord srv) {
                            etDomain.setEnabled(true);
                            btnAutoConfig.setEnabled(true);
                            if (srv != null) {
                                etHost.setText(srv.getTarget().toString(true));
                                etPort.setText(Integer.toString(srv.getPort()));
                            }
                        }

                        @Override
                        protected void onException(Bundle args, Throwable ex) {
                            etDomain.setEnabled(true);
                            btnAutoConfig.setEnabled(true);
                            if (ex instanceof IllegalArgumentException) {
                                Snackbar.make(view, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                            } else {
                                Helper.unexpectedError(getContext(), ex);
                            }
                        }
                    }.load(FragmentAccount.this, args);
                }
            });

        cbStartTls.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    updatePortHint();
                }
            });

        tilPassword
            .getEditText()
            .addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (authorized != null && !authorized.equals(s.toString())) {
                            authorized = null;
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                });

        btnAuthorize.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String permission = Manifest.permission.GET_ACCOUNTS;
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                        && ContextCompat.checkSelfPermission(getContext(), permission)
                        != PackageManager.PERMISSION_GRANTED) {
                        Log.i(Helper.TAG, "Requesting " + permission);
                        requestPermissions(new String[] {permission}, ActivitySetup.REQUEST_PERMISSION);
                    } else {
                        selectAccount();
                    }
                }
            });

        tvGmailNote.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Provider provider = (Provider) spProvider.getSelectedItem();
                    Spanned htmlMessage;
                    if ("Gmail".equalsIgnoreCase(provider.name)) {
                        htmlMessage = Html.fromHtml(getString(R.string.message_gmail_note));
                    } else if ("Outlook".equalsIgnoreCase(provider.name)) {
                        htmlMessage = Html.fromHtml(provider.documentation);
                    } else {
                        return;
                    }

                    final SpannableString dialogMessage = new SpannableString(htmlMessage);
                    Linkify.addLinks(dialogMessage, Linkify.WEB_URLS);

                    AlertDialog dialog = new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                        .setMessage(dialogMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();

                    ((TextView) dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                }
            }
        );

        btnAdvanced.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int visibility =
                        (grpAdvanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    grpAdvanced.setVisibility(visibility);
                    if (visibility == View.VISIBLE) {
                        new Handler()
                            .post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        ((ScrollView) view).smoothScrollTo(0, tvName.getTop());
                                    }
                                });
                    }
                }
            });

        btnColor.setColor(color);
        llContainerColor.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle args = new Bundle();
                    args.putInt("color", btnColor.getColor());
                    args.putString("title", getString(R.string.title_color));
                    args.putBoolean("reset", true);

                    ColorDialogFragment fragment = new ColorDialogFragment();
                    fragment.setArguments(args);
                    fragment.show(getParentFragmentManager(), "account:color");
                }
            });

        cbSynchronize.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    cbPrimary.setEnabled(checked);
                }
            });

        btnCheck.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Helper.setViewsEnabled(view, false);
                    btnAuthorize.setEnabled(false);
                    btnCheck.setEnabled(false);
                    pbCheck.setVisibility(View.VISIBLE);
                    tvIdle.setVisibility(View.GONE);
                    grpFolders.setVisibility(View.GONE);
                    btnSave.setVisibility(View.GONE);

                    Provider provider = (Provider) spProvider.getSelectedItem();

                    Bundle args = new Bundle();
                    args.putLong("id", id);
                    args.putString("host", etHost.getText().toString());
                    args.putBoolean("starttls", cbStartTls.isChecked());
                    args.putBoolean("insecure", cbInsecure.isChecked());
                    args.putString("port", etPort.getText().toString());
                    args.putString("user", etUser.getText().toString());
                    args.putString("protocol", rbPop3.isChecked() ? "pop3" : "imap");
                    String password = tilPassword.getEditText().getText().toString();
                    args.putString("password", password);
                    int auth_type = provider.getAuthType();
                    if (authorized == null &&
                        (auth_type == Helper.AUTH_TYPE_GMAIL || auth_type == Helper.AUTH_TYPE_OUTLOOK)) {
                        auth_type = Helper.AUTH_TYPE_PASSWORD;
                    }
                    args.putInt("auth_type", auth_type);
                    args.putString("maxtls", provider.maxtls);

                    new SimpleTask<CheckResult>() {
                        @Override
                        protected CheckResult onLoad(Context context, Bundle args) throws Throwable {
                            long id = args.getLong("id");
                            String host = args.getString("host");
                            boolean starttls = args.getBoolean("starttls");
                            boolean insecure = args.getBoolean("insecure");
                            String port = args.getString("port");
                            String protocol = args.getString("protocol");
                            String user = args.getString("user");
                            String password = args.getString("password");
                            int auth_type = args.getInt("auth_type");
                            String maxtls = args.getString("maxtls");

                            if (TextUtils.isEmpty(host)) {
                                throw new Throwable(getContext().getString(R.string.title_no_host));
                            }
                            if (TextUtils.isEmpty(port)) {
                                if ("pop3".equals(protocol)) {
                                    port = (starttls ? "110" : "995");
                                } else {
                                    port = (starttls ? "143" : "993");
                                }
                            }
                            if (TextUtils.isEmpty(user)) {
                                throw new Throwable(getContext().getString(R.string.title_no_user));
                            }
                            if (TextUtils.isEmpty(password) && !insecure) {
                                throw new Throwable(getContext().getString(R.string.title_no_password));
                            }

                            CheckResult result = new CheckResult();
                            result.folders = new ArrayList<>();

                            // Check IMAP/POP3 server / get folders
                            Properties props = MessageHelper.getSessionProperties(auth_type, insecure, maxtls);
                            Session isession = Session.getInstance(props, null);
                            isession.setDebug(true);
                            IMAPStore istore = null;

                            // Determine protocol
                            boolean isPop3 = "pop3".equals(protocol);

                            try {
                                if (isPop3) {
                                    // Handle POP3 connection and create basic folder structure
                                    POP3Store pop3Store = (POP3Store) isession.getStore(starttls ? "pop3" : "pop3s");
                                    String originalPassword = password;
                                    try {
                                        String type = Helper.getAccountType(auth_type);
                                        if (type != null) {
                                            password = Helper.refreshToken(context, type, user, password);
                                        }
                                        pop3Store.connect(host, Integer.parseInt(port), user, password);

                                        // POP3 doesn't have folders like IMAP, but we need to create a basic structure
                                        // for compatibility with the existing UI
                                        POP3Folder pop3Inbox = (POP3Folder) pop3Store.getFolder("INBOX");
                                        if (pop3Inbox.exists()) {
                                            // Create a basic folder structure for POP3 compatibility
                                            EntityFolder inbox = new EntityFolder();
                                            inbox.name = "INBOX";
                                            inbox.type = EntityFolder.INBOX;
                                            inbox.display = "INBOX";
                                            inbox.synchronize = true;
                                            result.folders.add(inbox);
                                        }

                                        // POP3 doesn't support IDLE or UIDPLUS
                                        result.idle = false;

                                    } catch (AuthenticationFailedException ex) {
                                        // Try normal pop3 access with gmail/outlook allowed "insecure" app enabled
                                        String type = Helper.getAccountType(auth_type);
                                        if (type != null) {
                                            auth_type = Helper.AUTH_TYPE_PASSWORD;
                                            args.putInt("auth_type", auth_type);
                                            props = MessageHelper.getSessionProperties(auth_type, insecure, maxtls);
                                            isession = Session.getInstance(props, null);
                                            pop3Store = (POP3Store) isession.getStore(starttls ? "pop3" : "pop3s");
                                            pop3Store.connect(host, Integer.parseInt(port), user, originalPassword);

                                            // Create basic folder structure for fallback
                                            EntityFolder inbox = new EntityFolder();
                                            inbox.name = "INBOX";
                                            inbox.type = EntityFolder.INBOX;
                                            inbox.display = "INBOX";
                                            inbox.synchronize = true;
                                            result.folders.add(inbox);
                                            result.idle = false;
                                        } else {
                                            throw ex;
                                        }
                                    }
                                } else {
                                    // IMAP handling
                                    istore = (IMAPStore) isession.getStore(starttls ? "imap" : "imaps");
                                    String originalPassword = password;
                                    try {
                                        String type = Helper.getAccountType(auth_type);
                                        if (type != null) {
                                            password = Helper.refreshToken(context, type, user, password);
                                        }
                                        istore.connect(host, Integer.parseInt(port), user, password);
                                    } catch (AuthenticationFailedException ex) {
                                        // Try normal imap access with gmail/outlook allowed "insecure" app enabled
                                        String type = Helper.getAccountType(auth_type);
                                        if (type != null) {
                                            auth_type = Helper.AUTH_TYPE_PASSWORD;
                                            args.putInt("auth_type", auth_type);
                                            props = MessageHelper.getSessionProperties(auth_type, insecure, maxtls);
                                            isession = Session.getInstance(props, null);
                                            istore = (IMAPStore) isession.getStore(starttls ? "imap" : "imaps");
                                            istore.connect(host, Integer.parseInt(port), user, originalPassword);
                                        } else {
                                            throw ex;
                                        }
                                    }

                                    if (!istore.hasCapability("UIDPLUS")) {
                                        throw new MessagingException(getContext().getString(R.string.title_no_uidplus));
                                    }

                                    result.idle = istore.hasCapability("IDLE");

                                    // IMAP folder listing
                                    for (Folder ifolder : istore.getDefaultFolder().list("*")) {
                                        String type = null;

                                        // First check folder attributes
                                        boolean selectable = true;
                                        String[] attrs = ((IMAPFolder) ifolder).getAttributes();
                                        for (String attr : attrs) {
                                            if ("\\Noselect".equals(attr)) {
                                                selectable = false;
                                            }
                                            if (attr.startsWith("\\")) {
                                                int index = EntityFolder.SYSTEM_FOLDER_ATTR.indexOf(attr.substring(1));
                                                if (index >= 0) {
                                                    type = EntityFolder.SYSTEM_FOLDER_TYPE.get(index);
                                                    break;
                                                }
                                            }
                                        }

                                        if (selectable) {
                                            // Next check folder full name
                                            if (type == null) {
                                                String fullname = ifolder.getFullName();
                                                for (String attr : EntityFolder.SYSTEM_FOLDER_ATTR) {
                                                    if (attr.equals(fullname)) {
                                                        int index = EntityFolder.SYSTEM_FOLDER_ATTR.indexOf(attr);
                                                        type = EntityFolder.SYSTEM_FOLDER_TYPE.get(index);
                                                        break;
                                                    }
                                                }
                                            }

                                            // Create entry
                                            DB db = DB.getInstance(context);
                                            EntityFolder folder = db.folder().getFolderByName(id, ifolder.getFullName());
                                            if (folder == null) {
                                                folder = new EntityFolder();
                                                folder.name = ifolder.getFullName();
                                                folder.type = (type == null ? EntityFolder.USER : type);
                                                folder.synchronize =
                                                    (type != null && EntityFolder.SYSTEM_FOLDER_SYNC.contains(type));
                                                folder.after =
                                                    (type == null
                                                        ? EntityFolder.DEFAULT_USER_SYNC
                                                        : EntityFolder.DEFAULT_SYSTEM_SYNC);
                                            }
                                            result.folders.add(folder);

                                            Log.i(
                                                Helper.TAG,
                                                folder.name
                                                    + " id="
                                                    + folder.id
                                                    + " type="
                                                    + folder.type
                                                    + " attr="
                                                    + TextUtils.join(",", attrs));
                                        }
                                    }
                                }
                            } finally {
                                if (istore != null) {
                                    istore.close();
                                }
                            }

                            return result;
                        }

                        @Override
                        protected void onLoaded(Bundle args, CheckResult result) {
                            Helper.setViewsEnabled(view, true);
                            btnAuthorize.setEnabled(true);
                            btnCheck.setEnabled(true);
                            pbCheck.setVisibility(View.GONE);

                            tvIdle.setVisibility(result.idle ? View.GONE : View.VISIBLE);

                            setFolders(result.folders);

                            new Handler()
                                .post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            ((ScrollView) view).smoothScrollTo(0, btnSave.getBottom());
                                        }
                                    });
                        }

                        @Override
                        protected void onException(Bundle args, Throwable ex) {
                            Helper.setViewsEnabled(view, true);
                            btnAuthorize.setEnabled(true);
                            btnCheck.setEnabled(true);
                            pbCheck.setVisibility(View.GONE);
                            grpFolders.setVisibility(View.GONE);
                            btnSave.setVisibility(View.GONE);

                            new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                                .setMessage(Helper.formatThrowable(ex))
                                .setPositiveButton(android.R.string.cancel, null)
                                .create()
                                .show();
                        }
                    }.load(FragmentAccount.this, args);
                }
            });

        btnSave.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Helper.setViewsEnabled(view, false);
                    btnAuthorize.setEnabled(false);
                    btnCheck.setEnabled(false);
                    btnSave.setEnabled(false);
                    pbSave.setVisibility(View.VISIBLE);

                    Provider provider = (Provider) spProvider.getSelectedItem();

                    EntityFolder drafts = (EntityFolder) spDrafts.getSelectedItem();
                    EntityFolder sent = (EntityFolder) spSent.getSelectedItem();
                    EntityFolder all = (EntityFolder) spAll.getSelectedItem();
                    EntityFolder trash = (EntityFolder) spTrash.getSelectedItem();
                    EntityFolder junk = (EntityFolder) spJunk.getSelectedItem();

                    if (drafts != null && drafts.type == null) {
                        drafts = null;
                    }
                    if (sent != null && sent.type == null) {
                        sent = null;
                    }
                    if (all != null && all.type == null) {
                        all = null;
                    }
                    if (trash != null && trash.type == null) {
                        trash = null;
                    }
                    if (junk != null && junk.type == null) {
                        junk = null;
                    }

                    Bundle args = new Bundle();
                    args.putLong("id", id);
                    args.putString("host", etHost.getText().toString());
                    args.putBoolean("starttls", cbStartTls.isChecked());
                    args.putBoolean("insecure", cbInsecure.isChecked());
                    args.putString("port", etPort.getText().toString());
                    args.putString("user", etUser.getText().toString());
                    args.putString("protocol", rbPop3.isChecked() ? "pop3" : "imap");
                    args.putString("password", tilPassword.getEditText().getText().toString());
                    args.putInt(
                        "auth_type",
                        authorized == null ? Helper.AUTH_TYPE_PASSWORD : provider.getAuthType());
                    args.putString("maxtls", provider.maxtls);

                    args.putString("name", etName.getText().toString());
                    args.putInt("color", color);
                    args.putString("signature", Html.toHtml(etSignature.getText()));

                    args.putBoolean("synchronize", cbSynchronize.isChecked());
                    args.putBoolean("primary", cbPrimary.isChecked());
                    args.putString("interval", etInterval.getText().toString());

                    args.putSerializable("drafts", drafts);
                    args.putSerializable("sent", sent);
                    args.putSerializable("all", all);
                    args.putSerializable("trash", trash);
                    args.putSerializable("junk", junk);

                    new SimpleTask<Void>() {
                        @Override
                        protected Void onLoad(Context context, Bundle args) throws Throwable {
                            String host = args.getString("host");
                            boolean starttls = args.getBoolean("starttls");
                            boolean insecure = args.getBoolean("insecure");
                            String port = args.getString("port");
                            String protocol = args.getString("protocol");
                            String user = args.getString("user");
                            String password = args.getString("password");
                            int auth_type = args.getInt("auth_type");
                            String maxtls = args.getString("maxtls");

                            String name = args.getString("name");
                            Integer color = args.getInt("color");
                            String signature = args.getString("signature");

                            boolean synchronize = args.getBoolean("synchronize");
                            boolean primary = args.getBoolean("primary");
                            String interval = args.getString("interval");

                            EntityFolder drafts = (EntityFolder) args.getSerializable("drafts");
                            EntityFolder sent = (EntityFolder) args.getSerializable("sent");
                            EntityFolder all = (EntityFolder) args.getSerializable("all");
                            EntityFolder trash = (EntityFolder) args.getSerializable("trash");
                            EntityFolder junk = (EntityFolder) args.getSerializable("junk");

                            if (TextUtils.isEmpty(host)) {
                                throw new Throwable(getContext().getString(R.string.title_no_host));
                            }
                            if (TextUtils.isEmpty(port)) {
                                if ("pop3".equals(protocol)) {
                                    port = (starttls ? "110" : "995");
                                } else {
                                    port = (starttls ? "143" : "993");
                                }
                            }
                            if (TextUtils.isEmpty(user)) {
                                throw new Throwable(getContext().getString(R.string.title_no_user));
                            }
                            if (TextUtils.isEmpty(password) && !insecure) {
                                throw new Throwable(getContext().getString(R.string.title_no_password));
                            }
                            if (TextUtils.isEmpty(interval)) {
                                interval = "19";
                            }
                            if (synchronize && drafts == null) {
                                throw new Throwable(getContext().getString(R.string.title_no_drafts));
                            }
                            if (Color.TRANSPARENT == color) {
                                color = null;
                            }

                            // Check IMAP/POP3 server
                            if (synchronize) {
                                Session isession =
                                    Session.getInstance(
                                        MessageHelper.getSessionProperties(auth_type, insecure, maxtls), null);
                                isession.setDebug(true);

                                boolean isPop3 = "pop3".equals(protocol);

                                if (isPop3) {
                                    POP3Store pop3Store = null;
                                    try {
                                        pop3Store = (POP3Store) isession.getStore(starttls ? "pop3" : "pop3s");
                                        String originalPassword = password;
                                        try {
                                            String type = Helper.getAccountType(auth_type);
                                            if (type != null) {
                                                password = Helper.refreshToken(context, type, user, password);
                                            }
                                            pop3Store.connect(host, Integer.parseInt(port), user, password);
                                        } catch (AuthenticationFailedException ex) {
                                            String type = Helper.getAccountType(auth_type);
                                            if (type != null) {
                                                auth_type = Helper.AUTH_TYPE_PASSWORD;
                                                isession = Session.getInstance(MessageHelper.getSessionProperties(auth_type, insecure, maxtls), null);
                                                pop3Store = (POP3Store) isession.getStore(starttls ? "pop3" : "pop3s");
                                                pop3Store.connect(host, Integer.parseInt(port), user, originalPassword);
                                                password = originalPassword;
                                            } else {
                                                throw ex;
                                            }
                                        }
                                    } finally {
                                        if (pop3Store != null) {
                                            pop3Store.close();
                                        }
                                    }
                                } else {
                                    IMAPStore istore = null;
                                    try {
                                        istore = (IMAPStore) isession.getStore(starttls ? "imap" : "imaps");
                                        String originalPassword = password;
                                        try {
                                            String type = Helper.getAccountType(auth_type);
                                            if (type != null) {
                                                password = Helper.refreshToken(context, type, user, password);
                                            }
                                            istore.connect(host, Integer.parseInt(port), user, password);
                                        } catch (AuthenticationFailedException ex) {
                                            String type = Helper.getAccountType(auth_type);
                                            if (type != null) {
                                                auth_type = Helper.AUTH_TYPE_PASSWORD;
                                                isession = Session.getInstance(MessageHelper.getSessionProperties(auth_type, insecure, maxtls), null);
                                                istore = (IMAPStore) isession.getStore(starttls ? "imap" : "imaps");
                                                istore.connect(host, Integer.parseInt(port), user, originalPassword);
                                                password = originalPassword;
                                            } else {
                                                throw ex;
                                            }
                                        }

                                        if (!istore.hasCapability("UIDPLUS")) {
                                            throw new MessagingException(
                                                getContext().getString(R.string.title_no_uidplus));
                                        }
                                    } finally {
                                        if (istore != null) {
                                            istore.close();
                                        }
                                    }
                                }
                            }

                            if (TextUtils.isEmpty(name)) {
                                name = user;
                            }

                            DB db = DB.getInstance(getContext());
                            try {
                                db.beginTransaction();

                                EntityAccount account = db.account().getAccount(args.getLong("id"));
                                boolean update = (account != null);
                                if (account == null) {
                                    account = new EntityAccount();
                                }

                                account.host = host;
                                account.protocol = protocol;
                                account.starttls = starttls;
                                account.insecure = insecure;
                                account.port = Integer.parseInt(port);
                                account.user = user;
                                account.password = password;
                                account.auth_type = auth_type;
                                account.maxtls = maxtls;

                                account.name = name;
                                account.color = color;
                                account.signature = signature;

                                account.synchronize = synchronize;
                                account.primary = (account.synchronize && primary);
                                account.poll_interval = Integer.parseInt(interval);

                                account.store_sent = false; // obsolete
                                account.seen_until = null; // obsolete

                                if (!synchronize) {
                                    account.error = null;
                                }

                                if (account.primary) {
                                    db.account().resetPrimary();
                                }

                                if (update) {
                                    db.account().updateAccount(account);
                                } else {
                                    account.id = db.account().insertAccount(account);
                                }

                                List<EntityFolder> folders = new ArrayList<>();

                                EntityFolder inbox = new EntityFolder();
                                inbox.name = "INBOX";
                                inbox.type = EntityFolder.INBOX;
                                inbox.synchronize = true;
                                inbox.unified = true;
                                inbox.after = EntityFolder.DEFAULT_INBOX_SYNC;

                                folders.add(inbox);

                                if (drafts != null) {
                                    drafts.type = EntityFolder.DRAFTS;
                                    folders.add(drafts);
                                }

                                if (sent != null) {
                                    sent.type = EntityFolder.SENT;
                                    folders.add(sent);
                                }
                                if (all != null) {
                                    all.type = EntityFolder.ARCHIVE;
                                    folders.add(all);
                                }
                                if (trash != null) {
                                    trash.type = EntityFolder.TRASH;
                                    folders.add(trash);
                                }
                                if (junk != null) {
                                    junk.type = EntityFolder.JUNK;
                                    folders.add(junk);
                                }

                                db.folder().setFoldersUser(account.id);
                                for (EntityFolder folder : folders) {
                                    EntityFolder existing = db.folder().getFolderByName(account.id, folder.name);
                                    if (existing == null) {
                                        folder.account = account.id;
                                        Log.i(
                                            Helper.TAG, "Creating folder=" + folder.name + " (" + folder.type + ")");
                                        folder.id = db.folder().insertFolder(folder);
                                    } else {
                                        db.folder().setFolderType(existing.id, folder.type);
                                    }
                                }

                                db.setTransactionSuccessful();
                            } finally {
                                db.endTransaction();
                            }

                            ServiceSynchronize.reload(getContext(), "save account");

                            return null;
                        }

                        @Override
                        protected void onLoaded(Bundle args, Void data) {
                            getFragmentManager().popBackStack();
                        }

                        @Override
                        protected void onException(Bundle args, Throwable ex) {
                            Helper.setViewsEnabled(view, true);
                            btnAuthorize.setEnabled(true);
                            btnCheck.setEnabled(true);
                            btnSave.setEnabled(true);
                            pbSave.setVisibility(View.GONE);

                            new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                                .setMessage(Helper.formatThrowable(ex))
                                .setPositiveButton(android.R.string.cancel, null)
                                .create()
                                .show();
                        }
                    }.load(FragmentAccount.this, args);
                }
            });

        ibDelete.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                        .setMessage(R.string.title_account_delete)
                        .setPositiveButton(
                            android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Helper.setViewsEnabled(view, false);
                                    btnAuthorize.setEnabled(false);
                                    btnCheck.setEnabled(false);
                                    btnSave.setEnabled(false);
                                    pbWait.setVisibility(View.VISIBLE);

                                    Bundle args = new Bundle();
                                    args.putLong("id", id);

                                    new SimpleTask<Void>() {
                                        @Override
                                        protected Void onLoad(Context context, Bundle args) {
                                            long id = args.getLong("id");
                                            DB.getInstance(context).account().deleteAccount(id);
                                            ServiceSynchronize.reload(getContext(), "delete account");
                                            return null;
                                        }

                                        @Override
                                        protected void onLoaded(Bundle args, Void data) {
                                            getFragmentManager().popBackStack();
                                        }

                                        @Override
                                        protected void onException(Bundle args, Throwable ex) {
                                            Helper.unexpectedError(getContext(), ex);
                                        }
                                    }.load(FragmentAccount.this, args);
                                }
                            })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                }
            });

        adapter =
            new ArrayAdapter<>(getContext(), R.layout.spinner_item, new ArrayList<EntityFolder>());
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);

        spDrafts.setAdapter(adapter);
        spSent.setAdapter(adapter);
        spAll.setAdapter(adapter);
        spTrash.setAdapter(adapter);
        spJunk.setAdapter(adapter);

        // Initialize
        Helper.setViewsEnabled(view, false);
        btnAuthorize.setVisibility(View.GONE);
        cbStartTls.setVisibility(View.GONE);
        cbInsecure.setVisibility(View.GONE);
        tilPassword.setPasswordVisibilityToggleEnabled(id < 0);

        btnAdvanced.setVisibility(View.GONE);

        tvIdle.setVisibility(View.GONE);

        btnCheck.setVisibility(View.GONE);
        pbCheck.setVisibility(View.GONE);

        btnSave.setVisibility(View.GONE);
        pbSave.setVisibility(View.GONE);

        ibDelete.setVisibility(View.GONE);

        grpServer.setVisibility(View.GONE);
        grpAuthorize.setVisibility(View.GONE);
        grpAdvanced.setVisibility(View.GONE);
        grpFolders.setVisibility(View.GONE);

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("provider", spProvider.getSelectedItemPosition());
        outState.putString("authorized", authorized);
        outState.putString("password", tilPassword.getEditText().getText().toString());
        outState.putInt("advanced", grpAdvanced.getVisibility());
        outState.putInt("color", color);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final DB db = DB.getInstance(getContext());

        // Observe
        db.account()
            .liveAccount(id)
            .observe(
                getViewLifecycleOwner(),
                new Observer<EntityAccount>() {
                    private boolean once = false;

                    @Override
                    public void onChanged(@Nullable EntityAccount account) {
                        if (once) {
                            return;
                        }
                        once = true;

                        // Get providers
                        List<Provider> providers = Provider.loadProfiles(getContext());
                        providers.add(0, new Provider(getString(R.string.title_select)));
                        providers.add(1, new Provider(getString(R.string.title_custom)));

                        ArrayAdapter<Provider> padapter =
                            new ArrayAdapter<>(getContext(), R.layout.spinner_item, providers);
                        padapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
                        spProvider.setAdapter(padapter);

                        if (savedInstanceState == null) {
                            if (account != null) {
                                boolean found = false;
                                for (int pos = 2; pos < providers.size(); pos++) {
                                    Provider provider = providers.get(pos);
                                    if ((provider.imap_host != null && provider.imap_host.equals(account.host)
                                        && provider.imap_port == account.port) ||
                                        (provider.pop3_host != null && provider.pop3_host.equals(account.host)
                                        && provider.pop3_port == account.port)) {
                                        found = true;
                                        spProvider.setTag(pos);
                                        spProvider.setSelection(pos);
                                        break;
                                    }
                                }
                                if (!found) {
                                    spProvider.setTag(1);
                                    spProvider.setSelection(1);
                                }
                                etHost.setText(account.host);
                                etPort.setText(Long.toString(account.port));
                            }

                            authorized =
                                (account != null && account.auth_type != Helper.AUTH_TYPE_PASSWORD
                                    ? account.password
                                    : null);
                            etUser.setText(account == null ? null : account.user);
                            tilPassword.getEditText().setText(account == null ? null : account.password);

                            etName.setText(account == null ? null : account.name);
                            etSignature.setText(
                                account == null || account.signature == null
                                    ? null
                                    : Html.fromHtml(account.signature));

                            cbSynchronize.setChecked(account == null ? true : account.synchronize);
                            cbPrimary.setChecked(account == null ? true : account.primary);
                            etInterval.setText(account == null ? "" : Long.toString(account.poll_interval));

                            color =
                                (account == null || account.color == null
                                    ? Color.TRANSPARENT
                                    : account.color);

                            if (account == null) {
                                new SimpleTask<Integer>() {
                                    @Override
                                    protected Integer onLoad(Context context, Bundle args) {
                                        return DB.getInstance(context).account().getSynchronizingAccountCount();
                                    }

                                    @Override
                                    protected void onLoaded(Bundle args, Integer count) {
                                        cbPrimary.setChecked(count == 0);
                                    }
                                }.load(FragmentAccount.this, new Bundle());
                            }
                        } else {
                            int provider = savedInstanceState.getInt("provider");
                            spProvider.setTag(provider);
                            spProvider.setSelection(provider);

                            authorized = savedInstanceState.getString("authorized");
                            tilPassword.getEditText().setText(savedInstanceState.getString("password"));
                            grpAdvanced.setVisibility(savedInstanceState.getInt("advanced"));
                            color = savedInstanceState.getInt("color");
                        }

                        Helper.setViewsEnabled(view, true);

                        btnColor.setColor(color);

                        etSignature.setHint(R.string.title_optional);
                        etSignature.setEnabled(true);

                        cbPrimary.setEnabled(cbSynchronize.isChecked());

                        // Consider previous check/save/delete as cancelled
                        ibDelete.setVisibility(account == null ? View.GONE : View.VISIBLE);
                        pbWait.setVisibility(View.GONE);

                        if (account != null) {
                            db.folder()
                                .liveFolders(account.id)
                                .observe(
                                    getViewLifecycleOwner(),
                                    new Observer<List<TupleFolderEx>>() {
                                        @Override
                                        public void onChanged(final List<TupleFolderEx> _folders) {
                                            new Handler()
                                                .post(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            List<EntityFolder> folders = new ArrayList<>();
                                                            if (_folders != null) {
                                                                folders.addAll(_folders);
                                                            }
                                                            setFolders(folders);
                                                        }
                                                    });
                                        }
                                    });
                        }
                    }
                });
    }

    /**
     * Set base account to button
     *
     * @param result - color dialog data from {@link FragmentResultListener} result callback
     */
    private void setAccountColor(Bundle result) {
        try {
            int resultCode = result.getInt("resultCode");
            if (resultCode == RESULT_OK) {
                color = result.getInt("color");
                btnColor.setColor(color);
            }
        } catch (Throwable ex) {
            Log.e(Helper.TAG, "Set account color error: " + ex + "\n" + Log.getStackTraceString(ex));
        }
    }

    private boolean isSupported(Provider provider) {
        if (provider == null || provider.type == null) {
            return false;
        }
        Context context = getContext();
        if (context == null) {
            return false;
        }
        AccountManager am = AccountManager.get(context);
        for (AuthenticatorDescription auth : am.getAuthenticatorTypes()) {
            if (provider.type.equals(auth.type)) {
                return true;
            }
            if ("com.microsoft".equals(provider.type) ||
                "office365".equals(provider.type) ||
                "office365pcke".equals(provider.type) ||
                "outlookgraph".equals(provider.type) ||
                "outlook".equals(provider.type)) {
                if ("com.microsoft".equals(auth.type) ||
                    "com.microsoft.office.outlook".equals(auth.type) ||
                    "com.microsoft.azure.authenticator".equals(auth.type) ||
                    "office365".equals(auth.type) ||
                    "com.google.android.gm.exchange".equals(auth.type) ||
                    "com.android.email".equals(auth.type) ||
                    "com.android.exchange".equals(auth.type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void selectAccount() {
        Log.i(Helper.TAG, "Select account");
        Provider provider = (Provider) spProvider.getSelectedItem();
        if (provider.type != null) {
            List<String> types = new ArrayList<>();
            types.add(provider.type);
            if ("com.microsoft".equals(provider.type) ||
                "office365".equals(provider.type) ||
                "office365pcke".equals(provider.type) ||
                "outlookgraph".equals(provider.type) ||
                "outlook".equals(provider.type)) {
                types.add("com.microsoft");
                types.add("com.microsoft.office.outlook");
                types.add("com.microsoft.azure.authenticator");
                types.add("office365");
                types.add("com.google.android.gm.exchange");
                types.add("com.android.exchange");
            }

            Intent intent = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent = newChooseAccountIntent(
                    null,
                    null,
                    types.toArray(new String[0]),
                    null,
                    null,
                    null,
                    null
                );
            } else {
                intent = newChooseAccountIntent(
                    null,
                    null,
                    types.toArray(new String[0]),
                    false,
                    null,
                    null,
                    null,
                    null
                );
            }
            PackageManager pm = getContext().getPackageManager();
            if (intent.resolveActivity(pm) == null) { // system whitelisted
                throw new IllegalArgumentException(getString(R.string.title_no_viewer, intent));
            }
            startActivityForResult(intent, ActivitySetup.REQUEST_CHOOSE_ACCOUNT);
        }
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == ActivitySetup.REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectAccount();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(
            Helper.TAG,
            "Activity result request=" + requestCode + " result=" + resultCode + " data=" + data);
        if (resultCode == RESULT_OK) {
            if (requestCode == ActivitySetup.REQUEST_CHOOSE_ACCOUNT) {
                String name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                String type = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);

                AccountManager am = AccountManager.get(getContext());
                Account[] accounts = am.getAccountsByType(type);
                Log.i(Helper.TAG, "Accounts=" + accounts.length);
                for (final Account account : accounts) {
                    if (name.equals(account.name)) {
                        final Snackbar snackbar =
                            Snackbar.make(view, R.string.title_authorizing, Snackbar.LENGTH_SHORT);
                        snackbar.show();
                        am.getAuthToken(
                            account,
                            Helper.getAuthTokenType(type),
                            new Bundle(),
                            getActivity(),
                            new AccountManagerCallback<Bundle>() {
                                @Override
                                public void run(AccountManagerFuture<Bundle> future) {
                                    try {
                                        Bundle bundle = future.getResult();
                                        String token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                                        Log.i(Helper.TAG, "Got token");

                                        authorized = token;
                                        etUser.setText(account.name);
                                        tilPassword.getEditText().setText(token);
                                    } catch (Throwable ex) {
                                        Log.e(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                                        snackbar.setText(Helper.formatThrowable(ex));
                                    } finally {
                                        snackbar.dismiss();
                                    }
                                }
                            },
                            null);
                        break;
                    }
                }
            }
        }
    }

    private void setFolders(List<EntityFolder> folders) {
        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

        Collections.sort(
            folders,
            new Comparator<EntityFolder>() {
                @Override
                public int compare(EntityFolder f1, EntityFolder f2) {
                    int s =
                        Integer.compare(
                            EntityFolder.FOLDER_SORT_ORDER.indexOf(f1.type),
                            EntityFolder.FOLDER_SORT_ORDER.indexOf(f2.type));
                    if (s != 0) {
                        return s;
                    }
                    int c = -f1.synchronize.compareTo(f2.synchronize);
                    if (c != 0) {
                        return c;
                    }
                    return collator.compare(f1.name == null ? "" : f1.name, f2.name == null ? "" : f2.name);
                }
            });

        EntityFolder none = new EntityFolder();
        none.name = "";
        folders.add(0, none);

        adapter.clear();
        adapter.addAll(folders);

        for (int pos = 0; pos < folders.size(); pos++) {
            if (EntityFolder.DRAFTS.equals(folders.get(pos).type)) {
                spDrafts.setSelection(pos);
            } else if (EntityFolder.SENT.equals(folders.get(pos).type)) {
                spSent.setSelection(pos);
            } else if (EntityFolder.ARCHIVE.equals(folders.get(pos).type)) {
                spAll.setSelection(pos);
            } else if (EntityFolder.TRASH.equals(folders.get(pos).type)) {
                spTrash.setSelection(pos);
            } else if (EntityFolder.JUNK.equals(folders.get(pos).type)) {
                spJunk.setSelection(pos);
            }
        }

        grpFolders.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
    }

    private class CheckResult {
        List<EntityFolder> folders;
        boolean idle;
    }

    private void updateProviderSettings(Provider provider, boolean usePop3) {
        if (provider == null) return;

        // Update host and port based on protocol selection
        if (usePop3) {
            if (provider.pop3_host != null) {
                etHost.setText(provider.pop3_host);
                etPort.setText(Integer.toString(provider.pop3_port));
                cbStartTls.setChecked(provider.pop3_port == 110);
            } else {
                etHost.setText("");
                etPort.setText("");
                cbStartTls.setChecked(false);
            }
        } else {
            if (provider.imap_host != null) {
                etHost.setText(provider.imap_host);
                etPort.setText(Integer.toString(provider.imap_port));
                cbStartTls.setChecked(provider.imap_port == 143);
            } else {
                etHost.setText("");
                etPort.setText("");
                cbStartTls.setChecked(false);
            }
        }
        updatePortHint();
    }

    private void updatePortHint() {
        boolean checked = cbStartTls.isChecked();
        if (rbPop3.isChecked()) {
            etPort.setHint(checked ? "110" : "995");
        } else {
            etPort.setHint(checked ? "143" : "993");
        }
    }
}
