package cz.martykan.forecastie.adapters.ViewHolder;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import cz.martykan.forecastie.R;

public class WeatherViewHolder extends RecyclerView.ViewHolder {
    public TextView itemDate;
    public TextView itemTemperature;
    public TextView itemDescription;
    public TextView itemWind;
    public TextView itemPressure;
    public TextView itemHumidity;
    public TextView itemIcon;
    public View lineView;

    public WeatherViewHolder(View view) {
        super(view);
        this.itemDate = view.findViewById(R.id.itemDate);
        this.itemTemperature = view.findViewById(R.id.itemTemperature);
        this.itemDescription = view.findViewById(R.id.itemDescription);
        this.itemWind = view.findViewById(R.id.itemWind);
        this.itemPressure = view.findViewById(R.id.itemPressure);
        this.itemHumidity = view.findViewById(R.id.itemHumidity);
        this.itemIcon = view.findViewById(R.id.itemIcon);
        this.lineView = view.findViewById(R.id.lineView);
    }
}
