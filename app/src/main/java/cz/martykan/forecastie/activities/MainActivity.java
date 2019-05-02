package cz.martykan.forecastie.activities;

import android.Manifest;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
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
import android.widget.Toast;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.Response;
import cz.martykan.forecastie.utils.TextFormatting;
import cz.martykan.forecastie.utils.UI;
import cz.martykan.forecastie.workers.WeatherWorker;

public class MainActivity extends BaseActivity implements LocationListener {
    protected static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION = 1;

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

    private Typeface weatherFont;

    private LocationManager locationManager;

    private boolean widgetTransparent;

    private int selectedCityId;

    private AppDatabase appDatabase;
    private WeatherRepository weatherRepository;
    private CityRepository cityRepository;

    private Preferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: Maybe initialize SharedPreferences on parent class?
        // Initialize the associated SharedPreferences file with default values
        PreferenceManager.setDefaultValues(this, R.xml.prefs, false);

        super.onCreate(savedInstanceState);

        preferences = Preferences.getInstance(prefs, this.getResources());

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // TODO: Why is it needed here?
        widgetTransparent = prefs.getBoolean("transparentWidget", false);

        setContentView(R.layout.activity_scrolling);
        appView = findViewById(R.id.viewApp);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);

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

        selectedCityId = preferences.getLastCityId();

        // Initializes drawer
        LinearLayout drawerContainer = findViewById(R.id.drawerContainer);
        sidebarDrawer = (LinearLayout) getLayoutInflater().inflate(R.layout.fragment_drawer, drawerContainer);
        initializeDrawer();

        // Set autoupdater
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_ROAMING)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(WeatherWorker.class, Formatting.getRefreshIntervalInMinutes(preferences), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance().enqueueUniquePeriodicWork("Weather", ExistingPeriodicWorkPolicy.REPLACE, workRequest);


        swipeRefreshLayout.setOnRefreshListener(this::downloadWeather);

        appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
            // Only allow pull to refresh when scrolled to top
            swipeRefreshLayout.setEnabled(verticalOffset == 0);
        });
    }

    @Override
    public void onStart() {
        super.onStart();

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

        updateCurrentLocation();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (UI.getTheme(preferences.getTheme()) != theme || preferences.isWidgetTransparent() != widgetTransparent) {
            // Restart activity to apply theme
            overridePendingTransition(0, 0);
            finish();
            overridePendingTransition(0, 0);
            startActivity(getIntent());
        }

        refreshWeather();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

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

        if (requestCode == ACTIVITY_REQUEST_CITY_SELECTED && resultCode == CitySearchActivity.ACTIVITY_RESULT_CITY_SELECTED) {
            Weather weather = (Weather) data.getSerializableExtra("weather");
            weather.getCity().setCityUsage(weather.getCity().getCityUsage() | City.USAGE_USER);
            persistLocation(weather.getCity(), false, true);
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

            LinearLayout citiesList = sidebarDrawer.findViewById(R.id.citiesList);
            citiesList.removeAllViews();

            Collections.sort(cities, (o1, o2) -> {
                if ((o1.getCityUsage() & City.USAGE_CURRENT_LOCATION) == City.USAGE_CURRENT_LOCATION) {
                    if ((o2.getCityUsage() & City.USAGE_CURRENT_LOCATION) == City.USAGE_CURRENT_LOCATION) {
                        Log.w("MainActivity", "Multiple current locations found");
                        return o1.getCity().compareTo(o2.getCity());
                    }

                    return -1;
                } else if ((o2.getCityUsage() & City.USAGE_CURRENT_LOCATION) == City.USAGE_CURRENT_LOCATION) {
                    return 1;
                } else {
                    return o1.getCity().compareTo(o2.getCity());
                }
            });

            for (int i = 0; i < cities.size(); i++) {
                City city = cities.get(i);
                // TODO: Maybe a method on city could be added
                boolean isCityCurrentLocation = (city.getCityUsage() & City.USAGE_CURRENT_LOCATION) == City.USAGE_CURRENT_LOCATION;

                ConstraintLayout drawerItem = (ConstraintLayout) getLayoutInflater().inflate(R.layout.fragment_drawer_item, citiesList, false);
                citiesList.addView(drawerItem);

                TextView weatherIcon = drawerItem.findViewById(R.id.weatherIcon);
                weatherIcon.setTypeface(weatherFont);

                TextView cityName = drawerItem.findViewById(R.id.cityName);
                cityName.setText(city.toString());

                ImageView removeButton = drawerItem.findViewById(R.id.removeLocationIcon);

                if (isCityCurrentLocation) {
                    ImageView currentLocation = drawerItem.findViewById(R.id.currentLocationIcon);
                    currentLocation.setColorFilter(weatherIcon.getCurrentTextColor(), PorterDuff.Mode.SRC_IN);
                    currentLocation.setVisibility(View.VISIBLE);
                    removeButton.setVisibility(View.INVISIBLE);
                } else {
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
                                        cityRepository.persistCity(city);

                                        if (cityWeather != null) {
                                            // TODO: Test if this reworked code works
                                            weatherRepository.persistWeathers(cityWeather);
                                            city.setCurrentWeatherId(cityWeather.getUid());
                                            cityRepository.persistCity(city);

//                                        appDatabase.weatherDao().insertAll(cityWeather);
//                                        city.setCurrentWeatherId(cityWeather.getUid());
//                                        appDatabase.cityDao().persist(city);
                                        }

                                        appDatabase.weatherDao().insertAll(cityForecast.toArray(new Weather[0]));

                                        // TODO: This doesn't work
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
                }


                TextView temperature = drawerItem.findViewById(R.id.temperature);

                // TODO: Use bulk download, https://openweathermap.org/current#severalid
                LiveResponse<Weather> weatherLiveData = weatherRepository.getCurrentWeather(city, false);

                weatherLiveData.getLiveData().observe(this, weather -> {
                    handleConnectionStatus(weatherLiveData.getStatus());

                    if (weather != null) {
                        weatherIcon.setText(TextFormatting.getIcon(getResources(), weather));
                        temperature.setText(TextFormatting.getTemperature(getResources(), preferences, weather));
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
        LiveResponse<City> currentCity = cityRepository.getCity(selectedCityId);

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
        LiveResponse<City> currentCity = cityRepository.getCity(selectedCityId);
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
        selectedCityId = city.getId();
        preferences.setLastCityId(city.getId());
    }

    private void persistLocation(City city, boolean asCurrentLocation, boolean updateSelectedLocation) {
        if (updateSelectedLocation) {
            selectLocation(city);
        }

        Handler handler = new Handler();
        Runnable runnable = () -> {
            if (asCurrentLocation) {
                cityRepository.persistCurrentLocation(city);
            } else {
                cityRepository.persistCity(city);
            }

            handler.post(this::refreshWeather);
        };

        new Thread(runnable).start();
    }

    private void aboutDialog() {
        new AboutDialogFragment().show(getSupportFragmentManager(), null);
    }

    private void updateWeatherUI() {
        assert todayWeather != null;

        Objects.requireNonNull(getSupportActionBar()).setTitle(todayWeather.getCity().toString());

        todayTemperature.setText(TextFormatting.getTemperature(getResources(), preferences, todayWeather));
        todayDescription.setText(TextFormatting.getDescription(getResources(), preferences, todayWeather));
        todayWind.setText(TextFormatting.getWindSpeed(getResources(), preferences, todayWeather));
        todayPressure.setText(TextFormatting.getPressure(getResources(), preferences, todayWeather));
        todayHumidity.setText(TextFormatting.getHumidity(getResources(), todayWeather));
        todaySunrise.setText(TextFormatting.getSunrise(getResources(), todayWeather));
        todaySunset.setText(TextFormatting.getSunset(getResources(), todayWeather));
        todayIcon.setText(TextFormatting.getIcon(getResources(), todayWeather));
        todayUvIndex.setText(TextFormatting.getUvIndex(getResources(), todayWeather));
        lastUpdate.setText(TextFormatting.getLastUpdate(getResources(), todayWeather, false));

        todayIcon.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, GraphActivity.class);
            intent.putExtra(GraphActivity.EXTRA_CITY, (Serializable) forecast);
            startActivity(intent);
        });
    }

    private void updateForecastUI() {
        // TODO: Can't we simply recycle existing adapters? It feels bad doing this like that
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

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) Objects.requireNonNull(getSystemService(Context.CONNECTIVITY_SERVICE));
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
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
                if (todayWeather != null) {
                    Intent intent = new Intent(MainActivity.this, MapActivity.class);
                    intent.putExtra(MapActivity.EXTRA_CITY, todayWeather.getCity());
                    startActivity(intent);
                    return true;
                } else {
                    Log.w("MainActivity", "Tried to open map with current weather being null");
                    return false;
                }
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
            if (value != null && value == 2) {
                swipeRefreshLayout.setRefreshing(false);
                refreshDrawer();
            }
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

    private boolean updateCurrentLocation() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        // TODO: Can't we use requestSingleUpdate here?
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        } else {
            return false;
        }

        return true;
    }

    void getCityByLocation() {
        // If couldn't update current location
        if (!updateCurrentLocation()) {
            if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showLocationSettingsDialog();
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // TODO: Show window explaining the need of GPS for detecting current location
                    Toast.makeText(this, "OBEY, RESISTANCE IS FUTILE", Toast.LENGTH_LONG).show();
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_ACCESS_FINE_LOCATION);
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_ACCESS_FINE_LOCATION);
                }
            }

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
        if (requestCode == MY_PERMISSIONS_ACCESS_FINE_LOCATION) {
            // If request wasn't cancelled
            if (grantResults.length > 0) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCityByLocation();
                } else {
                    // If user checked "Never ask again"
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                        alertDialog.setTitle(R.string.location_settings);
                        alertDialog.setMessage("FU");
                        alertDialog.setPositiveButton(R.string.location_settings_button, (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", this.getPackageName(), null));
                            startActivity(intent);
                        });
                        alertDialog.setNegativeButton(R.string.dialog_cancel, ((dialog, which) -> dialog.cancel()));
                        alertDialog.show();
                    } else {
                        Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                // Request is cancelled
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        try {
            locationManager.removeUpdates(this);
        } catch (SecurityException e) {
            Log.e("LocationManager", "Error while trying to stop listening for location updates. This is probably a permissions issue", e);
        }
        Log.i(String.format("LOCATION (%s)", location.getProvider().toUpperCase()), String.format("%s, %s", location.getLatitude(), location.getLongitude()));

        LiveResponse<City> cityLiveData = cityRepository.findCity(location);

        cityLiveData.getLiveData().observe(this, city -> {
            if (cityLiveData.getStatus() == Response.Status.SUCCESS) {
                assert city != null;

                city.setCityUsage(city.getCityUsage() | City.USAGE_CURRENT_LOCATION);
                // TODO: That's not enough, we need to know whether the user clicked the button or it was a background update
                persistLocation(city, true, selectedCityId == city.getId());
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
            // The "natural" order would be .equals(tomorrow) || .after(tomorrow) && .before(later)
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
