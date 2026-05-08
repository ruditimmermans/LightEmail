package org.light.email;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class AdapterEmail extends ArrayAdapter<AdapterEmail.Email> {
    private List<Email> emails = new ArrayList<>();

    public AdapterEmail(@NonNull Context context) {
        super(context, android.R.layout.simple_list_item_2, android.R.id.text1);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        }

        Email email = getItem(position);
        TextView tv1 = convertView.findViewById(android.R.id.text1);
        TextView tv2 = convertView.findViewById(android.R.id.text2);

        if (email != null) {
            tv1.setText(TextUtils.isEmpty(email.name) ? email.address : email.name);
            tv2.setText(TextUtils.isEmpty(email.name) ? null : email.address);
            tv2.setVisibility(TextUtils.isEmpty(email.name) ? View.GONE : View.VISIBLE);
        }

        return convertView;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint != null) {
                    List<Email> filtered = new ArrayList<>();

                    // Local contacts
                    DB db = DB.getInstance(getContext());
                    for (EntityContact contact : db.contact().searchContacts("%" + constraint + "%")) {
                        filtered.add(new Email(contact.name, contact.email));
                    }

                    // System contacts
                    if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
                        == PackageManager.PERMISSION_GRANTED) {
                        Cursor cursor = null;
                        try {
                            cursor = getContext().getContentResolver().query(
                                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                new String[]{
                                    ContactsContract.Contacts.DISPLAY_NAME,
                                    ContactsContract.CommonDataKinds.Email.DATA
                                },
                                ContactsContract.CommonDataKinds.Email.DATA + " <> ''" +
                                    " AND (" + ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?" +
                                    " OR " + ContactsContract.CommonDataKinds.Email.DATA + " LIKE ?)",
                                new String[]{"%" + constraint + "%", "%" + constraint + "%"},
                                ContactsContract.Contacts.DISPLAY_NAME + " COLLATE NOCASE");

                            if (cursor != null) {
                                int colName = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                                int colEmail = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);
                                while (cursor.moveToNext()) {
                                    filtered.add(new Email(cursor.getString(colName), cursor.getString(colEmail)));
                                }
                            }
                        } finally {
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    }

                    results.values = filtered;
                    results.count = filtered.size();
                }
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                emails.clear();
                if (results != null && results.count > 0) {
                    emails.addAll((List<Email>) results.values);
                }
                notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                Email email = (Email) resultValue;
                if (email.name == null) {
                    return email.address;
                } else {
                    return email.name.replace(",", "") + " <" + email.address + ">";
                }
            }
        };
    }

    @Override
    public int getCount() {
        return emails.size();
    }

    @Nullable
    @Override
    public Email getItem(int position) {
        return emails.get(position);
    }

    public static class Email {
        String name;
        String address;

        Email(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }
}
