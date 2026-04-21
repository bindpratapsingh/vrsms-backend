package com.vrsms.server.services;

import com.vrsms.server.models.Member;
import com.vrsms.server.models.InventoryItem;
import com.vrsms.server.models.User;
import com.vrsms.server.models.LoanStatus;
import com.vrsms.server.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class RentalServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private InventoryItemRepository itemRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private RentalService rentalService;

    @Test
    public void testIssueRental_BlocksWhenLimitReached() {
        // 1. SETUP: Create fake entities
        UUID fakeMemberId = UUID.randomUUID();
        Member fakeMember = new Member();
        fakeMember.setMemberId(fakeMemberId);

        InventoryItem fakeItem = new InventoryItem();
        User fakeClerk = new User();

        // 2. MOCK REPOSITORIES: Pretend the database finds them all
        when(memberRepository.findById(fakeMemberId)).thenReturn(Optional.of(fakeMember));
        when(itemRepository.findById(any())).thenReturn(Optional.of(fakeItem));
        when(userRepository.findById(any())).thenReturn(Optional.of(fakeClerk));

        // 3. MOCK BUSINESS RULE: Tell the database this member already has 2 active movies
        when(loanRepository.countByMember_MemberIdAndStatus(fakeMemberId, LoanStatus.ACTIVE)).thenReturn(2L);

        // 4. EXECUTE: Try to issue a 3rd movie, expect a crash
        Exception exception = assertThrows(RuntimeException.class, () -> {
            rentalService.issueRental(fakeMemberId, UUID.randomUUID(), UUID.randomUUID());
        });

        // 5. VERIFY: Make sure the crash message matches our exact security rule
        assertEquals("Rental Blocked: Member already has 2 active rentals (Maximum Limit Reached).", exception.getMessage());
    }
}