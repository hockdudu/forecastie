package cz.martykan.forecastie.utils;

import android.annotation.SuppressLint;
import android.support.annotation.IntDef;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import cz.martykan.forecastie.viewmodels.MapViewModel;

public class WebViewMap {

    public static final int MAP_RAIN_FLAG = 1;
    public static final int MAP_WIND_FLAG = 1 << 1;
    public static final int MAP_TEMP_FLAG = 1 << 2;

    @IntDef(flag=true, value={
            MAP_RAIN_FLAG,
            MAP_WIND_FLAG,
            MAP_TEMP_FLAG,
    })
    @interface MapFlag {}


    private final WebView webView;
    private final MapViewModel mapViewModel;

    public WebViewMap(WebView webView, MapViewModel mapViewModel) {
        this.webView = webView;
        this.mapViewModel = mapViewModel;
    }

    public void initialize() {
        initialize(false);
    }

    // TODO: Verify if it's correct to suppress ClickableViewAccessibility
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface", "ClickableViewAccessibility"})
    public void initialize(boolean displayPin) {
        webView.getSettings().setJavaScriptEnabled(true);

        StringBuilder url = new StringBuilder("file:///android_asset/map.html");
        url.append("?lat=").append(mapViewModel.mapLat);
        url.append("&lon=").append(mapViewModel.mapLon);
        url.append("&zoom=").append(mapViewModel.mapZoom);

        if (displayPin) {
            url.append("&displayPin=true");
        }

        webView.loadUrl(url.toString());
        webView.addJavascriptInterface(new HybridInterface(), "NativeInterface");

        webView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
    }

    public void setMap(@MapFlag int flag) {
        webView.loadUrl("javascript:" +
                getAction((flag & MAP_RAIN_FLAG) == MAP_RAIN_FLAG, "rainLayer") + ";" +
                getAction((flag & MAP_WIND_FLAG) == MAP_WIND_FLAG, "windLayer") + ";" +
                getAction((flag & MAP_TEMP_FLAG) == MAP_TEMP_FLAG, "tempLayer") + ";"
        );
    }

    private String getAction(boolean enabled, String layer) {
        return String.format("map.%s(%s)", enabled ? "addLayer" : "removeLayer" , layer);
    }

    public class HybridInterface {

        @JavascriptInterface
        public void transferLatLon(double lat, double lon) {
            mapViewModel.mapLat = lat;
            mapViewModel.mapLon = lon;
        }

        @JavascriptInterface
        public void transferZoom(int level) {
            mapViewModel.mapZoom = level;
        }
    }
}
