package cz.martykan.forecastie.database;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.location.Location;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.JsonParser;
import cz.martykan.forecastie.utils.LiveResponse;
import cz.martykan.forecastie.utils.Response;

public class CityRepository extends AbstractRepository {
    private CityDao cityDao;

    public CityRepository(CityDao cityDao, Context context) {
        super(context);
        this.cityDao = cityDao;
    }

    /**
     * @param city The City object to save
     * @return True if the city was successfully saved, false if it already existed
     */
    public boolean addCity(City city) {
        if (cityDao.findById(city.getId()) == null) {
            cityDao.insertAll(city);
            return true;
        }

        return false;
    }

    public LiveResponse<List<Weather>> searchCity(String cityName) {
        LiveResponse<List<Weather>> weatherLiveResponse = new LiveResponse<>();
        MutableLiveData<List<Weather>> citiesLiveData = new MutableLiveData<>();
        weatherLiveResponse.setLiveData(citiesLiveData);

        Runnable runnable = () -> {
            weatherLiveResponse.setResponse(downloadJson(provideCitySearchUrl(cityName)));

            try {
                JSONObject reader = new JSONObject(weatherLiveResponse.getDataString());

                // TODO: Does it need to be a string?
                final String code = reader.optString("cod");
                if ("404".equals(code)) {
                    Log.e("Geolocation", "No city found");
                    weatherLiveResponse.setStatus(Response.Status.CITY_NOT_FOUND);
                    weatherLiveResponse.setLiveData(null);
                    return;
                }

                JSONArray cityList = reader.getJSONArray("list");

                List<Weather> weathers = new ArrayList<>();

                for (int i = 0; i < cityList.length(); i++) {
                    JSONObject cityJSONObject = cityList.getJSONObject(i);
                    City city = JsonParser.convertJsonToCity(cityJSONObject);
                    Weather weather = JsonParser.convertJsonToWeather(cityJSONObject, city, getFormatting());

                    weathers.add(weather);
                }

                citiesLiveData.postValue(weathers);

            } catch (JSONException e) {
                Log.e("JSONException Data", weatherLiveResponse.getDataString());
                e.printStackTrace();
                weatherLiveResponse.setStatus(Response.Status.JSON_EXCEPTION);
                citiesLiveData.postValue(null);
            }
        };

        new Thread(runnable).start();

        return weatherLiveResponse;
    }

    public LiveResponse<City> getCity(int cityId) {
        LiveResponse<City> cityLiveResponse = new LiveResponse<>();
        MutableLiveData<City> cityLiveData = new MutableLiveData<>();
        cityLiveResponse.setLiveData(cityLiveData);

        Runnable runnable = () -> {
            City city = cityDao.findById(cityId);

            if (city == null) {
                cityLiveResponse.setResponse(downloadJson(provideCityIdUrl(cityId)));

                if (cityLiveResponse.getStatus() == Response.Status.SUCCESS) {
                    try {
                        JSONObject cityObject = new JSONObject(cityLiveResponse.getDataString());
                        city = JsonParser.convertJsonToCity(cityObject);

                        if (city != null) {
                            addCity(city);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        cityLiveResponse.setStatus(Response.Status.JSON_EXCEPTION);
                    }
                }
            } else {
                cityLiveResponse.setStatus(Response.Status.SUCCESS);
            }

            cityLiveData.postValue(city);
        };

        new Thread(runnable).start();

        return cityLiveResponse;
    }

    public LiveResponse<List<City>> getCities() {
        LiveResponse<List<City>> cityLiveResponse = new LiveResponse<>();
        MutableLiveData<List<City>> citiesLiveData = new MutableLiveData<>();
        cityLiveResponse.setLiveData(citiesLiveData);

        Runnable runnable = () -> {
            List<City> cities = cityDao.getAll();

            cityLiveResponse.setStatus(Response.Status.SUCCESS);
            citiesLiveData.postValue(cities);
        };

        new Thread(runnable).start();

        return cityLiveResponse;
    }

    public LiveResponse<City> findCity(Location location) {
        LiveResponse<City> cityLiveResponse = new LiveResponse<>();
        MutableLiveData<City> cityLiveData = new MutableLiveData<>();
        cityLiveResponse.setLiveData(cityLiveData);

        Runnable runnable = () -> {
            City city = null;

            cityLiveResponse.setResponse(downloadJson(provideCityLocationSearchUrl(location)));

            if (cityLiveResponse.getStatus() == Response.Status.SUCCESS) {
                try {
                    JSONObject reader = new JSONObject(cityLiveResponse.getDataString());
                    city = JsonParser.convertJsonToCity(reader);
                } catch (JSONException e) {
                    e.printStackTrace();
                    cityLiveResponse.setStatus(Response.Status.JSON_EXCEPTION);
                }
            }

            cityLiveData.postValue(city);

        };

        new Thread(runnable).start();

        return cityLiveResponse;
    }

    private URL provideCityLocationSearchUrl(Location location) {
        HashMap<String, String> params = new HashMap<>();
        params.put("lat", String.valueOf(location.getLatitude()));
        params.put("lon", String.valueOf(location.getLongitude()));
        return provideUrl("weather", params);
    }

    private URL provideCitySearchUrl(String cityToSearch) {
        HashMap<String, String> params = new HashMap<>();
        params.put("q", cityToSearch);
        return provideUrl("find", params);
    }

    private URL provideCityIdUrl(int cityId) {
        HashMap<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(cityId));
        return provideUrl("weather", params);
    }
}
