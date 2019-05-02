package cz.martykan.forecastie.database;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
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

    public void persistWeathers(Weather... weathers) {
        weatherDao.insertAll(weathers);
    }

    // TODO: Get forecast as a fallback if current weather is too old and there's no internet
    public LiveResponse<Weather> getCurrentWeather(City city, boolean forceDownload) {
        LiveResponse<Weather> weatherLiveResponse = new LiveResponse<>();
        MutableLiveData<Weather> weatherLiveData = new MutableLiveData<>();
        weatherLiveResponse.setLiveData(weatherLiveData);

        Runnable runnable = () -> {
            Weather currentWeather = null;
            if (city.getCurrentWeatherId() != null) {
                currentWeather = weatherDao.findByUid(city.getCurrentWeatherId());
            }

            if (forceDownload || currentWeather == null || isWeatherTooOld(currentWeather)) {
                Weather downloadedWeather = null;

                weatherLiveResponse.setResponse(downloadJson(provideWeatherUrl(city)));

                if (weatherLiveResponse.getStatus() == Response.Status.SUCCESS) {
                    Response uvResponse = downloadJson(provideUVIndexUrl(city));

                    try {
                        JSONObject jsonObject = new JSONObject(weatherLiveResponse.getDataString());
                        downloadedWeather = JsonParser.convertJsonToWeather(jsonObject, city);

                        JSONObject uvJsonObject = new JSONObject(uvResponse.getDataString());
                        double currentUVIndex = JsonParser.convertJsonToUVIndex(uvJsonObject);

                        downloadedWeather.setUvIndex(currentUVIndex);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        weatherLiveResponse.setStatus(Response.Status.JSON_EXCEPTION);
                    }

                    if (downloadedWeather != null) {
                        // TODO: Fix SQL Exception!
                        /*
                        What might happen: The downloaded weather is given a city, but the city is
                        deleted right before inserting it (on another thread). Again, this happens
                        mostly when opening the app on a new location. The app requests the current
                        weather, it's downloaded but at the same time there's a new location, so the
                        old one is deleted
                         */
                        long id = weatherDao.insertAll(downloadedWeather)[0];
                        downloadedWeather.setUid(id);
                        city.setCurrentWeatherId(id);
                        cityDao.persist(city);

                        if (currentWeather != null) {
                            weatherDao.delete(currentWeather);
                        }

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

    // TODO: Use this in more places
    public LiveResponse<List<Weather>> getCurrentWeathers(List<City> cities, boolean forceDownload) {
        LiveResponse<List<Weather>> weathersLiveResponse = new LiveResponse<>();
        MutableLiveData<List<Weather>> weathersLiveData = new MutableLiveData<>();
        weathersLiveResponse.setLiveData(weathersLiveData);

        Runnable runnable = () -> {
            List<City> outdatedCities;
            List<Weather> currentWeathers = new ArrayList<>();

            if (forceDownload) {
                outdatedCities = cities;
            } else {
                outdatedCities = new ArrayList<>();

                for (City city : cities) {
                    Weather currentWeather = null;

                    if (city.getCurrentWeatherId() != null) {
                        currentWeather = weatherDao.findByUid(city.getCurrentWeatherId());
                    }

                    if (currentWeather == null || isWeatherTooOld(currentWeather)) {
                        outdatedCities.add(city);
                    } else {
                        currentWeathers.add(currentWeather);
                    }
                }
            }

            if (outdatedCities.size() != 0) {
                URL[] urls = provideWeathersUrl(outdatedCities);

                for (URL url : urls) {
                    Response weathersResponse = downloadJson(url);

                    if (weathersResponse.getStatus() != Response.Status.SUCCESS) {
                        weathersLiveResponse.setResponse(weathersResponse);
                        break;
                    }

                    try {
                        // TODO: Update UV Index?
                        JSONObject jsonObject = new JSONObject(weathersResponse.getDataString());
                        Weather[] weathers = JsonParser.convertJsonToWeathers(jsonObject);
                        currentWeathers.addAll(Arrays.asList(weathers));
                    } catch (JSONException e) {
                        weathersLiveResponse.setStatus(Response.Status.JSON_EXCEPTION);
                        e.printStackTrace();
                        break;
                    }
                }
            }

            weathersLiveData.postValue(currentWeathers);
        };

        new Thread(runnable).start();

        return weathersLiveResponse;
    }

    public LiveResponse<List<Weather>> getWeatherForecast(City city, boolean forceDownload) {
        LiveResponse<List<Weather>> weatherLiveResponse = new LiveResponse<>();
        MutableLiveData<List<Weather>> weatherLiveData = new MutableLiveData<>();
        weatherLiveResponse.setLiveData(weatherLiveData);

        Runnable runnable = () -> {
            List<Weather> weathers = Collections.emptyList();
            if (city.getCurrentWeatherId() != null) {
                weathers = weatherDao.findForecast(city.getId(), city.getCurrentWeatherId());
            }

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
                        downloadedForecast = JsonParser.convertJsonToForecast(jsonObject, city);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        weatherLiveResponse.setStatus(Response.Status.JSON_EXCEPTION);
                    }

                    if (downloadedForecast.size() != 0) {
                        // TODO: Fix rare SQL Exception!
                        /*
                        SQL Exception, FOREIGN KEY constraint fails

                        What might be the problem: On the MainActivity, both updateTodayWeather()
                        and updateForecast() query for the city. Both receive it with it's current
                        weather ID. But updateTodayWeather() changes it and updateForecast() still
                        has a reference to the old one. So the "weathers" list also has the current
                        weather. We can't delete it, what causes the constraint failure.

                        Possible fix: Every weather has its purpose flag, like "current" and
                        "forecast".
                        It could also be a way of handling temporary cities for the current
                        location.
                         */
                        weatherDao.delete(weathers.toArray(new Weather[0]));

                        /*
                        SQL Exception, FOREIGN KEY constraint fails

                        Now that's other problem: On current location, the cities might get deleted
                        just like that. The city that was downloaded here has been deleted, because
                        the current location updated.

                        This can be fixed easily with a check in a transaction, yes, but: the waste
                        of bandwidth isn't acceptable! But I'm still to find a way to solve this
                        problem. Maybe add a delay to the current location? Or only update when the
                        location update succeeds / fails
                         */
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

    private URL[] provideWeathersUrl(List<City> cities) {
        int numberOfGroupsOf20 = cities.size() / 20 + 1;
        URL[] urls = new URL[numberOfGroupsOf20];

        for (int i = 0; i < numberOfGroupsOf20; i++) {
            HashMap<String, String> params = new HashMap<>();
            StringBuilder stringBuilder = new StringBuilder();

            int currentGroupIndexStart = 20 * i;
            for (int j = currentGroupIndexStart; j < currentGroupIndexStart + 20 && j < cities.size(); j++) {
                stringBuilder.append(cities.get(j).getId());
                if (j != currentGroupIndexStart + 19 && j != cities.size() - 1) {
                    stringBuilder.append(",");
                }

            }

            params.put("id", stringBuilder.toString());

            urls[i] = provideUrl("group", params);
        }

        return urls;
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
