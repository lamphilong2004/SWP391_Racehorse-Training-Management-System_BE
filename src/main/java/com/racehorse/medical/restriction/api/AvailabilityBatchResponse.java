package com.racehorse.medical.restriction.api;

import java.util.List;

public record AvailabilityBatchResponse(List<TrainingAvailabilityResponse> items) {
}