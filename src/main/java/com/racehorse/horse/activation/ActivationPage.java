package com.racehorse.horse.activation;

import java.util.List;

public record ActivationPage(
        List<ActivationStatus> items,
        int page,
        int size,
        long totalItems,
        long totalPages) {

    public ActivationPage {
        items = List.copyOf(items);
    }
}
