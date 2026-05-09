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
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.Collator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

public class AdapterMessage extends PagedListAdapter<TupleMessageEx, AdapterMessage.ViewHolder> {
    private Context context;
    private LifecycleOwner owner;
    private FragmentManager fragmentManager;
    private ViewType viewType;
    private long folder;
    private IProperties properties;

    private boolean contacts;
    private boolean avatars;
    private boolean compact;
    private boolean debug;

    private SelectionTracker<Long> selectionTracker = null;

    private DateFormat df =
        SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);

    enum ViewType {
        UNIFIED,
        FOLDER,
        THREAD,
        SEARCH
    }

    private static final long CACHE_IMAGE_DURATION = 3 * 24 * 3600 * 1000L;
    private static final int UNDO_TIMEOUT = 5000; // milliseconds

        public class ViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener, View.OnLongClickListener, BottomNavigationView.OnNavigationItemSelectedListener {
        private View itemView;
        private View vwColor;
        private ImageView ivExpander;
        private ImageView ivFlagged;
        private ImageView ivAvatar;
        private TextView tvFrom;
        private TextView tvSummary;
        private ImageView ivAddContact;
        private TextView tvSize;
        private TextView tvTime;
        private TextView tvTimeEx;
        private ImageView ivAttachments;
        private TextView tvSubject;
        private TextView tvFolder;
        private TextView tvAccount;
        private TextView tvCount;
        private ImageView ivThread;
        private TextView tvError;
        private ProgressBar pbLoading;

        private TextView tvFromEx;
        private TextView tvTo;
        private TextView tvReplyTo;
        private TextView tvCc;
        private TextView tvBcc;
        private TextView tvSubjectEx;

        private TextView tvHeaders;
        private ProgressBar pbHeaders;

        private BottomNavigationView bnvActions;

        private View vSeparatorBody;
        private Button btnImages;
        private TextView tvBody;
        private ProgressBar pbBody;

        private RecyclerView rvAttachment;
        private AdapterAttachment adapter;

        private Group grpDetails;
        private Group grpHeaders;
        private Group grpAttachments;
        private Group grpExpanded;

        private ItemDetailsMessage itemDetails = null;

        ViewHolder(View itemView) {
            super(itemView);

            this.itemView = itemView.findViewById(R.id.clItem);
            vwColor = itemView.findViewById(R.id.vwColor);
            ivExpander = itemView.findViewById(R.id.ivExpander);
            ivFlagged = itemView.findViewById(R.id.ivFlagged);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvFrom = itemView.findViewById(R.id.tvFrom);
            tvSummary = itemView.findViewById(R.id.tvSummary);
            ivAddContact = itemView.findViewById(R.id.ivAddContact);
            tvSize = itemView.findViewById(R.id.tvSize);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTimeEx = itemView.findViewById(R.id.tvTimeEx);
            ivAttachments = itemView.findViewById(R.id.ivAttachments);
            tvSubject = itemView.findViewById(R.id.tvSubject);
            tvAccount = itemView.findViewById(R.id.tvAccount);
            tvFolder = itemView.findViewById(R.id.tvFolder);
            tvCount = itemView.findViewById(R.id.tvCount);
            ivThread = itemView.findViewById(R.id.ivThread);
            tvError = itemView.findViewById(R.id.tvError);
            pbLoading = itemView.findViewById(R.id.pbLoading);

            tvFromEx = itemView.findViewById(R.id.tvFromEx);
            tvTo = itemView.findViewById(R.id.tvTo);
            tvReplyTo = itemView.findViewById(R.id.tvReplyTo);
            tvCc = itemView.findViewById(R.id.tvCc);
            tvBcc = itemView.findViewById(R.id.tvBcc);
            tvSubjectEx = itemView.findViewById(R.id.tvSubjectEx);

            tvHeaders = itemView.findViewById(R.id.tvHeaders);
            pbHeaders = itemView.findViewById(R.id.pbHeaders);

            bnvActions = itemView.findViewById(R.id.bnvActions);

            vSeparatorBody = itemView.findViewById(R.id.vSeparatorBody);
            btnImages = itemView.findViewById(R.id.btnImages);
            tvBody = itemView.findViewById(R.id.tvBody);
            pbBody = itemView.findViewById(R.id.pbBody);

            rvAttachment = itemView.findViewById(R.id.rvAttachment);
            rvAttachment.setHasFixedSize(false);
            LinearLayoutManager llm = new LinearLayoutManager(context);
            rvAttachment.setLayoutManager(llm);
            rvAttachment.setItemAnimator(null);

            adapter = new AdapterAttachment(context, owner, true);
            rvAttachment.setAdapter(adapter);

            grpDetails = itemView.findViewById(R.id.grpDetails);
            grpHeaders = itemView.findViewById(R.id.grpHeaders);
            grpAttachments = itemView.findViewById(R.id.grpAttachments);
            grpExpanded = itemView.findViewById(R.id.grpExpanded);

            tvBody.setMovementMethod(new UrlHandler());
        }

        private void wire() {
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
            ivAddContact.setOnClickListener(this);
            tvFrom.setOnClickListener(this);
            tvFromEx.setOnClickListener(this);
            tvTo.setOnClickListener(this);
            tvReplyTo.setOnClickListener(this);
            tvCc.setOnClickListener(this);
            tvBcc.setOnClickListener(this);
            bnvActions.setOnNavigationItemSelectedListener(this);
            btnImages.setOnClickListener(this);
        }

        private void unwire() {
            itemView.setOnClickListener(null);
            itemView.setOnLongClickListener(null);
            ivAddContact.setOnClickListener(null);
            tvFrom.setOnClickListener(null);
            tvFromEx.setOnClickListener(null);
            tvTo.setOnClickListener(null);
            tvReplyTo.setOnClickListener(null);
            tvCc.setOnClickListener(null);
            tvBcc.setOnClickListener(null);
            bnvActions.setOnNavigationItemSelectedListener(null);
            btnImages.setOnClickListener(null);
        }

        private void clear() {
            vwColor.setVisibility(View.GONE);
            ivExpander.setVisibility(View.GONE);
            ivFlagged.setVisibility(View.GONE);
            ivAvatar.setVisibility(View.GONE);
            tvFrom.setText(null);
            ivAddContact.setVisibility(View.GONE);
            tvSize.setText(null);
            tvTime.setText(null);
            ivAttachments.setVisibility(View.GONE);
            tvSubject.setText(null);
            tvFolder.setText(null);
            tvAccount.setText(null);
            tvCount.setText(null);
            ivThread.setVisibility(View.GONE);
            tvError.setVisibility(View.GONE);
            pbLoading.setVisibility(View.VISIBLE);
            pbHeaders.setVisibility(View.GONE);
            bnvActions.setVisibility(View.GONE);
            vSeparatorBody.setVisibility(View.GONE);
            btnImages.setVisibility(View.GONE);
            pbBody.setVisibility(View.GONE);
            grpDetails.setVisibility(View.GONE);
            grpHeaders.setVisibility(View.GONE);
            grpAttachments.setVisibility(View.GONE);
            grpExpanded.setVisibility(View.GONE);
        }

        private void bindTo(int position, final TupleMessageEx message) {
            final DB db = DB.getInstance(context);
            final boolean show_expanded = properties.isExpanded(message.id);
            boolean show_details = properties.showDetails(message.id);
            boolean show_headers = properties.showHeaders(message.id);

            pbLoading.setVisibility(View.GONE);

            boolean photo = false;
            if (avatars && message.avatar != null) {
                ContentResolver resolver = context.getContentResolver();
                InputStream is =
                    ContactsContract.Contacts.openContactPhotoInputStream(
                        resolver, Uri.parse(message.avatar));
                if (is != null) {
                    photo = true;
                    ivAvatar.setImageDrawable(Drawable.createFromStream(is, "avatar"));
                }
            }
            ivAvatar.setVisibility(photo ? View.VISIBLE : View.GONE);
            vwColor.setVisibility(View.GONE);
            vwColor.setBackgroundColor(Color.TRANSPARENT);

            if (message.accountColor != null
                && (viewType == ViewType.UNIFIED || viewType == ViewType.FOLDER)) {
                vwColor.setVisibility(View.VISIBLE);
                vwColor.setBackgroundColor(message.accountColor);
            } else {
                tvFolder.setBackgroundColor(
                    message.accountColor == null ? Color.GRAY : message.accountColor);
            }

            ivExpander.setImageResource(
                show_expanded ? R.drawable.baseline_expand_less_24 : R.drawable.baseline_expand_more_24);
            ivExpander.setVisibility(View.VISIBLE);

            if (viewType == ViewType.THREAD) {
                ivFlagged.setVisibility(message.unflagged == 1 ? View.GONE : View.VISIBLE);
            } else {
                ivFlagged.setVisibility(message.count - message.unflagged > 0 ? View.VISIBLE : View.GONE);
            }

            Address[] addresses = null;
            if (EntityFolder.DRAFTS.equals(message.folderType)
                || EntityFolder.OUTBOX.equals(message.folderType)
                || EntityFolder.SENT.equals(message.folderType)) {
                addresses = message.to;
                tvTime.setText(
                    DateUtils.getRelativeTimeSpanString(
                        context, message.sent == null ? message.received : message.sent));
            } else {
                addresses = message.from;
                tvTime.setText(DateUtils.getRelativeTimeSpanString(context, message.received));
            }

            if (compact && show_expanded) {
                tvFrom.setText(MessageHelper.getFormattedAddresses(addresses, null));
            } else {
                tvFrom.setText(MessageHelper.getFormattedAddresses(addresses, show_expanded ? MessageHelper.ADDRESS_FULL : MessageHelper.ADDRESS_NAME));
            }

            tvSize.setText(
                message.size == null ? null : Helper.humanReadableByteCount(message.size, true));
            tvSize.setAlpha(message.content ? 1.0f : 0.5f);

            ivAttachments.setVisibility(message.attachments > 0 ? View.VISIBLE : View.GONE);
            tvSubject.setText(message.subject);

            tvFolder.setVisibility(View.GONE);
            tvAccount.setVisibility(View.GONE);

            if (viewType == ViewType.UNIFIED || viewType == ViewType.FOLDER) {
                tvAccount.setText(message.accountName);
                tvAccount.setVisibility(View.VISIBLE);
            } else {
                tvFolder.setText(message.folderDisplay == null
                    ? Helper.localizeFolderName(context, message.folderName)
                    : message.folderDisplay);
                tvFolder.setVisibility(View.VISIBLE);
            }

            if (viewType == ViewType.THREAD || message.count == 1) {
                tvCount.setVisibility(View.GONE);
                ivThread.setVisibility(View.GONE);
            } else {
                tvCount.setText(Integer.toString(message.count));
                tvCount.setVisibility(View.VISIBLE);
                ivThread.setVisibility(View.VISIBLE);
            }

            tvFrom.setMaxLines(show_expanded ? Integer.MAX_VALUE : 1);
            tvSubject.setMaxLines(show_expanded ? Integer.MAX_VALUE : 1);
            tvSummary.setVisibility(View.GONE);

            if (message.content
                && !show_expanded
                && (!compact || viewType == ViewType.THREAD)) {
                String summary = summaries.get(message.id);
                if (summary != null) {
                    tvSummary.setText(summary);
                    tvSummary.setVisibility(View.VISIBLE);
                } else {
                    Bundle args = new Bundle();
                    args.putLong("id", message.id);
                    summaryTask.load(context, owner, args);
                }
            }

            if (debug) {
                db.operation().getOperationsByMessage(message.id).removeObservers(owner);
                db.operation()
                    .getOperationsByMessage(message.id)
                    .observe(
                        owner,
                        new Observer<List<EntityOperation>>() {
                            @Override
                            public void onChanged(List<EntityOperation> operations) {
                                String text =
                                    message.error
                                        + "\n"
                                        + message.id
                                        + " "
                                        + df.format(new Date(message.received))
                                        + "\n"
                                        + (message.ui_hide ? "HIDDEN " : "")
                                        + "seen="
                                        + message.seen
                                        + "/"
                                        + message.ui_seen
                                        + "/"
                                        + message.unseen
                                        + " "
                                        + message.uid
                                        + "/"
                                        + message.id
                                        + "\n"
                                        + message.msgid;
                                if (operations != null) {
                                    for (EntityOperation op : operations) {
                                        text +=
                                            "\n" + op.id + ":" + op.name + " " + df.format(new Date(op.created));
                                    }
                                }

                                tvError.setText(text);
                                tvError.setVisibility(View.VISIBLE);
                            }
                        });
            } else {
                tvError.setText(message.error);
                tvError.setVisibility(message.error == null ? View.GONE : View.VISIBLE);
            }

            int typeface = (message.unseen <= 0 || show_expanded ? Typeface.NORMAL : Typeface.BOLD);
            tvFrom.setTypeface(null, typeface);
            tvTime.setTypeface(null, typeface);
            tvSubject.setTypeface(null, typeface);
            tvCount.setTypeface(null, typeface);

            int colorUnseen = Helper.resolveColor(context, R.attr.colorUnread);
            int colorSecondary = Helper.resolveColor(context, android.R.attr.textColorSecondary);
            Drawable backgroundSeen = Helper.resolveDrawable(context, R.attr.drawableItemBackground);
            Drawable backgroundUnseen = Helper.resolveDrawable(context, R.attr.drawableItemUnreadBackground);
            tvSubject.setTextColor(colorUnseen);
            tvFrom.setTextColor(colorUnseen);
            tvTime.setTextColor(colorUnseen);
            tvSummary.setTextColor(colorSecondary);
            itemView.setBackground(!message.ui_seen && !show_expanded ? backgroundUnseen : backgroundSeen);

            grpExpanded.setVisibility(show_expanded ? View.VISIBLE : View.GONE);
            ivAddContact.setVisibility(
                show_expanded && contacts && message.from != null
                    ? View.VISIBLE
                    : View.GONE);

            grpDetails.setVisibility(show_details && show_expanded ? View.VISIBLE : View.GONE);

            pbHeaders.setVisibility(View.GONE);
            grpHeaders.setVisibility(show_headers && show_expanded ? View.VISIBLE : View.GONE);

            bnvActions.setVisibility(show_expanded ? View.INVISIBLE : View.GONE);
            vSeparatorBody.setVisibility(show_expanded ? View.GONE : View.GONE);
            btnImages.setVisibility(View.GONE);
            pbBody.setVisibility(View.GONE);
            grpAttachments.setVisibility(
                message.attachments > 0 && show_expanded ? View.VISIBLE : View.GONE);

            db.folder().liveSystemFolders(message.account).removeObservers(owner);
            db.attachment().liveAttachments(message.id).removeObservers(owner);

            bnvActions.setTag(null);

            if (show_expanded) {
                if (EntityFolder.DRAFTS.equals(message.folderType)
                    || EntityFolder.OUTBOX.equals(message.folderType)
                    || EntityFolder.SENT.equals(message.folderType)) {
                    tvTimeEx.setText(
                        df.format(new Date(message.sent == null ? message.received : message.sent)));
                } else {
                    tvTimeEx.setText(df.format(new Date(message.received)));
                }

                tvFromEx.setText(MessageHelper.getFormattedAddresses(message.from, MessageHelper.ADDRESS_FULL));
                tvTo.setText(MessageHelper.getFormattedAddresses(message.to, MessageHelper.ADDRESS_FULL));
                tvReplyTo.setText(MessageHelper.getFormattedAddresses(message.reply, MessageHelper.ADDRESS_FULL));
                tvCc.setText(MessageHelper.getFormattedAddresses(message.cc, MessageHelper.ADDRESS_FULL));
                tvBcc.setText(MessageHelper.getFormattedAddresses(message.bcc, MessageHelper.ADDRESS_FULL));
                tvSubjectEx.setText(message.subject);

                tvHeaders.setText(show_headers ? message.headers : null);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                int bodySp = prefs.getInt("body_text_size_sp", 24);
                tvBody.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, bodySp);

                vSeparatorBody.setVisibility(View.VISIBLE);
                tvBody.setText(null);
                pbBody.setVisibility(View.VISIBLE);

                if (message.content) {
                    Bundle args = new Bundle();
                    args.putSerializable("message", message);
                    bodyTask.load(context, owner, args);
                }

                if (!EntityFolder.OUTBOX.equals(message.folderType)) {
                    bnvActions.setHasTransientState(true);
                    db.folder()
                        .liveSystemFolders(message.account)
                        .observe(
                            owner,
                            new Observer<List<EntityFolder>>() {
                                @Override
                                public void onChanged(@Nullable List<EntityFolder> folders) {
                                    if (bnvActions.hasTransientState()) {
                                        boolean hasJunk = false;
                                        boolean hasTrash = false;
                                        boolean hasArchive = false;

                                        if (folders != null) {
                                            for (EntityFolder folder : folders) {
                                                if (EntityFolder.JUNK.equals(folder.type)) {
                                                    hasJunk = true;
                                                } else if (EntityFolder.TRASH.equals(folder.type)) {
                                                    hasTrash = true;
                                                } else if (EntityFolder.ARCHIVE.equals(folder.type)) {
                                                    hasArchive = true;
                                                }
                                            }
                                        }

                                        boolean inInbox = EntityFolder.INBOX.equals(message.folderType);
                                        boolean inOutbox = EntityFolder.OUTBOX.equals(message.folderType);
                                        boolean inArchive = EntityFolder.ARCHIVE.equals(message.folderType);
                                        boolean inTrash = EntityFolder.TRASH.equals(message.folderType);

                                        ActionData data = new ActionData();
                                        data.hasJunk = hasJunk;
                                        data.delete = (inTrash || !hasTrash || inOutbox);
                                        data.message = message;
                                        bnvActions.setTag(data);

                                        MenuItem deleteItem = bnvActions.getMenu().findItem(R.id.action_delete);
                                        if (deleteItem != null) {
                                            deleteItem.setVisible((message.uid != null && hasTrash)
                                                    || (inOutbox && !TextUtils.isEmpty(message.error)));
                                        }
                                        bnvActions
                                            .getMenu()
                                            .findItem(R.id.action_move)
                                            .setVisible(message.uid != null);
                                        bnvActions
                                            .getMenu()
                                            .findItem(R.id.action_archive)
                                            .setVisible(message.uid != null && !inArchive && hasArchive);

                                        bnvActions
                                            .getMenu()
                                            .findItem(R.id.action_reply)
                                            .setEnabled(message.content);
                                        bnvActions.getMenu().findItem(R.id.action_reply).setVisible(!inOutbox);

                                        bnvActions.setVisibility(View.VISIBLE);
                                        vSeparatorBody.setVisibility(View.GONE);

                                        bnvActions.setHasTransientState(false);
                                    }
                                }
                            });
                }

                // Observe attachments
                db.attachment()
                    .liveAttachments(message.id)
                    .observe(
                        owner,
                        new Observer<List<EntityAttachment>>() {
                            @Override
                            public void onChanged(@Nullable List<EntityAttachment> attachments) {
                                if (attachments == null) {
                                    attachments = new ArrayList<>();
                                }

                                adapter.set(attachments);

                                if (message.content) {
                                    Bundle args = new Bundle();
                                    args.putSerializable("message", message);
                                    bodyTask.load(context, owner, args);
                                }
                            }
                        });
            }

            itemDetails = new ItemDetailsMessage(position, message.id);
            itemView.setActivated(selectionTracker != null && selectionTracker.isSelected(message.id));
        }

        @Override
        public void onClick(View view) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }

            TupleMessageEx message = getItem(pos);

            if (view.getId() == R.id.ivAddContact) {
                Helper.onAddAddresses(context, message.from);
            } else if (view.getId() == R.id.tvFrom) {
                if (properties.isExpanded(message.id)) {
                    Helper.onAddAddresses(context, message.from);
                } else {
                    onExpandMessage(pos, message);
                }
            } else if (view.getId() == R.id.tvFromEx) {
                Helper.onAddAddresses(context, message.from);
            } else if (view.getId() == R.id.tvTo) {
                Helper.onAddAddresses(context, message.to);
            } else if (view.getId() == R.id.tvReplyTo) {
                Helper.onAddAddresses(context, message.reply);
            } else if (view.getId() == R.id.tvCc) {
                Helper.onAddAddresses(context, message.cc);
            } else if (view.getId() == R.id.tvBcc) {
                Helper.onAddAddresses(context, message.bcc);
            } else if (view.getId() == R.id.btnImages) {
                onShowImages(message);
            } else if (EntityFolder.DRAFTS.equals(message.folderType) && viewType != ViewType.THREAD) {
                context.startActivity(
                    new Intent(context, ActivityCompose.class)
                        .putExtra("action", "edit")
                        .putExtra("id", message.id));
            } else {
                onExpandMessage(pos, message);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return false;
            }

            final TupleMessageEx message = getItem(pos);
            if (message == null) {
                return false;
            }

            // Show options dialog with Archive / Delete
            CharSequence[] options = new CharSequence[] {
                context.getString(R.string.title_delete)
            };
            String dlgTitle = TextUtils.isEmpty(message.subject)
                ? context.getString(R.string.title_select)
                : message.subject;
            new DialogBuilderLifecycle(context, owner)
                .setTitle(dlgTitle)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            // Delete: move to trash if possible, else permanent delete
                            new SimpleTask<Boolean>() {
                                @Override
                                protected Boolean onLoad(Context context, Bundle args) {
                                    boolean inTrash = EntityFolder.TRASH.equals(message.folderType);
                                    boolean inOutbox = EntityFolder.OUTBOX.equals(message.folderType);
                                    boolean hasTrash = (DB.getInstance(context).folder()
                                        .getFolderByType(message.account, EntityFolder.TRASH) != null);
                                    return !(inTrash || !hasTrash || inOutbox); // true if can move to trash
                                }

                                @Override
                                protected void onLoaded(Bundle args, Boolean canMoveToTrash) {
                                    if (Boolean.TRUE.equals(canMoveToTrash)) {
                                        moveWithUndo(message, EntityFolder.TRASH, itemView);
                                    } else {
                                        // Permanent delete confirmation
                                        new DialogBuilderLifecycle(context, owner)
                                            .setMessage(R.string.title_ask_delete)
                                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    Bundle args = new Bundle();
                                                    args.putLong("id", message.id);
                                                    new SimpleTask<Void>() {
                                                        @Override
                                                        protected Void onLoad(Context context, Bundle args) {
                                                            long id = args.getLong("id");
                                                            DB db = DB.getInstance(context);
                                                            try {
                                                                db.beginTransaction();
                                                                EntityMessage em = db.message().getMessage(id);
                                                                if (em.uid == null && !TextUtils.isEmpty(em.error)) {
                                                                    db.message().deleteMessage(id);
                                                                } else {
                                                                    db.message().setMessageUiHide(em.id, true);
                                                                    EntityOperation.queue(db, em, EntityOperation.DELETE);
                                                                }
                                                                db.setTransactionSuccessful();
                                                            } finally {
                                                                db.endTransaction();
                                                            }
                                                            EntityOperation.process(context);
                                                            return null;
                                                        }

                                                        @Override
                                                        protected void onException(Bundle args, Throwable ex) {
                                                            Helper.unexpectedError(context, ex);
                                                        }
                                                    }.load(context, owner, args);
                                                }
                                            })
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show();
                                    }
                                }

                                @Override
                                protected void onException(Bundle args, Throwable ex) {
                                    Helper.unexpectedError(context, ex);
                                }
                            }.load(context, owner, new Bundle());
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();

            return true;
        }

        private void onShowImages(EntityMessage message) {
            properties.setImages(message.id, true);
            btnImages.setEnabled(false);

            Bundle args = new Bundle();
            args.putSerializable("message", message);
            bodyTask.load(context, owner, args);
        }

        private void onExpandMessage(int pos, EntityMessage message) {
            boolean expanded = !properties.isExpanded(message.id);
            properties.setExpanded(message.id, expanded);
            notifyItemChanged(pos);
        }

        private SimpleTask<Spanned> bodyTask =
            new SimpleTask<Spanned>() {
                @Override
                protected void onInit(Bundle args) {
                    btnImages.setHasTransientState(true);
                    tvBody.setHasTransientState(true);
                    pbBody.setHasTransientState(true);
                }

                @Override
                protected Spanned onLoad(final Context context, final Bundle args) throws Throwable {
                    TupleMessageEx message = (TupleMessageEx) args.getSerializable("message");
                    String body = message.read(context);
                    return decodeHtml(message, body);
                }

                @Override
                protected void onLoaded(Bundle args, Spanned body) {
                    TupleMessageEx message = (TupleMessageEx) args.getSerializable("message");

                    SpannedString ss = new SpannedString(body);
                    boolean has_images = (ss.getSpans(0, ss.length(), ImageSpan.class).length > 0);
                    boolean show_expanded = properties.isExpanded(message.id);
                    boolean show_images = properties.showImages(message.id);

                    btnImages.setVisibility(
                        has_images && show_expanded && !show_images ? View.VISIBLE : View.GONE);
                    tvBody.setText(body);
                    pbBody.setVisibility(View.GONE);

                    btnImages.setHasTransientState(false);
                    tvBody.setHasTransientState(false);
                    pbBody.setHasTransientState(false);
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    btnImages.setHasTransientState(false);
                    tvBody.setHasTransientState(false);
                    pbBody.setHasTransientState(false);

                    Helper.unexpectedError(context, ex);
                }
            };

        private SimpleTask<String> summaryTask = new SimpleTask<String>() {
            @Override
            protected String onLoad(Context context, Bundle args) throws IOException {
                long id = args.getLong("id");
                String body = EntityMessage.read(context, id);
                Document doc = Jsoup.parse(body);
                String plainText = doc.body().text();
                int limit = compact ? 60 : 120;
                return plainText.substring(0, Math.min(plainText.length(), limit)) + "...";
            }

            @Override
            protected void onLoaded(Bundle args, String summary) {
                long id = args.getLong("id");
                summaries.put(id, summary);
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    TupleMessageEx message = getItem(pos);
                    if (message != null && message.id.equals(id)) {
                        tvSummary.setText(summary);
                        tvSummary.setVisibility(View.VISIBLE);
                    }
                }
            }
        };

        private Spanned decodeHtml(final EntityMessage message, String body) {
            return Html.fromHtml(
                HtmlHelper.sanitize(body),
                new Html.ImageGetter() {
                    @Override
                    public Drawable getDrawable(String source) {
                        float scale = context.getResources().getDisplayMetrics().density;
                        int px = (int) (24 * scale + 0.5f);

                        if (source != null && source.startsWith("cid")) {
                            String s = source.substring(source.indexOf(':') + 1).replaceAll("[<>]", "");
                            String cid = "<" + s + ">";
                            EntityAttachment attachment =
                                DB.getInstance(context).attachment().getAttachment(message.id, cid);
                            if (attachment == null && message.thread != null) {
                                attachment = DB.getInstance(context).attachment().getAttachmentByThread(message.thread, cid);
                            }
                            if (attachment == null || !attachment.available) {
                                if (s.startsWith(BuildConfig.APPLICATION_ID)) {
                                    try {
                                        long id = Long.parseLong(s.replace(BuildConfig.APPLICATION_ID + ".", ""));
                                        attachment = DB.getInstance(context).attachment().getAttachment(id);
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                            }

                            if (attachment == null || !attachment.available) {
                                Drawable d =
                                    context
                                        .getResources()
                                        .getDrawable(R.drawable.baseline_warning_24, context.getTheme());
                                d.setBounds(0, 0, px, px);
                                return d;
                            } else {
                                File file = EntityAttachment.getFile(context, attachment.id);
                                Drawable d = Drawable.createFromPath(file.getAbsolutePath());
                                if (d == null) {
                                    d =
                                        context
                                            .getResources()
                                            .getDrawable(R.drawable.baseline_warning_24, context.getTheme());
                                    d.setBounds(0, 0, px, px);
                                } else {
                                    d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                                }
                                return d;
                            }
                        }

                        if (properties.showImages(message.id)) {
                            // Get cache folder
                            File dir = new File(context.getCacheDir(), "images");
                            dir.mkdir();

                            // Cleanup cache
                            long now = new Date().getTime();
                            File[] images = dir.listFiles();
                            if (images != null) {
                                for (File image : images) {
                                    if (image.isFile() && image.lastModified() + CACHE_IMAGE_DURATION < now) {
                                        Log.i(Helper.TAG, "Deleting from image cache " + image.getName());
                                        image.delete();
                                    }
                                }
                            }

                            InputStream is = null;
                            FileOutputStream os = null;
                            try {
                                if (source == null) {
                                    throw new IllegalArgumentException(
                                        "Html.ImageGetter.getDrawable(source == null)");
                                }

                                // Create unique file name
                                File file = new File(dir, message.id + "_" + source.hashCode());

                                // Get input stream
                                if (file.exists()) {
                                    Log.i(Helper.TAG, "Using cached " + file);
                                    is = new FileInputStream(file);
                                } else {
                                    Log.i(Helper.TAG, "Downloading " + source);
                                    is = new URL(source).openStream();
                                }

                                // Decode image from stream
                                Bitmap bm = BitmapFactory.decodeStream(is);
                                if (bm == null) {
                                    throw new IllegalArgumentException();
                                }

                                // Cache bitmap
                                if (!file.exists()) {
                                    os = new FileOutputStream(file);
                                    bm.compress(Bitmap.CompressFormat.PNG, 100, os);
                                }

                                // Create drawable from bitmap
                                Drawable d = new BitmapDrawable(context.getResources(), bm);
                                d.setBounds(0, 0, bm.getWidth(), bm.getHeight());
                                return d;
                            } catch (Throwable ex) {
                                // Show warning icon
                                Log.e(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                                Drawable d =
                                    context
                                        .getResources()
                                        .getDrawable(R.drawable.baseline_warning_24, context.getTheme());
                                d.setBounds(0, 0, px, px);
                                return d;
                            } finally {
                                // Close streams
                                if (is != null) {
                                    try {
                                        is.close();
                                    } catch (IOException e) {
                                        Log.w(Helper.TAG, e + "\n" + Log.getStackTraceString(e));
                                    }
                                }
                                if (os != null) {
                                    try {
                                        os.close();
                                    } catch (IOException e) {
                                        Log.w(Helper.TAG, e + "\n" + Log.getStackTraceString(e));
                                    }
                                }
                            }
                        } else {
                            // Show placeholder icon
                            Drawable d =
                                context
                                    .getResources()
                                    .getDrawable(R.drawable.baseline_image_24, context.getTheme());
                            d.setBounds(0, 0, px, px);
                            return d;
                        }
                    }
                },
                new Html.TagHandler() {
                    @Override
                    public void handleTag(
                        boolean opening, String tag, Editable output, XMLReader xmlReader) {
                        if (BuildConfig.DEBUG) {
                            Log.i(Helper.TAG, "HTML tag=" + tag + " opening=" + opening);
                        }
                    }
                });
        }

        private class UrlHandler extends LinkMovementMethod {
            public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }

                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getTotalPaddingLeft();
                y -= widget.getTotalPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);
                if (link.length != 0) {
                    String url = link[0].getURL();
                    Uri uri = Uri.parse(url);

                    if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.title_no_viewer, uri.toString()),
                            Toast.LENGTH_LONG)
                            .show();
                        return true;
                    }

                    View view = LayoutInflater.from(context).inflate(R.layout.dialog_link, null);
                    final EditText etLink = view.findViewById(R.id.etLink);
                    etLink.setText(url);
                    new DialogBuilderLifecycle(context, owner)
                        .setView(view)
                        .setPositiveButton(
                            R.string.title_yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Uri uri = Uri.parse(etLink.getText().toString());

                                    if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.title_no_viewer, uri.toString()),
                                            Toast.LENGTH_LONG)
                                            .show();
                                        return;
                                    }

                                    Helper.view(context, uri);
                                }
                            })
                        .setNegativeButton(R.string.title_no, null)
                        .show();
                }

                return true;
            }
        }

        private class ActionData {
            boolean hasJunk;
            boolean delete;
            TupleMessageEx message;
        }

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            ActionData data = (ActionData) bnvActions.getTag();
            if (data == null) {
                return false;
            }

            int itemId = item.getItemId();
            if (itemId == R.id.action_more) {
                onMore(data);
                return true;
            } else if (itemId == R.id.action_delete) {
                onDelete(data);
                return true;
            } else if (itemId == R.id.action_move) {
                onMove(data);
                return true;
            } else if (itemId == R.id.action_archive) {
                onArchive(data);
                return true;
            } else if (itemId == R.id.action_reply) {
                onReply(data);
                return true;
            } else {
                return false;
            }
        }

        private void onJunk(final ActionData data) {
            new DialogBuilderLifecycle(context, owner)
                .setMessage(R.string.title_ask_spam)
                .setPositiveButton(
                    android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Bundle args = new Bundle();
                            args.putLong("id", data.message.id);

                            new SimpleTask<Void>() {
                                @Override
                                protected Void onLoad(Context context, Bundle args) {
                                    long id = args.getLong("id");

                                    DB db = DB.getInstance(context);
                                    try {
                                        db.beginTransaction();

                                        db.message().setMessageUiHide(id, true);

                                        EntityMessage message = db.message().getMessage(id);
                                        EntityFolder spam =
                                            db.folder().getFolderByType(message.account, EntityFolder.JUNK);
                                        EntityOperation.queue(db, message, EntityOperation.MOVE, spam.id);

                                        db.setTransactionSuccessful();
                                    } finally {
                                        db.endTransaction();
                                    }

                                    EntityOperation.process(context);

                                    return null;
                                }

                                @Override
                                protected void onException(Bundle args, Throwable ex) {
                                    Helper.unexpectedError(context, ex);
                                }
                            }.load(context, owner, args);
                        }
                    })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        }

        private void onForward(final ActionData data) {
            Bundle args = new Bundle();
            args.putLong("id", data.message.id);

            new SimpleTask<Boolean>() {
                @Override
                protected Boolean onLoad(Context context, Bundle args) {
                    long id = args.getLong("id");
                    List<EntityAttachment> attachments =
                        DB.getInstance(context).attachment().getAttachments(id);
                    for (EntityAttachment attachment : attachments) {
                        if (!attachment.available) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                protected void onLoaded(Bundle args, Boolean available) {
                    final Intent forward =
                        new Intent(context, ActivityCompose.class)
                            .putExtra("action", "forward")
                            .putExtra("reference", data.message.id);
                    if (available) {
                        context.startActivity(forward);
                    } else {
                        new DialogBuilderLifecycle(context, owner)
                            .setMessage(R.string.title_attachment_unavailable)
                            .setPositiveButton(
                                android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        context.startActivity(forward);
                                    }
                                })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    }
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Helper.unexpectedError(context, ex);
                }
            }.load(context, owner, args);
        }

        private void onReplyAll(ActionData data) {
            context.startActivity(
                new Intent(context, ActivityCompose.class)
                    .putExtra("action", "reply_all")
                    .putExtra("reference", data.message.id));
        }

        private void onAnswer(final ActionData data) {
            final DB db = DB.getInstance(context);
            db.answer()
                .liveAnswers()
                .observe(
                    owner,
                    new Observer<List<EntityAnswer>>() {
                        @Override
                        public void onChanged(List<EntityAnswer> answers) {
                            if (answers == null || answers.size() == 0) {
                                Snackbar snackbar =
                                    Snackbar.make(
                                        itemView,
                                        context.getString(R.string.title_no_answers),
                                        Snackbar.LENGTH_LONG);
                                snackbar.setAction(
                                    R.string.title_fix,
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            FragmentTransaction fragmentTransaction =
                                                fragmentManager.beginTransaction();
                                            fragmentTransaction
                                                .replace(R.id.content_frame, new FragmentAnswers())
                                                .addToBackStack("answers");
                                            fragmentTransaction.commit();
                                        }
                                    });
                                snackbar.show();
                            } else {
                                final Collator collator = Collator.getInstance(Locale.getDefault());
                                collator.setStrength(Collator.SECONDARY); // Case insensitive, process
                                // accents etc

                                Collections.sort(
                                    answers,
                                    new Comparator<EntityAnswer>() {
                                        @Override
                                        public int compare(EntityAnswer a1, EntityAnswer a2) {
                                            return collator.compare(a1.name, a2.name);
                                        }
                                    });

                                View anchor = bnvActions.findViewById(R.id.action_more);
                                PopupMenu popupMenu = new PopupMenu(context, anchor);

                                int order = 0;
                                for (EntityAnswer answer : answers) {
                                    popupMenu
                                        .getMenu()
                                        .add(Menu.NONE, answer.id.intValue(), order++, answer.name);
                                }

                                popupMenu.setOnMenuItemClickListener(
                                    new PopupMenu.OnMenuItemClickListener() {
                                        @Override
                                        public boolean onMenuItemClick(MenuItem target) {
                                            context.startActivity(
                                                new Intent(context, ActivityCompose.class)
                                                    .putExtra("action", "reply")
                                                    .putExtra("reference", data.message.id)
                                                    .putExtra("answer", (long) target.getItemId()));
                                            return true;
                                        }
                                    });

                                popupMenu.show();
                            }

                            db.answer().liveAnswers().removeObservers(owner);
                        }
                    });
        }

        private void onUnseen(final ActionData data) {
            Bundle args = new Bundle();
            args.putLong("id", data.message.id);

            new SimpleTask<Void>() {
                @Override
                protected Void onLoad(Context context, Bundle args) {
                    long id = args.getLong("id");

                    DB db = DB.getInstance(context);
                    try {
                        db.beginTransaction();

                        EntityMessage message = db.message().getMessage(id);
                        db.message().setMessageUiSeen(message.id, false);
                        EntityOperation.queue(db, message, EntityOperation.SEEN, false);

                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }

                    EntityOperation.process(context);

                    return null;
                }

                @Override
                protected void onLoaded(Bundle args, Void ignored) {
                    properties.setExpanded(data.message.id, false);
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(pos);
                    }
                }
            }.load(context, owner, args);
        }

        private void onFlag(ActionData data) {
            Bundle args = new Bundle();
            args.putLong("id", data.message.id);
            args.putBoolean("flagged", !data.message.ui_flagged);
            Log.i(
                Helper.TAG, "Set message id=" + data.message.id + " flagged=" + !data.message.ui_flagged);

            new SimpleTask<Void>() {
                @Override
                protected Void onLoad(Context context, Bundle args) {
                    long id = args.getLong("id");
                    boolean flagged = args.getBoolean("flagged");
                    DB db = DB.getInstance(context);
                    EntityMessage message = db.message().getMessage(id);
                    db.message().setMessageUiFlagged(message.id, flagged);
                    EntityOperation.queue(db, message, EntityOperation.FLAG, flagged);
                    EntityOperation.process(context);
                    return null;
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Helper.unexpectedError(context, ex);
                }
            }.load(context, owner, args);
        }

        private void onShowDetails(ActionData data) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            boolean show_details = !properties.showDetails(data.message.id);
            properties.setDetails(data.message.id, show_details);
            if (show_details) {
                grpDetails.setVisibility(View.VISIBLE);
            } else {
                notifyItemChanged(pos);
            }
        }

        private void onShowHeaders(ActionData data) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            boolean show_headers = !properties.showHeaders(data.message.id);
            properties.setHeaders(data.message.id, show_headers);
            if (show_headers) {
                grpHeaders.setVisibility(View.VISIBLE);
                pbHeaders.setVisibility(View.VISIBLE);

                Bundle args = new Bundle();
                args.putLong("id", data.message.id);

                new SimpleTask<Void>() {
                    @Override
                    protected Void onLoad(Context context, Bundle args) {
                        Long id = args.getLong("id");
                        DB db = DB.getInstance(context);
                        EntityMessage message = db.message().getMessage(id);
                        EntityOperation.queue(db, message, EntityOperation.HEADERS);
                        EntityOperation.process(context);
                        return null;
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Helper.unexpectedError(context, ex);
                    }
                }.load(context, owner, args);
            } else {
                notifyItemChanged(pos);
            }
        }

        private void onShowHtml(ActionData data) {
            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
            lbm.sendBroadcast(
                new Intent(ActivityView.ACTION_VIEW_FULL)
                    .putExtra("id", data.message.id)
                    .putExtra("from", MessageHelper.getFormattedAddresses(data.message.from, MessageHelper.ADDRESS_FULL)));
        }

        private void onDecrypt(ActionData data) {
            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
            lbm.sendBroadcast(
                new Intent(ActivityView.ACTION_DECRYPT)
                    .putExtra("id", data.message.id)
                    .putExtra("to", ((InternetAddress) data.message.to[0]).getAddress()));
        }

        private void onMore(final ActionData data) {
            boolean inOutbox = EntityFolder.OUTBOX.equals(data.message.folderType);
            boolean show_details = properties.showDetails(data.message.id);
            boolean show_headers = properties.showHeaders(data.message.id);

            View anchor = bnvActions.findViewById(R.id.action_more);
            PopupMenu popupMenu = new PopupMenu(context, anchor);
            popupMenu.inflate(R.menu.menu_message);
            popupMenu
                .getMenu()
                .findItem(R.id.menu_junk)
                .setVisible(data.message.uid != null && data.hasJunk && !inOutbox);

            popupMenu.getMenu().findItem(R.id.menu_forward).setEnabled(data.message.content);
            popupMenu.getMenu().findItem(R.id.menu_forward).setVisible(!inOutbox);

            popupMenu.getMenu().findItem(R.id.menu_reply_all).setEnabled(data.message.content);
            popupMenu.getMenu().findItem(R.id.menu_reply_all).setVisible(!inOutbox);

            popupMenu.getMenu().findItem(R.id.menu_answer).setEnabled(data.message.content);
            popupMenu.getMenu().findItem(R.id.menu_answer).setVisible(!inOutbox);

            popupMenu
                .getMenu()
                .findItem(R.id.menu_unseen)
                .setVisible(data.message.uid != null && !inOutbox);

            popupMenu.getMenu().findItem(R.id.menu_flag).setChecked(data.message.unflagged != 1);
            popupMenu
                .getMenu()
                .findItem(R.id.menu_flag)
                .setVisible(data.message.uid != null && !inOutbox);

            popupMenu.getMenu().findItem(R.id.menu_show_details).setChecked(show_details);
            popupMenu.getMenu().findItem(R.id.menu_show_details).setVisible(data.message.uid != null);

            popupMenu.getMenu().findItem(R.id.menu_show_headers).setChecked(show_headers);
            popupMenu.getMenu().findItem(R.id.menu_show_headers).setVisible(data.message.uid != null);

            popupMenu
                .getMenu()
                .findItem(R.id.menu_show_html)
                .setEnabled(data.message.content && Helper.classExists("android.webkit.WebView"));

            popupMenu
                .getMenu()
                .findItem(R.id.menu_decrypt)
                .setEnabled(data.message.to != null && data.message.to.length > 0);

            popupMenu.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem target) {
                        int itemId = target.getItemId();
                        if (itemId == R.id.menu_junk) {
                            onJunk(data);
                            return true;
                        } else if (itemId == R.id.menu_forward) {
                            onForward(data);
                            return true;
                        } else if (itemId == R.id.menu_reply_all) {
                            onReplyAll(data);
                            return true;
                        } else if (itemId == R.id.menu_answer) {
                            onAnswer(data);
                            return true;
                        } else if (itemId == R.id.menu_unseen) {
                            onUnseen(data);
                            return true;
                        } else if (itemId == R.id.menu_flag) {
                            onFlag(data);
                            return true;
                        } else if (itemId == R.id.menu_show_details) {
                            onShowDetails(data);
                            return true;
                        } else if (itemId == R.id.menu_show_headers) {
                            onShowHeaders(data);
                            return true;
                        } else if (itemId == R.id.menu_show_html) {
                            onShowHtml(data);
                            return true;
                        } else if (itemId == R.id.menu_decrypt) {
                            onDecrypt(data);
                            return true;
                        } else if (itemId == R.id.menu_delete) {
                            onDelete(data);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
            popupMenu.show();
        }

        private void onDelete(final ActionData data) {
            delete(data.message, itemView);
        }

        private void onMove(ActionData data) {
            Bundle args = new Bundle();
            args.putLong("id", data.message.id);

            new SimpleTask<List<EntityFolder>>() {
                @Override
                protected List<EntityFolder> onLoad(Context context, Bundle args) {
                    DB db = DB.getInstance(context);
                    EntityMessage message = db.message().getMessage(args.getLong("id"));
                    List<EntityFolder> folders = db.folder().getFolders(message.account);
                    List<EntityFolder> targets = new ArrayList<>();
                    for (EntityFolder f : folders) {
                        if (!f.id.equals(message.folder) && !EntityFolder.DRAFTS.equals(f.type)) {
                            targets.add(f);
                        }
                    }

                    final Collator collator = Collator.getInstance(Locale.getDefault());
                    collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

                    Collections.sort(
                        targets,
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
                                return collator.compare(
                                    f1.name == null ? "" : f1.name, f2.name == null ? "" : f2.name);
                            }
                        });

                    return targets;
                }

                @Override
                protected void onLoaded(final Bundle args, List<EntityFolder> folders) {
                    View anchor = bnvActions.findViewById(R.id.action_move);
                    PopupMenu popupMenu = new PopupMenu(context, anchor);

                    int order = 0;
                    for (EntityFolder folder : folders) {
                        String name =
                            (folder.display == null
                                ? Helper.localizeFolderName(context, folder.name)
                                : folder.display);
                        popupMenu.getMenu().add(Menu.NONE, folder.id.intValue(), order++, name);
                    }

                    popupMenu.setOnMenuItemClickListener(
                        new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(final MenuItem target) {
                                args.putLong("target", target.getItemId());

                                new SimpleTask<Void>() {
                                    @Override
                                    protected Void onLoad(Context context, Bundle args) {
                                        long id = args.getLong("id");
                                        long target = args.getLong("target");

                                        DB db = DB.getInstance(context);
                                        try {
                                            db.beginTransaction();

                                            db.message().setMessageUiHide(id, true);

                                            EntityMessage message = db.message().getMessage(id);
                                            EntityOperation.queue(db, message, EntityOperation.MOVE, target);

                                            db.setTransactionSuccessful();
                                        } finally {
                                            db.endTransaction();
                                        }

                                        EntityOperation.process(context);

                                        return null;
                                    }

                                    @Override
                                    protected void onException(Bundle args, Throwable ex) {
                                        Helper.unexpectedError(context, ex);
                                    }
                                }.load(context, owner, args);

                                return true;
                            }
                        });

                    popupMenu.show();
                }
            }.load(context, owner, args);
        }

        private void onArchive(ActionData data) {
            Bundle args = new Bundle();
            args.putLong("id", data.message.id);

            new SimpleTask<Void>() {
                @Override
                protected Void onLoad(Context context, Bundle args) {
                    long id = args.getLong("id");

                    DB db = DB.getInstance(context);
                    try {
                        db.beginTransaction();

                        db.message().setMessageUiHide(id, true);

                        EntityMessage message = db.message().getMessage(id);
                        EntityFolder archive =
                            db.folder().getFolderByType(message.account, EntityFolder.ARCHIVE);
                        EntityOperation.queue(db, message, EntityOperation.MOVE, archive.id);

                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }

                    EntityOperation.process(context);

                    return null;
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Helper.unexpectedError(context, ex);
                }
            }.load(context, owner, args);
        }

        private void onReply(ActionData data) {
            context.startActivity(
                new Intent(context, ActivityCompose.class)
                    .putExtra("action", "reply")
                    .putExtra("reference", data.message.id));
        }

        ItemDetailsLookup.ItemDetails<Long> getItemDetails(@NonNull MotionEvent motionEvent) {
            return itemDetails;
        }
    }

    private Map<Long, String> summaries = new java.util.HashMap<>();

    AdapterMessage(
        Context context,
        LifecycleOwner owner,
        FragmentManager fragmentManager,
        ViewType viewType,
        long folder,
        IProperties properties) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.owner = owner;
        this.fragmentManager = fragmentManager;
        this.viewType = viewType;
        this.folder = folder;
        this.properties = properties;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        this.contacts =
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED);
        this.avatars = (prefs.getBoolean("avatars", true) && this.contacts);
        this.compact = prefs.getBoolean("compact", false);
        this.debug = prefs.getBoolean("debug", false);
    }

    private static final DiffUtil.ItemCallback<TupleMessageEx> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<TupleMessageEx>() {
            @Override
            public boolean areItemsTheSame(
                @NonNull TupleMessageEx prev, @NonNull TupleMessageEx next) {
                return prev.id.equals(next.id);
            }

            @Override
            public boolean areContentsTheSame(
                @NonNull TupleMessageEx prev, @NonNull TupleMessageEx next) {
                return prev.shallowEquals(next);
            }
        };

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(
            LayoutInflater.from(context)
                .inflate(
                    compact ? R.layout.item_message_compact : R.layout.item_message_normal,
                    parent,
                    false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.unwire();

        TupleMessageEx message = getItem(position);
        if (message == null) {
            holder.clear();
        } else {
            holder.bindTo(position, message);
            holder.wire();
        }
    }

    void setSelectionTracker(SelectionTracker<Long> selectionTracker) {
        this.selectionTracker = selectionTracker;
    }

    public void delete(int position, final View snackbarView) {
        final TupleMessageEx message = getItem(position);
        if (message != null) {
            delete(message, snackbarView);
        }
    }

    public void delete(final TupleMessageEx message, final View snackbarView) {
        new SimpleTask<Boolean>() {
            @Override
            protected Boolean onLoad(Context context, Bundle args) {
                boolean inTrash = EntityFolder.TRASH.equals(message.folderType);
                boolean inOutbox = EntityFolder.OUTBOX.equals(message.folderType);
                boolean hasTrash = (DB.getInstance(context).folder()
                        .getFolderByType(message.account, EntityFolder.TRASH) != null);
                return (inTrash || !hasTrash || inOutbox); // true if permanent delete
            }

            @Override
            protected void onLoaded(Bundle args, Boolean permanent) {
                if (permanent) {
                    new DialogBuilderLifecycle(context, owner)
                            .setMessage(R.string.title_ask_delete)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Bundle args = new Bundle();
                                    args.putLong("id", message.id);
                                    new SimpleTask<Void>() {
                                        @Override
                                        protected Void onLoad(Context context, Bundle args) {
                                            long id = args.getLong("id");
                                            DB db = DB.getInstance(context);
                                            try {
                                                db.beginTransaction();
                                                EntityMessage em = db.message().getMessage(id);
                                                if (em.uid == null && !TextUtils.isEmpty(em.error)) {
                                                    db.message().deleteMessage(id);
                                                } else {
                                                    db.message().setMessageUiHide(em.id, true);
                                                    EntityOperation.queue(db, em, EntityOperation.DELETE);
                                                }
                                                db.setTransactionSuccessful();
                                            } finally {
                                                db.endTransaction();
                                            }
                                            EntityOperation.process(context);
                                            return null;
                                        }
                                    }.load(context, owner, args);
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                } else {
                    moveWithUndo(message, EntityFolder.TRASH, snackbarView);
                }
            }
        }.load(context, owner, new Bundle());
    }

    private static class MoveTarget {
        long id;
        long targetId;
        String display;
    }

    private void moveWithUndo(final TupleMessageEx tuple, final String targetType, final View snackbarView) {
        Bundle args = new Bundle();
        args.putLong("id", tuple.id);
        args.putString("type", targetType);
        new SimpleTask<MoveTarget>() {
            @Override
            protected MoveTarget onLoad(Context context, Bundle args) {
                long id = args.getLong("id");
                String type = args.getString("type");
                DB db = DB.getInstance(context);
                MoveTarget result = new MoveTarget();
                try {
                    db.beginTransaction();
                    EntityMessage em = db.message().getMessage(id);
                    EntityFolder target = db.folder().getFolderByType(em.account, type);
                    if (target == null) {
                        return null;
                    }
                    db.message().setMessageUiHide(id, true);
                    result.id = id;
                    result.targetId = target.id;
                    result.display = (target.display == null ? target.name : target.display);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                return result;
            }

            @Override
            protected void onLoaded(Bundle args, final MoveTarget target) {
                if (target == null || snackbarView == null) {
                    return;
                }
                final Snackbar snackbar = Snackbar.make(
                        snackbarView,
                        String.format(context.getString(R.string.title_moving),
                                Helper.localizeFolderName(context, target.display)),
                        Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction(R.string.title_undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Bundle a = new Bundle();
                        a.putLong("id", target.id);
                        new SimpleTask<Void>() {
                            @Override
                            protected Void onLoad(Context context, Bundle args) {
                                long id = args.getLong("id");
                                DB.getInstance(context).message().setMessageUiHide(id, false);
                                return null;
                            }
                        }.load(context, owner, a);
                        snackbar.dismiss();
                    }
                });
                snackbar.show();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (snackbar.isShown()) {
                            snackbar.dismiss();
                        }
                        Bundle argsMove = new Bundle();
                        argsMove.putLong("id", target.id);
                        argsMove.putLong("target", target.targetId);
                        new SimpleTask<Void>() {
                            @Override
                            protected Void onLoad(Context context, Bundle args) {
                                long id = args.getLong("id");
                                long targetId = args.getLong("target");
                                DB db = DB.getInstance(context);
                                try {
                                    db.beginTransaction();
                                    EntityMessage em = db.message().getMessage(id);
                                    if (em != null && em.ui_hide) {
                                        EntityOperation.queue(db, em, EntityOperation.MOVE, targetId);
                                    }
                                    db.setTransactionSuccessful();
                                } finally {
                                    db.endTransaction();
                                }
                                EntityOperation.process(context);
                                return null;
                            }
                        }.load(context, owner, argsMove);
                    }
                }, UNDO_TIMEOUT);
            }
        }.load(context, owner, args);
    }

    interface IProperties {
        void setExpanded(long id, boolean expand);

        void setDetails(long id, boolean show);

        void setHeaders(long id, boolean show);

        void setImages(long id, boolean show);

        boolean isExpanded(long id);

        boolean showDetails(long id);

        boolean showHeaders(long id);

        boolean showImages(long id);
    }
}
