package org.light.email;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class FragmentContacts extends FragmentEx {
    private TextView tvNoContacts;
    private RecyclerView rvContact;
    private ProgressBar pbWait;
    private Group grpReady;
    private FloatingActionButton fab;

    private AdapterContact adapter;

    @Override
    @Nullable
    public View onCreateView(
        @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);

        tvNoContacts = view.findViewById(R.id.tvNoContacts);
        rvContact = view.findViewById(R.id.rvContact);
        pbWait = view.findViewById(R.id.pbWait);
        grpReady = view.findViewById(R.id.grpReady);
        fab = view.findViewById(R.id.fab);

        rvContact.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        rvContact.setLayoutManager(llm);

        adapter = new AdapterContact(getContext());
        rvContact.setAdapter(adapter);

        fab.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((ActivityView) getActivity()).addFragment(new FragmentContact(), "contact");
                }
            });

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setSubtitle(R.string.title_contacts);

        DB db = DB.getInstance(getContext());
        db.contact()
            .liveContacts()
            .observe(
                getViewLifecycleOwner(),
                new Observer<List<EntityContact>>() {
                    @Override
                    public void onChanged(List<EntityContact> contacts) {
                        if (contacts == null) {
                            contacts = new ArrayList<>();
                        }

                        adapter.set(contacts);

                        pbWait.setVisibility(View.GONE);
                        grpReady.setVisibility(View.VISIBLE);
                        tvNoContacts.setVisibility(contacts.size() == 0 ? View.VISIBLE : View.GONE);
                    }
                });
    }
}
