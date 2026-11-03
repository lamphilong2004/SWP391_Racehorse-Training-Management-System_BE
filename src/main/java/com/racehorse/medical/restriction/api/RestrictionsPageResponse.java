package com.racehorse.medical.restriction.api;

import java.util.List;

public record RestrictionsPageResponse(List<?> items, int page, int size, long totalElements) {
}