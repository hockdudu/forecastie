package cz.martykan.forecastie.utils;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;

public class JsonParser {

    public static City convertJsonToCity(JSONObject cityObject) {
        City city = new City();

        try {
            int cityId = cityObject.getInt("id");
            String cityName = cityObject.getString("name");
            String country;

            if (cityObject.has("country")) {
                country = cityObject.getString("country");
            } else {
                JSONObject countryObj = cityObject.getJSONObject("sys");
                country = countryObj.getString("country");
            }

            double lat = cityObject.getJSONObject("coord").getDouble("lat");
            double lon = cityObject.getJSONObject("coord").getDouble("lon");

            city.setId(cityId);
            city.setCity(cityName);
            city.setCountry(country);
            city.setLat(lat);
            city.setLon(lon);
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("JsonParser", "Error while parsing Json");
        }

        return city;
    }

    @NonNull
    public static Weather convertJsonToWeather(JSONObject weatherObject, City city) {
        Weather weather = new Weather();

        try {
            JSONObject main = weatherObject.getJSONObject("main");

            weather.setDate(weatherObject.getString("dt"));
            weather.setTemperature(main.getDouble("temp"));
            weather.setDescription(weatherObject.optJSONArray("weather").getJSONObject(0).getString("description"));
            JSONObject windObj = weatherObject.optJSONObject("wind");
            if (windObj != null) {
                weather.setWind(windObj.getDouble("speed"));

                if (windObj.has("deg")) {
                    weather.setWindDirectionDegree(windObj.getDouble("deg"));
                } else {
                    Log.e("parseTodayJson", "No wind direction available");
                    weather.setWindDirectionDegree(null);
                }
            }
            weather.setPressure(main.getInt("pressure"));
            weather.setHumidity(main.getInt("humidity"));

            JSONObject rainObj = weatherObject.optJSONObject("rain");
            double rain;
            if (rainObj != null) {
                rain = getRain(rainObj);
            } else {
                JSONObject snowObj = weatherObject.optJSONObject("snow");
                if (snowObj != null) {
                    rain = getRain(snowObj);
                } else {
                    rain = 0;
                }
            }
            weather.setRain(rain);

            final String weatherId = weatherObject.optJSONArray("weather").getJSONObject(0).getString("id");
            weather.setId(weatherId);

            // Only available on weather, but not on forecast
            JSONObject sysObj = weatherObject.optJSONObject("sys");
            if (sysObj != null && sysObj.has("sunrise") && sysObj.has("sunset")) {
                weather.setSunrise(sysObj.getString("sunrise"));
                weather.setSunset(sysObj.getString("sunset"));
            }

            weather.setCity(city);
            weather.setCityId(city.getId());
            weather.setLastUpdated(Calendar.getInstance().getTimeInMillis());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return weather;
    }

    public static Weather[] convertJsonToWeathers(JSONObject weathersObject) {
        Weather[] weathers;

        try {
            JSONArray array = weathersObject.getJSONArray("list");
            weathers = new Weather[array.length()];

            for (int i = 0; i < array.length(); i++) {
                JSONObject weatherObject = (JSONObject) array.get(i);

                City city = convertJsonToCity(weatherObject);
                Weather weather = convertJsonToWeather(weatherObject, city);
                weathers[i] = weather;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            weathers = new Weather[0];
        }

        return weathers;
    }

    private static double getRain(JSONObject rainObj) {
        double rain = 0;
        if (rainObj != null) {
            rain = rainObj.optDouble("3h");
            if (Double.isNaN(rain)) {
                rain = rainObj.optDouble("1h", 0);
            }
        }
        return rain;
    }

    public static List<Weather> convertJsonToForecast(JSONObject jsonObject, City city) {
        List<Weather> weatherList = new ArrayList<>();

        try {
            JSONArray weathers = jsonObject.getJSONArray("list");

            for (int i = 0; i < weathers.length(); i++) {
                JSONObject weathersJSONObject = weathers.getJSONObject(i);
                Weather weather = convertJsonToWeather(weathersJSONObject, city);
                weatherList.add(weather);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return weatherList;
    }

    public static double convertJsonToUVIndex(JSONObject jsonObject) {
        double UVIndex = 0;

        try {
            UVIndex = jsonObject.getDouble("value");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return UVIndex;
    }
}
