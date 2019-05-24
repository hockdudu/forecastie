package cz.martykan.forecastie.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.Response;

import static cz.martykan.forecastie.widgets.WidgetDataRepository.getWeatherRepository;

public abstract class AbstractWidgetProvider extends AppWidgetProvider {

    public static final int DASHCLOCK_WIDGET_ID = 0;

    protected static final String PREFS_NAME = "Widget";

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);

        Log.v(this.getClass().getSimpleName(), "Disable updates for this widget");
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Log.v(this.getClass().getSimpleName(), "Widget updated, id " + appWidgetId);
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Log.v(this.getClass().getSimpleName(), "Widget deleted, id " + appWidgetId);
            removeCityId(context, appWidgetId);
        }
    }

    protected static Bitmap getWeatherIcon(String text, Context context) {
        Bitmap myBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_4444);
        Canvas myCanvas = new Canvas(myBitmap);
        Paint paint = new Paint();
        Typeface clock = Typeface.createFromAsset(context.getAssets(), "fonts/weather.ttf");
        paint.setAntiAlias(true);
        paint.setSubpixelText(true);
        paint.setTypeface(clock);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(150);
        paint.setTextAlign(Paint.Align.CENTER);
        myCanvas.drawText(text, 128, 180, paint);
        return myBitmap;
    }

    public static void updateWidgets(Context context) {
        updateWidgets(context, ExtensiveWidgetProvider.class);
        updateWidgets(context, TimeWidgetProvider.class);
        updateWidgets(context, SimpleWidgetProvider.class);
    }

    private static void updateWidgets(Context context, Class widgetClass) {
        Intent intent = new Intent(context.getApplicationContext(), widgetClass).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = AppWidgetManager.getInstance(context.getApplicationContext()).getAppWidgetIds(new ComponentName(context.getApplicationContext(), widgetClass));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.getApplicationContext().sendBroadcast(intent);
    }

    protected static void setTheme(Context context, RemoteViews remoteViews) {
        Preferences preferences = Preferences.getInstance(PreferenceManager.getDefaultSharedPreferences(context), context.getResources());

        // TODO: Make transparency configurable on a per widget basis
        if (preferences.isWidgetTransparent()) {
            remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card_transparent);
            return;
        }

        switch (preferences.getTheme()) {
            case "dark":
            case "classicdark":
                remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card_dark);
                break;
            case "black":
            case "classicblack":
                remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card_black);
                break;
            case "classic":
                remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card_classic);
                break;
            default:
                remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card);
                break;
        }
    }

    protected static LiveResponse<Weather> getCurrentWeather(Context context, int cityId) {
        LiveResponse<Weather> liveResponse = new LiveResponse<>();
        MutableLiveData<Weather> weatherMutableLiveData = new MutableLiveData<>();
        liveResponse.setLiveData(weatherMutableLiveData);

        LiveResponse<City> cityLiveResponse = WidgetDataRepository.getCityRepository(context).getCity(cityId);

        // .observeForever(Observer) is needed, because it's triggered even after the lifecycle died.
        // The lifecycle dies e.g. after WidgetConfigureActivity finishes
        cityLiveResponse.getLiveData().observeForever(new Observer<City>() {
            @Override
            public void onChanged(@Nullable City city) {
                cityLiveResponse.getLiveData().removeObserver(this);

                if (cityLiveResponse.getStatus() == Response.Status.SUCCESS) {
                    assert city != null;

                    LiveResponse<Weather> weatherLiveResponse = getWeatherRepository(context).getCurrentWeather(city, false);

                    weatherLiveResponse.getLiveData().observeForever(new Observer<Weather>() {
                        @Override
                        public void onChanged(@Nullable Weather weather) {
                            weatherLiveResponse.getLiveData().removeObserver(this);

                            liveResponse.setResponse(weatherLiveResponse);
                            if (weatherLiveResponse.getStatus() == Response.Status.SUCCESS) {
                                weatherMutableLiveData.setValue(weather);
                            } else {
                                weatherMutableLiveData.setValue(null);
                            }
                        }
                    });
                } else {
                    liveResponse.setResponse(cityLiveResponse);
                    weatherMutableLiveData.setValue(null);
                }
            }
        });

        return liveResponse;
    }

    public static void saveCityId(Context context, int widgetId, int cityId) {
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.putInt(widgetConfigurationName(widgetId), cityId);
        editor.apply();
    }

    public static int getCityId(Context context, int widgetId, int defaultValue) {
        return getSharedPreferences(context).getInt(widgetConfigurationName(widgetId), defaultValue);
    }

    public static void removeCityId(Context context, int widgetId) {
        Map<String, ?> citiesList = getSharedPreferences(context).getAll();

        Runnable runnable = () -> {
            String currentWidgetConfigurationName = widgetConfigurationName(widgetId);
            int cityId = (Integer) citiesList.get(currentWidgetConfigurationName);

            for (Map.Entry<String, ?> entry : citiesList.entrySet()) {
                if (entry.getKey().equals(currentWidgetConfigurationName)) {
                    continue;
                }

                if ((Integer) entry.getValue() == cityId) {
                    return;
                }
            }

            WidgetDataRepository.getCityRepository(context).unsetCityAsWidget(cityId);
        };

        new Thread(runnable).start();

        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.remove(widgetConfigurationName(widgetId));
        editor.apply();
    }

    public static int[] getUsedCities(Context context) {
        Map<String, ?> citiesList = getSharedPreferences(context).getAll();

        Set<Integer> cities = new TreeSet<>();

        for (Map.Entry<String, ?> entry : citiesList.entrySet()) {
            cities.add((Integer) entry.getValue());
        }

        int[] citiesArray = new int[cities.size()];
        Iterator<Integer> citiesIterator = cities.iterator();

        for (int i = 0; i < cities.size(); i++) {
            citiesArray[i] = citiesIterator.next();
        }

        return citiesArray;
    }

    protected static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    protected static String widgetConfigurationName(int widgetId) {
        return "widget-" + widgetId;
    }

    public abstract void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId);
}
