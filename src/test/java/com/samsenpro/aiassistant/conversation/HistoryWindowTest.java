package com.samsenpro.aiassistant.conversation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryWindowTest {

    @Test
    void keepsTheMostRecentMessagesThatFitInChronologicalOrder() {
        List<Message> newestFirst = List.of(message("4", 100), message("3", 100), message("2", 100), message("1", 100));

        HistoryWindow.Selection selection = HistoryWindow.select(newestFirst, 10, 250);

        assertThat(selection.messages()).extracting(Message::getContent).containsExactly("3", "4");
        // 10 mensajes en total, 2 enviados
        assertThat(selection.omitted()).isEqualTo(8);
    }

    @Test
    void stopsAtTheFirstMessageThatDoesNotFitToKeepTheHistoryContiguous() {
        List<Message> newestFirst = List.of(message("3", 50), message("2", 500), message("1", 10));

        HistoryWindow.Selection selection = HistoryWindow.select(newestFirst, 3, 100);

        assertThat(selection.messages()).extracting(Message::getContent).containsExactly("3");
        assertThat(selection.omitted()).isEqualTo(2);
    }

    @Test
    void emptyConversation() {
        HistoryWindow.Selection selection = HistoryWindow.select(List.of(), 0, 100);

        assertThat(selection.messages()).isEmpty();
        assertThat(selection.omitted()).isZero();
    }

    private static Message message(String content, int tokens) {
        return new Message(1L, Message.Role.USER, content, tokens, Instant.EPOCH);
    }
}
