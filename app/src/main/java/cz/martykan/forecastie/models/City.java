package cz.martykan.forecastie.models;


import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;

import java.io.Serializable;

@Entity(foreignKeys = {@ForeignKey(entity = Weather.class, childColumns = "currentWeatherId", parentColumns = "uid", onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT)})
public class City implements Serializable {

    public static final int USAGE_USER = 1;
    public static final int USAGE_WIDGET = 1 << 1;
    public static final int USAGE_CURRENT_LOCATION = 1 << 2;

    @IntDef(flag = true, value = {
            USAGE_USER,
            USAGE_WIDGET,
            USAGE_CURRENT_LOCATION
    })
    private @interface CityUsage {}

    @PrimaryKey
    private int id;
    private String city;
    private String country;
    private double lat;
    private double lon;
    @Nullable @ColumnInfo(index = true)
    private Long currentWeatherId;
    @CityUsage
    private int cityUsage = 0;

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

    public @CityUsage int getCityUsage() {
        return cityUsage;
    }

    public void setCityUsage(@CityUsage int cityUsage) {
        this.cityUsage = cityUsage;
    }

    @Override
    public String toString() {
        return String.format("%s, %s", city, country);
    }

}
