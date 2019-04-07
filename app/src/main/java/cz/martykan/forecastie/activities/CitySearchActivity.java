package cz.martykan.forecastie.activities;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Objects;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.adapters.LocationsRecyclerAdapter;
import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.LiveResponse;

public class CitySearchActivity extends BaseActivity implements SearchView.OnQueryTextListener {

    public static final int ACTIVITY_RESULT_CITY_SELECTED = 1;

    CityRepository cityRepository;
    private LocationsRecyclerAdapter recyclerAdapter;
    private SearchView searchView;
    private RecyclerView citiesRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_city_search);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);

        configureSearchView();

        cityRepository = new CityRepository(AppDatabase.getDatabase(this).cityDao(), this);
        recyclerAdapter = new LocationsRecyclerAdapter(null, this, darkTheme, blackTheme);

        recyclerAdapter.setLocationSelectedListener(weather -> {
            Intent intent = new Intent();
            setResult(ACTIVITY_RESULT_CITY_SELECTED, intent);
            intent.putExtra("weather", weather);
            finish();
        });

        citiesRecyclerView = findViewById(R.id.citiesRecyclerView);
        citiesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        citiesRecyclerView.setAdapter(recyclerAdapter);
    }

    private void configureSearchView() {
        // TODO: Is SearchView the best alternative? I think we need to use way too many workarounds
        searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(this);

        LinearLayout searchEditFrame = searchView.findViewById(R.id.search_edit_frame);
        ((LinearLayout.LayoutParams) searchEditFrame.getLayoutParams()).leftMargin = 0;

        ImageView searchViewIcon = searchView.findViewById(android.support.v7.appcompat.R.id.search_mag_icon);
        ViewGroup linearLayoutViewGroup = (ViewGroup) searchViewIcon.getParent();
        linearLayoutViewGroup.removeView(searchViewIcon);

        EditText searchEditText = searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        searchEditText.setPadding(0, searchEditText.getPaddingTop(), 0, searchEditText.getPaddingBottom());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(Activity.RESULT_CANCELED);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public boolean onQueryTextSubmit(String query) {
        Log.i("CitySearch", String.format("User searched for \"%s\"", query));
        LiveResponse<List<Weather>> weatherLiveResponse = cityRepository.searchCity(query);
        weatherLiveResponse.getLiveData().observe(this, weathers -> {
            assert weathers != null;
            Log.i("CitySearch", String.format("Response for search for \"%s\" arrived, length: %d", query, weathers.size()));

            recyclerAdapter.replaceList(weathers);
            citiesRecyclerView.scrollToPosition(0);
        });

        searchView.clearFocus();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }
}
