package com.racehorse.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Common response envelope for every endpoint.
 * Success: {"success":true,"data":...}. Failure: {"success":false,"error":{code,message,details[]}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
