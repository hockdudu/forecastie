package cz.martykan.forecastie.widgets;

import android.content.Context;

import cz.martykan.forecastie.database.AppDatabase;
import cz.martykan.forecastie.database.CityDao;
import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.database.WeatherDao;
import cz.martykan.forecastie.database.WeatherRepository;

final class WidgetDataRepository {

    private static WeatherRepository weatherRepository;
    private static CityRepository cityRepository;

    static WeatherRepository getWeatherRepository(Context context) {
        if (weatherRepository == null) {
            WeatherDao weatherDao = AppDatabase.getDatabase(context).weatherDao();
            CityDao cityDao = AppDatabase.getDatabase(context).cityDao();
            weatherRepository = new WeatherRepository(weatherDao, cityDao, context);
        }

        return weatherRepository;
    }

    static CityRepository getCityRepository(Context context) {
        if (cityRepository == null) {
            CityDao cityDao = AppDatabase.getDatabase(context).cityDao();
            cityRepository = new CityRepository(cityDao, context);
        }

        return cityRepository;
    }
}
