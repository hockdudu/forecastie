package cz.martykan.forecastie.fragments;


import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import cz.martykan.forecastie.activities.MainActivity;
import cz.martykan.forecastie.R;

public class RecyclerViewFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setRetainInstance(true);
        Bundle bundle = this.getArguments();
        View view = inflater.inflate(R.layout.fragment_recycler_view, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        MainActivity mainActivity = (MainActivity) getActivity();
        // TODO: I don't like the way this is implemented, please use dependency injection
        recyclerView.setAdapter(mainActivity.getAdapter(bundle.getInt("day")));
        return view;
    }
}
