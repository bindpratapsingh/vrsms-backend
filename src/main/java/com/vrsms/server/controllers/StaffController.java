package com.vrsms.server.controllers;

import com.vrsms.server.models.Member;
import com.vrsms.server.models.User;
import com.vrsms.server.repositories.MemberRepository;
import com.vrsms.server.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/staff")
@CrossOrigin(origins = "*")
public class StaffController {

    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;

    @GetMapping("/lookup-member")
    public ResponseEntity<?> lookupMemberByPhone(@RequestParam String phone) {
        try {
            // 1. Find the user by their phone number
            User user = userRepository.findByPhone(phone)
                    .orElseThrow(() -> new RuntimeException("No account found with this phone number."));

            // 2. Find their specific Member profile
            Member member = memberRepository.findByUser_UserId(user.getUserId())
                    .orElseThrow(() -> new RuntimeException("This user is not registered as a Member."));

            // 3. Package up exactly what the React frontend needs
            Map<String, Object> response = new HashMap<>();
            response.put("memberId", member.getMemberId());
            response.put("fullName", user.getFullName());
            response.put("userId", user.getUserId());
            response.put("photoUrl", member.getPhotoUrl());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    // NEW: Get all registered members for the Manager/Clerk dashboards
    @GetMapping("/members/all")
    public ResponseEntity<?> getAllMembers() {
        try {
            // 1. Just grab everyone directly from the Member table!
            List<Member> allMembers = memberRepository.findAll();

            // 2. Prepare our list to send to React
            List<Map<String, Object>> responseList = new ArrayList<>();

            for (Member m : allMembers) {
                User u = m.getUser(); // Get the attached User profile for their name and phone

                if (u != null) {
                    Map<String, Object> memberData = new HashMap<>();
                    memberData.put("userId", u.getUserId());
                    memberData.put("memberId", m.getMemberId());
                    memberData.put("fullName", u.getFullName());
                    memberData.put("phone", u.getPhone());
                    memberData.put("address", m.getAddress());
                    memberData.put("photoUrl", m.getPhotoUrl());
                    // If your deposit field is named differently, just change getDepositPaid()
                    memberData.put("depositPaid", m.getDepositPaid());
                    memberData.put("currentDues", m.getCurrentDues());

                    responseList.add(memberData);
                }
            }
            return ResponseEntity.ok(responseList);
        } catch (Exception e) {
            e.printStackTrace(); // You can ignore IntelliJ's warning about this!
            return ResponseEntity.badRequest().body("Failed to fetch members: " + e.getMessage());
        }
    }
    // NEW: The Registration Endpoint
    @PostMapping("/register-member")
    public ResponseEntity<?> registerNewMember(@RequestBody RegistrationRequest request) {
        try {
            // 1. Create or find the User account
            User user = userRepository.findByPhone(request.getPhone()).orElse(new User());
            if (user.getUserId() == null) {
                user.setPhone(request.getPhone());
                user.setFullName(request.getFullName());
                user.setRole(com.vrsms.server.models.UserRole.MEMBER);
                user = userRepository.save(user);
            } else {
                // If user exists, check if they are already a member
                if (memberRepository.findByUser_UserId(user.getUserId()).isPresent()) {
                    return ResponseEntity.badRequest().body("A member with this phone number already exists.");
                }
            }

            // 2. Create the Member Profile matching your exact schema
            Member newMember = new Member();
            newMember.setUser(user);
            newMember.setAddress(request.getAddress());
            newMember.setPhotoUrl(request.getPhotoUrl());

            // Explicitly record the Rs 1000 deposit using BigDecimal!
            if (request.getDepositPaid() != null && request.getDepositPaid()) {
                newMember.setDepositPaid(java.math.BigDecimal.valueOf(1000.00));
            } else {
                newMember.setDepositPaid(java.math.BigDecimal.ZERO);
            }

            memberRepository.save(newMember);

            return ResponseEntity.ok("Member successfully registered!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Registration failed: " + e.getMessage());
        }
    }

    // NEW: The Data Carrier for the frontend payload
    public static class RegistrationRequest {
        private String fullName;
        private String phone;
        private String address;
        private String photoUrl; // Changed to match your schema!
        private Boolean depositPaid;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public Boolean getDepositPaid() { return depositPaid; }
        public void setDepositPaid(Boolean depositPaid) { this.depositPaid = depositPaid; }
    }
    // --- NEW: Edit Existing Member Details ---
    @PutMapping("/members/edit/{userId}")
    public ResponseEntity<?> updateMemberProfile(@PathVariable java.util.UUID userId, @RequestBody UpdateMemberRequest request) {
        try {
            com.vrsms.server.models.User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            com.vrsms.server.models.Member member = memberRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Member profile not found"));

            // Update User table details
            user.setFullName(request.getFullName());
            user.setPhone(request.getPhone());
            userRepository.save(user);

            // Update Member table details
            member.setAddress(request.getAddress());
            if (request.getPhotoUrl() != null && !request.getPhotoUrl().isEmpty()) {
                member.setPhotoUrl(request.getPhotoUrl());
            }
            memberRepository.save(member);

            return ResponseEntity.ok("Member profile updated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to update member: " + e.getMessage());
        }
    }

    // --- DTO for Edit Request ---
    public static class UpdateMemberRequest {
        private String fullName;
        private String phone;
        private String address;
        private String photoUrl;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    }

    // ==========================================
    // ENDPOINT: CLEAR MEMBER DUES
    // ==========================================
    @PostMapping("/members/{userId}/clear-dues")
    public ResponseEntity<?> clearMemberDues(@PathVariable java.util.UUID userId) {
        try {
            com.vrsms.server.models.User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            com.vrsms.server.models.Member member = memberRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Member profile not found"));

            // Reset their debt to ZERO
            member.setCurrentDues(java.math.BigDecimal.ZERO);
            memberRepository.save(member);

            return ResponseEntity.ok("Member's outstanding dues have been cleared!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to clear dues: " + e.getMessage());
        }
    }
}