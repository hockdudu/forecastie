package cz.martykan.forecastie.activities;

import android.Manifest;
import android.app.ProgressDialog;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.Serializable;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import cz.martykan.forecastie.AlarmReceiver;
import cz.martykan.forecastie.Constants;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.adapters.ViewPagerAdapter;
import cz.martykan.forecastie.adapters.WeatherRecyclerAdapter;
import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.database.WeatherRepository;
import cz.martykan.forecastie.fragments.AboutDialogFragment;
import cz.martykan.forecastie.fragments.RecyclerViewFragment;
import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Response;
import cz.martykan.forecastie.utils.UI;
import cz.martykan.forecastie.utils.UnitConverter;

public class MainActivity extends BaseActivity implements LocationListener {
    protected static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1;

    // Time in milliseconds; only reload weather if last update is longer ago than this value
    private static final int NO_UPDATE_REQUIRED_THRESHOLD = 300000;

    private static final int ACTIVITY_REQUEST_CITY_SELECTED = 1;

    @Nullable
    private Weather todayWeather;
    private List<Weather> forecast = Collections.emptyList();

    private Date tomorrow;
    private Date later;

    private TextView todayTemperature;
    private TextView todayDescription;
    private TextView todayWind;
    private TextView todayPressure;
    private TextView todayHumidity;
    private TextView todaySunrise;
    private TextView todaySunset;
    private TextView todayUvIndex;
    private TextView lastUpdate;
    private TextView todayIcon;
    private ViewPager viewPager;
    private TabLayout tabLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout sidebarDrawer;
    private DrawerLayout drawerLayout;

    private View appView;

    Typeface weatherFont;

    private LocationManager locationManager;
    private ProgressDialog progressDialog;

    private boolean widgetTransparent;
    private boolean destroyed = false;

    public int recentCityId;

    private AppDatabase appDatabase;
    private WeatherRepository weatherRepository;
    private CityRepository cityRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize the associated SharedPreferences file with default values
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);

        super.onCreate(savedInstanceState);

        widgetTransparent = prefs.getBoolean("transparentWidget", false);

        setContentView(R.layout.activity_scrolling);
        appView = findViewById(R.id.viewApp);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);

        // TODO: IT HURTS, PLEASE USE SOMETHING ELSE, ANYTHING BUT THIS
        progressDialog = new ProgressDialog(MainActivity.this);

        // Load toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);

        if (darkTheme) {
            toolbar.setPopupTheme(R.style.AppTheme_PopupOverlay_Dark);
        } else if (blackTheme) {
            toolbar.setPopupTheme(R.style.AppTheme_PopupOverlay_Black);
        }

        drawerLayout = findViewById(R.id.drawerLayout);

        // Saves an instance of the database
        appDatabase = AppDatabase.getDatabase(this);
        weatherRepository = new WeatherRepository(appDatabase.weatherDao(), appDatabase.cityDao(), this);
        cityRepository = new CityRepository(appDatabase.cityDao(), this);

        // Initialize textboxes
        todayTemperature = findViewById(R.id.todayTemperature);
        todayDescription = findViewById(R.id.todayDescription);
        todayWind = findViewById(R.id.todayWind);
        todayPressure = findViewById(R.id.todayPressure);
        todayHumidity = findViewById(R.id.todayHumidity);
        todaySunrise = findViewById(R.id.todaySunrise);
        todaySunset = findViewById(R.id.todaySunset);
        todayUvIndex = findViewById(R.id.todayUvIndex);
        lastUpdate = findViewById(R.id.lastUpdate);
        todayIcon = findViewById(R.id.todayIcon);
        weatherFont = Typeface.createFromAsset(this.getAssets(), "fonts/weather.ttf");
        todayIcon.setTypeface(weatherFont);

        // Initialize viewPager
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabs);

        destroyed = false;
        recentCityId = prefs.getInt("cityId", Constants.DEFAULT_CITY_ID);

        // Preload data from cache
        refreshWeather();

        // Initializes drawer
        LinearLayout drawerContainer = findViewById(R.id.drawerContainer);
        sidebarDrawer = (LinearLayout) getLayoutInflater().inflate(R.layout.fragment_drawer, drawerContainer);
        initializeDrawer();

        // Set autoupdater
        AlarmReceiver.setRecurringAlarm(this);

        swipeRefreshLayout.setOnRefreshListener(this::downloadWeather);

        appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
            // Only allow pull to refresh when scrolled to top
            swipeRefreshLayout.setEnabled(verticalOffset == 0);
        });

        Bundle bundle = getIntent().getExtras();

        if (bundle != null && bundle.getBoolean("shouldRefresh")) {
            refreshWeather();
        }
    }

    public WeatherRecyclerAdapter getAdapter(int id) {
        WeatherRecyclerAdapter weatherRecyclerAdapter;
        if (id == 0) {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, getForecastToday());
        } else if (id == 1) {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, getForecastTomorrow());
        } else {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, getForecastLater());
        }
        return weatherRecyclerAdapter;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeatherUI();
        updateForecastUI();

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        this.tomorrow = tomorrow.getTime();

        Calendar later = (Calendar) today.clone();
        later.add(Calendar.DAY_OF_YEAR, 2);
        this.later = later.getTime();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (UI.getTheme(prefs.getString("theme", "fresh")) != theme || prefs.getBoolean("transparentWidget", false) != widgetTransparent) {
            // Restart activity to apply theme
            overridePendingTransition(0, 0);
            finish();
            overridePendingTransition(0, 0);
            startActivity(getIntent());
        } else if (shouldUpdate()) {
            refreshWeather();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;

        if (locationManager != null) {
            try {
                locationManager.removeUpdates(MainActivity.this);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case ACTIVITY_REQUEST_CITY_SELECTED:
                switch (resultCode) {
                    case CitySearchActivity.ACTIVITY_RESULT_CITY_SELECTED:
                        Handler handler = new Handler();

                        Runnable runnable = () -> {
                            Weather weather = (Weather) data.getSerializableExtra("weather");
                            handler.post(() -> {
                                saveLocation(weather.getCity(), () -> {
                                    refreshDrawer();
                                    refreshWeather();
                                });
                            });
                        };

                        new Thread(runnable).start();

                        break;
                }
                break;
        }
    }

    private void initializeDrawer() {
        View bottomButtons = sidebarDrawer.findViewById(R.id.bottomButtons);
        bottomButtons.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Intent intent = new Intent(MainActivity.this, CitySearchActivity.class);
            startActivityForResult(intent, ACTIVITY_REQUEST_CITY_SELECTED);
        });

        ImageView searchIcon = sidebarDrawer.findViewById(R.id.searchIcon);
        TextView addLocationText = sidebarDrawer.findViewById(R.id.addLocation);
        // TODO: Is there any other way of getting this color?
        searchIcon.setColorFilter(addLocationText.getCurrentTextColor(), PorterDuff.Mode.SRC_IN);

        refreshDrawer();
    }

    private void refreshDrawer() {
        LiveResponse<List<City>> citiesLiveData = cityRepository.getCities();
        citiesLiveData.getLiveData().observe(this, cities -> {
            assert cities != null;

            // TODO: Add "current location" support
            LinearLayout citiesList = sidebarDrawer.findViewById(R.id.citiesList);
            citiesList.removeAllViews();
            Collections.sort(cities, (o1, o2) -> o1.getCity().compareTo(o2.getCity()));
            for (int i = 0; i < cities.size(); i++) {
                City city = cities.get(i);
                ConstraintLayout drawerItem = (ConstraintLayout) getLayoutInflater().inflate(R.layout.fragment_drawer_item, citiesList, false);
                citiesList.addView(drawerItem);

                TextView weatherIcon = drawerItem.findViewById(R.id.weatherIcon);
                weatherIcon.setTypeface(weatherFont);

                TextView cityName = drawerItem.findViewById(R.id.cityName);
                cityName.setText(city.toString());

                ImageView removeButton = drawerItem.findViewById(R.id.removeLocationIcon);
                // TODO: Is there any other way of getting this color?
                removeButton.setColorFilter(weatherIcon.getCurrentTextColor(), PorterDuff.Mode.SRC_IN);
                removeButton.setOnClickListener(removeButtonView -> {
                    drawerLayout.closeDrawers();

                    Handler handler = new Handler();

                    // TODO: You shouldn't access DAOs directly, use repositories for that
                    // TODO: Select other city when current city is deleted
                    Runnable runnable = () -> {
                        Log.i("CityDeletion", String.format("City deleted: %s (%d)", city, city.getId()));

                        Weather cityWeather;
                        List<Weather> cityForecast;
                        if (city.getCurrentWeatherId() != null) {
                            cityWeather = appDatabase.weatherDao().findByUid(city.getCurrentWeatherId());
                            cityForecast = appDatabase.weatherDao().findForecast(city.getId(), city.getCurrentWeatherId());
                        } else {
                            cityWeather = null;
                            cityForecast = appDatabase.weatherDao().findForecast(city.getId(), 0);
                        }

                        cityRepository.deleteCity(city);

                        Snackbar snackbar = Snackbar.make(appView, R.string.city_deleted, Snackbar.LENGTH_LONG);
                        snackbar.setAction(R.string.undo, snackView -> {
                            // This action is called on the UI, so we need another runnable :P
                            Runnable runnable1 = () -> {
                                appDatabase.runInTransaction(() -> {
                                    city.setCurrentWeatherId(null);
                                    cityRepository.addCity(city);

                                    if (cityWeather != null) {
                                        appDatabase.weatherDao().insertAll(cityWeather);
                                        city.setCurrentWeatherId(cityWeather.getUid());
                                        appDatabase.cityDao().persist(city);
                                    }

                                    appDatabase.weatherDao().insertAll(cityForecast.toArray(new Weather[0]));

                                    handler.post(this::refreshDrawer);
                                });
                            };

                            new Thread(runnable1).start();
                        });

                        handler.post(() -> {
                            refreshDrawer();
                            snackbar.show();
                        });

                    };

                    new Thread(runnable).start();
                });

                TextView temperature = drawerItem.findViewById(R.id.temperature);

                // TODO: Use bulk download, https://openweathermap.org/current#severalid
                LiveResponse<Weather> weatherLiveData = weatherRepository.getCurrentWeather(city, false);

                weatherLiveData.getLiveData().observe(this, weather -> {
                    handleConnectionStatus(weatherLiveData.getStatus());

                    if (weather != null) {
                        weatherIcon.setText(weather.getIcon());
                        temperature.setText(getString(R.string.format_temperature, UnitConverter.convertTemperature(weather.getTemperature(), prefs), prefs.getString("unit", "°C")));
                    }
                });

                drawerItem.setOnClickListener(v -> {
                    drawerLayout.closeDrawers();
                    selectLocation(city);
                    refreshWeather();
                });
            }
        });
    }

    private void updateTodayWeather(boolean forceDownload, Runnable onFinish) {
        LiveResponse<City> currentCity = cityRepository.getCity(recentCityId);

        currentCity.getLiveData().observe(this, city -> {
            if (handleConnectionStatus(currentCity.getStatus())) {
                LiveResponse<Weather> weatherLiveData = weatherRepository.getCurrentWeather(city, forceDownload);
                weatherLiveData.getLiveData().observe(this, weather -> {
                    handleConnectionStatus(weatherLiveData.getStatus());

                    if (weather != null) {
                        todayWeather = weather;
                        updateWeatherUI();
                    }

                    onFinish.run();
                });
            } else {
                onFinish.run();
            }
        });
    }

    private void updateForecast(boolean forceDownload, Runnable onFinish) {
        LiveResponse<City> currentCity = cityRepository.getCity(recentCityId);
        currentCity.getLiveData().observe(this, city -> {
            if (handleConnectionStatus(currentCity.getStatus())) {
                LiveResponse<List<Weather>> forecastLiveData = weatherRepository.getWeatherForecast(city, forceDownload);

                forecastLiveData.getLiveData().observe(this, weathers -> {
                    assert weathers != null;

                    forecast = weathers;
                    handleConnectionStatus(forecastLiveData.getStatus());

                    updateForecastUI();

                    onFinish.run();
                });
            } else {
                onFinish.run();
            }

        });
    }

    private void selectLocation(City city) {
        recentCityId = city.getId();

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("cityId", city.getId());
        editor.apply();
    }

    private void saveLocation(City city) {
        saveLocation(city, null);
    }

    private void saveLocation(City city, Runnable onFinish) {
        selectLocation(city);

        Handler handler = new Handler();
        Runnable runnable = () -> {
            cityRepository.addCity(city);

            handler.post(onFinish);
        };

        new Thread(runnable).start();
    }

    private void aboutDialog() {
        new AboutDialogFragment().show(getSupportFragmentManager(), null);
    }

    // TODO: Check why this is called twice
    private void updateWeatherUI() {
        if (todayWeather == null) { // If it wasn't initialized yet
            refreshWeather();
            return;
        }

        // TODO: Verify why this isn't used now resp. how it was used before
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getApplicationContext());
        // TODO: Fix inspection
        //noinspection ConstantConditions
        getSupportActionBar().setTitle(todayWeather.getCity().toString());

        // Temperature
        double temperature = UnitConverter.convertTemperature(todayWeather.getTemperature(), prefs);
        if (prefs.getBoolean("temperatureInteger", false)) {
            temperature = Math.round(temperature);
        }

        // Rain
        double rain = Double.parseDouble(todayWeather.getRain());
        String rainString = UnitConverter.getRainString(rain, prefs);

        // Wind
        double wind;
        try {
            wind = Double.parseDouble(todayWeather.getWind());
        } catch (Exception e) {
            e.printStackTrace();
            wind = 0;
        }
        wind = UnitConverter.convertWind(wind, prefs);

        // Pressure
        double pressure = UnitConverter.convertPressure(todayWeather.getPressure(), prefs);

        todayTemperature.setText(getString(R.string.format_temperature, temperature, prefs.getString("unit", "°C")));
        todayDescription.setText(rainString.length() > 0 ? getString(R.string.format_description_with_rain, Formatting.capitalize(todayWeather.getDescription()), rainString) : Formatting.capitalize(todayWeather.getDescription()));
        if (prefs.getString("speedUnit", "m/s").equals("bft")) {
            todayWind.setText(getString(R.string.format_wind_beaufort, UnitConverter.getBeaufortName((int) wind),
                    todayWeather.isWindDirectionAvailable() ? Formatting.getWindDirectionString(prefs, this, todayWeather) : "")
            );
        } else {
            todayWind.setText(getString(R.string.format_wind, wind,
                    Formatting.localize(prefs, this, "speedUnit", "m/s"),
                    todayWeather.isWindDirectionAvailable() ? Formatting.getWindDirectionString(prefs, this, todayWeather) : "")
            );
        }
        todayPressure.setText(getString(R.string.format_pressure, pressure, Formatting.localize(prefs, this, "pressureUnit", "hPa")));
        todayHumidity.setText(getString(R.string.format_humidity,todayWeather.getHumidity()));
        todaySunrise.setText(getString(R.string.format_sunrise, todayWeather.getSunrise()));
        todaySunset.setText(getString(R.string.format_sunset, todayWeather.getSunset()));
        todayIcon.setText(todayWeather.getIcon());
        todayUvIndex.setText(getString(R.string.format_uv_index, UnitConverter.convertUvIndexToRiskLevel(todayWeather.getUvIndex())));
        lastUpdate.setText(getString(R.string.last_update, Formatting.formatTimeWithDayIfNotToday(this, todayWeather.getLastUpdated())));

        todayIcon.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, GraphActivity.class);
            intent.putExtra(GraphActivity.EXTRA_CITY, (Serializable) forecast);
            startActivity(intent);
        });
    }

    private void updateForecastUI() {
        if (destroyed) {
            return;
        }

        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());

        Bundle bundleToday = new Bundle();
        bundleToday.putInt("day", 0);
        RecyclerViewFragment recyclerViewFragmentToday = new RecyclerViewFragment();
        recyclerViewFragmentToday.setArguments(bundleToday);
        viewPagerAdapter.addFragment(recyclerViewFragmentToday, getString(R.string.today));

        Bundle bundleTomorrow = new Bundle();
        bundleTomorrow.putInt("day", 1);
        RecyclerViewFragment recyclerViewFragmentTomorrow = new RecyclerViewFragment();
        recyclerViewFragmentTomorrow.setArguments(bundleTomorrow);
        viewPagerAdapter.addFragment(recyclerViewFragmentTomorrow, getString(R.string.tomorrow));

        Bundle bundle = new Bundle();
        bundle.putInt("day", 2);
        RecyclerViewFragment recyclerViewFragment = new RecyclerViewFragment();
        recyclerViewFragment.setArguments(bundle);
        viewPagerAdapter.addFragment(recyclerViewFragment, getString(R.string.later));

        int currentPage = viewPager.getCurrentItem();

        viewPagerAdapter.notifyDataSetChanged();
        viewPager.setAdapter(viewPagerAdapter);
        tabLayout.setupWithViewPager(viewPager);

        if (currentPage == 0 && getForecastToday().isEmpty() && !getForecastTomorrow().isEmpty()) {
            currentPage = 1;
        }
        viewPager.setCurrentItem(currentPage, false);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private boolean shouldUpdate() {
        long lastUpdate = -1;
        if (todayWeather != null) {
            lastUpdate = todayWeather.getLastUpdated();
        }
        boolean cityChanged = prefs.getBoolean("cityChanged", false);
        // Update if never checked or las"t update is longer ago than specified threshold
        return cityChanged || lastUpdate < 0 || (Calendar.getInstance().getTimeInMillis() - lastUpdate) > NO_UPDATE_REQUIRED_THRESHOLD;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
            case R.id.action_refresh:
                downloadWeather();
                return true;
            case R.id.action_map: {
                Intent intent = new Intent(MainActivity.this, MapActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_graphs: {
                Intent intent = new Intent(MainActivity.this, GraphActivity.class);
                intent.putExtra(GraphActivity.EXTRA_CITY, (Serializable) forecast);
                startActivity(intent);
                return true;
            }
            case R.id.action_location:
                getCityByLocation();
                return true;
            case R.id.action_settings: {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_about:
                aboutDialog();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void refreshWeather() {
        refreshWeather(false);
    }

    public void refreshWeather(boolean forceDownload) {
        swipeRefreshLayout.setRefreshing(true);
        MutableLiveData<Integer> finishCount = new MutableLiveData<>();
        Runnable updateFinishCountValue = () -> finishCount.setValue((finishCount.getValue() != null ? finishCount.getValue() : 0) + 1);

        updateTodayWeather(forceDownload, updateFinishCountValue);
        updateForecast(forceDownload, updateFinishCountValue);

        finishCount.observe(this, value -> {
            if (value != null && value == 2) swipeRefreshLayout.setRefreshing(false);
        });
    }

    public void downloadWeather() {
        if (isNetworkAvailable()) {
            refreshWeather(true);
        } else {
            Snackbar.make(appView, getString(R.string.msg_connection_not_available), Snackbar.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    void getCityByLocation() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Explanation not needed, since user requests this themmself

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_ACCESS_FINE_LOCATION);
            }

        } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(getString(R.string.getting_location));
            progressDialog.setCancelable(false);
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), (dialogInterface, i) -> {
                try {
                    locationManager.removeUpdates(MainActivity.this);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            });
            progressDialog.show();
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            }
        } else {
            showLocationSettingsDialog();
        }
    }

    private void showLocationSettingsDialog() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.location_settings);
        alertDialog.setMessage(R.string.location_settings_message);
        alertDialog.setPositiveButton(R.string.location_settings_button, (dialog, which) -> {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        });
        alertDialog.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.cancel());
        alertDialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCityByLocation();
                }
                break;
            }
        }
    }

    // TODO: Plan a way of deleting old cities
    // TODO: Don't change the location in view if it isn't a live location
    @Override
    public void onLocationChanged(Location location) {
        progressDialog.hide();
        try {
            locationManager.removeUpdates(this);
        } catch (SecurityException e) {
            Log.e("LocationManager", "Error while trying to stop listening for location updates. This is probably a permissions issue", e);
        }
        Log.i(String.format("LOCATION (%s)", location.getProvider().toUpperCase()), String.format("%s, %s", location.getLatitude(), location.getLongitude()));

        LiveResponse<City> cityLiveData = cityRepository.findCity(location);

        cityLiveData.getLiveData().observe(this, city -> {
            assert city != null;
            saveLocation(city);
        });
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private List<Weather> getForecastToday() {
        ArrayList<Weather> weathers = new ArrayList<>();

        for (Weather weather : forecast) {
            if (weather.getDate().before(tomorrow)) {
                weathers.add(weather);
            }
        }

        return weathers;
    }

    private List<Weather> getForecastTomorrow() {
        ArrayList<Weather> weathers = new ArrayList<>();

        for (Weather weather : forecast) {
            // The order is inverted for performance reasons, although it really might be negligible
            // Natural order would be .equals(tomorrow) || .after(tomorrow) && .before(later)
            if (weather.getDate().before(later) && weather.getDate().after(tomorrow) || weather.getDate().equals(tomorrow)) {
                weathers.add(weather);
            }
        }

        return weathers;
    }

    private List<Weather> getForecastLater() {
        ArrayList<Weather> weathers = new ArrayList<>();

        for (Weather weather : forecast) {
            if (weather.getDate().after(later) || weather.getDate().equals(later)) {
                weathers.add(weather);
            }
        }

        return weathers;
    }

    private boolean handleConnectionStatus(Response.Status status) {
        switch (status) {
            case SUCCESS:
                return true;
            case IO_EXCEPTION:
                if (!isNetworkAvailable()) {
                    Snackbar.make(appView, R.string.msg_connection_not_available, Snackbar.LENGTH_LONG).show();
                    return false;
                }
            default:
                Snackbar.make(appView, status.toString(), Snackbar.LENGTH_LONG).show();
                return false;
        }
    }
}
