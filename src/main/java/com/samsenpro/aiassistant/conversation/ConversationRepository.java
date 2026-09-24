package com.samsenpro.aiassistant.conversation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Page<Conversation> findByProjectIdAndUserId(Long projectId, Long userId, Pageable pageable);
}
