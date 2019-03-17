package cz.martykan.forecastie.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.arch.lifecycle.LiveData;
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
import android.os.AsyncTask;
import android.os.Bundle;
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
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.martykan.forecastie.AlarmReceiver;
import cz.martykan.forecastie.Constants;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.adapters.ViewPagerAdapter;
import cz.martykan.forecastie.adapters.WeatherRecyclerAdapter;
import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityDao;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.database.WeatherDao;
import cz.martykan.forecastie.database.WeatherRepository;
import cz.martykan.forecastie.fragments.AboutDialogFragment;
import cz.martykan.forecastie.fragments.AmbiguousLocationDialogFragment;
import cz.martykan.forecastie.fragments.RecyclerViewFragment;
import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.tasks.GenericRequestTask;
import cz.martykan.forecastie.tasks.ParseResult;
import cz.martykan.forecastie.tasks.TaskOutput;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.UI;
import cz.martykan.forecastie.utils.UnitConvertor;
import cz.martykan.forecastie.widgets.AbstractWidgetProvider;
import cz.martykan.forecastie.widgets.DashClockWeatherExtension;

import static cz.martykan.forecastie.utils.JsonParser.convertJsonToCity;
import static cz.martykan.forecastie.utils.JsonParser.convertJsonToWeather;

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

    public String recentCityId = "";

    private Formatting formatting;

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
        boolean darkTheme = super.darkTheme;
        boolean blackTheme = super.blackTheme;
        formatting = new Formatting(this);

        // Initiate activity
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        appView = findViewById(R.id.viewApp);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);

        progressDialog = new ProgressDialog(MainActivity.this);

        // Load toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
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

        // Initializes drawer
        LinearLayout drawerContainer = findViewById(R.id.drawerContainer);
        sidebarDrawer = (LinearLayout) getLayoutInflater().inflate(R.layout.fragment_drawer, drawerContainer);
        initializeDrawer();

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

        // Preload data from cache
        preloadWeather();
        preloadUVIndex();
        updateLastUpdateTime();

        // Set autoupdater
        AlarmReceiver.setRecurringAlarm(this);


        swipeRefreshLayout.setOnRefreshListener(() -> {
            refreshWeather();
            swipeRefreshLayout.setRefreshing(false);
        });

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
        updateUVIndexUI();
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
        } else if (shouldUpdate() && isNetworkAvailable()) {
            getTodayWeather();
            getLongTermWeather();
            getTodayUVIndex();
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
        searchIcon.setColorFilter(addLocationText.getCurrentTextColor(), PorterDuff.Mode.SRC_IN);

        LiveData<List<City>> citiesLiveData = cityRepository.getCities();
        citiesLiveData.observe(this, cities -> {
            assert cities != null;

            LinearLayout citiesList = sidebarDrawer.findViewById(R.id.citiesList);
            for (int i = 0; i < cities.size(); i++) {
                City city = cities.get(i);
                ConstraintLayout drawerItem = (ConstraintLayout) getLayoutInflater().inflate(R.layout.fragment_drawer_item, null);
                citiesList.addView(drawerItem);

                TextView weatherIcon = drawerItem.findViewById(R.id.weatherIcon);
                weatherIcon.setTypeface(weatherFont);

                TextView cityName = drawerItem.findViewById(R.id.cityName);
                cityName.setText(city.toString());

                LiveData<Weather> weatherLiveData = weatherRepository.getCurrentWeather(city);

                weatherLiveData.observe(this, weather -> {
                    assert weather != null;
                    weatherIcon.setText(weather.getIcon());
                });

                drawerItem.setOnClickListener(v -> {
                    drawerLayout.closeDrawers();
                    saveLocation(city);
                    refreshWeather();
                });
            }
        });
    }

    private void preloadUVIndex() {
        if (todayWeather != null) {
            double latitude = todayWeather.getCity().getLat();
            double longitude = todayWeather.getCity().getLon();

            new TodayUVITask(this, this, progressDialog).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "coords", Double.toString(latitude), Double.toString(longitude));
        }
    }

    private void preloadWeather() {

        LiveData<List<City>> citiesLiveData = cityRepository.getCities();
        citiesLiveData.observe(this, cities -> {
            assert cities != null;

            City city;
            // TODO: This is FAR from a good fix
            if (cities.size() == 0) {
                city = new City();
                city.setId(2643743);
                city.setCity("London");
                city.setCountry("GB");
                city.setLat(51.5073);
                city.setLon(-0.1277);
            } else {
                city = cities.get(0);
            }

            LiveData<Weather> weatherLiveData = weatherRepository.getCurrentWeather(city);
            weatherLiveData.observe(this, weather -> {
                todayWeather = weather;
                updateTodayWeatherUI();
            });
        });
    }

    private void getTodayUVIndex() { // TODO: Fix this, maybe hide if null?
        double latitude = 0;
        double longitude = 0;

        if (todayWeather != null) {
            latitude = todayWeather.getCity().getLat();
            longitude = todayWeather.getCity().getLon();
        }

        new TodayUVITask(this, this, progressDialog).execute("coords", Double.toString(latitude), Double.toString(longitude));
    }

    private void getTodayWeather() {
        new TodayWeatherTask(this, this, progressDialog).execute();
    }

    private void getLongTermWeather() {
        new LongTermWeatherTask(this, this, progressDialog).execute();
    }

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
                LiveData<List<Weather>> cities = cityRepository.searchCity(result.trim());
                cities.observe(this, citiesList -> {
                    assert citiesList != null;

                    if (citiesList.size() == 0) {
                        Log.e("Geolocation", "No city found");
                        // TODO: We cant return it here, do something else
                        // return ParseResult.CITY_NOT_FOUND;
                    } else if (citiesList.size() == 1) {
                        saveLocation(citiesList.get(0).getCity());
                    } else {
                        launchLocationPickerDialog(citiesList);
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
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        CityDao cityDao = appDatabase.cityDao();

        Runnable runnable = () -> {
            if (cityDao.findById(city.getId()) == null) {
                cityDao.insertAll(city);
            }

            recentCityId = preferences.getString("cityId", Constants.DEFAULT_CITY_ID);

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("cityId", String.valueOf(city.getId()));

            editor.apply();
        };

        new Thread(runnable).start();

//        if (!recentCityId.equals(result)) {
//            // New location, update weather
//            getTodayWeather();
//            getLongTermWeather();
//            getTodayUVIndex();
//        }
    }

    private void saveLocation(int cityId, String cityName, String country) {
        City city = new City();
        city.setId(cityId);
        city.setCity(cityName);
        city.setCountry(country);

        saveLocation(city);
    }

    private void aboutDialog() {
        new AboutDialogFragment().show(getSupportFragmentManager(), null);
    }

    private ParseResult parseTodayJson(String result) {
        try {
            JSONObject reader = new JSONObject(result);

            final String code = reader.optString("cod");
            if ("404".equals(code)) {
                return ParseResult.CITY_NOT_FOUND;
            }

            City city = convertJsonToCity(reader);

            // TODO: It shouldn't be saved globally
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            sp.edit().putFloat("latitude", (float) city.getLat()).putFloat("longitude", (float) city.getLon()).apply();

            todayWeather = convertJsonToWeather(reader, city, formatting, true);

            WeatherDao weatherDao = appDatabase.weatherDao();
            long id = weatherDao.insert(todayWeather);
            todayWeather.setUid(id);

            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
            editor.putString("lastToday", result);
            editor.apply();

        } catch (JSONException e) {
            Log.e("JSONException Data", result);
            e.printStackTrace();
            return ParseResult.JSON_EXCEPTION;
        }

        return ParseResult.OK;
    }

    private ParseResult parseTodayUVIJson(String result) {
        // TODO: Do something else than simply return OK?
        if (todayWeather == null) {
            return ParseResult.OK;
        }

        try {
            JSONObject reader = new JSONObject(result);

            final String code = reader.optString("cod");
            if ("404".equals(code)) {
                todayWeather.setUvIndex(-1);
                return ParseResult.CITY_NOT_FOUND;
            }

            double value = reader.getDouble("value");
            todayWeather.setUvIndex(value);
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
            editor.putString("lastUVIToday", result);
            editor.apply();
        } catch (JSONException e) {
            Log.e("JSONException Data", result);
            e.printStackTrace();
            return ParseResult.JSON_EXCEPTION;
        }

        return ParseResult.OK;
    }

    // TODO: Check why this is called twice
    private void updateTodayWeatherUI() {
        if (todayWeather == null) { // If it wasn't initialized yet
            preloadWeather();
            return;
        }

        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getApplicationContext());
        // TODO: Fix inspection
        //noinspection ConstantConditions
        getSupportActionBar().setTitle(todayWeather.getCity().toString());

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        // Temperature
        float temperature = UnitConvertor.convertTemperature(Float.parseFloat(todayWeather.getTemperature()), sp);
        if (sp.getBoolean("temperatureInteger", false)) {
            temperature = Math.round(temperature);
        }

        // Rain
        double rain = Double.parseDouble(todayWeather.getRain());
        String rainString = UnitConvertor.getRainString(rain, sp);

        // Wind
        double wind;
        try {
            wind = Double.parseDouble(todayWeather.getWind());
        } catch (Exception e) {
            e.printStackTrace();
            wind = 0;
        }
        wind = UnitConvertor.convertWind(wind, sp);

        // Pressure
        double pressure = UnitConvertor.convertPressure((float) Double.parseDouble(todayWeather.getPressure()), sp);

        // TODO: OMG FIX THIS HUGE MESS
        todayTemperature.setText(new DecimalFormat("0.#").format(temperature) + " " + sp.getString("unit", "°C"));
        todayDescription.setText(todayWeather.getDescription().substring(0, 1).toUpperCase() +
                todayWeather.getDescription().substring(1) + rainString);
        if (sp.getString("speedUnit", "m/s").equals("bft")) {
            todayWind.setText(getString(R.string.wind) + ": " +
                    UnitConvertor.getBeaufortName((int) wind) +
                    (todayWeather.isWindDirectionAvailable() ? " " + getWindDirectionString(sp, this, todayWeather) : ""));
        } else {
            todayWind.setText(getString(R.string.wind) + ": " + new DecimalFormat("0.0").format(wind) + " " +
                    localize(sp, "speedUnit", "m/s") +
                    (todayWeather.isWindDirectionAvailable() ? " " + getWindDirectionString(sp, this, todayWeather) : ""));
        }
        todayPressure.setText(getString(R.string.pressure) + ": " + new DecimalFormat("0.0").format(pressure) + " " +
                localize(sp, "pressureUnit", "hPa"));
        todayHumidity.setText(getString(R.string.humidity) + ": " + todayWeather.getHumidity() + " %");
        todaySunrise.setText(getString(R.string.sunrise) + ": " + timeFormat.format(todayWeather.getSunrise()));
        todaySunset.setText(getString(R.string.sunset) + ": " + timeFormat.format(todayWeather.getSunset()));
        todayIcon.setText(todayWeather.getIcon());

        todayIcon.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, GraphActivity.class);
            startActivity(intent);
        });
    }

    private void updateUVIndexUI() {
        // TODO: This doesn't do anything, as preloadUVIndex verifies if weather is null, FIX IT
        if (todayWeather == null) { // If it wasn't initialized yet
            preloadUVIndex();
            return;
        }

        // UV Index
        double uvIndex = todayWeather.getUvIndex();
        todayUvIndex.setText(getString(R.string.uvindex) + ": " + UnitConvertor.convertUvIndexToRiskLevel(uvIndex));
    }

    public ParseResult parseLongTermJson(String result) {
        int i;
        try {
            JSONObject reader = new JSONObject(result);

            final String code = reader.optString("cod");
            if ("404".equals(code)) {
                if (longTermWeather == null) {
                    longTermWeather = new ArrayList<>();
                    longTermTodayWeather = new ArrayList<>();
                    longTermTomorrowWeather = new ArrayList<>();
                }
                return ParseResult.CITY_NOT_FOUND;
            }

            longTermWeather = new ArrayList<>();
            longTermTodayWeather = new ArrayList<>();
            longTermTomorrowWeather = new ArrayList<>();

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar tomorrow = (Calendar) today.clone();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);

            Calendar later = (Calendar) today.clone();
            later.add(Calendar.DAY_OF_YEAR, 2);

            City city = convertJsonToCity(reader);

            JSONArray list = reader.getJSONArray("list");
            for (i = 0; i < list.length(); i++) {
                Weather weather = convertJsonToWeather(list.getJSONObject(i), city, formatting);
                Calendar cal = Calendar.getInstance();
                cal.setTime(weather.getDate());

                if (cal.before(tomorrow)) {
                    longTermTodayWeather.add(weather);
                } else if (cal.before(later)) {
                    longTermTomorrowWeather.add(weather);
                } else {
                    longTermWeather.add(weather);
                }
            }

            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
            editor.putString("lastLongterm", result);
            editor.apply();
        } catch (JSONException e) {
            Log.e("JSONException Data", result);
            e.printStackTrace();
            return ParseResult.JSON_EXCEPTION;
        }

        return ParseResult.OK;
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

        if (currentPage == 0 && longTermTodayWeather.isEmpty()) {
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
        long lastUpdate = PreferenceManager.getDefaultSharedPreferences(this).getLong("lastUpdate", -1);
        boolean cityChanged = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("cityChanged", false);
        // Update if never checked or last update is longer ago than specified threshold
        return cityChanged || lastUpdate < 0 || (Calendar.getInstance().getTimeInMillis() - lastUpdate) > NO_UPDATE_REQUIRED_THRESHOLD;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            refreshWeather();
            return true;
        }
        if (id == R.id.action_map) {
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(intent);
        }
        if (id == R.id.action_graphs) {
            Intent intent = new Intent(MainActivity.this, GraphActivity.class);
            startActivity(intent);
        }
        if (id == R.id.action_search) {
            searchCities();
            return true;
        }
        if (id == R.id.action_location) {
            getCityByLocation();
            return true;
        }
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        }
        if (id == R.id.action_about) {
            aboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void refreshWeather() {
        if (isNetworkAvailable()) {
            getTodayWeather();
            getLongTermWeather();
            getTodayUVIndex();
        } else {
            Snackbar.make(appView, getString(R.string.msg_connection_not_available), Snackbar.LENGTH_LONG).show();
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
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    try {
                        locationManager.removeUpdates(MainActivity.this);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
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
        alertDialog.setPositiveButton(R.string.location_settings_button, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });
        alertDialog.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
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
        Log.i("LOCATION (" + location.getProvider().toUpperCase() + ")", location.getLatitude() + ", " + location.getLongitude());
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        new ProvideCityNameTask(this, this, progressDialog).execute("coords", Double.toString(latitude), Double.toString(longitude));
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


    class TodayWeatherTask extends GenericRequestTask {
        public TodayWeatherTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected void onPreExecute() {
            loading = 0;
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(TaskOutput output) {
            super.onPostExecute(output);
            // Update widgets
            AbstractWidgetProvider.updateWidgets(MainActivity.this);
            DashClockWeatherExtension.updateDashClock(MainActivity.this);
        }

        @Override
        protected ParseResult parseResponse(String response) {
            return parseTodayJson(response);
        }

        @Override
        protected String getAPIName() {
            return "weather";
        }

        @Override
        protected void updateMainUI() {
            updateTodayWeatherUI();
            updateLastUpdateTime();
            updateUVIndexUI();
        }
    }

    class LongTermWeatherTask extends GenericRequestTask {
        public LongTermWeatherTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected ParseResult parseResponse(String response) {
            return parseLongTermJson(response);
        }

        @Override
        protected String getAPIName() {
            return "forecast";
        }

        @Override
        protected void updateMainUI() {
            updateLongTermWeatherUI();
        }
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

    // TODO: Use common function
    class ProvideCityNameTask extends GenericRequestTask {

        public ProvideCityNameTask(Context context, MainActivity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected void onPreExecute() { /*Nothing*/ }

        @Override
        protected String getAPIName() {
            return "weather";
        }

        @Override
        protected ParseResult parseResponse(String response) {
            Log.i("RESULT", response);
            try {
                JSONObject reader = new JSONObject(response);

                final String code = reader.optString("cod");
                if ("404".equals(code)) {
                    Log.e("Geolocation", "No city found");
                    return ParseResult.CITY_NOT_FOUND;
                }

                saveLocation(reader.getInt("id"), reader.getString("name"), reader.getJSONObject("sys").getString("country"));

            } catch (JSONException e) {
                Log.e("JSONException Data", response);
                e.printStackTrace();
                return ParseResult.JSON_EXCEPTION;
            }

            return ParseResult.OK;
        }

        @Override
        protected void onPostExecute(TaskOutput output) {
            /* Handle possible errors only */
            handleTaskOutput(output);

            refreshWeather();
        }
    }

    class TodayUVITask extends GenericRequestTask {
        public TodayUVITask(Context context, MainActivity activity, ProgressDialog progressDialog) {
            super(context, activity, progressDialog);
        }

        @Override
        protected void onPreExecute() {
            loading = 0;
            super.onPreExecute();
        }

        @Override
        protected ParseResult parseResponse(String response) {
            return parseTodayUVIJson(response);
        }

        @Override
        protected String getAPIName() {
            return "uvi";
        }

        @Override
        protected void updateMainUI() {
            updateUVIndexUI();
        }
    }

    public static long saveLastUpdateTime(SharedPreferences sp) {
        Calendar now = Calendar.getInstance();
        sp.edit().putLong("lastUpdate", now.getTimeInMillis()).apply();
        return now.getTimeInMillis();
    }

    private void updateLastUpdateTime() {
        updateLastUpdateTime(
                PreferenceManager.getDefaultSharedPreferences(this).getLong("lastUpdate", -1)
        );
    }

    private void updateLastUpdateTime(long timeInMillis) {
        if (timeInMillis < 0) {
            // No time
            lastUpdate.setText("");
        } else {
            lastUpdate.setText(getString(R.string.last_update, formatTimeWithDayIfNotToday(this, timeInMillis)));
        }
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
}
