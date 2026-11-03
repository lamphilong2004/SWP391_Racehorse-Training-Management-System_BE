package com.racehorse.horse.activation;

public record ActivationChecklistItem(
        String id,
        String label,
        String description,
        String requirement,
        String state) {
}
