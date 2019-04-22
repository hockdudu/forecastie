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
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.Response;
import cz.martykan.forecastie.utils.TextFormatting;

public class SimpleWidgetProvider extends AbstractWidgetProvider {

    @Override
    public void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.simple_widget);

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

                        remoteViews.setTextViewText(R.id.widgetCity, weather.getCity().toString());
                        remoteViews.setTextViewText(R.id.widgetTemperature, TextFormatting.getTemperature(context.getResources(), preferences, weather));
                        // TODO: Verify if using TextFormatting is needed -> it adds the rain info to the text
                        remoteViews.setTextViewText(R.id.widgetDescription, TextFormatting.getDescription(context.getResources(), preferences, weather));
                        remoteViews.setImageViewBitmap(R.id.widgetIcon, getWeatherIcon(TextFormatting.getIcon(context.getResources(), weather), context));

                        appWidgetManager.updateAppWidget(widgetId, remoteViews);
                    }
                }
            });
        } else {
            Log.w("SimpleWidgetProvider", String.format("Couldn't load city id for widget %d", widgetId));
        }

    }
}
