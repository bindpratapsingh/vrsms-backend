package com.vrsms.server.controllers;

import com.vrsms.server.models.Member;
import com.vrsms.server.models.WishlistItem;
import com.vrsms.server.repositories.MemberRepository;
import com.vrsms.server.repositories.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
@CrossOrigin(origins = "*")
public class WishlistController {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private MemberRepository memberRepository;

    // 1. Toggle item in/out of wishlist
    @PostMapping("/toggle")
    @Transactional
    public ResponseEntity<?> toggleWishlist(@RequestBody WishlistRequest request) {
        try {
            Member member = memberRepository.findByUser_UserId(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("Member not found."));

            boolean exists = wishlistRepository.existsByMemberIdAndItemId(member.getMemberId(), request.getItemId());

            if (exists) {
                wishlistRepository.deleteByMemberIdAndItemId(member.getMemberId(), request.getItemId());
                return ResponseEntity.ok("REMOVED");
            } else {
                WishlistItem item = new WishlistItem();
                item.setMemberId(member.getMemberId());
                item.setItemId(request.getItemId());
                wishlistRepository.save(item);
                return ResponseEntity.ok("ADDED");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 2. Get user's saved items when they log in
    @GetMapping("/{userId}")
    public ResponseEntity<?> getMyWishlist(@PathVariable UUID userId) {
        try {
            Member member = memberRepository.findByUser_UserId(userId)
                    .orElseThrow(() -> new RuntimeException("Member not found."));

            List<WishlistItem> list = wishlistRepository.findByMemberId(member.getMemberId());
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- DTO ---
    public static class WishlistRequest {
        private UUID userId;
        private UUID itemId;
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public UUID getItemId() { return itemId; }
        public void setItemId(UUID itemId) { this.itemId = itemId; }
    }
}