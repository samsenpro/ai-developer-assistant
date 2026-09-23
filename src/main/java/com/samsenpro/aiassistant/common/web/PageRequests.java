package com.samsenpro.aiassistant.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Paginación con límites: un cliente no puede pedir páginas arbitrariamente grandes. */
public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SIZE), sort);
    }
}
