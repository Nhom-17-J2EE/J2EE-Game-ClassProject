package com.game.hub.controller;

import com.game.hub.entity.Friendship;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.FriendshipRepository;
import com.game.hub.repository.UserAccountRepository;
import com.game.hub.service.DataExportAuditService;
import com.game.hub.support.UserExportSupport;
import com.game.hub.util.PasswordValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final UserAccountRepository userAccountRepository;
    private final FriendshipRepository friendshipRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataExportAuditService dataExportAuditService;

    public AdminController(UserAccountRepository userAccountRepository,
                           FriendshipRepository friendshipRepository,
                           PasswordEncoder passwordEncoder,
                           DataExportAuditService dataExportAuditService) {
        this.userAccountRepository = userAccountRepository;
        this.friendshipRepository = friendshipRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataExportAuditService = dataExportAuditService;
    }

    @GetMapping({"", "/"})
    public String adminCenterPage() {
        return "admin/index";
    }

    @GetMapping("/users")
    public String usersPage(@RequestParam(required = false) String searchTerm,
                            @RequestParam(required = false) String banFilter,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {
        return "redirect:/admin";
    }

    @ResponseBody
    @GetMapping("/api/users")
    public List<UserAccount> usersApi(@RequestParam(required = false) String searchTerm,
                                      @RequestParam(required = false) String banFilter) {
        return filterUsers(searchTerm, banFilter);
    }

    @ResponseBody
    @PostMapping("/users")
    public Object create(@RequestBody CreateUserRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            return Map.of("success", false, "error", "Email is required");
        }
        java.util.List<String> pwdErrors = PasswordValidator.validate(request.password());
        if (!pwdErrors.isEmpty()) {
            return Map.of("success", false, "error", String.join("; ", pwdErrors));
        }
        if (userAccountRepository.findByEmail(request.email()).isPresent()) {
            return Map.of("success", false, "error", "Email already exists");
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.email());
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setScore(request.score());
        user.setRole(request.role() == null || request.role().isBlank() ? "User" : request.role());
        user.setAvatarPath(request.avatarPath() == null || request.avatarPath().isBlank()
            ? "/uploads/avatars/default-avatar.jpg" : request.avatarPath());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmailConfirmed(true);
        userAccountRepository.save(user);

        return Map.of("success", true, "user", user);
    }

    @GetMapping("/users/{id}")
    public String userDetailPage(@PathVariable String id, Model model) {
        UserAccount user = userAccountRepository.findById(id).orElse(null);
        if (user == null) {
            return "redirect:/admin/users";
        }
        model.addAttribute("user", user);
        return "admin/user-detail";
    }

    @ResponseBody
    @GetMapping("/api/users/{id}")
    public Object details(@PathVariable String id) {
        UserAccount user = userAccountRepository.findById(id).orElse(null);
        if (user == null) {
            return Map.of("success", false, "error", "User not found");
        }
        return user;
    }

    @ResponseBody
    @PatchMapping("/users/{id}")
    public Object edit(@PathVariable String id, @RequestBody EditUserRequest request) {
        UserAccount user = userAccountRepository.findById(id).orElse(null);
        if (user == null) {
            return Map.of("success", false, "error", "User not found");
        }

        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.score() != null) user.setScore(request.score());
        if (request.role() != null && !request.role().isBlank()) user.setRole(request.role());
        if (request.avatarPath() != null && !request.avatarPath().isBlank()) user.setAvatarPath(request.avatarPath());

        userAccountRepository.save(user);
        return Map.of("success", true, "user", user);
    }

    @ResponseBody
    @DeleteMapping("/users/{id}")
    public Object delete(@PathVariable String id) {
        UserAccount user = userAccountRepository.findById(id).orElse(null);
        if (user == null) {
            return Map.of("success", false, "error", "User not found");
        }

        List<Friendship> friendships = friendshipRepository.findByRequesterIdOrAddresseeId(id, id);
        friendshipRepository.deleteAll(friendships);
        userAccountRepository.delete(user);

        return Map.of("success", true);
    }

    @ResponseBody
    @PostMapping("/users/{id}/ban")
    public Object ban(@PathVariable String id, @RequestBody BanRequest request) {
        UserAccount user = userAccountRepository.findById(id).orElse(null);
        if (user == null) {
            return Map.of("success", false, "error", "User not found");
        }

        if (request.durationMinutes() == -1) {
            user.setBannedUntil(LocalDateTime.of(9999, 12, 31, 23, 59));
        } else {
            user.setBannedUntil(LocalDateTime.now().plusMinutes(request.durationMinutes()));
        }

        userAccountRepository.save(user);
        return Map.of("success", true, "bannedUntil", user.getBannedUntil());
    }

    @ResponseBody
    @PostMapping("/users/{id}/unban")
    public Object unban(@PathVariable String id) {
        UserAccount user = userAccountRepository.findById(id).orElse(null);
        if (user == null) {
            return Map.of("success", false, "error", "User not found");
        }
        user.setBannedUntil(null);
        userAccountRepository.save(user);
        return Map.of("success", true);
    }

    @GetMapping("/export-users-csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String searchTerm,
                                            @RequestParam(required = false) String banFilter,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(defaultValue = "page") String scope,
                                            HttpServletRequest request) {
        List<UserAccount> users = resolveUsersForExport(searchTerm, banFilter, page, size, scope);
        byte[] body = UserExportSupport.toCsv(users);
        String filename = "users-" + exportSuffix(scope, page) + ".csv";
        dataExportAuditService.recordExport(request, "admin-users", "Tai khoan admin", "csv", filename, scope, users.size(), null);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(body);
    }

    @GetMapping("/export-users-excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String searchTerm,
                                              @RequestParam(required = false) String banFilter,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(defaultValue = "page") String scope,
                                              HttpServletRequest request) {
        List<UserAccount> users = resolveUsersForExport(searchTerm, banFilter, page, size, scope);
        byte[] body = UserExportSupport.toExcel(users);
        String filename = "users-" + exportSuffix(scope, page) + ".xlsx";
        dataExportAuditService.recordExport(request, "admin-users", "Tai khoan admin", "excel", filename, scope, users.size(), null);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }

    private List<UserAccount> filterUsers(String searchTerm, String banFilter) {
        List<UserAccount> users = new ArrayList<>(userAccountRepository.findAll());

        if (searchTerm != null && !searchTerm.isBlank()) {
            String lower = searchTerm.toLowerCase();
            users = users.stream().filter(u ->
                (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(lower))
                    || (u.getEmail() != null && u.getEmail().toLowerCase().contains(lower))
            ).toList();
        }

        if ("banned".equalsIgnoreCase(banFilter)) {
            users = users.stream().filter(UserAccount::isBanned).toList();
        } else if ("active".equalsIgnoreCase(banFilter)) {
            users = users.stream().filter(u -> !u.isBanned()).toList();
        }
        return users;
    }

    private <T> PageSlice<T> paginate(List<T> source, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int totalItems = source.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) safeSize));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * safeSize;
        int to = Math.min(from + safeSize, totalItems);
        List<T> items = from >= to ? List.of() : source.subList(from, to);
        return new PageSlice<>(items, safePage, safeSize, totalPages);
    }

    private record PageSlice<T>(List<T> items, int page, int size, int totalPages) {
    }

    private List<UserAccount> resolveUsersForExport(String searchTerm,
                                                    String banFilter,
                                                    int page,
                                                    int size,
                                                    String scope) {
        List<UserAccount> filtered = filterUsers(searchTerm, banFilter);
        if ("all".equalsIgnoreCase(scope)) {
            return filtered;
        }
        return paginate(filtered, page, size).items();
    }

    private String exportSuffix(String scope, int page) {
        if ("all".equalsIgnoreCase(scope)) {
            return "all";
        }
        int safePage = Math.max(page, 0);
        return "page-" + (safePage + 1);
    }

    public record CreateUserRequest(String email, String displayName, String password, int score, String role, String avatarPath) {
    }

    public record EditUserRequest(String displayName, Integer score, String role, String avatarPath) {
    }

    public record BanRequest(int durationMinutes) {
    }
}
