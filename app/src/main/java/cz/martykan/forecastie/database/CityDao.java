package cz.martykan.forecastie.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

import cz.martykan.forecastie.models.City;

@Dao
public interface CityDao {
    @Query("SELECT * FROM city")
    List<City> getAll();

    @Query("SELECT * FROM city WHERE id = :id")
    City findById(int id);

    @Insert
    void insertAll(City... cities);

    @Update
    void persist(City city);

    @Delete
    void delete(City... city);
}
