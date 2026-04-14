package com.vrsms.server.services;

import com.vrsms.server.models.*;
import com.vrsms.server.repositories.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class RentalService {

    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private InventoryItemRepository itemRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SystemConfigService configService;
    @Autowired private CouponRepository couponRepository;

    // ==========================================
    // USE CASE: ISSUE RENTAL
    // ==========================================
    @Transactional
    public Loan issueRental(UUID memberId, UUID itemId, UUID clerkId) {
        // 1. Find the Member, Item, and Clerk in the database
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        User clerk = userRepository.findById(clerkId)
                .orElseThrow(() -> new RuntimeException("Clerk not found"));

        // --- NEW RULE: RENTAL LIMIT (MAX 2) ---
        // Count how many ACTIVE loans this member currently has
        long activeCount = loanRepository.countByMember_MemberIdAndStatus(member.getMemberId(), LoanStatus.ACTIVE);

        if (activeCount >= 2) {
            throw new RuntimeException("Rental Blocked: Member already has 2 active rentals (Maximum Limit Reached).");
        }
        // --------------------------------------

        // 2. CHECK RULES: Is the item actually available?
        if (item.getStatus() != ItemStatus.AVAILABLE) {
            throw new RuntimeException("Item is currently not available for rent.");
        }

        // 3. CHECK RULES: Does the member owe money?
        if (member.getCurrentDues().compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Rental blocked: Member has outstanding fines.");
        }

        // 4. Get System Rules (10 days limit)
        SystemConfig config = configService.getConfig();

        // 5. Create the exact "Receipt" (Loan Record)
        Loan loan = new Loan();
        loan.setMember(member);
        loan.setItem(item);
        loan.setIssuedBy(clerk);
        loan.setIssueDate(LocalDate.now());

        // Calculate due date (Today + Max Loan Days)
        loan.setDueDate(LocalDate.now().plusDays(config.getMaxRentalDays()));

        // Set the rental price from the item
        loan.setRentAmount(item.getDailyRate().multiply(BigDecimal.valueOf(config.getMaxRentalDays())));
        loan.setStatus(LoanStatus.ACTIVE);

        // 6. Update the Item's status so no one else can rent it
        item.setStatus(ItemStatus.ON_LOAN);
        itemRepository.save(item);

        // 7. Save the loan to the database
        return loanRepository.save(loan);
    }

    // ==========================================
    // USE CASE: PROCESS RETURN & CALCULATE FINES
    // ==========================================
    @Transactional
    public Loan processReturn(UUID loanId, UUID clerkId, String couponCode) { // <-- ADDED PARAMETER
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan record not found"));
        User clerk = userRepository.findById(clerkId)
                .orElseThrow(() -> new RuntimeException("Clerk not found"));

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new RuntimeException("This item is already returned or cancelled.");
        }

        LocalDate today = LocalDate.now();
        loan.setReturnDate(today);
        loan.setReturnedBy(clerk);
        loan.setStatus(LoanStatus.RETURNED);

        long daysKept = ChronoUnit.DAYS.between(loan.getIssueDate(), today);
        if (daysKept < 1) daysKept = 1;

        BigDecimal actualRent = loan.getItem().getDailyRate().multiply(BigDecimal.valueOf(daysKept));


        // ==========================================
        // DYNAMIC DATABASE COUPON LOGIC
        // ==========================================
        if (couponCode != null && !couponCode.trim().isEmpty()) {
            Optional<Coupon> promo = couponRepository.findByCodeIgnoreCaseAndActiveTrue(couponCode.trim());

            if (promo.isPresent()) {
                double discount = promo.get().getDiscountPercentage() / 100.0;
                BigDecimal discountAmount = actualRent.multiply(BigDecimal.valueOf(discount));
                actualRent = actualRent.subtract(discountAmount);
            }
        }
        loan.setRentAmount(actualRent);

        // 2. Calculate Fines if it is late!
        if (today.isAfter(loan.getDueDate())) {
            long daysLate = ChronoUnit.DAYS.between(loan.getDueDate(), today);
            SystemConfig config = configService.getConfig();

            // Fine = days late * fine per day
            BigDecimal totalFine = config.getLateFeePerDay().multiply(BigDecimal.valueOf(daysLate));
            loan.setFineAmount(totalFine);

            // Add the fine to the member's profile
            Member member = loan.getMember();
            member.setCurrentDues(member.getCurrentDues().add(totalFine));
            memberRepository.save(member);
        } else {
            loan.setFineAmount(BigDecimal.ZERO); // No fine if returned on time
        }

        // 3. Put the item back on the shelf
        InventoryItem item = loan.getItem();
        item.setStatus(ItemStatus.AVAILABLE);
        itemRepository.save(item);

        // 4. Save the updated receipt
        return loanRepository.save(loan);
    }
}