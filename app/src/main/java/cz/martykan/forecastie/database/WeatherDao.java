package cz.martykan.forecastie.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.support.annotation.Nullable;

import java.util.List;

import cz.martykan.forecastie.models.Weather;

@Dao
public interface WeatherDao {
    @Nullable
    @Query("SELECT * FROM weather WHERE uid = :uid")
    Weather findByUid(long uid);

    @Query("SELECT * FROM weather WHERE cityId = :cityId AND uid <> :currentForecastId")
    List<Weather> findForecast(int cityId, long currentForecastId);

    @Insert
    long[] insertAll(Weather... weathers);

    @Delete
    void delete(Weather... weather);
}
