package com.vrsms.server.controllers;

import com.vrsms.server.models.InventoryItem;
import com.vrsms.server.models.ItemStatus;
import com.vrsms.server.repositories.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    @Autowired
    private InventoryRepository inventoryRepository;

    @GetMapping("/available")
    public ResponseEntity<List<InventoryItem>> getAvailableInventory() {
        return ResponseEntity.ok(inventoryRepository.findAll());
    }

    @PostMapping("/add")
    public ResponseEntity<?> addInventoryItem(@RequestBody InventoryItemRequest request) {
        try {
            InventoryItem item = new InventoryItem();
            item.setTitle(request.getTitle());
            item.setCategory(request.getCategory());
            item.setFormat(request.getFormat());
            item.setPurchasePrice(request.getPurchasePrice());
            item.setDailyRate(request.getDailyRate());
            item.setPurchasedOn(LocalDate.now());
            item.setStatus(ItemStatus.AVAILABLE);

            InventoryItem savedItem = inventoryRepository.save(item);
            return ResponseEntity.ok(savedItem);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to add item: " + e.getMessage());
        }
    }

    // --- DTO for incoming data ---
    public static class InventoryItemRequest {
        private String title;
        private String category;
        private String format;

        // Upgraded to BigDecimal to match your database!
        private BigDecimal purchasePrice;
        private BigDecimal dailyRate;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; } // Fixed the missing dot!

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public BigDecimal getPurchasePrice() { return purchasePrice; }
        public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }

        public BigDecimal getDailyRate() { return dailyRate; }
        public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
    }
}