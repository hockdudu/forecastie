package cz.martykan.forecastie.utils;

import android.arch.lifecycle.LiveData;

public class LiveResponse<T> extends Response {
    private LiveData<T> liveData;

    public void setResponse(Response response) {
        this.status = response.getStatus();
        this.dataString = response.getDataString();
    }

    public LiveData<T> getLiveData() {
        return liveData;
    }

    public void setLiveData(LiveData<T> liveData) {
        this.liveData = liveData;
    }
}
