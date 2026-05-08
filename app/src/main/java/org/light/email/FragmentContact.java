package org.light.email;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;

public class FragmentContact extends FragmentEx {
    private EditText etName;
    private EditText etEmail;
    private Button btnSave;
    private ImageButton ibDelete;
    private ProgressBar pbWait;

    private long id = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            id = getArguments().getLong("id", -1);
        }
    }

    @Override
    @Nullable
    public View onCreateView(
        @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contact, container, false);

        etName = view.findViewById(R.id.etName);
        etEmail = view.findViewById(R.id.etEmail);
        btnSave = view.findViewById(R.id.btnSave);
        ibDelete = view.findViewById(R.id.ibDelete);
        pbWait = view.findViewById(R.id.pbWait);

        pbWait.setVisibility(View.GONE);
        ibDelete.setVisibility(id == -1 ? View.GONE : View.VISIBLE);

        btnSave.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onActionSave();
                }
            });

        ibDelete.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onActionDelete();
                }
            });

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setSubtitle(id == -1 ? R.string.title_contact : R.string.title_contact); // Could be more specific

        if (id != -1) {
            new SimpleTask<EntityContact>() {
                @Override
                protected EntityContact onLoad(Context context, Bundle args) {
                    return DB.getInstance(context).contact().getContact(id);
                }

                @Override
                protected void onLoaded(Bundle args, EntityContact contact) {
                    if (contact != null) {
                        etName.setText(contact.name);
                        etEmail.setText(contact.email);
                    }
                }
            }.load(this, new Bundle());
        }
    }

    private void onActionSave() {
        final String name = etName.getText().toString().trim();
        final String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {
            return;
        }

        btnSave.setEnabled(false);
        pbWait.setVisibility(View.VISIBLE);

        new SimpleTask<Void>() {
            @Override
            protected Void onLoad(Context context, Bundle args) {
                DB db = DB.getInstance(context);
                EntityContact contact = (id == -1 ? new EntityContact() : db.contact().getContact(id));
                contact.name = name;
                contact.email = email;

                if (id == -1) {
                    db.contact().insertContact(contact);
                } else {
                    db.contact().updateContact(contact);
                }
                return null;
            }

            @Override
            protected void onLoaded(Bundle args, Void result) {
                finish();
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                btnSave.setEnabled(true);
                pbWait.setVisibility(View.GONE);
                Toast.makeText(getContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }.load(this, new Bundle());
    }

    private void onActionDelete() {
        new SimpleTask<Void>() {
            @Override
            protected Void onLoad(Context context, Bundle args) {
                DB.getInstance(context).contact().deleteContact(id);
                return null;
            }

            @Override
            protected void onLoaded(Bundle args, Void result) {
                finish();
            }
        }.load(this, new Bundle());
    }
}
