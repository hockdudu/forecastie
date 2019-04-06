package cz.martykan.forecastie.database;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.JsonParser;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Response;

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

    public LiveResponse<Weather> getCurrentWeather(City city, boolean forceDownload) {
        LiveResponse<Weather> weatherLiveResponse = new LiveResponse<>();
        MutableLiveData<Weather> weatherLiveData = new MutableLiveData<>();
        weatherLiveResponse.setLiveData(weatherLiveData);

        Runnable runnable = () -> {
            Weather currentWeather = weatherDao.findByUid(city.getCurrentWeatherId());

            if (forceDownload || currentWeather == null || isWeatherTooOld(currentWeather)) {
                Weather downloadedWeather = null;

                weatherLiveResponse.setResponse(downloadJson(provideWeatherUrl(city)));

                if (weatherLiveResponse.getStatus() == Response.Status.SUCCESS) {
                    Response uvResponse = downloadJson(provideUVIndexUrl(city));

                    double currentUVIndex = 0;

                    try {
                        JSONObject jsonObject = new JSONObject(uvResponse.getDataString());
                        currentUVIndex = JsonParser.convertJsonToUVIndex(jsonObject);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        // We don't need to set the uvResponse's status, as only
                        // weatherLiveResponse is returned
                        weatherLiveResponse.setStatus(Response.Status.JSON_EXCEPTION);
                    }

                    try {
                        JSONObject jsonObject = new JSONObject(weatherLiveResponse.getDataString());
                        downloadedWeather = JsonParser.convertJsonToWeather(jsonObject, city, getFormatting());
                    } catch (JSONException e) {
                        e.printStackTrace();
                        weatherLiveResponse.setStatus(Response.Status.JSON_EXCEPTION);
                    }

                    if (downloadedWeather != null) {
                        if (currentWeather != null) {
                            weatherDao.delete(currentWeather);
                        }

                        long id = weatherDao.insert(downloadedWeather);
                        downloadedWeather.setUid(id);
                        downloadedWeather.setUvIndex(currentUVIndex);
                        city.setCurrentWeatherId(id);
                        cityDao.persist(city);

                        currentWeather = downloadedWeather;
                    }
                } else {
                    // Triggers observers
                    // Setting status isn't needed, because it already is something else than SUCCESS
                    weatherLiveResponse.setLiveData(null);
                }
            } else {
                weatherLiveResponse.setStatus(Response.Status.SUCCESS);
            }

            if (currentWeather != null) {
                // We don't need to query cityId, because cities should only have weathers
                // that have the city as the cityId
                currentWeather.setCity(city);
            }

            weatherLiveData.postValue(currentWeather);
        };

        new Thread(runnable).start();

        return weatherLiveResponse;
    }

    public LiveResponse<List<Weather>> getWeatherForecast(City city, boolean forceDownload) {
        LiveResponse<List<Weather>> weatherLiveResponse = new LiveResponse<>();
        MutableLiveData<List<Weather>> weatherLiveData = new MutableLiveData<>();
        weatherLiveResponse.setLiveData(weatherLiveData);

        Runnable runnable = () -> {
            List<Weather> weathers = weatherDao.findForecast(city.getId(), city.getCurrentWeatherId());

            boolean isWeatherTooOld = false;

            // No need to loop if it must be downloaded anyway
            if (!forceDownload) {
                for (Weather weather : weathers) {
                    if (isWeatherTooOld(weather)) {
                        isWeatherTooOld = true;
                        break;
                    }
                }
            }

            if (isWeatherTooOld || forceDownload || weathers.size() == 0) {
                List<Weather> downloadedForecast = new ArrayList<>();

                weatherLiveResponse.setResponse(downloadJson(provideForecastUrl(city)));

                if (weatherLiveResponse.getStatus() == Response.Status.SUCCESS) {
                    try {
                        JSONObject jsonObject = new JSONObject(weatherLiveResponse.getDataString());
                        downloadedForecast = JsonParser.convertJsonToForecast(jsonObject, city, getFormatting());
                    } catch (JSONException e) {
                        e.printStackTrace();
                        weatherLiveResponse.setStatus(Response.Status.JSON_EXCEPTION);
                    }

                    if (downloadedForecast.size() != 0) {
                        weatherDao.delete(weathers.toArray(new Weather[0]));
                        weatherDao.insertAll(downloadedForecast.toArray(new Weather[0]));
                        weathers = downloadedForecast;
                    }
                }
            } else {
                weatherLiveResponse.setStatus(Response.Status.SUCCESS);
            }

            weatherLiveData.postValue(weathers);
        };

        new Thread(runnable).start();

        return weatherLiveResponse;
    }

    private URL provideWeatherUrl(City city) {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(city.getId()));
        return provideUrl("weather", params);
    }

    private URL provideForecastUrl(City city) {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(city.getId()));
        return provideUrl("forecast", params);
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
