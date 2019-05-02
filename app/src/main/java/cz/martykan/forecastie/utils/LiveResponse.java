package cz.martykan.forecastie.utils;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

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

    public static <U> LiveData<List<LiveResponse<U>>> onAllDone(List<LiveResponse<U>> liveResponses) {
        MutableLiveData<Integer> finishCount = new MutableLiveData<>();
        Runnable updateFinishCountValue = () -> finishCount.setValue((finishCount.getValue() != null ? finishCount.getValue() : 0) + 1);

        MutableLiveData<List<LiveResponse<U>>> returnValuesLiveData = new MutableLiveData<>();
        List<LiveResponse<U>> returnValues = new ArrayList<>(liveResponses.size());

        for (LiveResponse<U> liveResponse : liveResponses) {
            liveResponse.getLiveData().observeForever(new Observer<U>() {
                @Override
                public void onChanged(@Nullable Object o) {
                    liveResponse.getLiveData().removeObserver(this);
                    returnValues.add(liveResponse);
                    updateFinishCountValue.run();
                }
            });
        }

        finishCount.observeForever(new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer value) {
                Log.i("LiveResponse", "Got response " + value);
                if (value != null && value == liveResponses.size()) {
                    finishCount.removeObserver(this);
                    returnValuesLiveData.postValue(returnValues);
                    Log.i("LiveResponse", "Got all responses");
                }
            }
        });

        return returnValuesLiveData;
    }
}
