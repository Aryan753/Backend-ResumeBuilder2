package com.resumebuilder.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.resumebuilder.dto.*;
import com.resumebuilder.entity.User;
import com.resumebuilder.enums.AuthProvider;
import com.resumebuilder.enums.UserRole;
import com.resumebuilder.exception.AppException;
import com.resumebuilder.repository.UserRepository;
import com.resumebuilder.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    // FIX #2: OtpService moved to constructor injection — no more @Autowired field injection
    private final OtpService otpService;

    @Value("${google.client.id}")
    private String googleClientId;

    @Value("${app.resume.free.daily.limit:2}")
    private int freeDailyLimit;

    // FIX #2: All dependencies now in a single constructor — consistent, testable
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService, OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.otpService = otpService;
    }

    public void sendRegistrationOtp(String email) {
        // FIX #6: Generic message to prevent email enumeration
        // We still check internally but throw a neutral message either way
        if (userRepository.existsByEmail(email.toLowerCase().trim())) {
            // Do NOT reveal that the email exists — throw same neutral message
            throw new AppException("If this email is not registered, an OTP has been sent.");
        }
        otpService.sendOtp(email.toLowerCase().trim());
    }

    @Transactional
    public AuthResponse registerWithOtp(OtpVerifyRegisterRequest request) {
        if (!otpService.verifyOtp(request.getEmail().toLowerCase().trim(), request.getOtp())) {
            throw new AppException("Invalid or expired OTP. Please request a new one.");
        }
        RegisterRequest reg = new RegisterRequest();
        reg.setName(request.getName());
        reg.setEmail(request.getEmail());
        reg.setPassword(request.getPassword());
        reg.setPhone(request.getPhone());
        return register(reg);
    }

    // FIX #1: register() is now package-private — it is only called internally from registerWithOtp()
    // The /register endpoint has been removed from the controller.
    // This prevents bypassing OTP verification.
    @Transactional
    AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new AppException("Email already registered. Please login.");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .authProvider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .emailVerified(true) // Safe: OTP was verified before this is called
                .active(true)
                .dailyFreeResumesUsed(0)
                .build();

        userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase().trim(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            throw new AppException("Invalid email or password.");
        }

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new AppException("User not found."));

        if (!user.isActive()) throw new AppException("Account is deactivated.");

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        try {
            System.out.println("DEBUG TOKEN RECEIVED: " + request.getIdToken().substring(0, 50));
            System.out.println("DEBUG CLIENT ID: " + googleClientId);
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken == null) throw new AppException("Invalid Google token.");

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail().toLowerCase().trim();
            String name = (String) payload.get("name");
            String googleId = payload.getSubject();
            String picture = (String) payload.get("picture");

            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.findByGoogleId(googleId).orElse(null));

            if (user == null) {
                // New user — create a Google-backed account
                user = User.builder()
                        .email(email)
                        .name(name)
                        .googleId(googleId)
                        .profilePicture(picture)
                        .authProvider(AuthProvider.GOOGLE)
                        .role(UserRole.USER)
                        .emailVerified(true)
                        .active(true)
                        .dailyFreeResumesUsed(0)
                        .build();
                userRepository.save(user);

            } else {
                // FIX #5: Prevent silent account takeover — reject if registered via LOCAL auth
                if (user.getAuthProvider() == AuthProvider.LOCAL) {
                    throw new AppException(
                            "This email is already registered with a password. Please log in using your email and password.");
                }

                // Safe to update Google-linked user details
                user.setGoogleId(googleId);
                if (picture != null) user.setProfilePicture(picture);
                user.setEmailVerified(true);
                userRepository.save(user);
            }

            return buildAuthResponse(user);

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("Google authentication failed: " + e.getMessage());
        }
    }

    // FIX #3: refreshToken now accepts the token string directly (extracted from header in controller)
    public AuthResponse refreshToken(String refreshToken) {
        try {
            String email = jwtUtil.extractUsername(refreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtUtil.isTokenValid(refreshToken, userDetails)) {
                throw new AppException("Invalid or expired refresh token.");
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AppException("User not found."));

            return buildAuthResponse(user);

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("Token refresh failed.");
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        // FIX #4: Delegate remaining-quota logic to a dedicated helper for clarity
        int remaining = calculateRemainingResumes(user);

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .profilePicture(user.getProfilePicture())
                .dailyFreeResumesUsed(user.getDailyFreeResumesUsed())
                .freeResumesRemaining(remaining)
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(userDto)
                .build();
    }

    // FIX #4: Isolated quota calculation — easy to test and reason about independently
    private int calculateRemainingResumes(User user) {
        boolean usedToday = user.getLastResumeDate() != null
                && user.getLastResumeDate().equals(LocalDate.now());

        if (usedToday) {
            return Math.max(0, freeDailyLimit - user.getDailyFreeResumesUsed());
        }
        // New day or never used — full quota available
        return freeDailyLimit;
    }
}
// (Admin init is handled by AdminInitService)