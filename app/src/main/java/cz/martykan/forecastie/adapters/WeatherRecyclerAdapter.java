package cz.martykan.forecastie.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.adapters.ViewHolder.WeatherViewHolder;
import cz.martykan.forecastie.utils.Formatting;
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.TextFormatting;
import cz.martykan.forecastie.utils.UnitConverter;

public class WeatherRecyclerAdapter extends RecyclerView.Adapter<WeatherViewHolder> {
    private List<Weather> itemList;
    private Context context;
    private Preferences preferences;

    public WeatherRecyclerAdapter(Context context, List<Weather> itemList) {
        this.itemList = itemList;
        this.context = context;
        this.preferences = Preferences.getInstance(PreferenceManager.getDefaultSharedPreferences(context), context.getResources());
    }

    @NonNull
    @Override
    public WeatherViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list_row, viewGroup, false);

        return new WeatherViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WeatherViewHolder customViewHolder, int i) {
        Weather weatherItem = itemList.get(i);

        TimeZone tz = TimeZone.getDefault();
        String dateFormat = preferences.getDateFormat();
        String dateString;
        try {
            SimpleDateFormat resultFormat = new SimpleDateFormat(dateFormat);
            resultFormat.setTimeZone(tz);
            dateString = resultFormat.format(weatherItem.getDate());
        } catch (IllegalArgumentException e) {
            dateString = context.getResources().getString(R.string.error_dateFormat);
        }

        if (preferences.areDaysDifferentiatedWithColor()) {
            Date now = new Date();
            /* Unfortunately, the getColor() that takes a theme (the next commented line) is Android 6.0 only, so we have to do it manually
             * customViewHolder.itemView.setBackgroundColor(context.getResources().getColor(R.attr.colorTintedBackground, context.getTheme())); */
            int color;
            if (weatherItem.getNumDaysFrom(now) > 1) {
                TypedArray ta = context.obtainStyledAttributes(new int[]{R.attr.colorTintedBackground, R.attr.colorBackground});
                if (weatherItem.getNumDaysFrom(now) % 2 == 1) {
                    color = ta.getColor(0, context.getResources().getColor(R.color.colorTintedBackground));
                } else {
                    /* We must explicitly set things back, because RecyclerView seems to reuse views and
                     * without restoring back the "normal" color, just about everything gets tinted if we
                     * scroll a couple of times! */
                    color = ta.getColor(1, context.getResources().getColor(R.color.colorBackground));
                }
                ta.recycle();
                customViewHolder.itemView.setBackgroundColor(color);
            }
        }

        customViewHolder.itemDate.setText(dateString);
        customViewHolder.itemTemperature.setText(TextFormatting.getTemperature(context.getResources(), preferences, weatherItem));
        customViewHolder.itemDescription.setText(TextFormatting.getDescription(context.getResources(), preferences, weatherItem));

        customViewHolder.itemIcon.setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/weather.ttf"));
        customViewHolder.itemIcon.setText(TextFormatting.getIcon(context.getResources(), weatherItem));

        customViewHolder.itemWind.setText(TextFormatting.getWindSpeed(context.getResources(), preferences, weatherItem));
        customViewHolder.itemPressure.setText(TextFormatting.getPressure(context.getResources(), preferences, weatherItem));
        customViewHolder.itemHumidity.setText(TextFormatting.getHumidity(context.getResources(), weatherItem));
    }

    @Override
    public int getItemCount() {
        return (null != itemList ? itemList.size() : 0);
    }
}
