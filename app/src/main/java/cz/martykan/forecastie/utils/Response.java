package cz.martykan.forecastie.utils;

public class Response {

    protected Status status;
    @SuppressWarnings("WeakerAccess")
    protected String dataString;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getDataString() {
        return dataString;
    }

    public void setDataString(String dataString) {
        this.dataString = dataString;
    }

    public enum Status {
        SUCCESS,
        BAD_RESPONSE,
        CONNECTION_ERROR, // TODO: This doesn't look to be used anywhere
        TOO_MANY_REQUESTS,
        IO_EXCEPTION,
        JSON_EXCEPTION,
        CITY_NOT_FOUND,
        INVALID_CITY_ID,
        CITY_IS_NOW_INVALID,
    }
}
