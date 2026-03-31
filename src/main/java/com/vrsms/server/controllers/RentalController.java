package com.vrsms.server.controllers;

import com.vrsms.server.models.Loan;
import com.vrsms.server.services.RentalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/rentals")
@CrossOrigin(origins = "*")
public class RentalController {

    @Autowired
    private RentalService rentalService;

    // ==========================================
    // ENDPOINT: ISSUE RENTAL
    // React calls: POST http://localhost:8080/api/rentals/issue
    // ==========================================
    @PostMapping("/issue")
    public ResponseEntity<?> issueRental(@RequestBody RentalRequest request) {
        try {
            // Hand the JSON data straight to your awesome Chunk 3 Service
            Loan newLoan = rentalService.issueRental(request.getMemberId(), request.getItemId(), request.getClerkId());
            return ResponseEntity.ok(newLoan); // Send the completed receipt back to React
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage()); // Send the error (e.g., "Member has fines") to React
        }
    }

    // ==========================================
    // ENDPOINT: PROCESS RETURN
    // React calls: POST http://localhost:8080/api/rentals/return
    // ==========================================
    @PostMapping("/return")
    public ResponseEntity<?> processReturn(@RequestBody ReturnRequest request) {
        try {
            Loan updatedLoan = rentalService.processReturn(request.getLoanId(), request.getClerkId());
            return ResponseEntity.ok(updatedLoan);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ==========================================
    // DATA TRANSFER OBJECTS (DTOs)
    // These classes perfectly match the JSON shape React will send to Java.
    // ==========================================

    public static class RentalRequest {
        private UUID memberId;
        private UUID itemId;
        private UUID clerkId;

        public UUID getMemberId() { return memberId; }
        public void setMemberId(UUID memberId) { this.memberId = memberId; }
        public UUID getItemId() { return itemId; }
        public void setItemId(UUID itemId) { this.itemId = itemId; }
        public UUID getClerkId() { return clerkId; }
        public void setClerkId(UUID clerkId) { this.clerkId = clerkId; }
    }

    public static class ReturnRequest {
        private UUID loanId;
        private UUID clerkId;

        public UUID getLoanId() { return loanId; }
        public void setLoanId(UUID loanId) { this.loanId = loanId; }
        public UUID getClerkId() { return clerkId; }
        public void setClerkId(UUID clerkId) { this.clerkId = clerkId; }
    }

    @Autowired
    private com.vrsms.server.repositories.MemberRepository memberRepository;

    @Autowired
    private com.vrsms.server.repositories.LoanRepository loanRepository;

    // React calls: GET /api/rentals/my-active/{userId}
    @GetMapping("/my-active/{userId}")
    public ResponseEntity<?> getMyActiveRentals(@PathVariable java.util.UUID userId) {
        try {
            // 1. Find the Member profile linked to this User ID
            com.vrsms.server.models.Member member = memberRepository.findByUser_UserId(userId)
                    .orElseThrow(() -> new RuntimeException("Member profile not found."));

            // 2. Fetch all their active loans
            java.util.List<com.vrsms.server.models.Loan> activeLoans =
                    loanRepository.findByMember_MemberIdAndStatus(member.getMemberId(), com.vrsms.server.models.LoanStatus.ACTIVE);

            return ResponseEntity.ok(activeLoans);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}