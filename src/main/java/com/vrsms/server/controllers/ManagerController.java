package com.vrsms.server.controllers;

import com.vrsms.server.models.InventoryItem;
import com.vrsms.server.models.Loan;
import com.vrsms.server.models.SystemConfig;
import com.vrsms.server.repositories.InventoryRepository;
import com.vrsms.server.repositories.LoanRepository;
import com.vrsms.server.repositories.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager")
@CrossOrigin(origins = "*")
public class ManagerController {

    @Autowired private SystemConfigRepository configRepo;
    @Autowired private LoanRepository loanRepo;
    @Autowired private InventoryRepository inventoryRepo;

    // --- 1. SYSTEM CONFIGURATION ---
    @GetMapping("/config")
    public ResponseEntity<SystemConfig> getConfig() {
        return ResponseEntity.ok(configRepo.findById(1).orElse(new SystemConfig()));
    }

    @PostMapping("/config")
    public ResponseEntity<SystemConfig> updateConfig(@RequestBody SystemConfig updatedConfig) {
        updatedConfig.setConfigId(1); // Force it to update the single row
        return ResponseEntity.ok(configRepo.save(updatedConfig));
    }

    // --- 2. PROFIT & LOSS ANALYTICS ---
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getFinancialStats() {
        List<Loan> allLoans = loanRepo.findAll();
        List<InventoryItem> allItems = inventoryRepo.findAll();

        // Calculate Total Revenue (Rent paid + Fines paid)
        double totalRevenue = allLoans.stream().mapToDouble(l -> {
            double rent = l.getRentAmount() != null ? l.getRentAmount().doubleValue() : 0.0;
            double fines = l.getFineAmount() != null ? l.getFineAmount().doubleValue() : 0.0;
            return rent + fines;
        }).sum();

        // Calculate Total Costs (Sum of all movie purchase prices)
        double totalCost = allItems.stream()
                .mapToDouble(i -> i.getPurchasePrice() != null ? i.getPurchasePrice().doubleValue() : 0.0)
                .sum();

        double profit = totalRevenue - totalCost;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRevenue", totalRevenue);
        stats.put("totalCost", totalCost);
        stats.put("netProfit", profit);
        stats.put("totalInventoryCount", allItems.size());
        stats.put("totalLoansCount", allLoans.size());

        // NEW: Attach the raw lists so the React dashboard can display the ledger!
        stats.put("inventory", allItems);
        stats.put("loans", allLoans);

        return ResponseEntity.ok(stats);

    }
}