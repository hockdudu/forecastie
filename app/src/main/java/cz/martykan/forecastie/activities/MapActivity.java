package cz.martykan.forecastie.activities;

import android.annotation.SuppressLint;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.roughike.bottombar.BottomBar;
import com.roughike.bottombar.OnMenuTabClickListener;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.utils.WebViewMap;
import cz.martykan.forecastie.viewmodels.MapViewModel;

public class MapActivity extends BaseActivity {

    private BottomBar bottomBar;
    private WebViewMap webViewMap;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        MapViewModel mapViewModel = ViewModelProviders.of(this).get(MapViewModel.class);

        if (savedInstanceState == null) {
            mapViewModel.sharedPreferences = prefs;
            mapViewModel.mapLat = prefs.getFloat("latitude", 0);
            mapViewModel.mapLon = prefs.getFloat("longitude", 0);
            mapViewModel.apiKey = mapViewModel.sharedPreferences.getString("apiKey", getResources().getString(R.string.apiKey));
        }

        WebView webView = findViewById(R.id.webView);

        webViewMap = new WebViewMap(webView, mapViewModel);
        webViewMap.initialize();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (savedInstanceState != null) {
                    setMapState(mapViewModel.tabPosition);
                } else {
                    webViewMap.setMap(WebViewMap.MAP_RAIN_FLAG);
                }
            }
        });

        bottomBar = BottomBar.attach(this, savedInstanceState);
        bottomBar.setItems(R.menu.menu_map_bottom);
        bottomBar.setOnMenuTabClickListener(new OnMenuTabClickListener() {
            @Override
            public void onMenuTabSelected(@IdRes int menuItemId) {
                setMapState(menuItemId);
                mapViewModel.tabPosition = menuItemId;
            }

            @Override
            public void onMenuTabReSelected(@IdRes int menuItemId) {
            }
        });
    }

    private void setMapState(int item) {
        switch (item) {
            case R.id.map_rain:
                webViewMap.setMap(WebViewMap.MAP_RAIN_FLAG);
                break;
            case R.id.map_wind:
                webViewMap.setMap(WebViewMap.MAP_WIND_FLAG);
                break;
            case R.id.map_temperature:
                webViewMap.setMap(WebViewMap.MAP_TEMP_FLAG);
                break;
            default:
                Log.w("WeatherMap", "Layer not configured");
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        bottomBar.onSaveInstanceState(outState);
    }
}
