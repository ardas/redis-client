package ua.ardas.redis.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisResponse<T> {
    private ResponseKey key;
    private String message;
    private T body;

    @JsonIgnore
    public boolean isFailure() {
        return !Objects.equals(key, ResponseKey.OK);
    }
}
