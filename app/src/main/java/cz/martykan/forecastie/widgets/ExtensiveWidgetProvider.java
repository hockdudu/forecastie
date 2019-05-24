package cz.martykan.forecastie.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.RemoteViews;

import cz.martykan.forecastie.activities.MainActivity;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.Response;
import cz.martykan.forecastie.utils.TextFormatting;

public class ExtensiveWidgetProvider extends AbstractWidgetProvider {

    @Override
    public void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.extensive_widget);

        setTheme(context, remoteViews);

//        Intent intent = new Intent(context, AlarmReceiver.class);
//        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
//        remoteViews.setOnClickPendingIntent(R.id.widgetButtonRefresh, pendingIntent);

        Intent intent2 = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent2 = PendingIntent.getActivity(context, 0, intent2, 0);
        remoteViews.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent2);

        int currentCityId = getCityId(context, widgetId, CityRepository.INVALID_CITY);
        if (currentCityId != CityRepository.INVALID_CITY) {
            LiveResponse<Weather> weatherLiveResponse = getCurrentWeather(context, currentCityId);
            weatherLiveResponse.getLiveData().observeForever(new Observer<Weather>() {
                @Override
                public void onChanged(@Nullable Weather weather) {
                    weatherLiveResponse.getLiveData().removeObserver(this);
                    if (weatherLiveResponse.getStatus() == Response.Status.SUCCESS) {
                        assert weather != null;

                        Preferences preferences = Preferences.getInstance(PreferenceManager.getDefaultSharedPreferences(context), context.getResources());

                        remoteViews.setTextViewText(R.id.widgetCity, weather.getCity().toString());
                        remoteViews.setTextViewText(R.id.widgetCity, weather.getCity().toString());
                        remoteViews.setTextViewText(R.id.widgetTemperature, TextFormatting.getTemperature(context.getResources(), preferences, weather));
                        remoteViews.setTextViewText(R.id.widgetDescription, TextFormatting.getDescription(context.getResources(), preferences, weather));
                        remoteViews.setTextViewText(R.id.widgetWind, TextFormatting.getWindSpeed(context.getResources(), preferences, weather));
                        remoteViews.setTextViewText(R.id.widgetPressure, TextFormatting.getPressure(context.getResources(), preferences, weather));
                        remoteViews.setTextViewText(R.id.widgetHumidity, TextFormatting.getHumidity(context.getResources(), weather));
                        remoteViews.setTextViewText(R.id.widgetSunrise, TextFormatting.getSunrise(context.getResources(), weather));
                        remoteViews.setTextViewText(R.id.widgetSunset, TextFormatting.getSunset(context.getResources(), weather));
                        remoteViews.setTextViewText(R.id.widgetLastUpdate, TextFormatting.getLastUpdate(context.getResources(), weather, true));
                        remoteViews.setImageViewBitmap(R.id.widgetIcon, getWeatherIcon(TextFormatting.getIcon(context.getResources(), weather), context));

                        appWidgetManager.updateAppWidget(widgetId, remoteViews);
                    }
                }
            });
        } else {
            Log.w("ExtensiveWidgetProvider", String.format("Couldn't load city id for widget %d", widgetId));
        }
    }
}
