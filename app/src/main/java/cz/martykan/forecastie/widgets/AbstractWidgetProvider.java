package cz.martykan.forecastie.widgets;

import android.app.AlarmManager;
import android.app.PendingIntent;
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
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityDao;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.database.WeatherDao;
import cz.martykan.forecastie.database.WeatherRepository;
import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Response;

public abstract class AbstractWidgetProvider extends AppWidgetProvider {
    protected static final long DURATION_MINUTE = TimeUnit.SECONDS.toMillis(30);
    protected static final String ACTION_UPDATE_TIME = "cz.martykan.forecastie.UPDATE_TIME";

    protected static final String PREFS_NAME = "Widget";

    private WeatherRepository weatherRepository;
    private CityRepository cityRepository;

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: Check why this is being called many times on widget creation
        if (ACTION_UPDATE_TIME.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName provider = new ComponentName(context.getPackageName(), getClass().getName());
            int[] ids = appWidgetManager.getAppWidgetIds(provider);
            onUpdate(context, appWidgetManager, ids);
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);

        Log.v(this.getClass().getSimpleName(), "Disable updates for this widget");
        cancelUpdate(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Log.v(this.getClass().getSimpleName(), "Widget updated, id " + appWidgetId);
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        scheduleNextUpdate(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Log.v(this.getClass().getSimpleName(), "Widget deleted, id " + appWidgetId);
            removeCityId(context, appWidgetId);
        }
    }

    protected Bitmap getWeatherIcon(String text, Context context) {
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

    protected void setTheme(Context context, RemoteViews remoteViews) {
        // TODO: Make transparency configurable on a per widget basis
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("transparentWidget", false)) {
            remoteViews.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.widget_card_transparent);
            return;
        }
        String theme = PreferenceManager.getDefaultSharedPreferences(context).getString("theme", "fresh");
        switch (theme) {
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

    protected void scheduleNextUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) Objects.requireNonNull(context.getSystemService(Context.ALARM_SERVICE));
        long now = new Date().getTime();
        long nextUpdate = now + DURATION_MINUTE - now % DURATION_MINUTE;
        Log.v(this.getClass().getSimpleName(), "Next widget update: " + android.text.format.DateFormat.getTimeFormat(context).format(new Date(nextUpdate)));
        if (Build.VERSION.SDK_INT >= 19) {
            alarmManager.setExact(AlarmManager.RTC, nextUpdate, getTimeIntent(context));
        } else {
            alarmManager.set(AlarmManager.RTC, nextUpdate, getTimeIntent(context));
        }
    }

    protected void cancelUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) Objects.requireNonNull(context.getSystemService(Context.ALARM_SERVICE));
        alarmManager.cancel(getTimeIntent(context));
    }

    protected PendingIntent getTimeIntent(Context context) {
        Intent intent = new Intent(context, this.getClass());
        intent.setAction(ACTION_UPDATE_TIME);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    protected LiveResponse<Weather> getCurrentWeather(Context context, int cityId) {
        LiveResponse<Weather> liveResponse = new LiveResponse<>();
        MutableLiveData<Weather> weatherMutableLiveData = new MutableLiveData<>();
        liveResponse.setLiveData(weatherMutableLiveData);

        LiveResponse<City> cityLiveResponse = getCityRepository(context).getCity(cityId);

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

    protected WeatherRepository getWeatherRepository(Context context) {
        if (weatherRepository == null) {
            WeatherDao weatherDao = AppDatabase.getDatabase(context).weatherDao();
            CityDao cityDao = AppDatabase.getDatabase(context).cityDao();
            weatherRepository = new WeatherRepository(weatherDao, cityDao, context);
        }

        return weatherRepository;
    }

    protected CityRepository getCityRepository(Context context) {
        if (cityRepository == null) {
            CityDao cityDao = AppDatabase.getDatabase(context).cityDao();
            cityRepository = new CityRepository(cityDao, context);
        }

        return cityRepository;
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
        SharedPreferences.Editor editor = getSharedPreferences(context).edit();
        editor.remove(widgetConfigurationName(widgetId));
        editor.apply();
    }

    protected static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    protected static String widgetConfigurationName(int widgetId) {
        return "widget-" + widgetId;
    }

    public abstract void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId);
}
