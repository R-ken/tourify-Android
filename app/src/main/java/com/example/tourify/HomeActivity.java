package com.example.tourify;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 123;
    FirebaseUser currentUser;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    List<TourifyEvent> tourifyEvents;

    //recycler view
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    private Button buttonSignout;

    List<AuthUI.IdpConfig> providers;
    CollectionReference eventsCollectionRef;
    ListenerRegistration eventsUpdatesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tourifyEvents = new ArrayList<>();

        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(mLayoutManager);

        buttonSignout = findViewById(R.id.buttonSignout);

        // Choose authentication providers
        providers = Arrays.asList(
                new AuthUI.IdpConfig.GoogleBuilder().build()
        );
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        if (auth.getCurrentUser() != null) {
            eventsCollectionRef = db.collection("events");
            eventsUpdatesListener = eventsCollectionRef.addSnapshotListener(new MyEventListener());
        }

        buttonSignout.setOnClickListener(new SignOutEventListener());
        startSignOnActivity(providers);
    }

    private class SignOutEventListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if (auth.getCurrentUser() == null) {
                startSignOnActivity(providers);
                buttonSignout.setText("SignOut");
            } else {
                signOut();
                buttonSignout.setText("SignIn");
            }

        }
    }

    private class MyEventListener implements EventListener<QuerySnapshot> {

        @Override
        public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
            List<TourifyEvent> updatedEvents = new ArrayList<>();

            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                Map<String, Object> data = doc.getData();

                if (!data.containsKey("start_time")
                        || !data.containsKey("end_time")
                        || !data.containsKey("event_name")) {
                    return;
                }

                Timestamp startTime = (Timestamp) data.get("start_time");
                Timestamp endTime = (Timestamp) data.get("end_time");

                TourifyEvent tourifyEvent = new TourifyEvent(doc.getId(), (String) data.get("event_name"), startTime.getSeconds(), endTime.getSeconds());
                updatedEvents.add(tourifyEvent);
            }
            tourifyEvents = updatedEvents;

            mAdapter = new MyAdapter(tourifyEvents, getApplicationContext());
            mRecyclerView.setAdapter(mAdapter);
            mAdapter.notifyDataSetChanged();
        }
    }

    public void signOut() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        eventsUpdatesListener.remove();
                        tourifyEvents.clear();
                        mAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void startSignOnActivity(List<AuthUI.IdpConfig> providers) {
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build(),
                RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                // Successfully signed in
                eventsCollectionRef = db.collection("events");
                eventsUpdatesListener = eventsCollectionRef.addSnapshotListener(new MyEventListener());
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
            }
        }
    }

    private class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {

        private List<TourifyEvent> mTourifyEvents;
        Context mContext;

        public MyAdapter(List<TourifyEvent> tourifyEvents, Context context) {
            mTourifyEvents = tourifyEvents;
            mContext = context;
        }

        @NonNull
        @Override
        public MyAdapter.MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // inflate the layout with a custom event_item layout.
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.event_item, parent, false);
            MyViewHolder viewHolder = new MyViewHolder(v);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull MyAdapter.MyViewHolder holder, int position) {
            TourifyEvent currentEvent = mTourifyEvents.get(position);

            holder.textViewRideName.setText(currentEvent.getEventName());
        }

        @Override
        public int getItemCount() {
            return mTourifyEvents.size();
        }

        public class MyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView textViewRideName;
            Context context;

            public MyViewHolder(View itemView) {
                super(itemView);
                itemView.findViewById(R.id.rideItem);

                //get references to the views
                textViewRideName = itemView.findViewById(R.id.textViewRideName);
                context = itemView.getContext();
                itemView.setOnClickListener(this);
            }

            @Override
            public void onClick(View view) {
                Intent i = new Intent(context, RecordingActivity.class);
                int position = getAdapterPosition();
                TourifyEvent tourifyEvent = MyAdapter.this.mTourifyEvents.get(position);

                i.putExtra(Constants.EVENT_ID, tourifyEvent.getId());
                i.putExtra(Constants.EVENT_NAME, tourifyEvent.getEventName());
                i.putExtra(Constants.START_TIME, tourifyEvent.getStartTime());
                i.putExtra(Constants.END_TIME, tourifyEvent.getEndTime());

                context.startActivity(i);
            }
        }
    }

}
