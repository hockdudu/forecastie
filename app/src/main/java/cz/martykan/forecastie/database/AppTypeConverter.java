package cz.martykan.forecastie.database;

import android.arch.persistence.room.TypeConverter;

import java.util.Date;

@SuppressWarnings("WeakerAccess")
public class AppTypeConverter {

    @TypeConverter
    public static long fromDate(Date date) {
        if (date != null) {
            return date.getTime();
        } else {
            return 0;
        }
    }

    @TypeConverter
    public static Date toDate(long date) {
        return new Date(date);
    }
}
