package cz.martykan.forecastie.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.models.Weather;

@Database(entities = {City.class, Weather.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    public abstract CityDao cityDao();
    public abstract WeatherDao weatherDao();

    static private AppDatabase INSTANCE;

    static public AppDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }

        return INSTANCE;
    }
}
