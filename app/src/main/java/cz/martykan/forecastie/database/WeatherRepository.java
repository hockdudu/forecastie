package cz.martykan.forecastie.database;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.JsonParser;

public class WeatherRepository extends AbstractRepository {

    private WeatherDao weatherDao;
    private CityDao cityDao;

    private final static int UPDATE_THRESHOLD = 30 * 1000;

    // TODO: Use dagger2, https://developer.android.com/jetpack/docs/guide#manage-dependencies
    public WeatherRepository(WeatherDao weatherDao, CityDao cityDao, Context context) {
        super(context);
        this.weatherDao = weatherDao;
        this.cityDao = cityDao;
    }

    public LiveData<Weather> getCurrentWeather(City city) {
        return getCurrentWeather(city, false);
    }

    public LiveData<Weather> getCurrentWeather(City city, boolean forceRedownload) {
        MutableLiveData<Weather> weather = new MutableLiveData<>();

        Handler handler = new Handler();
        Runnable runnable = () -> {
            Weather currentWeather;

            if (forceRedownload) {
                currentWeather = null;
            } else {
                currentWeather = weatherDao.findByUid(city.getCurrentWeatherId());

                if (currentWeather == null || (Calendar.getInstance().getTimeInMillis() - currentWeather.getDate().getTime()) > UPDATE_THRESHOLD) {
                    currentWeather = null;
                }
            }

            final Weather finalWeather;
            if (currentWeather != null) {
                 finalWeather = currentWeather;
            } else {
                finalWeather = downloadCurrentWeather(city);
            }

            handler.post(() -> weather.setValue(finalWeather));
        };

        new Thread(runnable).start();

        return weather;
    }

    // TODO: Implement this
    public LiveData<List<Weather>> getWeatherForecast(City city, boolean forceRedownload) {
        MutableLiveData<List<Weather>> weatherList = new MutableLiveData<>();

        return weatherList;
    }

    private Weather downloadCurrentWeather(City city) {
        Weather weather = null;

        String response = downloadJson(provideWeatherUrl(city));

        try {
            JSONObject jsonObject = new JSONObject(response);
            weather = JsonParser.convertJsonToWeather(jsonObject, city, getFormatting());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (weather != null) {
            long id = weatherDao.insert(weather);
            weather.setUid(id);
            city.setCurrentWeatherId(id);
            cityDao.persist(city);
        }

        return weather;
    }

    private URL provideWeatherUrl(City city) {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(city.getId()));
        return provideUrl("weather", params);
    }
}
