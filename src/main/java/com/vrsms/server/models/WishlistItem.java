package com.vrsms.server.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wishlist_items")
public class WishlistItem {

    @Id
    @GeneratedValue
    private UUID wishlistId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private UUID itemId;

    private LocalDateTime addedOn = LocalDateTime.now();

    // --- GETTERS AND SETTERS ---
    public UUID getWishlistId() { return wishlistId; }
    public void setWishlistId(UUID wishlistId) { this.wishlistId = wishlistId; }

    public UUID getMemberId() { return memberId; }
    public void setMemberId(UUID memberId) { this.memberId = memberId; }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }

    public LocalDateTime getAddedOn() { return addedOn; }
    public void setAddedOn(LocalDateTime addedOn) { this.addedOn = addedOn; }
}