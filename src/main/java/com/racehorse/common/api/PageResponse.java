package com.racehorse.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Paged list body (contract PageMeta + items): zero-based {@code page}, {@code size}, totals, {@code items}. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
