package cz.martykan.forecastie.utils;

import android.content.SharedPreferences;

import java.util.Locale;

public class UnitConverter {
    public static float convertTemperature(float temperature, SharedPreferences sp) {
        switch (sp.getString("unit", "°C")) {
            case "°C":
                return UnitConverter.kelvinToCelsius(temperature);
            case "°F":
                return UnitConverter.kelvinToFahrenheit(temperature);
            default:
                return temperature;
        }
    }

    public static float kelvinToCelsius(float kelvinTemp) {
        return kelvinTemp - 273.15f;
    }

    public static float kelvinToFahrenheit(float kelvinTemp) {
        return (((9 * kelvinToCelsius(kelvinTemp)) / 5) + 32);
    }

    public static String getRainString(double rain, SharedPreferences sp) {
        if (rain > 0) {
            if (sp.getString("lengthUnit", "mm").equals("mm")) {
                if (rain < 0.1) {
                    return "<0.1 mm";
                } else {
                    return String.format(Locale.ENGLISH, "%.1f %s", rain, sp.getString("lengthUnit", "mm"));
                }
            } else {
                rain = rain / 25.4;
                if (rain < 0.01) {
                    return "<0.01 in";
                } else {
                    return String.format(Locale.ENGLISH, "%.2f %s", rain, sp.getString("lengthUnit", "mm"));
                }
            }
        } else {
            return "";
        }
    }

    public static float convertPressure(float pressure, SharedPreferences sp) {
        switch (sp.getString("pressureUnit", "hPa")) {
            case "kPa":
                return pressure / 10;
            case "mm Hg":
                return (float) (pressure * 0.750061561303);
            case "in Hg":
                return (float) (pressure * 0.0295299830714);
            default:
                return pressure;
        }
    }

    public static double convertWind(double wind, SharedPreferences sp) {
        switch (sp.getString("speedUnit", "m/s")) {
            case "kph":
                return wind * 3.6;
            case "mph":
                return wind * 2.23693629205;
            case "kn":
                return wind * 1.943844;
            case "bft":
                if (wind < 0.3) return 0; // Calm
                else if (wind < 1.5) return 1; // Light air
                else if (wind < 3.3) return 2; // Light breeze
                else if (wind < 5.5) return 3; // Gentle breeze
                else if (wind < 7.9) return 4; // Moderate breeze
                else if (wind < 10.7) return 5; // Fresh breeze
                else if (wind < 13.8) return 6; // Strong breeze
                else if (wind < 17.1) return 7; // High wind
                else if (wind < 20.7) return 8; // Gale
                else if (wind < 24.4) return 9; // Strong gale
                else if (wind < 28.4) return 10; // Storm
                else if (wind < 32.6) return 11; // Violent storm
                else return 12; // Hurricane
            default:
                return wind;
        }
    }

    // TODO: Make this translatable
    public static String convertUvIndexToRiskLevel(double value) {
        /* based on: https://en.wikipedia.org/wiki/Ultraviolet_index */
        if (value < 0) return "no info"; /* error */
        else if (value < 3) return "Low";
        else if (value < 6) return "Moderate";
        else if (value < 8) return "High";
        else if (value < 11) return "Very High";
        else return "Extreme";
    }

    public static String getBeaufortName(int wind) {
        switch (wind) {
            case 0:
                return "Calm";
            case 1:
                return "Light air";
            case 2:
                return "Light breeze";
            case 3:
                return "Gentle breeze";
            case 4:
                return "Moderate breeze";
            case 5:
                return "Fresh breeze";
            case 6:
                return "Strong breeze";
            case 7:
                return "High wind";
            case 8:
                return "Gale";
            case 9:
                return "Strong gale";
            case 10:
                return "Storm";
            case 11:
                return "Violent storm";
            default:
                return "Hurricane";
        }
    }
}
