package cz.martykan.forecastie.workers;

import android.Manifest;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import androidx.concurrent.futures.ResolvableFuture;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityDao;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.database.WeatherDao;
import cz.martykan.forecastie.database.WeatherRepository;
import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.Response;
import cz.martykan.forecastie.widgets.AbstractWidgetProvider;
import cz.martykan.forecastie.widgets.DashClockWeatherExtension;

public class WeatherWorker extends ListenableWorker {

    private CityRepository cityRepository;
    private WeatherRepository weatherRepository;
    private Preferences preferences;

    /**
     * @param appContext   The application {@link Context}
     * @param workerParams Parameters to setup the internal state of this worker
     */
    public WeatherWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
        WeatherDao weatherDao = AppDatabase.getDatabase(appContext).weatherDao();
        CityDao cityDao = AppDatabase.getDatabase(appContext).cityDao();
        this.cityRepository = new CityRepository(cityDao, appContext);
        this.weatherRepository = new WeatherRepository(weatherDao, cityDao, appContext);
        this.preferences = Preferences.getInstance(PreferenceManager.getDefaultSharedPreferences(appContext), appContext.getResources());
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        ResolvableFuture<Result> resolvableFuture = ResolvableFuture.create();

        Log.i("WeatherWorker", "WeatherWorker called, time: " + DateFormat.getDateTimeInstance().format(new Date()));

        // We wait until we have the current location before we update the forecast
        Runnable locationUpdateWaiter = () -> {
            int[] usedCities = AbstractWidgetProvider.getUsedCities(getApplicationContext());
            List<LiveResponse<City>> liveResponses = new ArrayList<>();

            for (int cityId : usedCities) {
                if (cityId == CityRepository.CURRENT_CITY) {
                    liveResponses.add(cityRepository.getCurrentLocation());
                } else {
                    liveResponses.add(cityRepository.getCity(cityId));
                }
            }

            LiveData<List<LiveResponse<City>>> citiesLiveData = LiveResponse.onAllDone(liveResponses);

            citiesLiveData.observeForever(new Observer<List<LiveResponse<City>>>() {
                @Override
                public void onChanged(@Nullable List<LiveResponse<City>> liveResponses) {
                    citiesLiveData.removeObserver(this);
                    assert liveResponses != null;

                    List<City> citiesList = new ArrayList<>(liveResponses.size());

                    for (LiveResponse<City> cityLiveResponse : liveResponses) {
                        citiesList.add(cityLiveResponse.getLiveData().getValue());
                    }

                    LiveResponse<List<Weather>> weathersLiveResponse = weatherRepository.getCurrentWeathers(citiesList, true);

                    weathersLiveResponse.getLiveData().observeForever(new Observer<List<Weather>>() {
                        @Override
                        public void onChanged(@Nullable List<Weather> weathers) {
                            weathersLiveResponse.getLiveData().removeObserver(this);

                            if (weathersLiveResponse.getStatus() == Response.Status.SUCCESS) {
                                AbstractWidgetProvider.updateWidgets(getApplicationContext());
                                DashClockWeatherExtension.updateDashClock(getApplicationContext());

                                Result success = Result.success();
                                resolvableFuture.set(success);
                            } else {
                                Result failure = Result.failure();
                                resolvableFuture.set(failure);
                            }
                        }
                    });
                }
            });
        };

        backgroundUpdate:
        if (preferences.updateLocationInBackground()) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w("WeatherWorker", "Requested background location update without permissions");
                locationUpdateWaiter.run();
                break backgroundUpdate;
            }

            LocationManager locationManager = (LocationManager) Objects.requireNonNull(getApplicationContext().getSystemService(Context.LOCATION_SERVICE));

            LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    locationManager.removeUpdates(this);

                    Log.i(String.format("LOCATION (%s)", location.getProvider().toUpperCase()), String.format("%s, %s", location.getLatitude(), location.getLongitude()));

                    LiveResponse<City> cityLiveData = cityRepository.findCity(location);

                    cityLiveData.getLiveData().observeForever(new Observer<City>() {
                        @Override
                        public void onChanged(@Nullable City city) {
                            cityLiveData.getLiveData().removeObserver(this);

                            if (cityLiveData.getStatus() == Response.Status.SUCCESS) {
                                assert city != null;

                                city.setCityUsage(city.getCityUsage() | City.USAGE_CURRENT_LOCATION);

                                Handler handler = new Handler();
                                Runnable runnable = () -> {
                                    cityRepository.persistCurrentLocation(city);
                                    handler.post(locationUpdateWaiter);
                                };

                                new Thread(runnable).start();
                            } else {
                                locationUpdateWaiter.run();
                            }
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
            };

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
            } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            } else {
                Log.w("WeatherWorker", "Requested background location update, but no provider is available");
                locationUpdateWaiter.run();
            }
        } else {
            locationUpdateWaiter.run();
        }

        return resolvableFuture;
    }
}
