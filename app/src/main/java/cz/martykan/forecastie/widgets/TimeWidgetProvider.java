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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cz.martykan.forecastie.activities.MainActivity;
import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.Response;
import cz.martykan.forecastie.utils.TextFormatting;

public class TimeWidgetProvider extends AbstractWidgetProvider {

    @Override
    public void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.time_widget);

        setTheme(context, remoteViews);

//        Intent intent = new Intent(context, AlarmReceiver.class);
//        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
//        remoteViews.setOnClickPendingIntent(R.id.widgetButtonRefresh, pendingIntent);

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

                        Preferences preferences = Preferences.getInstance(PreferenceManager.getDefaultSharedPreferences(context), context.getResources());

                        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);

                        String dateFormat = preferences.getDateFormat();
                        // TODO: I don't like the way this is done, no a single bit
                        dateFormat = dateFormat.substring(0, dateFormat.indexOf("-") - 1);
                        String dateString;

                        try {
                            SimpleDateFormat resultFormat = new SimpleDateFormat(dateFormat, Locale.getDefault());
                            dateString = resultFormat.format(new Date());
                        } catch (IllegalArgumentException e) {
                            dateString = context.getResources().getString(R.string.error_dateFormat);
                        }

                        remoteViews.setTextViewText(R.id.time, timeFormat.format(new Date()));
                        remoteViews.setTextViewText(R.id.date, dateString);
                        remoteViews.setTextViewText(R.id.widgetCity, weather.getCity().toString());
                        remoteViews.setTextViewText(R.id.widgetTemperature, TextFormatting.getTemperature(context.getResources(), preferences, weather));
                        remoteViews.setTextViewText(R.id.widgetDescription, TextFormatting.getDescription(context.getResources(), preferences, weather));
                        remoteViews.setImageViewBitmap(R.id.widgetIcon, getWeatherIcon(TextFormatting.getIcon(context.getResources(), weather), context));

                        appWidgetManager.updateAppWidget(widgetId, remoteViews);
                    }
                }
            });
        } else {
            Log.w("TimeWidgetProvider", String.format("Couldn't load city id for widget %d", widgetId));
        }
    }
}
