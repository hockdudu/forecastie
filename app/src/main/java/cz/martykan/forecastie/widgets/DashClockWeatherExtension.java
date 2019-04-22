package cz.martykan.forecastie.widgets;

import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import org.json.JSONObject;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.activities.SplashActivity;
import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityDao;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.database.WeatherDao;
import cz.martykan.forecastie.database.WeatherRepository;
import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.Response;
import cz.martykan.forecastie.utils.UnitConverter;

public class DashClockWeatherExtension extends DashClockExtension {
    private static final Uri URI_BASE = Uri.parse("content://cz.martykan.forecastie.authority");
    private static final String UPDATE_URI_PATH_SEGMENT = "dashclock/update";

    private static final String PREFS_NAME = "DashClock";
    private static final String CONFIGURATION_NAME = "cityId";

    private Preferences preferences;
    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);

        // Watch for weather updates
//        removeAllWatchContentUris();
//        addWatchContentUris(new String[]{getUpdateUri().toString()});
        preferences = Preferences.getInstance(PreferenceManager.getDefaultSharedPreferences(this), getResources());
    }

    // TODO: Use common parser
    @Override
    protected void onUpdateData(int reason) {
        Log.v("DC-WeatherExtension", "Data update requested, reason: " + reason);

        int cityId = getCityid(this, 0);
        LiveResponse<City> cityLiveResponse = WidgetDataRepository.getCityRepository(this).getCity(cityId);
        cityLiveResponse.getLiveData().observeForever(new Observer<City>() {
            @Override
            public void onChanged(@Nullable City city) {
                cityLiveResponse.getLiveData().removeObserver(this);

                if (cityLiveResponse.getStatus() == Response.Status.SUCCESS) {
                    assert city != null;

                    LiveResponse<Weather> weatherLiveResponse = WidgetDataRepository.getWeatherRepository(DashClockWeatherExtension.this).getCurrentWeather(city, false);
                    weatherLiveResponse.getLiveData().observeForever(new Observer<Weather>() {
                        @Override
                        public void onChanged(@Nullable Weather weather) {
                            weatherLiveResponse.getLiveData().removeObserver(this);

                            if (weatherLiveResponse.getStatus() == Response.Status.SUCCESS) {
                                assert weather != null;

                                DashClockWeatherExtension.this.publishUpdate(new ExtensionData()
                                        .visible(true)
                                        .icon(R.drawable.ic_cloud_white_18dp)
                                        .status(DashClockWeatherExtension.this.getString(R.string.dash_clock_status, new DecimalFormat("0.#").format(UnitConverter.convertTemperature(weather.getTemperature(), preferences)), preferences.getTemperatureUnit()))
                                        .expandedTitle(DashClockWeatherExtension.this.getString(R.string.dash_clock_expanded_title, new DecimalFormat("0.#").format(UnitConverter.convertTemperature(weather.getTemperature(), preferences)), preferences.getTemperatureUnit(), weather.getDescription()))
                                        .expandedBody(DashClockWeatherExtension.this.getString(R.string.dash_clock_expanded_body, city.getCity(), city.getCountry(),
                                                new DecimalFormat("0.0").format(weather.getWind()), preferences.getSpeedUnit(),
                                                new DecimalFormat("0.0").format(weather.getPressure()), preferences.getPressureUnit(),
                                                // TODO: Please, remove the need to use toString() here
                                                Integer.toString(weather.getHumidity()))

                                                // TODO: That's for debugging only, remove on release
                                                + DateFormat.getInstance().format(new Date()))
                                        .clickIntent(new Intent(DashClockWeatherExtension.this, SplashActivity.class)));
                            } else {
                                Log.e("DC-WeatherExtension", String.format("Forecast for %s [%d] not found", city.toString(), city.getId()));
                            }
                        }
                    });
                } else {
                    Log.e("DC-WeatherExtension", "City not found, city id: " + cityId);
                }
            }
        });
    }

    public static void updateDashClock(Context context) {
        // TODO: Disabled for the time being, as this might code be unnecessary, but thorough analysis is needed
//        ContentResolver contentResolver = context.getContentResolver();
//        contentResolver.notifyChange(getUpdateUri(), null);
    }

    private static Uri getUpdateUri() {
        return Uri.withAppendedPath(URI_BASE, UPDATE_URI_PATH_SEGMENT);
    }

    public static void saveCityId(Context context, int cityId) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putInt(CONFIGURATION_NAME, cityId);
        editor.apply();
    }

    public static int getCityid(Context context, int defaultValue) {
        return getSharedPreferences(context).getInt(CONFIGURATION_NAME, defaultValue);
    }

    protected static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

}
