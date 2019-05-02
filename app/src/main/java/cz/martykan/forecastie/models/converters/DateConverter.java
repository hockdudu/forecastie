package cz.martykan.forecastie.models.converters;

import android.arch.persistence.room.TypeConverter;

import java.util.Date;

public class DateConverter {
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
