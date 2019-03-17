package cz.martykan.forecastie.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

import cz.martykan.forecastie.models.Weather;

@Dao
public interface WeatherDao {
    @Query("SELECT * FROM weather")
    List<Weather> getAll();

    @Query("SELECT * FROM weather WHERE uid = :uid")
    Weather findByUid(long uid);

    @Insert
    long[] insertAll(Weather... weathers);

    @Insert
    long insert(Weather weather);

    @Delete
    void delete(Weather weather);
}
