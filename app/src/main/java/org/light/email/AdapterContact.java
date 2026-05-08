package org.light.email;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AdapterContact extends RecyclerView.Adapter<AdapterContact.ViewHolder> {
    private Context context;
    private List<EntityContact> contacts = new ArrayList<>();

    public AdapterContact(Context context) {
        this.context = context;
    }

    public void set(List<EntityContact> contacts) {
        DiffUtil.DiffResult result =
            DiffUtil.calculateDiff(
                new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return AdapterContact.this.contacts.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return contacts.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return AdapterContact.this.contacts.get(oldItemPosition).id.equals(contacts.get(newItemPosition).id);
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        return AdapterContact.this.contacts.get(oldItemPosition).equals(contacts.get(newItemPosition));
                    }
                });

        this.contacts.clear();
        this.contacts.addAll(contacts);
        result.dispatchUpdatesTo(this);
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_contact, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(contacts.get(position));
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView tvName;
        private TextView tvEmail;
        private EntityContact contact;

        public ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            itemView.setOnClickListener(this);
        }

        public void bind(EntityContact contact) {
            this.contact = contact;
            tvName.setText(contact.name);
            tvEmail.setText(contact.email);
        }

        @Override
        public void onClick(View v) {
            Bundle args = new Bundle();
            args.putLong("id", contact.id);

            FragmentContact fragment = new FragmentContact();
            fragment.setArguments(args);

            ((ActivityView) context).addFragment(fragment, "contact");
        }
    }
}
