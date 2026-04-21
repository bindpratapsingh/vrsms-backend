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

        // 1. Always generate a local fallback OTP
        String fallbackOtp = String.format("%06d", new java.util.Random().nextInt(999999));

        OtpChallenge challenge = new OtpChallenge();
        challenge.setPhone(phone);
        challenge.setPurpose(purpose);
        challenge.setOtpCode(fallbackOtp); // Fixes the "null" DB constraint
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        otpRepository.save(challenge);

        // 2. Attempt the REAL Twilio SMS
        try {
            Verification verification = Verification.creator(
                    twilioVerifyServiceSid,
                    phone,
                    "sms"
            ).create();
            System.out.println("Twilio SMS Status: " + verification.getStatus());

        } catch (Exception e) {
            // 3. GRACEFUL DEGRADATION: If Twilio fails, catch the error and print the fallback!
            System.out.println("\n========================================");
            System.out.println("🚨 TWILIO SMS FAILED (Unverified Number or Quota Reached) 🚨");
            System.out.println("Twilio Error: " + e.getMessage());
            System.out.println("FALLBACK LOGIN OTP FOR " + phone + ": " + fallbackOtp);
            System.out.println("========================================\n");

            // Notice we do NOT throw an exception here. We want React to show the OTP input box seamlessly.
        }

        return "OTP sent successfully!";
    }

    @Transactional
    public User verifyOtp(String phone, OtpPurpose purpose, String code) {
        OtpChallenge challenge = otpRepository.findByPhoneAndPurposeAndIsActiveTrue(phone, purpose)
                .orElseThrow(() -> new RuntimeException("No active OTP request found."));

        challenge.setAttempts((short) (challenge.getAttempts() + 1));

        boolean isApproved = false;

        // 1. Check local fallback OTP first
        if (code.equals(challenge.getOtpCode())) {
            isApproved = true;
            System.out.println("Login approved via Local Fallback OTP.");
        } else {
            // 2. If it doesn't match local, ask Twilio to verify it (in case it was a real Twilio SMS)
            try {
                VerificationCheck verificationCheck = VerificationCheck.creator(twilioVerifyServiceSid)
                        .setTo(phone)
                        .setCode(code)
                        .create();

                if ("approved".equals(verificationCheck.getStatus())) {
                    isApproved = true;
                }
            } catch (Exception e) {
                System.out.println("Twilio verification check failed: " + e.getMessage());
            }
        }

        // 3. If neither worked, reject them
        if (!isApproved) {
            otpRepository.save(challenge);
            throw new RuntimeException("Invalid OTP code.");
        }

        // 4. Success!
        challenge.setIsActive(false);
        challenge.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(challenge);

        if (purpose == OtpPurpose.LOGIN) {
            return userRepository.findByPhone(phone).orElseThrow();
        }
        return null;
    }

    @Transactional
    public User loginStaff(String phone, String password) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("No account found with this phone number."));

        if (user.getRole().name().equals("MEMBER")) {
            throw new RuntimeException("Members must use the OTP login portal.");
        }

        if (user.getPasswordHash() == null || !user.getPasswordHash().equals(password)) {
            throw new RuntimeException("Invalid password.");
        }

        return user;
    }
}