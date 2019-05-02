package cz.martykan.forecastie.widgets;

import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.activities.SplashActivity;
import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.Response;
import cz.martykan.forecastie.utils.UnitConverter;

public class DashClockWeatherExtension extends DashClockExtension {
    private static final String UPDATE_INTENT = "updated";

    private Preferences preferences;

    private DashClockBroadcastReceiver broadcastReceiver;

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);

        if (broadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        }

        preferences = Preferences.getInstance(PreferenceManager.getDefaultSharedPreferences(this), getResources());

        IntentFilter intentFilter = new IntentFilter(UPDATE_INTENT);
        broadcastReceiver = new DashClockBroadcastReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    // TODO: Use common parser
    @Override
    protected void onUpdateData(int reason) {
        Log.v("DC-WeatherExtension", "Data update requested, reason: " + reason);

        // TODO: Add check if 0
        int cityId = AbstractWidgetProvider.getCityId(this, 0, 0);
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (broadcastReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        }
    }

    public static void updateDashClock(Context context) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(UPDATE_INTENT));
    }

    private class DashClockBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            onUpdateData(UPDATE_REASON_MANUAL);
        }
    }
}
