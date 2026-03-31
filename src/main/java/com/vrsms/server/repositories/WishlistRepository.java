package com.vrsms.server.repositories;

import com.vrsms.server.models.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistItem, UUID> {
    List<WishlistItem> findByMemberId(UUID memberId);
    boolean existsByMemberIdAndItemId(UUID memberId, UUID itemId);
    void deleteByMemberIdAndItemId(UUID memberId, UUID itemId);
}