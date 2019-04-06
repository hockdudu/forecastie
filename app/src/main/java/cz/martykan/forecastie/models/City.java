package cz.martykan.forecastie.models;


import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.Nullable;

import java.io.Serializable;

@Entity(foreignKeys = {@ForeignKey(entity = Weather.class, childColumns = "currentWeatherId", parentColumns = "uid", onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT)})
public class City implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String city;
    private String country;
    private double lat;
    private double lon;
    @Nullable
    private Long currentWeatherId;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    @Nullable
    public Long getCurrentWeatherId() {
        return currentWeatherId;
    }

    public void setCurrentWeatherId(@Nullable Long currentWeatherId) {
        this.currentWeatherId = currentWeatherId;
    }

    @Override
    public String toString() {
        return String.format("%s, %s", city, country);
    }
}
