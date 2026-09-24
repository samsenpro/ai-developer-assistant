package com.samsenpro.aiassistant.ai.analysis.dto;

import java.util.List;
import java.util.Objects;

/**
 * Normalización de las listas que devuelve el modelo: una lista ausente es una lista vacía (el
 * modelo puede omitirla cuando no hay nada que decir) y los elementos null se descartan.
 */
final class Lists {

    private Lists() {
    }

    static <T> List<T> clean(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    /** Además descarta textos vacíos. */
    static List<String> cleanText(List<String> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).map(String::strip)
                .filter(text -> !text.isEmpty()).toList();
    }
}
