package cz.martykan.forecastie.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.adapters.ViewHolder.LocationViewHolder;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.Preferences;
import cz.martykan.forecastie.utils.TextFormatting;
import cz.martykan.forecastie.utils.UnitConverter;
import cz.martykan.forecastie.utils.WebViewMap;
import cz.martykan.forecastie.viewmodels.MapViewModel;

public class LocationsRecyclerAdapter extends RecyclerView.Adapter<LocationViewHolder> {

    private final ArrayList<Weather> weathers = new ArrayList<>();
    private final Context context;
    private final LayoutInflater inflater;
    private final boolean darkTheme;
    private final boolean blackTheme;
    private final Preferences preferences;

    private LocationSelectedListener locationSelectedListener = null;

    public LocationsRecyclerAdapter(@Nullable List<Weather> weathers, Context context, boolean darkTheme, boolean blackTheme) {
        if (weathers != null) {
            this.weathers.addAll(weathers);
        }
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.darkTheme = darkTheme;
        this.blackTheme = blackTheme;
        this.preferences = Preferences.getInstance(PreferenceManager.getDefaultSharedPreferences(context), context.getResources());
    }

    public void replaceList(@NonNull List<Weather> newWeathers) {
        weathers.clear();
        weathers.addAll(newWeathers);
        notifyDataSetChanged();
    }

    public void setLocationSelectedListener(LocationSelectedListener locationSelectedListener) {
        this.locationSelectedListener = locationSelectedListener;
    }

    private void dispatchLocationSelected(Weather weather) {
        if (locationSelectedListener != null) {
            locationSelectedListener.onLocationSelectedListener(weather);
        }
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LocationViewHolder locationViewHolder = new LocationViewHolder(inflater.inflate(R.layout.list_location_row, parent, false));

        locationViewHolder.itemView.setOnClickListener(v -> {
            int position = locationViewHolder.getAdapterPosition();
            Weather weather = weathers.get(position);
            dispatchLocationSelected(weather);
        });

        return locationViewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        Typeface weatherFont = Typeface.createFromAsset(context.getAssets(), "fonts/weather.ttf");
        Weather weather = weathers.get(position);

        holder.getCityTextView().setText(weather.getCity().toString());
        holder.getTemperatureTextView().setText(TextFormatting.getTemperature(context.getResources(), preferences, weather));
        holder.getDescriptionTextView().setText(TextFormatting.getDescription(context.getResources(), preferences, weather));
        holder.getIconTextView().setText(TextFormatting.getIcon(context.getResources(), weather));
        holder.getIconTextView().setTypeface(weatherFont);

        MapViewModel mapViewModel = new MapViewModel();
        mapViewModel.mapLat = weather.getCity().getLat();
        mapViewModel.mapLon = weather.getCity().getLon();

        WebViewMap webViewMap = new WebViewMap(holder.getWebView(), mapViewModel);
        webViewMap.initialize(true);

        // TODO: Setting the theme this way can't possibly be the best solution
        if (darkTheme || blackTheme) {
            holder.getCityTextView().setTextColor(Color.WHITE);
            holder.getTemperatureTextView().setTextColor(Color.WHITE);
            holder.getDescriptionTextView().setTextColor(Color.WHITE);
            holder.getIconTextView().setTextColor(Color.WHITE);
        }

        if (darkTheme) {
            holder.getCardView().setCardBackgroundColor(Color.parseColor("#2e3c43"));
        }

        if (blackTheme) {
            holder.getCardView().setCardBackgroundColor(Color.parseColor("#2f2f2f"));
        }
    }

    @Override
    public int getItemCount() {
        return weathers.size();
    }

    public interface LocationSelectedListener {
        void onLocationSelectedListener(Weather weather);
    }
}
