package com.vrsms.server.services;

import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.vrsms.server.models.OtpChallenge;
import com.vrsms.server.models.OtpPurpose;
import com.vrsms.server.models.User;
import com.vrsms.server.repositories.OtpChallengeRepository;
import com.vrsms.server.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    @Autowired
    private OtpChallengeRepository otpRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${twilio.account-sid}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token}")
    private String twilioAuthToken;

    @Value("${twilio.verify-service-sid}")
    private String twilioVerifyServiceSid;

    @PostConstruct
    public void initTwilio() {
        Twilio.init(twilioAccountSid, twilioAuthToken);
    }

    //---------Making changes to hardcode OTP for test-------------------

    @Transactional
    public String requestOtp(String phone, OtpPurpose purpose) {
        if (purpose == OtpPurpose.LOGIN) {
            userRepository.findByPhone(phone)
                    .orElseThrow(() -> new RuntimeException("No account found. Please register first."));
        }

        otpRepository.findByPhoneAndPurposeAndIsActiveTrue(phone, purpose).ifPresent(oldOtp -> {
            oldOtp.setIsActive(false);
            otpRepository.saveAndFlush(oldOtp);
        });

        OtpChallenge challenge = new OtpChallenge();
        challenge.setPhone(phone);
        challenge.setPurpose(purpose);
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        otpRepository.save(challenge);

        try {
            // === TEST MODE BYPASS: DO NOT CALL TWILIO ===
            // Verification verification = Verification.creator(twilioVerifyServiceSid, phone, "sms").create();
            // System.out.println("Twilio Status: " + verification.getStatus());

            System.out.println("**************************************************");
            System.out.println("TEST MODE: BYPASSING TWILIO SMS FOR " + phone);
            System.out.println("TEST MODE: USE MASTER OTP CODE: 123456");
            System.out.println("**************************************************");

        } catch (Exception e) {
            throw new RuntimeException("Twilio Failed: " + e.getMessage());
        }

        return "OTP sent successfully!";
    }

    @Transactional
    public User verifyOtp(String phone, OtpPurpose purpose, String code) {
        OtpChallenge challenge = otpRepository.findByPhoneAndPurposeAndIsActiveTrue(phone, purpose)
                .orElseThrow(() -> new RuntimeException("No active OTP request found."));

        challenge.setAttempts((short) (challenge.getAttempts() + 1));

        try {
            // === TEST MODE BYPASS: CHECK FOR MASTER KEY ===
            if ("123456".equals(code)) {
                System.out.println("TEST MODE: Master OTP accepted!");
                // We skip the Twilio check completely!
            } else {
                // If they don't type 123456, we reject it
                otpRepository.save(challenge);
                throw new RuntimeException("Invalid OTP code. (Test mode requires 123456)");

                // ORIGINAL TWILIO CODE COMMENTED OUT:
                // VerificationCheck verificationCheck = VerificationCheck.creator(twilioVerifyServiceSid).setTo(phone).setCode(code).create();
                // if (!"approved".equals(verificationCheck.getStatus())) { throw new RuntimeException("Invalid OTP code."); }
            }
        } catch (Exception e) {
            otpRepository.save(challenge);
            throw new RuntimeException("Verification failed: " + e.getMessage());
        }

        // Success!
        challenge.setIsActive(false);
        challenge.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(challenge);

        if (purpose == OtpPurpose.LOGIN) {
            return userRepository.findByPhone(phone).orElseThrow();
        }
        return null;
    }
/*
    @Transactional
    public String requestOtp(String phone, OtpPurpose purpose) {
        // If logging in, make sure they actually exist first
        if (purpose == OtpPurpose.LOGIN) {
            userRepository.findByPhone(phone)
                    .orElseThrow(() -> new RuntimeException("No account found. Please register first."));
        }

        // Deactivate old requests
        otpRepository.findByPhoneAndPurposeAndIsActiveTrue(phone, purpose).ifPresent(oldOtp -> {
            oldOtp.setIsActive(false);
            otpRepository.saveAndFlush(oldOtp); // Forcing Java to update the DB immediately!
        });

        // Log the new attempt
        OtpChallenge challenge = new OtpChallenge();
        challenge.setPhone(phone);
        challenge.setPurpose(purpose);
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        otpRepository.save(challenge);

        try {
            // Trigger Twilio Verify API
            Verification verification = Verification.creator(
                    twilioVerifyServiceSid,
                    phone,
                    "sms"
            ).create();
            System.out.println("Twilio Status: " + verification.getStatus());
        } catch (Exception e) {
            throw new RuntimeException("Twilio Failed: " + e.getMessage());
        }

        return "OTP sent successfully!";
    }

    @Transactional
    public User verifyOtp(String phone, OtpPurpose purpose, String code) {
        OtpChallenge challenge = otpRepository.findByPhoneAndPurposeAndIsActiveTrue(phone, purpose)
                .orElseThrow(() -> new RuntimeException("No active OTP request found."));

        challenge.setAttempts((short) (challenge.getAttempts() + 1));

        try {
            // Ask Twilio if the code the user typed is correct
            VerificationCheck verificationCheck = VerificationCheck.creator(
                            twilioVerifyServiceSid)
                    .setTo(phone)
                    .setCode(code)
                    .create();

            if (!"approved".equals(verificationCheck.getStatus())) {
                otpRepository.save(challenge);
                throw new RuntimeException("Invalid OTP code.");
            }
        } catch (Exception e) {
            otpRepository.save(challenge);
            throw new RuntimeException("Verification failed: " + e.getMessage());
        }

        // Success!
        challenge.setIsActive(false);
        challenge.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(challenge);

        // If it's a login, return the user so React can log them into the dashboard
        if (purpose == OtpPurpose.LOGIN) {
            return userRepository.findByPhone(phone).orElseThrow();
        }
        return null;
    }

 */

    @Transactional
    public User loginStaff(String phone, String password) {
        // 1. Find the user
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("No account found with this phone number."));

        // 2. Block Members from using this door
        if (user.getRole().name().equals("MEMBER")) {
            throw new RuntimeException("Members must use the OTP login portal.");
        }

        // 3. Check the password (In a real app this uses BCrypt, but for this lab we use plain text matching)
        if (user.getPasswordHash() == null || !user.getPasswordHash().equals(password)) {
            throw new RuntimeException("Invalid password.");
        }

        // 4. Success!
        return user;
    }
}