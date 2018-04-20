package ua.ardas.redis.client.dto;

public enum ResponseKey {
    TIMEOUT, OK, INTERNAL_ERROR;

    @Override
    public String toString() {
        return name();
    }
}
