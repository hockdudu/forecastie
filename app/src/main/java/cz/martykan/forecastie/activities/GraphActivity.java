package cz.martykan.forecastie.activities;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.TextView;

import com.db.chart.Tools;
import com.db.chart.model.LineSet;
import com.db.chart.view.ChartView;
import com.db.chart.view.LineChartView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import cz.martykan.forecastie.R;
import cz.martykan.forecastie.models.Weather;
import cz.martykan.forecastie.utils.UnitConverter;

public class GraphActivity extends BaseActivity {

    List<Weather> weatherList;

    float minTemp = 100000;
    float maxTemp = 0;

    float minRain = 100000;
    float maxRain = 0;

    float minPressure = 100000;
    float maxPressure = 0;

    float minWindSpeed = 100000;
    float maxWindSpeed = 0;

    private String labelColor = "#000000";
    private String lineColor = "#333333";

    public static String EXTRA_CITY = "extra_city";

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        Toolbar toolbar = findViewById(R.id.graph_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView temperatureTextView = findViewById(R.id.graphTemperatureTextView);
        TextView rainTextView = findViewById(R.id.graphRainTextView);
        TextView pressureTextView = findViewById(R.id.graphPressureTextView);
        TextView windSpeedTextView = findViewById(R.id.graphWindSpeedTextView);

        if (darkTheme) {
            toolbar.setPopupTheme(R.style.AppTheme_PopupOverlay_Dark);
            labelColor = "#FFFFFF";
            lineColor = "#FAFAFA";

            temperatureTextView.setTextColor(Color.parseColor(labelColor));
            rainTextView.setTextColor(Color.parseColor(labelColor));
            pressureTextView.setTextColor(Color.parseColor(labelColor));
            windSpeedTextView.setTextColor(Color.parseColor(labelColor));
        }

        if (getIntent().hasExtra(EXTRA_CITY)) {
            weatherList = (List<Weather>) getIntent().getSerializableExtra(EXTRA_CITY);

            temperatureGraph();
            rainGraph();
            pressureGraph();
            windSpeedGraph();
        } else {
            Log.e("GraphActivity", "No extra was given");
        }
    }

    private void temperatureGraph() {
        LineChartView lineChartView = findViewById(R.id.graph_temperature);

        // Data
        LineSet dataset = new LineSet();
        for (int i = 0; i < weatherList.size(); i++) {
            float temperature = UnitConverter.convertTemperature(Float.parseFloat(weatherList.get(i).getTemperature()), prefs);

            if (temperature < minTemp) {
                minTemp = temperature;
            }

            if (temperature > maxTemp) {
                maxTemp = temperature;
            }

            dataset.addPoint(getDateLabel(weatherList.get(i), i), temperature);
        }
        dataset.setSmooth(false);
        dataset.setColor(Color.parseColor("#FF5722"));
        dataset.setThickness(4);

        lineChartView.addData(dataset);

        // Grid
        lineChartView.setGrid(ChartView.GridType.HORIZONTAL, getPaint());
        lineChartView.setBorderSpacing(Tools.fromDpToPx(10));
        lineChartView.setAxisBorderValues(Math.round(minTemp) - 1, Math.round(maxTemp) + 1);
        lineChartView.setStep(2);
        lineChartView.setXAxis(false);
        lineChartView.setYAxis(false);
        lineChartView.setLabelsColor(Color.parseColor(labelColor));

        lineChartView.show();
    }

    private void rainGraph() {
        LineChartView lineChartView = findViewById(R.id.graph_rain);

        // Data
        LineSet dataset = new LineSet();
        for (int i = 0; i < weatherList.size(); i++) {
            float rain = Float.parseFloat(weatherList.get(i).getRain());

            if (rain < minRain) {
                minRain = rain;
            }

            if (rain > maxRain) {
                maxRain = rain;
            }

            dataset.addPoint(getDateLabel(weatherList.get(i), i), rain);
        }
        dataset.setSmooth(false);
        dataset.setColor(Color.parseColor("#2196F3"));
        dataset.setThickness(4);

        lineChartView.addData(dataset);

        // Grid
        lineChartView.setGrid(ChartView.GridType.HORIZONTAL, getPaint());
        lineChartView.setBorderSpacing(Tools.fromDpToPx(10));
        lineChartView.setAxisBorderValues(0, (Math.round(maxRain)) + 1);
        lineChartView.setStep(1);
        lineChartView.setXAxis(false);
        lineChartView.setYAxis(false);
        lineChartView.setLabelsColor(Color.parseColor(labelColor));

        lineChartView.show();
    }

    private void pressureGraph() {
        LineChartView lineChartView = findViewById(R.id.graph_pressure);

        // Data
        LineSet dataset = new LineSet();
        for (int i = 0; i < weatherList.size(); i++) {
            float pressure = UnitConverter.convertPressure(Float.parseFloat(weatherList.get(i).getPressure()), prefs);

            if (pressure < minPressure) {
                minPressure = pressure;
            }

            if (pressure > maxPressure) {
                maxPressure = pressure;
            }

            dataset.addPoint(getDateLabel(weatherList.get(i), i), pressure);
        }
        // TODO: Why is this specific graph smooth but the others aren't?
        dataset.setSmooth(true);
        dataset.setColor(Color.parseColor("#4CAF50"));
        dataset.setThickness(4);

        lineChartView.addData(dataset);

        // Grid
        lineChartView.setGrid(ChartView.GridType.HORIZONTAL, getPaint());
        lineChartView.setBorderSpacing(Tools.fromDpToPx(10));
        lineChartView.setAxisBorderValues((int) minPressure - 1, (int) maxPressure + 1);
        lineChartView.setStep(2);
        lineChartView.setXAxis(false);
        lineChartView.setYAxis(false);
        lineChartView.setLabelsColor(Color.parseColor(labelColor));

        lineChartView.show();
    }

    private void windSpeedGraph() {
        LineChartView lineChartView = findViewById(R.id.graph_windspeed);
        String graphLineColor = "#efd214";

        if (darkTheme) {
            graphLineColor = "#FFF600";
        }

        // Data
        LineSet dataset = new LineSet();
        for (int i = 0; i < weatherList.size(); i++) {
            float windSpeed = (float) UnitConverter.convertWind(Float.parseFloat(weatherList.get(i).getWind()), prefs);

            if (windSpeed < minWindSpeed) {
                minWindSpeed = windSpeed;
            }

            if (windSpeed > maxWindSpeed) {
                maxWindSpeed = windSpeed;
            }

            dataset.addPoint(getDateLabel(weatherList.get(i), i), windSpeed);
        }
        dataset.setSmooth(false);
        dataset.setColor(Color.parseColor(graphLineColor));
        dataset.setThickness(4);

        lineChartView.addData(dataset);

        // Grid
        lineChartView.setGrid(ChartView.GridType.HORIZONTAL, getPaint());
        lineChartView.setBorderSpacing(Tools.fromDpToPx(10));
        lineChartView.setAxisBorderValues((int) minWindSpeed - 1, (int) maxWindSpeed + 1);
        lineChartView.setStep(2);
        lineChartView.setXAxis(false);
        lineChartView.setYAxis(false);
        lineChartView.setLabelsColor(Color.parseColor(labelColor));

        lineChartView.show();
    }

    String previous = "";

    private String getDateLabel(Weather weather, int i) {
        if ((i + 4) % 4 == 0) {
            // TODO: User locale?
            SimpleDateFormat resultFormat = new SimpleDateFormat("E");
            resultFormat.setTimeZone(TimeZone.getDefault());
            String output = resultFormat.format(weather.getDate());
            if (!output.equals(previous)) {
                previous = output;
                return output;
            } else {
                return "";
            }
        } else {
            return "";
        }
    }

    private Paint getPaint() {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        paint.setColor(Color.parseColor(lineColor));
        paint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));
        paint.setStrokeWidth(1);

        return paint;
    }
}
