package cz.martykan.forecastie.adapters.ViewHolder;

import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import cz.martykan.forecastie.R;

public class LocationViewHolder extends RecyclerView.ViewHolder {
    private TextView cityTextView;
    private TextView temperatureTextView;
    private TextView descriptionTextView;
    private TextView iconTextView;
    private WebView webView;
    private CardView cardView;

    public LocationViewHolder(View itemView) {
        super(itemView);

        cityTextView = itemView.findViewById(R.id.rowCityTextView);
        temperatureTextView = itemView.findViewById(R.id.rowTemperatureTextView);
        descriptionTextView = itemView.findViewById(R.id.rowDescriptionTextView);
        iconTextView = itemView.findViewById(R.id.rowIconTextView);
        webView = itemView.findViewById(R.id.webView2);
        cardView = itemView.findViewById(R.id.rowCardView);
    }

    public TextView getCityTextView() {
        return cityTextView;
    }

    public TextView getTemperatureTextView() {
        return temperatureTextView;
    }

    public TextView getDescriptionTextView() {
        return descriptionTextView;
    }

    public TextView getIconTextView() {
        return iconTextView;
    }

    public WebView getWebView() {
        return webView;
    }

    public CardView getCardView() {
        return cardView;
    }
}