package cz.martykan.forecastie.database;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.tasks.ParseResult;
import cz.martykan.forecastie.utils.JsonParser;

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
    public boolean Add(City city) {
        if (cityDao.findById(city.getId()) == null) {
            cityDao.insertAll(city);
            return true;
        }

        return false;
    }

    public LiveData<List<Weather>> searchCity(String cityName) {
        MutableLiveData<List<Weather>> citiesLiveData = new MutableLiveData<>();

        Handler handler = new Handler();
        Runnable runnable = () -> {
            String response = downloadJson(provideCitySearchUrl(cityName));

            try {
                JSONObject reader = new JSONObject(response);

                final String code = reader.optString("cod");
                if ("404".equals(code)) {
                    Log.e("Geolocation", "No city found");
                    // TODO: What do we do now?
                }
                final JSONArray cityList = reader.getJSONArray("list");

                List<Weather> weathers = new ArrayList<>();

                for (int i = 0; i < cityList.length(); i++) {
                    JSONObject cityJSONObject = cityList.getJSONObject(i);
                    City city = JsonParser.convertJsonToCity(cityJSONObject);
                    Weather weather = JsonParser.convertJsonToWeather(cityJSONObject, city, getFormatting());

                    weathers.add(weather);
                }

                handler.post(() -> citiesLiveData.setValue(weathers));

            } catch (JSONException e) {
                Log.e("JSONException Data", response);
                e.printStackTrace();
            }
        };

        new Thread(runnable).start();

        return citiesLiveData;
    }

    public LiveData<List<City>> getCities() {
        MutableLiveData<List<City>> citiesLiveData = new MutableLiveData<>();

        Handler handler = new Handler();
        Runnable runnable = () -> {
            List<City> cities = cityDao.getAll();

            handler.post(() -> {
                citiesLiveData.setValue(cities);
            });
        };

        new Thread(runnable).start();

        return citiesLiveData;
    }

    private URL provideCitySearchUrl(String cityToSearch) {
        HashMap<String, String> params = new HashMap<>();
        params.put("q", cityToSearch);
        return provideUrl("find", params);
    }
}
