package cz.martykan.forecastie.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.RemoteViews;

import cz.martykan.forecastie.AlarmReceiver;
import cz.martykan.forecastie.activities.MainActivity;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Response;
import cz.martykan.forecastie.utils.UnitConverter;

public class SimpleWidgetProvider extends AbstractWidgetProvider {

    @Override
    public void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.simple_widget);

        setTheme(context, remoteViews);

        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.widgetButtonRefresh, pendingIntent);

        Intent intent2 = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent2 = PendingIntent.getActivity(context, 0, intent2, 0);
        remoteViews.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent2);

        int currentCityId = getCityId(context, widgetId, 0);
        if (currentCityId != 0) {
            LiveResponse<Weather> weatherLiveResponse = getCurrentWeather(context, currentCityId);
            weatherLiveResponse.getLiveData().observeForever(new Observer<Weather>() {
                @Override
                public void onChanged(@Nullable Weather weather) {
                    weatherLiveResponse.getLiveData().removeObserver(this);
                    if (weatherLiveResponse.getStatus() == Response.Status.SUCCESS) {
                        assert weather != null;

                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

                        remoteViews.setTextViewText(R.id.widgetCity, weather.getCity().toString());

                        String temperature = context.getString(R.string.format_temperature, UnitConverter.convertTemperature(weather.getTemperature(), sp), sp.getString("unit", "°C"));
                        remoteViews.setTextViewText(R.id.widgetTemperature, temperature);
                        remoteViews.setTextViewText(R.id.widgetDescription, weather.getDescription());
                        remoteViews.setImageViewBitmap(R.id.widgetIcon, getWeatherIcon(weather.getIcon(), context));

                        appWidgetManager.updateAppWidget(widgetId, remoteViews);
                    }
                }
            });

            weatherLiveResponse.getLiveData().observe(ProcessLifecycleOwner.get(), weather -> {

                // TODO: else...?
            });
        } else {
            Log.w("SimpleWidgetProvider", String.format("Couldn't load city id for widget %d", widgetId));
        }

    }
}
