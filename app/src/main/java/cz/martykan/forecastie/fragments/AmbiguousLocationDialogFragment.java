package cz.martykan.forecastie.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.activities.MainActivity;
import cz.martykan.forecastie.adapters.LocationsRecyclerAdapter;
import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.UnitConvertor;

public class AmbiguousLocationDialogFragment extends DialogFragment implements LocationsRecyclerAdapter.ItemClickListener {

    private LocationsRecyclerAdapter recyclerAdapter;
    private SharedPreferences sharedPreferences;
    private CityRepository cityRepository;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dialog_ambiguous_location, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Bundle bundle = getArguments();
        final Toolbar toolbar = view.findViewById(R.id.dialogToolbar);
        final RecyclerView recyclerView = view.findViewById(R.id.locationsRecyclerView);
        final LinearLayout linearLayout = view.findViewById(R.id.locationsLinearLayout);

        toolbar.setTitle("Locations");

        toolbar.setNavigationIcon(R.drawable.ic_close_black_24dp);
        toolbar.setNavigationOnClickListener(view1 -> getActivity().getSupportFragmentManager().popBackStack());

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        final int theme = getTheme(sharedPreferences.getString("theme", "fresh"));
        final boolean darkTheme = theme == R.style.AppTheme_NoActionBar_Dark ||
                theme == R.style.AppTheme_NoActionBar_Classic_Dark;
        final boolean blackTheme = theme == R.style.AppTheme_NoActionBar_Black ||
                theme == R.style.AppTheme_NoActionBar_Classic_Black;

        if (darkTheme) {
            linearLayout.setBackgroundColor(Color.parseColor("#2f2f2f"));
        }

        if (blackTheme) {
            linearLayout.setBackgroundColor(Color.BLACK);
        }

        @SuppressWarnings({"unchecked", "ConstantConditions"}) @NonNull
        final List<Weather> weathers = (List<Weather>) bundle.getSerializable("cityList");
        recyclerAdapter = new LocationsRecyclerAdapter(getActivity().getApplicationContext(), weathers, darkTheme, blackTheme);

        recyclerAdapter.setClickListener(AmbiguousLocationDialogFragment.this);

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(recyclerAdapter);
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cityRepository = new CityRepository(AppDatabase.getDatabase(getContext()).cityDao(), getContext());
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onItemClickListener(View view, int position) {
        Handler handler = new Handler();
        Runnable runnable = () -> {
            final Weather weather = recyclerAdapter.getItem(position);
            final Intent intent = new Intent(getActivity(), MainActivity.class);
            final Bundle bundle = new Bundle();

            cityRepository.Add(weather.getCity());
            bundle.putBoolean("shouldRefresh", true);
            intent.putExtras(bundle);

            // Probably error
            startActivity(intent);
        };

        new Thread(runnable).start();
    }

    private int getTheme(String themePref) {
        switch (themePref) {
            case "dark":
                return R.style.AppTheme_NoActionBar_Dark;
            case "black":
                return R.style.AppTheme_NoActionBar_Black;
            case "classic":
                return R.style.AppTheme_NoActionBar_Classic;
            case "classicdark":
                return R.style.AppTheme_NoActionBar_Classic_Dark;
            case "classicblack":
                return R.style.AppTheme_NoActionBar_Classic_Black;
            default:
                return R.style.AppTheme_NoActionBar;
        }
    }

}
