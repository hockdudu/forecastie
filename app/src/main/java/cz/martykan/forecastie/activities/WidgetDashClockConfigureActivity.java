package cz.martykan.forecastie.activities;

import cz.martykan.forecastie.database.CityRepository;
import cz.martykan.forecastie.widgets.AbstractWidgetProvider;

public class WidgetDashClockConfigureActivity extends WidgetConfigureActivity {

    @Override
    protected void onWidgetConfigured(int cityId) {
        AbstractWidgetProvider.saveCityId(this, 0, cityId);
        finish();
    }

    @Override
    protected boolean isActivityValid() {
        // We can't verify whether it's valid or not
        // DashClockExtension.EXTRA_FROM_DASHCLOCK_SETTINGS should be set, but that's not always the case
        return true;
    }

    @Override
    protected int getDefaultSelectedCityIndex(int[] cityIds) {
        int cityId = AbstractWidgetProvider.getCityId(this, AbstractWidgetProvider.DASHCLOCK_WIDGET_ID, CityRepository.INVALID_CITY);
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
