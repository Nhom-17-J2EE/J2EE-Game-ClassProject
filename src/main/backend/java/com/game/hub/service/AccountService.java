package com.game.hub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.hub.entity.EmailVerificationToken;
import com.game.hub.entity.PasswordResetToken;
import com.game.hub.entity.UserAccount;
import com.game.hub.entity.UserAvatarBinary;
import com.game.hub.repository.EmailVerificationTokenRepository;
import com.game.hub.repository.PasswordResetTokenRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.repository.UserAvatarBinaryRepository;
import org.springframework.beans.factory.annotation.Value;
import com.game.hub.util.PasswordValidator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AccountService {
    private static final int VERIFICATION_CODE_TTL_MINUTES = 5;
    private static final long VERIFICATION_RESEND_COOLDOWN_SECONDS = 30;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._]{6,20}$");
    private static final Set<String> ALLOWED_THEME_MODES = Set.of("system", "light", "dark");
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("vi", "en");
    private static final Set<String> ALLOWED_GENDERS = Set.of("male", "female", "other", "prefer_not_to_say");
    private static final Set<Integer> ALLOWED_FRIEND_REFRESH_MS = Set.of(5000, 10000, 15000, 20000, 30000, 60000);
    private static final String GAME_CODE_CHESS_OFFLINE = "chess-offline";
    private static final String GAME_CODE_XIANGQI_OFFLINE = "xiangqi-offline";
    private static final String GAME_CODE_MINESWEEPER = "minesweeper";
    private static final String GAME_CODE_QUIZ_PRACTICE = "quiz-practice";
    private static final String GAME_CODE_TYPING_PRACTICE = "typing-practice";
    private static final int PUZZLE_RECENT_LIMIT = 8;
    private static final int GAMES_BROWSER_RECENT_LIMIT = 20;
    private static final String DEFAULT_AVATAR_PATH = "/uploads/avatars/default-avatar.jpg";
    private static final String DB_AVATAR_PATH_PREFIX = "/account/avatar/";
    private static final String SOCIAL_PROVIDER_GOOGLE = "google";
    private static final String SOCIAL_PROVIDER_FACEBOOK = "facebook";

    private final UserAccountRepository userAccountRepository;
    private final UserAvatarBinaryRepository userAvatarBinaryRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AchievementService achievementService;
    private final ObjectMapper objectMapper;
    private final String defaultAdminEmail;
    private final String adminActivationCode;
    private final Random random = new Random();

    public AccountService(UserAccountRepository userAccountRepository,
                          UserAvatarBinaryRepository userAvatarBinaryRepository,
                          EmailVerificationTokenRepository emailVerificationTokenRepository,
                          PasswordResetTokenRepository passwordResetTokenRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService,
                          AchievementService achievementService,
                          ObjectMapper objectMapper,
                          @Value("${app.admin.default-email:luckhaikiet@gmail.com}") String defaultAdminEmail,
                          @Value("${app.admin.activation-code:j2ee20262027}") String adminActivationCode) {
        this.userAccountRepository = userAccountRepository;
        this.userAvatarBinaryRepository = userAvatarBinaryRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.achievementService = achievementService;
        this.objectMapper = objectMapper;
        this.defaultAdminEmail = normalizeEmail(defaultAdminEmail);
        this.adminActivationCode = trimToNull(adminActivationCode);
    }

    public ServiceResult register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request == null ? null : request.email());
        String displayName = trimToNull(request == null ? null : request.displayName());
        String rawUsername = request == null ? null : request.username();
        String password = request == null ? null : request.password();
        String avatarPath = trimToNull(request == null ? null : request.avatarPath());
        String country = normalizeCountry(request == null ? null : request.country());
        String gender = normalizeGender(request == null ? null : request.gender());
        LocalDate birthDate = parseBirthDate(request == null ? null : request.birthDate());

        if (normalizedEmail == null) {
            return ServiceResult.error("Email is required");
        }
        List<String> passwordErrors = PasswordValidator.validate(password);
        if (!passwordErrors.isEmpty()) {
            return ServiceResult.error(String.join("; ", passwordErrors));
        }
        ServiceResult usernameValidation = validateUsernameCandidate(rawUsername, null);
        if (!usernameValidation.success()) {
            return usernameValidation;
        }
        if (country == null) {
            return ServiceResult.error("Country is required");
        }
        if (gender == null) {
            return ServiceResult.error("Gender is required");
        }
        if (birthDate == null) {
            return ServiceResult.error("Birth date is required");
        }

        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            return ServiceResult.error("Email already exists");
        }
        if (PendingRegisterStore.get(normalizedEmail) != null) {
            return ServiceResult.error("Email is waiting for verification");
        }

        String username = readUsernameFromPayload(usernameValidation.data(), rawUsername);
        String effectiveDisplayName = displayName == null ? username : displayName;
        RegisterRequest normalizedRequest = new RegisterRequest(
            normalizedEmail,
            effectiveDisplayName,
            username,
            password,
            avatarPath,
            country,
            gender,
            birthDate == null ? null : birthDate.toString()
        );
        PendingRegisterStore.put(normalizedEmail, normalizedRequest, LocalDateTime.now().plusMinutes(VERIFICATION_CODE_TTL_MINUTES));
        ServiceResult issueResult = issueVerificationCode(normalizedEmail, false);
        if (!issueResult.success()) {
            PendingRegisterStore.remove(normalizedEmail);
            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
            return issueResult;
        }
        return issueResult;
    }

    public ServiceResult resendVerificationCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return ServiceResult.error("Email is required");
        }
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            return ServiceResult.error("Email already registered");
        }

        RegisterRequest pending = PendingRegisterStore.get(normalizedEmail);
        if (pending == null) {
            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
            return ServiceResult.error("No pending registration for this email");
        }

        ServiceResult cooldown = validateResendCooldown(normalizedEmail);
        if (!cooldown.success()) {
            return cooldown;
        }

        return issueVerificationCode(normalizedEmail, true);
    }

    public ServiceResult verifyEmail(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = trimToNull(code);
        if (normalizedEmail == null || normalizedCode == null) {
            return ServiceResult.error("Email and code are required");
        }
        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository
            .findTopByEmailAndTokenOrderByCreatedAtDesc(normalizedEmail, normalizedCode);

        if (tokenOpt.isEmpty() || tokenOpt.get().getExpireAt().isBefore(LocalDateTime.now())) {
            return ServiceResult.error("Invalid or expired verification code");
        }

        RegisterRequest pending = PendingRegisterStore.get(normalizedEmail);
        if (pending == null) {
            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
            return ServiceResult.error("No pending registration for this email");
        }
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            PendingRegisterStore.remove(normalizedEmail);
            emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
            return ServiceResult.error("Email already registered");
        }
        ServiceResult usernameValidation = validateUsernameCandidate(pending.username(), null);
        if (!usernameValidation.success()) {
            return usernameValidation;
        }

        UserAccount user = new UserAccount();
        user.setEmail(pending.email());
        user.setUsername(readUsernameFromPayload(usernameValidation.data(), pending.username()));
        user.setDisplayName(resolveDisplayName(pending.displayName(), user.getUsername(), pending.email()));
        user.setAvatarPath(pending.avatarPath() == null || pending.avatarPath().isBlank()
            ? DEFAULT_AVATAR_PATH : pending.avatarPath());
        user.setCountry(normalizeCountry(pending.country()));
        user.setGender(normalizeGender(pending.gender()));
        user.setBirthDate(parseBirthDate(pending.birthDate()));
        user.setPassword(passwordEncoder.encode(pending.password()));
        user.setEmailConfirmed(true);
        user.setRole(isDefaultAdminEmail(pending.email()) ? "Admin" : "User");
        user.setOnboardingCompleted(isProfileCompleted(user));
        userAccountRepository.save(user);

        PendingRegisterStore.remove(normalizedEmail);
        emailVerificationTokenRepository.deleteAll(emailVerificationTokenRepository.findByEmail(normalizedEmail));
        return ServiceResult.ok(buildProfilePayload(user, "Account created"));
    }

    public ServiceResult login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || password == null) {
            return ServiceResult.error("Email and password are required");
        }
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ServiceResult.error("Invalid email or password");
        }

        if (user.isBanned()) {
            return ServiceResult.error("Account banned until " + user.getBannedUntil());
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ServiceResult.error("Invalid email or password");
        }

        applyPostLoginState(user);
        return ServiceResult.ok(toLoginPayload(user));
    }

    public ServiceResult loginWithOAuth2(OAuth2LoginRequest request) {
        String provider = normalizeSocialProvider(request == null ? null : request.provider());
        String providerUserId = trimToNull(request == null ? null : request.providerUserId());
        String normalizedEmail = normalizeEmail(request == null ? null : request.email());
        String displayName = trimToNull(request == null ? null : request.displayName());

        if (provider == null) {
            provider = "social";
        }
        if (providerUserId == null) {
            return ServiceResult.error("Cannot read social account id");
        }

        if (normalizedEmail == null) {
            normalizedEmail = syntheticOauthEmail(provider, providerUserId);
        }
        if (displayName == null) {
            displayName = defaultDisplayNameFromEmail(normalizedEmail);
        }

        UserAccount user = findBySocialProviderId(provider, providerUserId);
        if (user == null && normalizedEmail != null) {
            user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        }

        boolean profileUpdated = false;
        if (user == null) {
            user = new UserAccount();
            user.setEmail(normalizedEmail);
            user.setUsername(generateUniqueUsername(null, displayName, normalizedEmail, provider));
            user.setDisplayName(resolveDisplayName(displayName, user.getUsername(), normalizedEmail));
            user.setAvatarPath(DEFAULT_AVATAR_PATH);
            user.setPassword(passwordEncoder.encode(UUID.randomUUID() + ":" + provider + ":" + providerUserId));
            user.setEmailConfirmed(true);
            user.setRole(isDefaultAdminEmail(normalizedEmail) ? "Admin" : "User");
            user.setOnboardingCompleted(isProfileCompleted(user));
            profileUpdated = true;
        } else {
            if (trimToNull(user.getEmail()) == null && normalizedEmail != null) {
                UserAccount existingByEmail = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
                if (existingByEmail != null && !user.getId().equals(existingByEmail.getId())) {
                    return ServiceResult.error("Email already exists");
                }
                user.setEmail(normalizedEmail);
                profileUpdated = true;
            }
            if (trimToNull(user.getDisplayName()) == null && displayName != null) {
                user.setDisplayName(displayName);
                profileUpdated = true;
            }
            if (!isValidUsername(user.getUsername())) {
                user.setUsername(generateUniqueUsername(user.getId(), displayName, normalizedEmail, provider));
                profileUpdated = true;
            }
            if (trimToNull(user.getAvatarPath()) == null) {
                user.setAvatarPath(DEFAULT_AVATAR_PATH);
                profileUpdated = true;
            }
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                profileUpdated = true;
            }
        }
        if (!isProfileCompleted(user) && trimToNull(user.getDisplayName()) == null) {
            user.setDisplayName(resolveDisplayName(displayName, user.getUsername(), normalizedEmail));
            profileUpdated = true;
        }
        boolean onboardingCompleted = isProfileCompleted(user);
        if (user.isOnboardingCompleted() != onboardingCompleted) {
            user.setOnboardingCompleted(onboardingCompleted);
            profileUpdated = true;
        }

        if (user.isBanned()) {
            return ServiceResult.error("Account banned until " + user.getBannedUntil());
        }

        if (isSupportedSocialProvider(provider)) {
            ServiceResult ensureLinkResult = ensureSocialProviderLinked(user, provider, providerUserId, false);
            if (!ensureLinkResult.success()) {
                return ensureLinkResult;
            }
            if (Boolean.TRUE.equals(ensureLinkResult.data())) {
                profileUpdated = true;
            }
        }

        if (profileUpdated) {
            userAccountRepository.save(user);
        }

        applyPostLoginState(user);
        return ServiceResult.ok(toLoginPayload(user));
    }

    public ServiceResult logout(String userId) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        user.setOnline(false);
        userAccountRepository.save(user);
        return ServiceResult.ok(Map.of("message", "Logged out"));
    }

    public ServiceResult changePassword(String userId, String currentPassword, String newPassword) {
        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) return ServiceResult.error("User not found");
        if (currentPassword == null || currentPassword.isBlank()) {
            return ServiceResult.error("Current password is required");
        }
        List<String> passwordErrors = PasswordValidator.validate(newPassword);
        if (!passwordErrors.isEmpty()) {
            return ServiceResult.error(String.join("; ", passwordErrors));
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ServiceResult.error("Current password incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);
        return ServiceResult.ok(Map.of("message", "Password changed"));
    }

    public ServiceResult updateProfile(String userId,
                                       String username,
                                       String displayName,
                                       String email,
                                       String avatarPath,
                                       String country,
                                       String gender,
                                       String birthDate) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            normalizedEmail = normalizeEmail(user.getEmail());
        }
        if (normalizedEmail == null) {
            return ServiceResult.error("Email is required");
        }

        UserAccount existingByEmail = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (existingByEmail != null && !normalizedUserId.equals(existingByEmail.getId())) {
            return ServiceResult.error("Email already exists");
        }

        String fallbackUsername = normalizeUsername(username);
        if (fallbackUsername == null) {
            fallbackUsername = isValidUsername(user.getUsername())
                ? normalizeUsername(user.getUsername())
                : generateUniqueUsername(normalizedUserId, displayName, normalizedEmail, "player");
        }
        ServiceResult usernameValidation = validateUsernameCandidate(fallbackUsername, normalizedUserId);
        if (!usernameValidation.success()) {
            return usernameValidation;
        }

        String normalizedDisplayName = resolveDisplayName(
            trimToNull(displayName),
            readUsernameFromPayload(usernameValidation.data(), fallbackUsername),
            normalizedEmail
        );
        String normalizedAvatarPath = trimToNull(avatarPath);
        String normalizedCountry = normalizeCountry(country);
        String normalizedGender = normalizeGender(gender);
        LocalDate normalizedBirthDate = parseBirthDate(birthDate);

        user.setUsername(readUsernameFromPayload(usernameValidation.data(), fallbackUsername));
        user.setDisplayName(normalizedDisplayName);
        user.setEmail(normalizedEmail);
        user.setAvatarPath(normalizedAvatarPath == null ? DEFAULT_AVATAR_PATH : normalizedAvatarPath);
        user.setCountry(normalizedCountry);
        user.setGender(normalizedGender);
        user.setBirthDate(normalizedBirthDate);
        user.setOnboardingCompleted(isProfileCompleted(user));
        userAccountRepository.save(user);

        return ServiceResult.ok(buildProfilePayload(user, "Profile updated"));
    }

    public ServiceResult updateAvatar(String userId, String avatarPath) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        String normalizedAvatarPath = trimToNull(avatarPath);
        if (normalizedAvatarPath == null) {
            return ServiceResult.error("Avatar path is required");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        user.setAvatarPath(normalizedAvatarPath);
        userAccountRepository.save(user);
        return ServiceResult.ok(buildProfilePayload(user, null));
    }

    @Transactional
    public ServiceResult updateAvatarBinary(String userId, AvatarStorageService.StoreResult avatarContent) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        if (avatarContent == null || !avatarContent.success() || avatarContent.binaryData() == null || avatarContent.binaryData().length == 0) {
            return ServiceResult.error("Avatar data is required");
        }
        if (avatarContent.sizeBytes() > AvatarStorageService.MAX_AVATAR_BYTES || avatarContent.binaryData().length > AvatarStorageService.MAX_AVATAR_BYTES) {
            return ServiceResult.error("Avatar vuot qua gioi han 406MB");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        UserAvatarBinary avatarBinary = userAvatarBinaryRepository.findById(normalizedUserId).orElseGet(UserAvatarBinary::new);
        avatarBinary.setUserId(normalizedUserId);
        avatarBinary.setBinaryData(avatarContent.binaryData());
        avatarBinary.setContentType(normalizeAvatarContentType(avatarContent.contentType()));
        avatarBinary.setOriginalFileName(trimToNull(avatarContent.originalFileName()));
        long resolvedSizeBytes = avatarContent.sizeBytes() > 0L ? avatarContent.sizeBytes() : avatarContent.binaryData().length;
        avatarBinary.setSizeBytes(resolvedSizeBytes);
        userAvatarBinaryRepository.save(avatarBinary);

        user.setAvatarPath(DB_AVATAR_PATH_PREFIX + normalizedUserId);
        userAccountRepository.save(user);

        return ServiceResult.ok(buildProfilePayload(user, "Avatar uploaded"));
    }

    public AvatarBinaryPayload getAvatarBinary(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return null;
        }
        return userAvatarBinaryRepository.findById(normalizedUserId)
            .map(avatar -> new AvatarBinaryPayload(
                avatar.getBinaryData(),
                normalizeAvatarContentType(avatar.getContentType()),
                avatar.getOriginalFileName(),
                avatar.getSizeBytes() > 0L ? avatar.getSizeBytes() : (avatar.getBinaryData() == null ? 0L : avatar.getBinaryData().length)
            ))
            .orElse(null);
    }

    public ServiceResult getSocialLinks(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }
        return ServiceResult.ok(buildSocialLinksPayload(user, null));
    }

    @Transactional
    public ServiceResult linkOAuth2Provider(String userId, OAuth2LoginRequest request) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }

        String provider = normalizeSocialProvider(request == null ? null : request.provider());
        String providerUserId = trimToNull(request == null ? null : request.providerUserId());
        String normalizedEmail = normalizeEmail(request == null ? null : request.email());
        String displayName = trimToNull(request == null ? null : request.displayName());

        if (!isSupportedSocialProvider(provider)) {
            return ServiceResult.error("Unsupported social provider");
        }
        if (providerUserId == null) {
            return ServiceResult.error("Cannot read social account id");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        ServiceResult ensureLinkResult = ensureSocialProviderLinked(user, provider, providerUserId, false);
        if (!ensureLinkResult.success()) {
            return ensureLinkResult;
        }

        boolean profileUpdated = Boolean.TRUE.equals(ensureLinkResult.data());
        if (trimToNull(user.getDisplayName()) == null && displayName != null) {
            user.setDisplayName(displayName);
            profileUpdated = true;
        }
        if (trimToNull(user.getEmail()) == null && normalizedEmail != null) {
            UserAccount existingByEmail = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
            if (existingByEmail == null || normalizedUserId.equals(existingByEmail.getId())) {
                user.setEmail(normalizedEmail);
                if (!isValidUsername(user.getUsername())) {
                    user.setUsername(generateUniqueUsername(normalizedUserId, displayName, normalizedEmail, provider));
                }
                profileUpdated = true;
            }
        }
        if (user.isOnboardingCompleted() != isProfileCompleted(user)) {
            user.setOnboardingCompleted(isProfileCompleted(user));
            profileUpdated = true;
        }

        if (profileUpdated) {
            userAccountRepository.save(user);
        }

        String providerName = providerDisplayName(provider);
        String message = Boolean.TRUE.equals(ensureLinkResult.data())
            ? "Linked " + providerName + " account"
            : providerName + " account already linked";
        return ServiceResult.ok(buildSocialLinksPayload(user, message));
    }

    @Transactional
    public ServiceResult unlinkSocialProvider(String userId, String provider) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }

        String normalizedProvider = normalizeSocialProvider(provider);
        if (!isSupportedSocialProvider(normalizedProvider)) {
            return ServiceResult.error("Unsupported social provider");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        boolean changed = clearSocialProviderLink(user, normalizedProvider);
        if (changed) {
            userAccountRepository.save(user);
        }

        String providerName = providerDisplayName(normalizedProvider);
        String message = changed
            ? "Unlinked " + providerName + " account"
            : providerName + " account is not linked";
        return ServiceResult.ok(buildSocialLinksPayload(user, message));
    }

    public ServiceResult getPreferences(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }
        return ServiceResult.ok(toPreferencesPayload(user));
    }

    public ServiceResult getSessionUserSummary(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }
        return ServiceResult.ok(toLoginPayload(user));
    }

    public ServiceResult checkUsernameAvailability(String username, String excludeUserId) {
        ServiceResult validation = validateUsernameCandidate(username, excludeUserId);
        if (!validation.success()) {
            return validation;
        }
        String normalizedUsername = readUsernameFromPayload(validation.data(), username);
        return ServiceResult.ok(Map.of(
            "username", normalizedUsername,
            "available", true,
            "message", "Username available"
        ));
    }

    public ServiceResult updatePreferences(String userId, PreferencesRequest request) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        if (request == null) {
            return ServiceResult.error("Invalid request");
        }
        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        user.setThemeMode(normalizeThemeMode(request.themeMode()));
        user.setLanguage(normalizeLanguage(request.language()));
        user.setSidebarDesktopVisibleByDefault(valueOrDefault(request.sidebarDesktopVisibleByDefault(), user.isSidebarDesktopVisibleByDefault()));
        user.setSidebarMobileAutoClose(valueOrDefault(request.sidebarMobileAutoClose(), user.isSidebarMobileAutoClose()));
        user.setHomeMusicEnabled(valueOrDefault(request.homeMusicEnabled(), user.isHomeMusicEnabled()));
        user.setToastNotificationsEnabled(valueOrDefault(request.toastNotificationsEnabled(), user.isToastNotificationsEnabled()));
        user.setShowOfflineFriendsInSidebar(valueOrDefault(request.showOfflineFriendsInSidebar(), user.isShowOfflineFriendsInSidebar()));
        user.setAutoRefreshFriendList(valueOrDefault(request.autoRefreshFriendList(), user.isAutoRefreshFriendList()));
        user.setFriendListRefreshMs(normalizeFriendRefreshMs(request.friendListRefreshMs(), user.getFriendListRefreshMs()));
        userAccountRepository.save(user);

        return ServiceResult.ok(toPreferencesPayload(user));
    }

    public ServiceResult getGameStats(String userId, String gameCode) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        String normalizedGameCode = normalizeGameCode(gameCode);
        if (normalizedGameCode == null) {
            return ServiceResult.error("Unsupported gameCode");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        Map<String, Object> stats = normalizeStatsByGameCode(
            normalizedGameCode,
            readRawStatsForGameCode(user, normalizedGameCode)
        );
        return ServiceResult.ok(Map.of(
            "gameCode", normalizedGameCode,
            "stats", stats
        ));
    }

    public ServiceResult updateGameStats(String userId, String gameCode, Object statsPayload, boolean mergeExisting) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        String normalizedGameCode = normalizeGameCode(gameCode);
        if (normalizedGameCode == null) {
            return ServiceResult.error("Unsupported gameCode");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }
        if (!(statsPayload instanceof Map<?, ?>)) {
            return ServiceResult.error("Stats payload is required");
        }

        Map<String, Object> incoming = normalizeStatsByGameCode(normalizedGameCode, statsPayload);
        Map<String, Object> normalizedToPersist = incoming;
        if (mergeExisting) {
            Map<String, Object> existing = normalizeStatsByGameCode(
                normalizedGameCode,
                readRawStatsForGameCode(user, normalizedGameCode)
            );
            normalizedToPersist = mergeStatsByGameCode(normalizedGameCode, existing, incoming);
        }

        ServiceResult writeResult = writeStatsForGameCode(user, normalizedGameCode, normalizedToPersist);
        if (!writeResult.success()) {
            return writeResult;
        }
        maybeAwardStatsAchievement(normalizedUserId, normalizedGameCode, normalizedToPersist);

        return ServiceResult.ok(Map.of(
            "gameCode", normalizedGameCode,
            "stats", normalizedToPersist
        ));
    }

    public ServiceResult getPuzzleCatalogState(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        return ServiceResult.ok(toPuzzleCatalogStatePayload(user));
    }

    public ServiceResult updatePuzzleCatalogState(String userId, PuzzleCatalogStateRequest request) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        if (request == null) {
            return ServiceResult.error("Invalid request");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        boolean merge = request.merge() == null || request.merge();

        List<String> existingFavorites = readStringList(user.getPuzzleCatalogFavoritesJson());
        Map<String, Integer> existingRatings = normalizePuzzleCatalogRatings(readJsonMap(user.getPuzzleCatalogRatingsJson()));
        List<String> existingRecentCodes = readStringList(user.getPuzzleCatalogRecentJson());

        List<String> nextFavorites = existingFavorites;
        if (request.favorites() != null) {
            List<String> incomingFavorites = normalizePuzzleCatalogStringList(request.favorites(), 256);
            nextFavorites = merge
                ? mergePuzzleCatalogStringLists(incomingFavorites, existingFavorites, 256)
                : incomingFavorites;
        }

        Map<String, Integer> nextRatings = existingRatings;
        if (request.ratings() != null) {
            Map<String, Integer> incomingRatings = normalizePuzzleCatalogRatings(request.ratings());
            nextRatings = merge ? mergePuzzleCatalogRatings(existingRatings, incomingRatings) : incomingRatings;
        }

        List<String> nextRecentCodes = existingRecentCodes;
        if (request.recentCodes() != null) {
            List<String> incomingRecentCodes = normalizePuzzleCatalogStringList(request.recentCodes(), PUZZLE_RECENT_LIMIT);
            nextRecentCodes = merge
                ? mergePuzzleCatalogStringLists(incomingRecentCodes, existingRecentCodes, PUZZLE_RECENT_LIMIT)
                : incomingRecentCodes;
        }

        String favoritesJson = writeJson(nextFavorites);
        String ratingsJson = writeJson(nextRatings);
        String recentJson = writeJson(nextRecentCodes);
        if (favoritesJson == null || ratingsJson == null || recentJson == null) {
            return ServiceResult.error("Cannot serialize puzzle catalog state");
        }

        user.setPuzzleCatalogFavoritesJson(favoritesJson);
        user.setPuzzleCatalogRatingsJson(ratingsJson);
        user.setPuzzleCatalogRecentJson(recentJson);
        userAccountRepository.save(user);

        return ServiceResult.ok(toPuzzleCatalogStatePayload(user));
    }

    public ServiceResult getGamesBrowserState(String userId) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        return ServiceResult.ok(toGamesBrowserStatePayload(user));
    }

    public ServiceResult updateGamesBrowserState(String userId, GamesBrowserStateRequest request) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        if (request == null) {
            return ServiceResult.error("Invalid request");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        boolean merge = request.merge() == null || request.merge();

        List<String> existingFavorites = normalizeGamesBrowserFavoriteCodes(readStringList(user.getGamesBrowserFavoritesJson()));
        List<Map<String, Object>> existingRecentGames = readGamesBrowserRecentGames(user.getGamesBrowserRecentJson());

        List<String> nextFavorites = existingFavorites;
        if (request.favorites() != null) {
            List<String> incomingFavorites = normalizeGamesBrowserFavoriteCodes(request.favorites());
            nextFavorites = merge
                ? mergePuzzleCatalogStringLists(incomingFavorites, existingFavorites, 256)
                : incomingFavorites;
        }

        List<Map<String, Object>> nextRecentGames = existingRecentGames;
        if (request.recentGames() != null) {
            List<Map<String, Object>> incomingRecentGames = normalizeGamesBrowserRecentGames(request.recentGames());
            nextRecentGames = merge
                ? mergeGamesBrowserRecentGames(incomingRecentGames, existingRecentGames)
                : incomingRecentGames;
        }

        String favoritesJson = writeJson(nextFavorites);
        String recentGamesJson = writeJson(nextRecentGames);
        if (favoritesJson == null || recentGamesJson == null) {
            return ServiceResult.error("Cannot serialize games browser state");
        }

        user.setGamesBrowserFavoritesJson(favoritesJson);
        user.setGamesBrowserRecentJson(recentGamesJson);
        userAccountRepository.save(user);

        return ServiceResult.ok(toGamesBrowserStatePayload(user));
    }

    @Transactional
    public ServiceResult migrateGuestData(String userId, GuestMigrationRequest request) {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        if (request == null) {
            return ServiceResult.error("Invalid request");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        Map<String, Object> response = new HashMap<>();
        boolean migratedPreferences = false;
        List<String> migratedGameStats = new java.util.ArrayList<>();

        if (request.preferences() != null) {
            ServiceResult preferencesResult = updatePreferences(normalizedUserId, request.preferences());
            if (!preferencesResult.success()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return preferencesResult;
            }
            migratedPreferences = true;
            response.put("preferences", preferencesResult.data());
        }

        if (request.gameStats() != null && !request.gameStats().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.gameStats().entrySet()) {
                ServiceResult statsResult = updateGameStats(normalizedUserId, entry.getKey(), entry.getValue(), true);
                if (!statsResult.success()) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return statsResult;
                }
                Object data = statsResult.data();
                if (data instanceof Map<?, ?> statsMap) {
                    Object normalizedGameCode = statsMap.get("gameCode");
                    if (normalizedGameCode != null) {
                        migratedGameStats.add(String.valueOf(normalizedGameCode));
                    }
                }
            }
        }

        if (request.puzzleCatalogState() != null) {
            ServiceResult puzzleCatalogResult = updatePuzzleCatalogState(normalizedUserId, request.puzzleCatalogState());
            if (!puzzleCatalogResult.success()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return puzzleCatalogResult;
            }
            response.put("puzzleCatalogState", puzzleCatalogResult.data());
        }

        if (request.gamesBrowserState() != null) {
            ServiceResult gamesBrowserResult = updateGamesBrowserState(normalizedUserId, request.gamesBrowserState());
            if (!gamesBrowserResult.success()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return gamesBrowserResult;
            }
            response.put("gamesBrowserState", gamesBrowserResult.data());
        }

        response.put("migratedPreferences", migratedPreferences);
        response.put("migratedGameStats", migratedGameStats);
        response.put("migratedGameStatsCount", migratedGameStats.size());
        response.put("migratedPuzzleCatalogState", request.puzzleCatalogState() != null);
        response.put("migratedGamesBrowserState", request.gamesBrowserState() != null);
        return ServiceResult.ok(response);
    }

    public ServiceResult sendResetCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) return ServiceResult.error("Email is required");
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ServiceResult.ok(Map.of("message", "If the email exists, a reset code has been sent"));
        }

        String code = String.valueOf(100000 + random.nextInt(900000));
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(code);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpireAt(LocalDateTime.now().plusMinutes(5));
        passwordResetTokenRepository.save(token);

        try {
            emailService.sendEmail(normalizedEmail, "Caro Reset Password", "Your reset code is: " + code);
        } catch (RuntimeException ex) {
            passwordResetTokenRepository.delete(token);
            return ServiceResult.error("Cannot send reset code right now. Please try again.");
        }
        return ServiceResult.ok(Map.of("message", "If the email exists, a reset code has been sent"));
    }

    public ServiceResult verifyResetCode(String userId, String code) {
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository
            .findTopByUserIdAndTokenOrderByCreatedAtDesc(userId, code);

        if (tokenOpt.isEmpty() || tokenOpt.get().getExpireAt().isBefore(LocalDateTime.now())) {
            return ServiceResult.error("Invalid or expired reset code");
        }
        return ServiceResult.ok(Map.of("message", "Code verified"));
    }

    public ServiceResult verifyResetCodeByEmail(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = trimToNull(code);
        if (normalizedEmail == null || normalizedCode == null) {
            return ServiceResult.error("Email and code are required");
        }
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ServiceResult.error("Invalid or expired reset code");
        }
        return verifyResetCode(user.getId(), normalizedCode);
    }

    public ServiceResult resetPassword(String userId, String code, String newPassword, String confirmPassword) {
        List<String> passwordErrors = PasswordValidator.validate(newPassword);
        if (!passwordErrors.isEmpty()) {
            return ServiceResult.error(String.join("; ", passwordErrors));
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            return ServiceResult.error("Password confirmation is required");
        }
        if (!newPassword.equals(confirmPassword)) {
            return ServiceResult.error("Password confirmation does not match");
        }

        ServiceResult verify = verifyResetCode(userId, code);
        if (!verify.success()) {
            return verify;
        }

        UserAccount user = userAccountRepository.findById(userId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findByUserId(userId);
        passwordResetTokenRepository.deleteAll(tokens);
        return ServiceResult.ok(Map.of("message", "Password reset success"));
    }

    public ServiceResult resetPasswordByEmail(String email, String code, String newPassword, String confirmPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return ServiceResult.error("Email is required");
        }
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return ServiceResult.error("Invalid or expired reset code");
        }
        return resetPassword(user.getId(), code, newPassword, confirmPassword);
    }

    public ServiceResult listUsers(String searchTerm, String banFilter) {
        List<UserAccount> users = userAccountRepository.findAll();

        if (searchTerm != null && !searchTerm.isBlank()) {
            String lower = searchTerm.toLowerCase();
            users = users.stream().filter(u ->
                (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(lower))
                    || (u.getUsername() != null && u.getUsername().toLowerCase().contains(lower))
                    || (u.getEmail() != null && u.getEmail().toLowerCase().contains(lower))
            ).toList();
        }

        if ("banned".equalsIgnoreCase(banFilter)) {
            users = users.stream().filter(UserAccount::isBanned).toList();
        } else if ("active".equalsIgnoreCase(banFilter)) {
            users = users.stream().filter(u -> !u.isBanned()).toList();
        }

        users = users.stream().sorted(Comparator.comparing(UserAccount::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
        return ServiceResult.ok(Map.of("users", users));
    }

    public ServiceResult activateAdminRole(String userId, String activationCode) {
        String normalizedUserId = trimToNull(userId);
        String normalizedCode = trimToNull(activationCode);
        if (normalizedUserId == null) {
            return ServiceResult.error("Login required");
        }
        if (normalizedCode == null) {
            return ServiceResult.error("Activation code is required");
        }
        if (adminActivationCode == null || !adminActivationCode.equals(normalizedCode)) {
            return ServiceResult.error("Activation code is invalid");
        }

        UserAccount user = userAccountRepository.findById(normalizedUserId).orElse(null);
        if (user == null) {
            return ServiceResult.error("User not found");
        }
        if (user.isBanned()) {
            return ServiceResult.error("Account is banned");
        }

        boolean roleChanged = !"Admin".equalsIgnoreCase(user.getRole());
        if (roleChanged) {
            user.setRole("Admin");
            userAccountRepository.save(user);
        }

        return ServiceResult.ok(buildProfilePayload(
            user,
            roleChanged ? "Admin role activated" : "Account is already Admin"
        ));
    }

    public static final class PendingRegisterStore {
        private static final Map<String, PendingRegisterEntry> STORE = new java.util.concurrent.ConcurrentHashMap<>();

        private PendingRegisterStore() {
        }

        public static void put(String email, RegisterRequest request, LocalDateTime expireAt) {
            STORE.put(email, new PendingRegisterEntry(request, expireAt));
        }

        public static RegisterRequest get(String email) {
            PendingRegisterEntry entry = STORE.get(email);
            if (entry == null) {
                return null;
            }
            if (entry.expireAt() != null && entry.expireAt().isBefore(LocalDateTime.now())) {
                STORE.remove(email);
                return null;
            }
            return entry.request();
        }

        public static void remove(String email) {
            STORE.remove(email);
        }

        private record PendingRegisterEntry(RegisterRequest request, LocalDateTime expireAt) {
        }
    }

    public record RegisterRequest(String email,
                                  String displayName,
                                  String username,
                                  String password,
                                  String avatarPath,
                                  String country,
                                  String gender,
                                  String birthDate) {
    }

    public record OAuth2LoginRequest(String provider, String providerUserId, String email, String displayName) {
    }

    public record AvatarBinaryPayload(byte[] binaryData, String contentType, String originalFileName, long sizeBytes) {
    }

    public record PreferencesRequest(String themeMode,
                                     String language,
                                     Boolean sidebarDesktopVisibleByDefault,
                                     Boolean sidebarMobileAutoClose,
                                     Boolean homeMusicEnabled,
                                     Boolean toastNotificationsEnabled,
                                     Boolean showOfflineFriendsInSidebar,
                                     Boolean autoRefreshFriendList,
                                     Integer friendListRefreshMs) {
    }

    public record PuzzleCatalogStateRequest(List<String> favorites,
                                            Map<String, Object> ratings,
                                            List<String> recentCodes,
                                            Boolean merge) {
    }

    public record GamesBrowserStateRequest(List<String> favorites,
                                           List<Map<String, Object>> recentGames,
                                           Boolean merge) {
    }

    public record GuestMigrationRequest(PreferencesRequest preferences,
                                        Map<String, Object> gameStats,
                                        PuzzleCatalogStateRequest puzzleCatalogState,
                                        GamesBrowserStateRequest gamesBrowserState) {
    }

    public record ServiceResult(boolean success, String error, Object data) {
        public static ServiceResult ok(Object data) {
            return new ServiceResult(true, null, data);
        }

        public static ServiceResult error(String error) {
            return new ServiceResult(false, error, null);
        }
    }

    private String normalizeGameCode(String gameCode) {
        String normalized = trimToNull(gameCode);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase();
        if ("chess-offline".equals(lowered) || "chess_offline".equals(lowered)
            || "chessoffline".equals(lowered) || "chess".equals(lowered)) {
            return GAME_CODE_CHESS_OFFLINE;
        }
        if ("xiangqi-offline".equals(lowered) || "xiangqi_offline".equals(lowered)
            || "xiangqioffline".equals(lowered) || "xiangqi".equals(lowered)) {
            return GAME_CODE_XIANGQI_OFFLINE;
        }
        if ("minesweeper".equals(lowered)) {
            return GAME_CODE_MINESWEEPER;
        }
        if ("quiz-practice".equals(lowered) || "quiz_practice".equals(lowered)
            || "quizpractice".equals(lowered) || "quiz-local".equals(lowered)
            || "quiz_local".equals(lowered) || "quizlocal".equals(lowered)
            || "quiz-bot".equals(lowered) || "quiz_bot".equals(lowered)
            || "quizbot".equals(lowered) || "quiz".equals(lowered)) {
            return GAME_CODE_QUIZ_PRACTICE;
        }
        if ("typing-practice".equals(lowered) || "typing_practice".equals(lowered)
            || "typingpractice".equals(lowered) || "typing-local".equals(lowered)
            || "typing_local".equals(lowered) || "typinglocal".equals(lowered)
            || "typing-bot".equals(lowered) || "typing_bot".equals(lowered)
            || "typingbot".equals(lowered) || "typing".equals(lowered)) {
            return GAME_CODE_TYPING_PRACTICE;
        }
        return null;
    }

    private Map<String, Object> readRawStatsForGameCode(UserAccount user, String gameCode) {
        if (user == null || gameCode == null) {
            return Map.of();
        }
        String rawJson;
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            rawJson = user.getChessOfflineStatsJson();
        } else if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            rawJson = user.getXiangqiOfflineStatsJson();
        } else if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            rawJson = user.getMinesweeperStatsJson();
        } else if (GAME_CODE_QUIZ_PRACTICE.equals(gameCode)) {
            rawJson = user.getQuizPracticeStatsJson();
        } else if (GAME_CODE_TYPING_PRACTICE.equals(gameCode)) {
            rawJson = user.getTypingPracticeStatsJson();
        } else {
            return Map.of();
        }
        return readJsonMap(rawJson);
    }

    private ServiceResult writeStatsForGameCode(UserAccount user, String gameCode, Map<String, Object> normalizedStats) {
        if (user == null || gameCode == null) {
            return ServiceResult.error("Invalid user or gameCode");
        }
        String json = writeJson(normalizedStats);
        if (json == null) {
            return ServiceResult.error("Cannot serialize stats");
        }

        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            user.setChessOfflineStatsJson(json);
        } else if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            user.setXiangqiOfflineStatsJson(json);
        } else if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            user.setMinesweeperStatsJson(json);
        } else if (GAME_CODE_QUIZ_PRACTICE.equals(gameCode)) {
            user.setQuizPracticeStatsJson(json);
        } else if (GAME_CODE_TYPING_PRACTICE.equals(gameCode)) {
            user.setTypingPracticeStatsJson(json);
        } else {
            return ServiceResult.error("Unsupported gameCode");
        }
        userAccountRepository.save(user);
        return ServiceResult.ok(Map.of());
    }

    private Map<String, Object> normalizeStatsByGameCode(String gameCode, Object rawStats) {
        Map<String, Object> source = asStringObjectMap(rawStats);
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            return normalizeChessStats(source);
        }
        if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            return normalizeXiangqiStats(source);
        }
        if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            return normalizeMinesweeperStats(source);
        }
        if (GAME_CODE_QUIZ_PRACTICE.equals(gameCode)) {
            return normalizeQuizPracticeStats(source);
        }
        if (GAME_CODE_TYPING_PRACTICE.equals(gameCode)) {
            return normalizeTypingPracticeStats(source);
        }
        return Map.of();
    }

    private Map<String, Object> mergeStatsByGameCode(String gameCode,
                                                     Map<String, Object> existing,
                                                     Map<String, Object> incoming) {
        if (GAME_CODE_CHESS_OFFLINE.equals(gameCode)) {
            return mergeChessStats(existing, incoming);
        }
        if (GAME_CODE_XIANGQI_OFFLINE.equals(gameCode)) {
            return mergeXiangqiStats(existing, incoming);
        }
        if (GAME_CODE_MINESWEEPER.equals(gameCode)) {
            return mergeMinesweeperStats(existing, incoming);
        }
        if (GAME_CODE_QUIZ_PRACTICE.equals(gameCode)) {
            return mergeQuizPracticeStats(existing, incoming);
        }
        if (GAME_CODE_TYPING_PRACTICE.equals(gameCode)) {
            return mergeTypingPracticeStats(existing, incoming);
        }
        return Map.of();
    }

    private Map<String, Object> normalizeChessStats(Map<String, Object> source) {
        int whiteWins = Math.max(0, toInt(source.get("whiteWins"), 0));
        int blackWins = Math.max(0, toInt(source.get("blackWins"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        return Map.of(
            "whiteWins", whiteWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> normalizeXiangqiStats(Map<String, Object> source) {
        int redWins = Math.max(0, toInt(source.get("redWins"), 0));
        int blackWins = Math.max(0, toInt(source.get("blackWins"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        return Map.of(
            "redWins", redWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> normalizeMinesweeperStats(Map<String, Object> source) {
        int totalGames = Math.max(0, toInt(source.get("totalGames"), 0));
        int wins = Math.max(0, toInt(source.get("wins"), 0));
        int losses = Math.max(0, toInt(source.get("losses"), Math.max(0, totalGames - wins)));
        totalGames = Math.max(totalGames, wins + losses);

        Map<String, Integer> bestTimes = normalizeBestTimesMap(source.get("bestTimes"));

        Map<String, Object> result = new HashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("bestTimes", bestTimes);
        return result;
    }

    private Map<String, Object> normalizeQuizPracticeStats(Map<String, Object> source) {
        int totalGames = Math.max(0, toInt(source.get("totalGames"), 0));
        int wins = Math.max(0, toInt(source.get("wins"), 0));
        int losses = Math.max(0, toInt(source.get("losses"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        int bestScore = Math.max(0, toInt(source.get("bestScore"), 0));
        int perfectRounds = Math.max(0, toInt(source.get("perfectRounds"), 0));
        totalGames = Math.max(totalGames, wins + losses + draws);

        Map<String, Object> result = new HashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("draws", draws);
        result.put("bestScore", bestScore);
        result.put("perfectRounds", perfectRounds);
        return result;
    }

    private Map<String, Object> normalizeTypingPracticeStats(Map<String, Object> source) {
        int totalGames = Math.max(0, toInt(source.get("totalGames"), 0));
        int wins = Math.max(0, toInt(source.get("wins"), 0));
        int losses = Math.max(0, toInt(source.get("losses"), 0));
        int draws = Math.max(0, toInt(source.get("draws"), 0));
        int bestWpm = Math.max(0, toInt(source.get("bestWpm"), 0));
        double bestAccuracy = normalizePercentage(source.get("bestAccuracy"));
        int completedQuotes = Math.max(0, toInt(source.get("completedQuotes"), 0));
        totalGames = Math.max(totalGames, wins + losses + draws);

        Map<String, Object> result = new HashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("draws", draws);
        result.put("bestWpm", bestWpm);
        result.put("bestAccuracy", bestAccuracy);
        result.put("completedQuotes", completedQuotes);
        return result;
    }

    private Map<String, Object> mergeChessStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeChessStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeChessStats(asStringObjectMap(incoming));
        int whiteWins = Math.max(toInt(safeExisting.get("whiteWins"), 0), toInt(safeIncoming.get("whiteWins"), 0));
        int blackWins = Math.max(toInt(safeExisting.get("blackWins"), 0), toInt(safeIncoming.get("blackWins"), 0));
        int draws = Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0));
        return Map.of(
            "whiteWins", whiteWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> mergeXiangqiStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeXiangqiStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeXiangqiStats(asStringObjectMap(incoming));
        int redWins = Math.max(toInt(safeExisting.get("redWins"), 0), toInt(safeIncoming.get("redWins"), 0));
        int blackWins = Math.max(toInt(safeExisting.get("blackWins"), 0), toInt(safeIncoming.get("blackWins"), 0));
        int draws = Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0));
        return Map.of(
            "redWins", redWins,
            "blackWins", blackWins,
            "draws", draws
        );
    }

    private Map<String, Object> mergeMinesweeperStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeMinesweeperStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeMinesweeperStats(asStringObjectMap(incoming));

        int wins = Math.max(toInt(safeExisting.get("wins"), 0), toInt(safeIncoming.get("wins"), 0));
        int losses = Math.max(toInt(safeExisting.get("losses"), 0), toInt(safeIncoming.get("losses"), 0));
        int totalGames = Math.max(toInt(safeExisting.get("totalGames"), 0), toInt(safeIncoming.get("totalGames"), 0));
        totalGames = Math.max(totalGames, wins + losses);

        Map<String, Integer> mergedBestTimes = normalizeBestTimesMap(safeExisting.get("bestTimes"));
        Map<String, Integer> incomingBestTimes = normalizeBestTimesMap(safeIncoming.get("bestTimes"));
        for (Map.Entry<String, Integer> entry : incomingBestTimes.entrySet()) {
            String key = entry.getKey();
            Integer sec = entry.getValue();
            Integer prev = mergedBestTimes.get(key);
            if (prev == null || sec < prev) {
                mergedBestTimes.put(key, sec);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalGames", totalGames);
        result.put("wins", wins);
        result.put("losses", losses);
        result.put("bestTimes", mergedBestTimes);
        return result;
    }

    private Map<String, Object> mergeQuizPracticeStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeQuizPracticeStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeQuizPracticeStats(asStringObjectMap(incoming));

        Map<String, Object> result = new HashMap<>();
        result.put("totalGames", Math.max(toInt(safeExisting.get("totalGames"), 0), toInt(safeIncoming.get("totalGames"), 0)));
        result.put("wins", Math.max(toInt(safeExisting.get("wins"), 0), toInt(safeIncoming.get("wins"), 0)));
        result.put("losses", Math.max(toInt(safeExisting.get("losses"), 0), toInt(safeIncoming.get("losses"), 0)));
        result.put("draws", Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0)));
        result.put("bestScore", Math.max(toInt(safeExisting.get("bestScore"), 0), toInt(safeIncoming.get("bestScore"), 0)));
        result.put("perfectRounds", Math.max(toInt(safeExisting.get("perfectRounds"), 0), toInt(safeIncoming.get("perfectRounds"), 0)));
        return normalizeQuizPracticeStats(result);
    }

    private Map<String, Object> mergeTypingPracticeStats(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> safeExisting = normalizeTypingPracticeStats(asStringObjectMap(existing));
        Map<String, Object> safeIncoming = normalizeTypingPracticeStats(asStringObjectMap(incoming));

        Map<String, Object> result = new HashMap<>();
        result.put("totalGames", Math.max(toInt(safeExisting.get("totalGames"), 0), toInt(safeIncoming.get("totalGames"), 0)));
        result.put("wins", Math.max(toInt(safeExisting.get("wins"), 0), toInt(safeIncoming.get("wins"), 0)));
        result.put("losses", Math.max(toInt(safeExisting.get("losses"), 0), toInt(safeIncoming.get("losses"), 0)));
        result.put("draws", Math.max(toInt(safeExisting.get("draws"), 0), toInt(safeIncoming.get("draws"), 0)));
        result.put("bestWpm", Math.max(toInt(safeExisting.get("bestWpm"), 0), toInt(safeIncoming.get("bestWpm"), 0)));
        result.put("bestAccuracy", Math.max(normalizePercentage(safeExisting.get("bestAccuracy")), normalizePercentage(safeIncoming.get("bestAccuracy"))));
        result.put("completedQuotes", Math.max(toInt(safeExisting.get("completedQuotes"), 0), toInt(safeIncoming.get("completedQuotes"), 0)));
        return normalizeTypingPracticeStats(result);
    }

    private Map<String, Integer> normalizeBestTimesMap(Object raw) {
        Map<String, Integer> result = new HashMap<>();
        if (!(raw instanceof Map<?, ?> source)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey()).trim();
            if (key.isEmpty()) {
                continue;
            }
            int sec = toInt(entry.getValue(), -1);
            if (sec >= 0) {
                result.put(key, sec);
            }
        }
        return result;
    }

    private Map<String, Object> asStringObjectMap(Object raw) {
        Map<String, Object> result = new HashMap<>();
        if (!(raw instanceof Map<?, ?> source)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double normalizePercentage(Object value) {
        if (value == null) {
            return 0.0;
        }
        double parsed;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return 0.0;
            }
            try {
                parsed = Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
        double clamped = Math.max(0.0, Math.min(100.0, parsed));
        return Math.round(clamped * 10.0) / 10.0;
    }

    private void maybeAwardStatsAchievement(String userId, String gameCode, Map<String, Object> stats) {
        if (achievementService == null || userId == null || gameCode == null || stats == null) {
            return;
        }
        if (GAME_CODE_QUIZ_PRACTICE.equals(gameCode) && toInt(stats.get("wins"), 0) > 0) {
            achievementService.checkAndAward(userId, "Quiz", true);
            return;
        }
        if (GAME_CODE_TYPING_PRACTICE.equals(gameCode) && toInt(stats.get("wins"), 0) > 0) {
            achievementService.checkAndAward(userId, "Typing", true);
        }
    }

    private long toLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        String raw = trimToNull(json);
        if (raw == null) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> readStringList(String json) {
        String raw = trimToNull(json);
        if (raw == null) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            return normalizePuzzleCatalogStringList(parsed, 256);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> normalizePuzzleCatalogStringList(Object rawValue, int limit) {
        if (!(rawValue instanceof Iterable<?> iterable)) {
            return List.of();
        }
        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        for (Object item : iterable) {
            String normalized = trimToNull(item == null ? null : String.valueOf(item));
            if (normalized == null) {
                continue;
            }
            ordered.add(normalized);
            if (ordered.size() >= limit) {
                break;
            }
        }
        return List.copyOf(ordered);
    }

    private List<String> mergePuzzleCatalogStringLists(List<String> primary, List<String> secondary, int limit) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        for (String value : primary == null ? List.<String>of() : primary) {
            String normalized = trimToNull(value);
            if (normalized == null) {
                continue;
            }
            merged.add(normalized);
            if (merged.size() >= limit) {
                return List.copyOf(merged);
            }
        }
        for (String value : secondary == null ? List.<String>of() : secondary) {
            String normalized = trimToNull(value);
            if (normalized == null) {
                continue;
            }
            merged.add(normalized);
            if (merged.size() >= limit) {
                break;
            }
        }
        return List.copyOf(merged);
    }

    private Map<String, Integer> normalizePuzzleCatalogRatings(Object rawValue) {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        if (!(rawValue instanceof Map<?, ?> source)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String code = trimToNull(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (code == null) {
                continue;
            }
            int rating = toInt(entry.getValue(), 0);
            if (rating >= 1 && rating <= 5) {
                result.put(code, rating);
            }
        }
        return result;
    }

    private Map<String, Integer> mergePuzzleCatalogRatings(Map<String, Integer> existing, Map<String, Integer> incoming) {
        Map<String, Integer> merged = new java.util.LinkedHashMap<>();
        if (existing != null) {
            merged.putAll(existing);
        }
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return merged;
    }

    private Map<String, Object> toPuzzleCatalogStatePayload(UserAccount user) {
        if (user == null) {
            return Map.of(
                "favorites", List.of(),
                "ratings", Map.of(),
                "recentCodes", List.of()
            );
        }
        return Map.of(
            "favorites", readStringList(user.getPuzzleCatalogFavoritesJson()),
            "ratings", normalizePuzzleCatalogRatings(readJsonMap(user.getPuzzleCatalogRatingsJson())),
            "recentCodes", normalizePuzzleCatalogStringList(readStringList(user.getPuzzleCatalogRecentJson()), PUZZLE_RECENT_LIMIT)
        );
    }

    private List<String> normalizeGamesBrowserFavoriteCodes(Object rawValue) {
        if (!(rawValue instanceof Iterable<?> iterable)) {
            return List.of();
        }
        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        for (Object item : iterable) {
            String normalized = normalizeGamesBrowserCode(item == null ? null : String.valueOf(item));
            if (normalized == null) {
                continue;
            }
            ordered.add(normalized);
            if (ordered.size() >= 256) {
                break;
            }
        }
        return List.copyOf(ordered);
    }

    private String normalizeGamesBrowserCode(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private List<Map<String, Object>> readGamesBrowserRecentGames(String json) {
        String raw = trimToNull(json);
        if (raw == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {
            });
            return normalizeGamesBrowserRecentGames(parsed);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> normalizeGamesBrowserRecentGames(Object rawValue) {
        if (!(rawValue instanceof Iterable<?> iterable)) {
            return List.of();
        }
        java.util.LinkedHashMap<String, Map<String, Object>> ordered = new java.util.LinkedHashMap<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> source)) {
                continue;
            }
            String code = normalizeGamesBrowserCode(source.get("code") == null ? null : String.valueOf(source.get("code")));
            if (code == null) {
                continue;
            }
            String name = trimToNull(source.get("name") == null ? null : String.valueOf(source.get("name")));
            long at = toLong(source.get("at"), System.currentTimeMillis());
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("code", code);
            payload.put("name", name == null ? code : name);
            payload.put("at", Math.max(0L, at));
            ordered.putIfAbsent(code, Map.copyOf(payload));
            if (ordered.size() >= GAMES_BROWSER_RECENT_LIMIT) {
                break;
            }
        }
        return List.copyOf(ordered.values());
    }

    private List<Map<String, Object>> mergeGamesBrowserRecentGames(List<Map<String, Object>> primary,
                                                                   List<Map<String, Object>> secondary) {
        java.util.LinkedHashMap<String, Map<String, Object>> merged = new java.util.LinkedHashMap<>();
        for (Map<String, Object> item : primary == null ? List.<Map<String, Object>>of() : primary) {
            String code = normalizeGamesBrowserCode(item == null ? null : String.valueOf(item.get("code")));
            if (code == null) {
                continue;
            }
            merged.putIfAbsent(code, item);
            if (merged.size() >= GAMES_BROWSER_RECENT_LIMIT) {
                return List.copyOf(merged.values());
            }
        }
        for (Map<String, Object> item : secondary == null ? List.<Map<String, Object>>of() : secondary) {
            String code = normalizeGamesBrowserCode(item == null ? null : String.valueOf(item.get("code")));
            if (code == null) {
                continue;
            }
            merged.putIfAbsent(code, item);
            if (merged.size() >= GAMES_BROWSER_RECENT_LIMIT) {
                break;
            }
        }
        return List.copyOf(merged.values());
    }

    private Map<String, Object> toGamesBrowserStatePayload(UserAccount user) {
        if (user == null) {
            return Map.of(
                "favorites", List.of(),
                "recentGames", List.of()
            );
        }
        return Map.of(
            "favorites", normalizeGamesBrowserFavoriteCodes(readStringList(user.getGamesBrowserFavoritesJson())),
            "recentGames", readGamesBrowserRecentGames(user.getGamesBrowserRecentJson())
        );
    }

    private String normalizeUsername(String username) {
        String normalized = trimToNull(username);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("@")) {
            normalized = trimToNull(normalized.substring(1));
        }
        return normalized;
    }

    private boolean isValidUsername(String username) {
        String normalized = normalizeUsername(username);
        return normalized != null && USERNAME_PATTERN.matcher(normalized).matches();
    }

    private ServiceResult validateUsernameCandidate(String username, String excludeUserId) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            return ServiceResult.error("Username is required");
        }
        if (normalized.length() < 6 || normalized.length() > 20) {
            return ServiceResult.error("Username must be between 6 and 20 characters");
        }
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            return ServiceResult.error("Username may contain only letters, numbers, '.' and '_'");
        }

        UserAccount existing = userAccountRepository.findByUsernameIgnoreCase(normalized).orElse(null);
        String normalizedExcludeUserId = trimToNull(excludeUserId);
        if (existing != null && !Objects.equals(normalizedExcludeUserId, existing.getId())) {
            return ServiceResult.error("Username already exists");
        }

        return ServiceResult.ok(Map.of(
            "username", normalized,
            "available", true
        ));
    }

    private String readUsernameFromPayload(Object data, String fallback) {
        if (data instanceof Map<?, ?> payload) {
            Object value = payload.get("username");
            if (value != null) {
                String normalized = normalizeUsername(String.valueOf(value));
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return normalizeUsername(fallback);
    }

    private String sanitizeUsernameSeed(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String cleaned = normalized
            .replace("@", "")
            .replaceAll("[^A-Za-z0-9._]+", "");
        return trimToNull(cleaned);
    }

    private String prepareUsernameBase(String value) {
        String cleaned = sanitizeUsernameSeed(value);
        if (cleaned == null) {
            return null;
        }
        String candidate = cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
        if (candidate.length() >= 6 && USERNAME_PATTERN.matcher(candidate).matches()) {
            return candidate;
        }
        String padded = (candidate + "playerxx");
        String resolved = padded.substring(0, Math.min(20, Math.max(6, padded.length())));
        while (resolved.length() < 6) {
            resolved += "x";
        }
        if (resolved.length() > 20) {
            resolved = resolved.substring(0, 20);
        }
        return USERNAME_PATTERN.matcher(resolved).matches() ? resolved : "playerx";
    }

    private boolean isUsernameAvailable(String username, String excludeUserId) {
        if (!isValidUsername(username)) {
            return false;
        }
        UserAccount existing = userAccountRepository.findByUsernameIgnoreCase(username).orElse(null);
        return existing == null || Objects.equals(trimToNull(excludeUserId), existing.getId());
    }

    private String generateUniqueUsername(String excludeUserId, String... candidates) {
        java.util.LinkedHashSet<String> seeds = new java.util.LinkedHashSet<>();
        if (candidates != null) {
            for (String candidate : candidates) {
                String prepared = prepareUsernameBase(candidate);
                if (prepared != null) {
                    seeds.add(prepared);
                }
            }
        }
        seeds.add("playerxx");

        for (String seed : seeds) {
            if (isUsernameAvailable(seed, excludeUserId)) {
                return seed;
            }
            for (int i = 2; i <= 9999; i++) {
                String suffix = String.valueOf(i);
                int baseLength = Math.max(1, 20 - suffix.length());
                String base = seed.length() > baseLength ? seed.substring(0, baseLength) : seed;
                String candidate = base + suffix;
                if (isUsernameAvailable(candidate, excludeUserId)) {
                    return candidate;
                }
            }
        }

        for (int i = 0; i < 128; i++) {
            String candidate = "player" + (1000 + random.nextInt(9000));
            if (isUsernameAvailable(candidate, excludeUserId)) {
                return candidate;
            }
        }
        return "player" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String resolveDisplayName(String requestedDisplayName, String username, String email) {
        String normalizedDisplayName = trimToNull(requestedDisplayName);
        if (normalizedDisplayName != null) {
            return normalizedDisplayName;
        }
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername != null) {
            return normalizedUsername;
        }
        return defaultDisplayNameFromEmail(email);
    }

    private String normalizeCountry(String country) {
        String normalized = trimToNull(country);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String normalizeGender(String gender) {
        String normalized = trimToNull(gender);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized
            .toLowerCase()
            .replace('-', '_')
            .replace(' ', '_');
        if ("nam".equals(lowered)) {
            lowered = "male";
        } else if ("nu".equals(lowered)) {
            lowered = "female";
        } else if ("khac".equals(lowered)) {
            lowered = "other";
        } else if ("prefernot".equals(lowered) || "khongnoi".equals(lowered)) {
            lowered = "prefer_not_to_say";
        }
        return ALLOWED_GENDERS.contains(lowered) ? lowered : null;
    }

    private LocalDate parseBirthDate(String birthDate) {
        String normalized = trimToNull(birthDate);
        if (normalized == null) {
            return null;
        }
        try {
            LocalDate parsed = LocalDate.parse(normalized);
            LocalDate now = LocalDate.now();
            if (parsed.isAfter(now)) {
                return null;
            }
            int years = Period.between(parsed, now).getYears();
            if (years < 6 || years > 120) {
                return null;
            }
            return parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isProfileCompleted(UserAccount user) {
        return user != null
            && isValidUsername(user.getUsername())
            && normalizeCountry(user.getCountry()) != null
            && normalizeGender(user.getGender()) != null
            && user.getBirthDate() != null;
    }

    private String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private boolean isDefaultAdminEmail(String email) {
        String normalized = normalizeEmail(email);
        return normalized != null && normalized.equals(defaultAdminEmail);
    }

    private void applyPostLoginState(UserAccount user) {
        boolean updated = false;
        if (isDefaultAdminEmail(user.getEmail()) && !"Admin".equalsIgnoreCase(user.getRole())) {
            user.setRole("Admin");
            updated = true;
        }
        if (!isValidUsername(user.getUsername())) {
            user.setUsername(generateUniqueUsername(user.getId(), user.getDisplayName(), user.getEmail()));
            updated = true;
        }
        if (trimToNull(user.getDisplayName()) == null) {
            user.setDisplayName(resolveDisplayName(null, user.getUsername(), user.getEmail()));
            updated = true;
        }

        if (!user.isEmailConfirmed()) {
            user.setEmailConfirmed(true);
            updated = true;
        }

        if (!user.isOnline()) {
            user.setOnline(true);
            updated = true;
        }
        boolean onboardingCompleted = isProfileCompleted(user);
        if (user.isOnboardingCompleted() != onboardingCompleted) {
            user.setOnboardingCompleted(onboardingCompleted);
            updated = true;
        }

        if (updated || user.getId() == null || user.getId().isBlank()) {
            userAccountRepository.save(user);
        }
    }

    private Map<String, Object> toLoginPayload(UserAccount user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId());
        payload.put("username", normalizeUsername(user.getUsername()) == null ? "" : normalizeUsername(user.getUsername()));
        payload.put("email", user.getEmail() == null ? "" : user.getEmail());
        payload.put("displayName", resolveDisplayName(user.getDisplayName(), user.getUsername(), user.getEmail()));
        payload.put("role", user.getRole() == null ? "User" : user.getRole());
        payload.put("avatarPath", user.getAvatarPath() == null ? DEFAULT_AVATAR_PATH : user.getAvatarPath());
        payload.put("country", user.getCountry() == null ? "" : user.getCountry());
        payload.put("gender", user.getGender() == null ? "" : user.getGender());
        payload.put("birthDate", user.getBirthDate() == null ? "" : user.getBirthDate().toString());
        payload.put("onboardingCompleted", user.isOnboardingCompleted());
        return payload;
    }

    private Map<String, Object> buildProfilePayload(UserAccount user, String message) {
        Map<String, Object> payload = new HashMap<>(toLoginPayload(user));
        if (message != null) {
            payload.put("message", message);
        }
        return payload;
    }

    private Map<String, Object> buildSocialLinksPayload(UserAccount user, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("google", Map.of("linked", trimToNull(user.getOauthGoogleId()) != null));
        payload.put("facebook", Map.of("linked", trimToNull(user.getOauthFacebookId()) != null));
        if (message != null) {
            payload.put("message", message);
        }
        return payload;
    }

    private ServiceResult ensureSocialProviderLinked(UserAccount user,
                                                     String provider,
                                                     String providerUserId,
                                                     boolean allowReplace) {
        if (user == null) {
            return ServiceResult.error("User not found");
        }
        if (!isSupportedSocialProvider(provider)) {
            return ServiceResult.error("Unsupported social provider");
        }

        String normalizedProviderUserId = trimToNull(providerUserId);
        if (normalizedProviderUserId == null) {
            return ServiceResult.error("Cannot read social account id");
        }

        UserAccount owner = findBySocialProviderId(provider, normalizedProviderUserId);
        if (owner != null && owner.getId() != null && !owner.getId().equals(user.getId())) {
            return ServiceResult.error(providerDisplayName(provider) + " account is linked to another user");
        }

        String currentLinkedId = trimToNull(readSocialProviderId(user, provider));
        if (currentLinkedId != null) {
            if (currentLinkedId.equals(normalizedProviderUserId)) {
                return ServiceResult.ok(Boolean.FALSE);
            }
            if (!allowReplace) {
                return ServiceResult.error("This account is linked to another " + providerDisplayName(provider) + " id");
            }
        }

        boolean changed = writeSocialProviderId(user, provider, normalizedProviderUserId);
        return ServiceResult.ok(changed);
    }

    private UserAccount findBySocialProviderId(String provider, String providerUserId) {
        String normalizedProvider = normalizeSocialProvider(provider);
        String normalizedProviderUserId = trimToNull(providerUserId);
        if (!isSupportedSocialProvider(normalizedProvider) || normalizedProviderUserId == null) {
            return null;
        }
        if (SOCIAL_PROVIDER_GOOGLE.equals(normalizedProvider)) {
            return userAccountRepository.findByOauthGoogleId(normalizedProviderUserId).orElse(null);
        }
        if (SOCIAL_PROVIDER_FACEBOOK.equals(normalizedProvider)) {
            return userAccountRepository.findByOauthFacebookId(normalizedProviderUserId).orElse(null);
        }
        return null;
    }

    private String readSocialProviderId(UserAccount user, String provider) {
        if (user == null) {
            return null;
        }
        String normalizedProvider = normalizeSocialProvider(provider);
        if (SOCIAL_PROVIDER_GOOGLE.equals(normalizedProvider)) {
            return user.getOauthGoogleId();
        }
        if (SOCIAL_PROVIDER_FACEBOOK.equals(normalizedProvider)) {
            return user.getOauthFacebookId();
        }
        return null;
    }

    private boolean writeSocialProviderId(UserAccount user, String provider, String providerUserId) {
        if (user == null) {
            return false;
        }
        String normalizedProvider = normalizeSocialProvider(provider);
        String normalizedProviderUserId = trimToNull(providerUserId);
        if (!isSupportedSocialProvider(normalizedProvider) || normalizedProviderUserId == null) {
            return false;
        }

        if (SOCIAL_PROVIDER_GOOGLE.equals(normalizedProvider)) {
            String current = trimToNull(user.getOauthGoogleId());
            if (normalizedProviderUserId.equals(current)) {
                return false;
            }
            user.setOauthGoogleId(normalizedProviderUserId);
            return true;
        }
        if (SOCIAL_PROVIDER_FACEBOOK.equals(normalizedProvider)) {
            String current = trimToNull(user.getOauthFacebookId());
            if (normalizedProviderUserId.equals(current)) {
                return false;
            }
            user.setOauthFacebookId(normalizedProviderUserId);
            return true;
        }
        return false;
    }

    private boolean clearSocialProviderLink(UserAccount user, String provider) {
        if (user == null) {
            return false;
        }
        String normalizedProvider = normalizeSocialProvider(provider);
        if (SOCIAL_PROVIDER_GOOGLE.equals(normalizedProvider)) {
            if (trimToNull(user.getOauthGoogleId()) == null) {
                return false;
            }
            user.setOauthGoogleId(null);
            return true;
        }
        if (SOCIAL_PROVIDER_FACEBOOK.equals(normalizedProvider)) {
            if (trimToNull(user.getOauthFacebookId()) == null) {
                return false;
            }
            user.setOauthFacebookId(null);
            return true;
        }
        return false;
    }

    private boolean isSupportedSocialProvider(String provider) {
        String normalizedProvider = normalizeSocialProvider(provider);
        return SOCIAL_PROVIDER_GOOGLE.equals(normalizedProvider) || SOCIAL_PROVIDER_FACEBOOK.equals(normalizedProvider);
    }

    private String normalizeSocialProvider(String provider) {
        String normalized = trimToNull(provider);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase();
        if (SOCIAL_PROVIDER_GOOGLE.equals(lowered)) {
            return SOCIAL_PROVIDER_GOOGLE;
        }
        if (SOCIAL_PROVIDER_FACEBOOK.equals(lowered) || "fb".equals(lowered)) {
            return SOCIAL_PROVIDER_FACEBOOK;
        }
        return lowered;
    }

    private String providerDisplayName(String provider) {
        String normalizedProvider = normalizeSocialProvider(provider);
        if (SOCIAL_PROVIDER_GOOGLE.equals(normalizedProvider)) {
            return "Google";
        }
        if (SOCIAL_PROVIDER_FACEBOOK.equals(normalizedProvider)) {
            return "Facebook";
        }
        return normalizedProvider == null ? "Social" : normalizedProvider;
    }

    private String normalizeAvatarContentType(String contentType) {
        String normalized = trimToNull(contentType);
        return normalized == null ? "application/octet-stream" : normalized;
    }

    private String syntheticOauthEmail(String provider, String providerUserId) {
        String providerPart = provider == null ? "social" : provider.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String idPart = providerUserId.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return providerPart + "-" + idPart + "@oauth.local";
    }

    private String defaultDisplayNameFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Player";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "Player";
        }
        String prefix = email.substring(0, at).trim();
        return prefix.isEmpty() ? "Player" : prefix;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeThemeMode(String mode) {
        String normalized = trimToNull(mode);
        if (normalized == null) {
            return "system";
        }
        String lowered = normalized.toLowerCase();
        return ALLOWED_THEME_MODES.contains(lowered) ? lowered : "system";
    }

    private String normalizeLanguage(String language) {
        String normalized = trimToNull(language);
        if (normalized == null) {
            return "vi";
        }
        String lowered = normalized.toLowerCase();
        return ALLOWED_LANGUAGES.contains(lowered) ? lowered : "vi";
    }

    private boolean valueOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private int normalizeFriendRefreshMs(Integer requested, int fallback) {
        int value = requested == null ? fallback : requested;
        if (ALLOWED_FRIEND_REFRESH_MS.contains(value)) {
            return value;
        }
        return 5000;
    }

    private Map<String, Object> toPreferencesPayload(UserAccount user) {
        String themeMode = normalizeThemeMode(user.getThemeMode());
        String language = normalizeLanguage(user.getLanguage());
        int refreshMs = normalizeFriendRefreshMs(user.getFriendListRefreshMs(), 5000);
        return Map.of(
            "themeMode", themeMode,
            "language", language,
            "sidebarDesktopVisibleByDefault", user.isSidebarDesktopVisibleByDefault(),
            "sidebarMobileAutoClose", user.isSidebarMobileAutoClose(),
            "homeMusicEnabled", user.isHomeMusicEnabled(),
            "toastNotificationsEnabled", user.isToastNotificationsEnabled(),
            "showOfflineFriendsInSidebar", user.isShowOfflineFriendsInSidebar(),
            "autoRefreshFriendList", user.isAutoRefreshFriendList(),
            "friendListRefreshMs", refreshMs
        );
    }

    private ServiceResult issueVerificationCode(String normalizedEmail, boolean keepPendingOnSendFailure) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusMinutes(VERIFICATION_CODE_TTL_MINUTES);
        String code = String.valueOf(100000 + random.nextInt(900000));

        EmailVerificationToken token = new EmailVerificationToken();
        token.setEmail(normalizedEmail);
        token.setToken(code);
        token.setCreatedAt(now);
        token.setExpireAt(expireAt);
        emailVerificationTokenRepository.save(token);

        try {
            emailService.sendEmail(normalizedEmail, "Caro Verify Email", "Your verification code is: " + code);
        } catch (RuntimeException ex) {
            emailVerificationTokenRepository.delete(token);
            if (!keepPendingOnSendFailure) {
                PendingRegisterStore.remove(normalizedEmail);
            }
            return ServiceResult.error("Cannot send verification email right now. Please try again.");
        }

        RegisterRequest pending = PendingRegisterStore.get(normalizedEmail);
        if (pending != null) {
            PendingRegisterStore.put(normalizedEmail, pending, expireAt);
        }
        cleanupOldVerificationTokens(normalizedEmail, token.getId());
        return ServiceResult.ok(Map.of(
            "email", normalizedEmail,
            "message", "Verification code sent",
            "expireInSeconds", VERIFICATION_CODE_TTL_MINUTES * 60
        ));
    }

    private void cleanupOldVerificationTokens(String email, Long keepTokenId) {
        List<EmailVerificationToken> tokens = emailVerificationTokenRepository.findByEmail(email);
        List<EmailVerificationToken> toDelete = tokens.stream()
            .filter(t -> keepTokenId == null || t.getId() == null || !keepTokenId.equals(t.getId()))
            .toList();
        if (!toDelete.isEmpty()) {
            emailVerificationTokenRepository.deleteAll(toDelete);
        }
    }

    private ServiceResult validateResendCooldown(String normalizedEmail) {
        LocalDateTime now = LocalDateTime.now();
        Optional<LocalDateTime> latestCreatedAt = emailVerificationTokenRepository.findByEmail(normalizedEmail).stream()
            .map(EmailVerificationToken::getCreatedAt)
            .filter(java.util.Objects::nonNull)
            .max(LocalDateTime::compareTo);

        if (latestCreatedAt.isEmpty()) {
            return ServiceResult.ok(Map.of());
        }

        LocalDateTime nextAllowed = latestCreatedAt.get().plusSeconds(VERIFICATION_RESEND_COOLDOWN_SECONDS);
        if (nextAllowed.isAfter(now)) {
            long waitSeconds = java.time.Duration.between(now, nextAllowed).toSeconds();
            if (waitSeconds <= 0) {
                waitSeconds = 1;
            }
            return ServiceResult.error("Please wait " + waitSeconds + " seconds before requesting a new code");
        }
        return ServiceResult.ok(Map.of());
    }
}
