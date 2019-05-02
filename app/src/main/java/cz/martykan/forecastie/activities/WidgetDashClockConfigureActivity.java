package cz.martykan.forecastie.activities;

import cz.martykan.forecastie.models.City;
import cz.martykan.forecastie.widgets.AbstractWidgetProvider;

public class WidgetDashClockConfigureActivity extends WidgetConfigureActivity {

    @Override
    protected void onWidgetConfigured(City city) {
        AbstractWidgetProvider.saveCityId(this, 0, city.getId());
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
        int cityId = AbstractWidgetProvider.getCityId(this, 0, 0);
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
