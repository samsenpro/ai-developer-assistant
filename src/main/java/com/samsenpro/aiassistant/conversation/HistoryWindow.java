package com.samsenpro.aiassistant.conversation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estrategia de selección del historial: se envían los mensajes más recientes que caben en el
 * presupuesto de tokens, en orden cronológico. Los antiguos se omiten (y se indica al modelo
 * cuántos), en lugar de enviar siempre la conversación completa.
 * <p>
 * Se descartó resumir los mensajes antiguos con el propio LLM: costaría una llamada extra por
 * mensaje y añadiría latencia, y en conversaciones sobre código la pregunta suele depender de los
 * últimos intercambios y de los archivos, que se envían aparte.
 */
public final class HistoryWindow {

    private HistoryWindow() {
    }

    /**
     * @param newestFirst mensajes anteriores, del más reciente al más antiguo
     * @param totalBefore número total de mensajes anteriores en la conversación
     */
    public static Selection select(List<Message> newestFirst, long totalBefore, int tokenBudget) {
        List<Message> included = new ArrayList<>();
        int used = 0;
        for (Message message : newestFirst) {
            if (used + message.getTokenEstimate() > tokenBudget) {
                break;
            }
            included.add(message);
            used += message.getTokenEstimate();
        }
        Collections.reverse(included);
        return new Selection(List.copyOf(included), (int) (totalBefore - included.size()));
    }

    /** @param messages mensajes incluidos, en orden cronológico */
    public record Selection(List<Message> messages, int omitted) {
    }
}
