package cz.martykan.forecastie.activities;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import java.util.Collections;
import java.util.List;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.widgets.AbstractWidgetProvider;

public class WidgetConfigureActivity extends AppCompatActivity {

    protected int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    protected CityRepository cityRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_configure);
        setResult(RESULT_CANCELED);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (!isActivityValid()) {
            finish();
            return;
        }

        cityRepository = new CityRepository(AppDatabase.getDatabase(this).cityDao(), this);

        Spinner citySpinner = findViewById(R.id.citySpinner);
        Button okButton = findViewById(R.id.buttonOk);
        Button cancelButton = findViewById(R.id.buttonCancel);

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);

        CityRepository cityRepository = new CityRepository(AppDatabase.getDatabase(this).cityDao(), this);
        LiveResponse<List<City>> citiesLiveResponse = cityRepository.getCities();
        citiesLiveResponse.getLiveData().observe(this, cities -> {
            assert cities != null;

            String[] cityNames = new String[cities.size()];
            int[] cityIds = new int[cities.size()];

            Collections.sort(cities, (o1, o2) -> o1.getCity().compareTo(o2.getCity()));

            // TODO: Add button to open activity if there's no city, or something like that
            for (int i = 0; i < cities.size(); i++) {
                cityNames[i] = cities.get(i).toString();
                cityIds[i] = cities.get(i).getId();
            }

            arrayAdapter.addAll(cityNames);
            citySpinner.setSelection(getCityIndex(cityIds));
        });

        citySpinner.setAdapter(arrayAdapter);

        okButton.setOnClickListener(v -> {
            int selectedCityIndex = citySpinner.getSelectedItemPosition();
            List<City> cities = citiesLiveResponse.getLiveData().getValue();

            if (cities != null) {
                if (selectedCityIndex != AdapterView.INVALID_POSITION) {
                    City selectedCity = cities.get(selectedCityIndex);
                    selectedCity.setCityUsage(selectedCity.getCityUsage() | City.USAGE_WIDGET);

                    onWidgetConfigured(selectedCity);

                    Runnable runnable = () -> cityRepository.persistCity(selectedCity);

                    new Thread(runnable).start();
                }
                // TODO: What to do if there's no city (it's empty)?
            }
            // TODO: What to do if it is null?
        });

        cancelButton.setOnClickListener(v -> finish());
    }

    protected void onWidgetConfigured(City city) {
        AbstractWidgetProvider.saveCityId(this, appWidgetId, city.getId());

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        AppWidgetProviderInfo appWidgetProviderInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
        String className = appWidgetProviderInfo.provider.getClassName();

        try {
            Class widgetClass = Class.forName(className);

            Intent broadcastIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, widgetClass);
            broadcastIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
//            broadcastIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            sendBroadcast(broadcastIntent);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    protected boolean isActivityValid() {
        return appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID;
    }

    protected int getCityIndex(int[] cityIds) {
        return 0;
    }
}
