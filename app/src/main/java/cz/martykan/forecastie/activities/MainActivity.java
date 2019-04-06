package cz.martykan.forecastie.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.arch.lifecycle.LiveData;
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
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.Serializable;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cz.martykan.forecastie.AlarmReceiver;
import cz.martykan.forecastie.Constants;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.adapters.ViewPagerAdapter;
import cz.martykan.forecastie.adapters.WeatherRecyclerAdapter;
import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityDao;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.database.WeatherRepository;
import cz.martykan.forecastie.fragments.AboutDialogFragment;
import cz.martykan.forecastie.fragments.AmbiguousLocationDialogFragment;
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

    private static Map<String, Integer> speedUnits = new HashMap<>(3);
    private static Map<String, Integer> pressUnits = new HashMap<>(3);
    private static boolean mappingsInitialised = false;

    @Nullable
    private Weather todayWeather;

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

    private int theme;
    private boolean widgetTransparent;
    private boolean destroyed = false;

    private List<Weather> longTermWeather = new ArrayList<>();
    private List<Weather> longTermTodayWeather = new ArrayList<>();
    private List<Weather> longTermTomorrowWeather = new ArrayList<>();

    public int recentCityId;

    private AppDatabase appDatabase;
    private WeatherRepository weatherRepository;
    private CityRepository cityRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize the associated SharedPreferences file with default values
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        widgetTransparent = prefs.getBoolean("transparentWidget", false);
        setTheme(theme = UI.getTheme(prefs.getString("theme", "fresh")));

        // Initiate activity
        super.onCreate(savedInstanceState);
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

        initMappings();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        recentCityId = preferences.getInt("cityId", Constants.DEFAULT_CITY_ID);

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
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, longTermTodayWeather);
        } else if (id == 1) {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, longTermTomorrowWeather);
        } else {
            weatherRecyclerAdapter = new WeatherRecyclerAdapter(this, longTermWeather);
        }
        return weatherRecyclerAdapter;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateTodayWeatherUI();
        updateLongTermWeatherUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (UI.getTheme(PreferenceManager.getDefaultSharedPreferences(this).getString("theme", "fresh")) != theme ||
                PreferenceManager.getDefaultSharedPreferences(this).getBoolean("transparentWidget", false) != widgetTransparent) {
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

    private void initializeDrawer() {
        View bottomButtons = sidebarDrawer.findViewById(R.id.bottomButtons);
        bottomButtons.setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            searchCities();
        });

        ImageView searchIcon = sidebarDrawer.findViewById(R.id.searchIcon);
        TextView addLocationText = sidebarDrawer.findViewById(R.id.addLocation);
        // TODO: Is there any other way of getting this color?
        searchIcon.setColorFilter(addLocationText.getCurrentTextColor(), PorterDuff.Mode.SRC_IN);

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

                    Runnable runnable = () -> {
                        Log.i("CityDeletion", String.format("City deleted: %s (%d)", city, city.getId()));
                        cityRepository.deleteCity(city);

                        Snackbar snackbar = Snackbar.make(appView, R.string.city_deleted, Snackbar.LENGTH_LONG);
                        snackbar.setAction(R.string.undo, snackView -> {
                            cityRepository.addCity(city);
                            // TODO: Refresh list
                        });

                        handler.post(() -> {
                            // TODO: Refresh list
                            snackbar.show();
                        });

                    };

                    new Thread(runnable).start();
                });

                TextView temperature = drawerItem.findViewById(R.id.temperature);

                LiveResponse<Weather> weatherLiveData = weatherRepository.getCurrentWeather(city, false);

                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

                weatherLiveData.getLiveData().observe(this, weather -> {
                    handleConnectionStatus(weatherLiveData.getStatus());

                    if (weather != null) {
                        weatherIcon.setText(weather.getIcon());
                        temperature.setText(getString(R.string.format_temperature, UnitConverter.convertTemperature(Float.parseFloat(weather.getTemperature()), sp), sp.getString("unit", "°C")));
                    }
                });

                drawerItem.setOnClickListener(v -> {
                    drawerLayout.closeDrawers();
                    saveLocation(city);
                    refreshWeather();
                });
            }
        });
    }

    private LiveData<Boolean> updateTodayWeather(boolean forceDownload) {
        MutableLiveData<Boolean> isDone = new MutableLiveData<>();
        LiveResponse<City> currentCity = cityRepository.getCity(recentCityId);

        currentCity.getLiveData().observe(this, city -> {
            if (handleConnectionStatus(currentCity.getStatus())) {
                LiveResponse<Weather> weatherLiveData = weatherRepository.getCurrentWeather(city, forceDownload);
                weatherLiveData.getLiveData().observe(this, weather -> {
                    handleConnectionStatus(weatherLiveData.getStatus());

                    if (weather != null) {
                        todayWeather = weather;
                        updateTodayWeatherUI();
                    }

                    isDone.setValue(true);
                });
            } else {
                isDone.setValue(true);
            }
        });

        return isDone;
    }

    private LiveData<Boolean> updateForecast(boolean forceDownload) {
        MutableLiveData<Boolean> isDone = new MutableLiveData<>();

        LiveResponse<City> currentCity = cityRepository.getCity(recentCityId);
        currentCity.getLiveData().observe(this, city -> {
            if (handleConnectionStatus(currentCity.getStatus())) {
                LiveResponse<List<Weather>> forecastLiveData = weatherRepository.getWeatherForecast(city, forceDownload);

                forecastLiveData.getLiveData().observe(this, weathers -> {
                    assert weathers != null;

                    handleConnectionStatus(forecastLiveData.getStatus());

                    Calendar today = Calendar.getInstance();
                    today.set(Calendar.HOUR_OF_DAY, 0);
                    today.set(Calendar.MINUTE, 0);
                    today.set(Calendar.SECOND, 0);
                    today.set(Calendar.MILLISECOND, 0);

                    Calendar tomorrow = (Calendar) today.clone();
                    tomorrow.add(Calendar.DAY_OF_YEAR, 1);

                    Calendar later = (Calendar) today.clone();
                    later.add(Calendar.DAY_OF_YEAR, 2);
                    longTermTodayWeather = new ArrayList<>();
                    longTermTomorrowWeather = new ArrayList<>();
                    longTermWeather = new ArrayList<>();

                    for (Weather weather : weathers) {
                        if (weather.getDate().after(later.getTime())) {
                            longTermWeather.add(weather);
                        } else if (weather.getDate().after(tomorrow.getTime())) {
                            longTermTomorrowWeather.add(weather);
                        } else if (weather.getDate().after(today.getTime())) {
                            longTermTodayWeather.add(weather);
                        }
                    }

                    isDone.setValue(true);
                    updateLongTermWeatherUI();
                });
            } else {
                isDone.setValue(true);
            }

        });

        return isDone;
    }

    // TODO: Use something else than alert?
    @SuppressLint("RestrictedApi")
    private void searchCities() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(this.getString(R.string.search_title));
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setMaxLines(1);
        input.setSingleLine(true);
        alert.setView(input, 32, 0, 32, 0);

        alert.setPositiveButton(R.string.dialog_ok, (dialog, whichButton) -> {
            String result = input.getText().toString();
            if (!result.isEmpty()) {
                LiveResponse<List<Weather>> cities = cityRepository.searchCity(result.trim());
                cities.getLiveData().observe(this, citiesList -> {
                    if (handleConnectionStatus(cities.getStatus())) {
                        assert citiesList != null;

                        // TODO: Is it ever going to be empty? Wouldn't a 404 be returned?
                        if (citiesList.size() == 0) {
                            Log.e("Geolocation", "No city found");
                            // TODO: We cant return it here, do something else
                            // return ParseResult.CITY_NOT_FOUND;
                        } else if (citiesList.size() == 1) {
                            saveLocation(citiesList.get(0).getCity());
                        } else {
                            launchLocationPickerDialog(citiesList);
                        }
                        // TODO: Redraw drawer
                    }
                });
            }
        });
        alert.setNegativeButton(R.string.dialog_cancel, (dialog, whichButton) -> {
            // Cancelled
        });
        alert.show();
    }

    private void saveLocation(City city) {
        recentCityId = city.getId();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("cityId", city.getId());
        editor.apply();

        CityDao cityDao = appDatabase.cityDao();

        Runnable runnable = () -> {
            if (cityDao.findById(city.getId()) == null) {
                cityDao.insertAll(city);
            }
        };

        new Thread(runnable).start();

//        if (!recentCityId.equals(result)) {
//            // New location, update weather
//            updateTodayWeather();
//            updateForecast();
//            getTodayUVIndex();
//        }
    }

    private void aboutDialog() {
        new AboutDialogFragment().show(getSupportFragmentManager(), null);
    }

    // TODO: Check why this is called twice
    private void updateTodayWeatherUI() {
        if (todayWeather == null) { // If it wasn't initialized yet
            refreshWeather();
            return;
        }

        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getApplicationContext());
        // TODO: Fix inspection
        //noinspection ConstantConditions
        getSupportActionBar().setTitle(todayWeather.getCity().toString());

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        // Temperature
        float temperature = UnitConverter.convertTemperature(Float.parseFloat(todayWeather.getTemperature()), sp);
        if (sp.getBoolean("temperatureInteger", false)) {
            temperature = Math.round(temperature);
        }

        // Rain
        double rain = Double.parseDouble(todayWeather.getRain());
        String rainString = UnitConverter.getRainString(rain, sp);

        // Wind
        double wind;
        try {
            wind = Double.parseDouble(todayWeather.getWind());
        } catch (Exception e) {
            e.printStackTrace();
            wind = 0;
        }
        wind = UnitConverter.convertWind(wind, sp);

        // Pressure
        double pressure = UnitConverter.convertPressure((float) Double.parseDouble(todayWeather.getPressure()), sp);

        todayTemperature.setText(getString(R.string.format_temperature, temperature, sp.getString("unit", "°C")));
        todayDescription.setText(rainString.length() > 0 ? getString(R.string.format_description_with_rain, Formatting.capitalize(todayWeather.getDescription()), rainString) : Formatting.capitalize(todayWeather.getDescription()));
        if (sp.getString("speedUnit", "m/s").equals("bft")) {
            todayWind.setText(getString(R.string.format_wind_beaufort, UnitConverter.getBeaufortName((int) wind),
                    todayWeather.isWindDirectionAvailable() ? getWindDirectionString(sp, this, todayWeather) : "")
            );
        } else {
            todayWind.setText(getString(R.string.format_wind, wind,
                    localize(sp, "speedUnit", "m/s"),
                    todayWeather.isWindDirectionAvailable() ? getWindDirectionString(sp, this, todayWeather) : "")
            );
        }
        todayPressure.setText(getString(R.string.format_pressure, pressure, localize(sp, "pressureUnit", "hPa")));
        // TODO: Convert humidity to int
        todayHumidity.setText(getString(R.string.format_humidity, Integer.parseInt(todayWeather.getHumidity())));
        todaySunrise.setText(getString(R.string.format_sunrise, todayWeather.getSunrise()));
        todaySunset.setText(getString(R.string.format_sunset, todayWeather.getSunset()));
        todayIcon.setText(todayWeather.getIcon());
        todayUvIndex.setText(getString(R.string.format_uv_index, UnitConverter.convertUvIndexToRiskLevel(todayWeather.getUvIndex())));
        lastUpdate.setText(getString(R.string.last_update, formatTimeWithDayIfNotToday(this, todayWeather.getLastUpdated())));

        todayIcon.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, GraphActivity.class);
            startActivity(intent);
        });
    }

    private void updateLongTermWeatherUI() {
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

        if (currentPage == 0 && longTermTodayWeather.isEmpty() && !longTermTomorrowWeather.isEmpty()) {
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
        boolean cityChanged = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("cityChanged", false);
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
        swipeRefreshLayout.setRefreshing(true);
        LiveData<Boolean> isWeatherDownloaded = updateTodayWeather(false);
        LiveData<Boolean> isForecastDownloaded = updateForecast(false);

        isWeatherDownloaded.observe(this, weatherDownloaded ->
                isForecastDownloaded.observe(this, forecastDownloaded ->
                        swipeRefreshLayout.setRefreshing(false)
                )
        );
    }

    public void downloadWeather() {
        if (isNetworkAvailable()) {
            swipeRefreshLayout.setRefreshing(true);
            LiveData<Boolean> isWeatherDownloaded = updateTodayWeather(true);
            LiveData<Boolean> isForecastDownloaded = updateForecast(true);

            isWeatherDownloaded.observe(this, weatherDownloaded ->
                    isForecastDownloaded.observe(this, forecastDownloaded ->
                            swipeRefreshLayout.setRefreshing(false)
                    ));
        } else {
            Snackbar.make(appView, getString(R.string.msg_connection_not_available), Snackbar.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    public static void initMappings() {
        if (mappingsInitialised)
            return;
        mappingsInitialised = true;
        speedUnits.put("m/s", R.string.speed_unit_mps);
        speedUnits.put("kph", R.string.speed_unit_kph);
        speedUnits.put("mph", R.string.speed_unit_mph);
        speedUnits.put("kn", R.string.speed_unit_kn);

        pressUnits.put("hPa", R.string.pressure_unit_hpa);
        pressUnits.put("kPa", R.string.pressure_unit_kpa);
        pressUnits.put("mm Hg", R.string.pressure_unit_mmhg);
    }

    private String localize(SharedPreferences sp, String preferenceKey, String defaultValueKey) {
        return localize(sp, this, preferenceKey, defaultValueKey);
    }

    public static String localize(SharedPreferences sp, Context context, String preferenceKey, String defaultValueKey) {
        String preferenceValue = sp.getString(preferenceKey, defaultValueKey);
        String result = preferenceValue;
        if ("speedUnit".equals(preferenceKey)) {
            if (speedUnits.containsKey(preferenceValue)) {
                result = context.getString(speedUnits.get(preferenceValue));
            }
        } else if ("pressureUnit".equals(preferenceKey)) {
            if (pressUnits.containsKey(preferenceValue)) {
                result = context.getString(pressUnits.get(preferenceValue));
            }
        }
        return result;
    }

    public static String getWindDirectionString(SharedPreferences sp, Context context, Weather weather) {
        try {
            if (Double.parseDouble(weather.getWind()) != 0) {
                String pref = sp.getString("windDirectionFormat", null);
                if ("arrow".equals(pref)) {
                    return weather.getWindDirection(8).getArrow(context);
                } else if ("abbr".equals(pref)) {
                    return weather.getWindDirection().getLocalizedString(context);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    // TODO:
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCityByLocation();
                }
                return;
            }
        }
    }

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
            // TODO: What do we do if city if city is null?
            if (city != null) {
                saveLocation(city);
            }
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

    private void launchLocationPickerDialog(List<Weather> cityList) {
        AmbiguousLocationDialogFragment fragment = new AmbiguousLocationDialogFragment();
        Bundle bundle = new Bundle();
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();

        bundle.putSerializable("cityList", (Serializable) cityList);
        fragment.setArguments(bundle);

        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.add(R.id.drawerLayout, fragment)
                .addToBackStack(null).commit();
    }

    public static String formatTimeWithDayIfNotToday(Context context, long timeInMillis) {
        Calendar now = Calendar.getInstance();
        Calendar lastCheckedCal = new GregorianCalendar();
        lastCheckedCal.setTimeInMillis(timeInMillis);
        Date lastCheckedDate = new Date(timeInMillis);
        String timeFormat = android.text.format.DateFormat.getTimeFormat(context).format(lastCheckedDate);
        if (now.get(Calendar.YEAR) == lastCheckedCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == lastCheckedCal.get(Calendar.DAY_OF_YEAR)) {
            // Same day, only show time
            return timeFormat;
        } else {
            return android.text.format.DateFormat.getDateFormat(context).format(lastCheckedDate) + " " + timeFormat;
        }
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
