package cz.martykan.forecastie.database;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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

    public LiveData<Weather> getCurrentWeather(City city, boolean forceDownload) {
        MutableLiveData<Weather> weather = new MutableLiveData<>();

        Runnable runnable = () -> {
            Weather currentWeather = weatherDao.findByUid(city.getCurrentWeatherId());

            if (forceDownload || currentWeather == null || isWeatherTooOld(currentWeather)) {
                Weather downloadedWeather = downloadCurrentWeather(city);

                if (downloadedWeather != null) {
                    // TODO: Should downloadCurrentWeather set the city's current weather?
                    if (currentWeather != null) {
                        weatherDao.delete(currentWeather);
                    }
                    currentWeather = downloadedWeather;
                }
            }

            // We don't need to query cityId, because cities should only have weathers
            // that have the city as the cityId
            // TODO: What if weather is still null?
            currentWeather.setCity(city);

            weather.postValue(currentWeather);
        };

        new Thread(runnable).start();

        return weather;
    }

    public LiveData<List<Weather>> getWeatherForecast(City city, boolean forceDownload) {
        MutableLiveData<List<Weather>> weatherList = new MutableLiveData<>();

        Runnable runnable = () -> {
            List<Weather> weathers = weatherDao.findForecast(city.getId(), city.getCurrentWeatherId());

            boolean weatherMustBeRedownloaded = false;

            if (!forceDownload) {
                for (Weather weather : weathers) {
                    if (isWeatherTooOld(weather)) {
                        weatherMustBeRedownloaded = true;
                        break;
                    }
                }
            }

            if (weatherMustBeRedownloaded || forceDownload || weathers.size() == 0) {
                List<Weather> downloadedForecast = downloadCurrentForecast(city);

                if (downloadedForecast.size() != 0) {
                    weatherDao.delete(weathers.toArray(new Weather[0]));
                    weatherDao.insertAll(downloadedForecast.toArray(new Weather[0]));
                    weathers = downloadedForecast;
                }
            }

            weatherList.postValue(weathers);
        };

        new Thread(runnable).start();

        return weatherList;
    }

    @Nullable
    private Weather downloadCurrentWeather(City city) {
        Weather weather = null;

        String response = downloadJson(provideWeatherUrl(city));
        double currentUVIndex = downloadCurrentUVIndex(city);

        try {
            JSONObject jsonObject = new JSONObject(response);
            weather = JsonParser.convertJsonToWeather(jsonObject, city, getFormatting());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (weather != null) {
            long id = weatherDao.insert(weather);
            weather.setUid(id);
            weather.setUvIndex(currentUVIndex);
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

    private List<Weather> downloadCurrentForecast(City city) {
        List<Weather> weathers = new ArrayList<>();

        String response = downloadJson(provideForecastUrl(city));

        try {
            JSONObject jsonObject = new JSONObject(response);
            weathers = JsonParser.convertJsonToForecast(jsonObject, city, getFormatting());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return weathers;
    }

    private URL provideForecastUrl(City city) {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(city.getId()));
        return provideUrl("forecast", params);
    }

    private double downloadCurrentUVIndex(City city) {
        double currentUVIndex = 0;

        String response = downloadJson(provideUVIndexUrl(city));

        try {
            JSONObject jsonObject = new JSONObject(response);
            currentUVIndex = JsonParser.convertJsonToUVIndex(jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return currentUVIndex;
    }

    private URL provideUVIndexUrl(City city) {
        HashMap<String, String> params = new HashMap<>();
        params.put("lat", String.valueOf(city.getLat()));
        params.put("lon", String.valueOf(city.getLon()));
        return provideUrl("uvi", params);
    }

    private boolean isWeatherTooOld(@NonNull Weather weather) {
        return (Calendar.getInstance().getTimeInMillis() - weather.getLastUpdated()) > UPDATE_THRESHOLD;
    }
}
