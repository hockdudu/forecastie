package cz.martykan.forecastie.activities;

import java.util.Arrays;
import java.util.List;

import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.widgets.DashClockWeatherExtension;

public class WidgetDashClockConfigureActivity extends WidgetConfigureActivity {

    @Override
    protected void onWidgetConfigured(City city) {
        DashClockWeatherExtension.saveCityId(this, city.getId());
        finish();
    }

    @Override
    protected boolean isActivityValid() {
        // We can't verify whether it's valid or not
        // DashClockExtension.EXTRA_FROM_DASHCLOCK_SETTINGS should be set, but that's not always the case
        return true;
    }

    @Override
    protected int getCityIndex(int[] cityIds) {
        int cityId = DashClockWeatherExtension.getCityid(this, 0);
        int index = 0;

        for (int i = 0; i < cityIds.length; i++) {
            if (cityIds[i] == cityId) {
                index = i;
                break;
            }
        }

        return index;
    }
}
