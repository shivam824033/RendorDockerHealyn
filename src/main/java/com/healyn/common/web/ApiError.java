package com.healyn.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, List<ApiErrorDetail> details, String traceId) {

    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(code, message, List.of(), traceId);
    }

    public static ApiError of(String code, String message, List<ApiErrorDetail> details, String traceId) {
        return new ApiError(code, message, details == null ? List.of() : details, traceId);
    }
}
