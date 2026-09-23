package com.samsenpro.aiassistant.conversation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    /** Los mensajes más recientes primero (para construir la ventana de historial). */
    List<Message> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    long countByConversationId(Long conversationId);
}
