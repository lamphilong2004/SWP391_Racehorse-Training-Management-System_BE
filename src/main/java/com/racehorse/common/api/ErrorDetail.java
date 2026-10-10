package com.racehorse.common.api;

/** One field-level validation problem. {@code field} is null for object-level errors. */
public record ErrorDetail(String field, String message) {
}
