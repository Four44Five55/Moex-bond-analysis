package org.example.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Универсальный пагинированный ответ со сводкой (сводка может быть null). */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        SummaryDto summary
) {
    public static <T> PagedResponse<T> of(Page<T> page, SummaryDto summary) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                summary
        );
    }
}
