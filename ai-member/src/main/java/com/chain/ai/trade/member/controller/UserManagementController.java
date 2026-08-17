package com.chain.ai.trade.member.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.member.controller.dto.UserManagementDTO;
import com.chain.ai.trade.member.entity.User;
import com.chain.ai.trade.member.entity.UserRole;
import com.chain.ai.trade.member.entity.UserStatus;
import com.chain.ai.trade.member.entity.CreditsLog;
import com.chain.ai.trade.member.service.ICreditsService;
import com.chain.ai.trade.member.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserManagementController {

    private final IUserService userService;
    private final ICreditsService creditsService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public Map<String, Object> getUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(username)) {
                wrapper.like(User::getUsername, username.trim());
            }
            if (StringUtils.hasText(email)) {
                wrapper.like(User::getEmail, email.trim());
            }
            UserRole roleEnum = parseRole(role);
            if (roleEnum != null) {
                wrapper.eq(User::getRole, roleEnum);
            }

            Page<User> pageParam = new Page<>(Math.max(1, page), Math.max(1, limit));
            pageParam.addOrder(com.baomidou.mybatisplus.core.metadata.OrderItem.desc("create_time"));
            IPage<User> userPage = userService.page(pageParam, wrapper);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("users", userPage.getRecords().stream().map(this::convertToDTO).collect(Collectors.toList()));
            result.put("total", userPage.getTotal());
            result.put("page", userPage.getCurrent());
            result.put("limit", userPage.getSize());
            result.put("pages", userPage.getPages());
            return result;
        } catch (Exception e) {
            log.error("分页查询用户失败", e);
            return failure("查询用户失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前登录用户个人信息（含积分、会员等账单信息）
     */
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getProfile(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        User user = userService.getById(userId);
        if (user == null) {
            return failure("用户不存在");
        }
        Map<String, Object> billing = new HashMap<>();
        billing.put("credits", user.getCreditsBalance() != null ? user.getCreditsBalance() : 0);
        billing.put("is_vip", user.getMembershipLevel() != null
                && !"BASIC".equalsIgnoreCase(user.getMembershipLevel())
                && user.getMembershipExpireTime() != null
                && user.getMembershipExpireTime().isAfter(java.time.LocalDateTime.now()));
        billing.put("vip_expires_at", user.getMembershipExpireTime());
        billing.put("membership_level", user.getMembershipLevel());

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getUserId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("isActive", user.isActive());
        data.put("isAdmin", user.isAdmin());
        data.put("isPremium", user.isPremium());
        data.put("role", user.getRole() != null ? user.getRole().name().toLowerCase() : null);
        data.put("status", user.getStatus() != null ? user.getStatus().name().toLowerCase() : null);
        data.put("billing", billing);
        data.put("createdAt", user.getCreateTime());
        data.put("updatedAt", user.getUpdateTime());
        data.put("lastLoginAt", user.getLastLoginTime());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);
        return result;
    }

    /**
     * 获取当前用户的推广邀请记录
     */
    @GetMapping("/my-referrals")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getMyReferrals(
            Authentication auth,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("total", 0);
        result.put("referral_code", "");
        result.put("referral_bonus", 0);
        result.put("register_bonus", 0);
        result.put("list", Collections.emptyList());
        return result;
    }

    /**
     * 获取当前用户的积分变动记录
     */
    @GetMapping("/my-credits-log")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getMyCreditsLog(
            Authentication auth,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        String userId = (String) auth.getPrincipal();
        List<CreditsLog> logs = creditsService.getCreditsLogs(userId, page, pageSize);
        List<Map<String, Object>> items = logs.stream().map(log -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("amount", log.getAmount());
            item.put("balance_after", log.getBalanceAfter());
            item.put("type", log.getType());
            item.put("description", log.getDescription());
            item.put("created_at", log.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", Map.of(
                "items", items,
                "list", items,
                "total", items.size(),
                "page", page,
                "page_size", pageSize));
        return result;
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:read')")
    public Map<String, Object> getUserById(@PathVariable String userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return failure("用户不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("user", convertToDTO(user));
        return result;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> data) {
        try {
            String username = str(data.get("username"));
            String email = normalizeEmail(str(data.get("email")));
            String phone = str(data.get("phone"));
            String password = str(data.get("password"));
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                return failure("用户名和密码不能为空");
            }
            User created = userService.register(username, email, phone, password);

            UserRole role = parseRole(str(data.get("role")));
            if (role != null && role != UserRole.BASIC) {
                userService.updateUserRole(created.getUserId(), role);
            }

            UserStatus status = parseStatus(str(data.get("status")));
            if (status != null && status != UserStatus.ACTIVE) {
                userService.updateUserStatus(created.getUserId(), status);
            }

            User latest = userService.getById(created.getUserId());
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "创建成功");
            result.put("user", convertToDTO(latest != null ? latest : created));
            return result;
        } catch (IllegalArgumentException e) {
            return failure(e.getMessage());
        } catch (Exception e) {
            log.error("创建用户失败", e);
            return failure("创建用户失败: " + e.getMessage());
        }
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:update')")
    public Map<String, Object> updateUser(@PathVariable String userId, @RequestBody Map<String, Object> data) {
        try {
            User user = userService.getById(userId);
            if (user == null) {
                return failure("用户不存在");
            }

            String newUsername = str(data.get("username"));
            if (StringUtils.hasText(newUsername) && !newUsername.equals(user.getUsername())) {
                User exists = userService.getByUsername(newUsername);
                if (exists != null && !exists.getUserId().equals(userId)) {
                    return failure("用户名已存在: " + newUsername);
                }
                user.setUsername(newUsername.trim());
            }

            String newEmail = normalizeEmail(str(data.get("email")));
            if (StringUtils.hasText(newEmail) && !newEmail.equalsIgnoreCase(String.valueOf(user.getEmail()))) {
                User exists = userService.getByEmail(newEmail);
                if (exists != null && !exists.getUserId().equals(userId)) {
                    return failure("邮箱已存在: " + newEmail);
                }
                user.setEmail(newEmail);
            }

            String newPhone = str(data.get("phone"));
            if (StringUtils.hasText(newPhone) && !newPhone.equals(String.valueOf(user.getPhone()))) {
                User exists = userService.getByPhone(newPhone);
                if (exists != null && !exists.getUserId().equals(userId)) {
                    return failure("手机号已存在: " + newPhone);
                }
                user.setPhone(newPhone.trim());
            }

            UserRole role = parseRole(str(data.get("role")));
            if (role != null) {
                user.setRole(role);
            }
            UserStatus status = parseStatus(str(data.get("status")));
            if (status != null) {
                user.setStatus(status);
            }

            user.setUpdateTime(LocalDateTime.now());
            boolean ok = userService.updateById(user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", ok);
            result.put("message", ok ? "更新成功" : "更新失败");
            if (ok) {
                result.put("user", convertToDTO(userService.getById(userId)));
            }
            return result;
        } catch (Exception e) {
            log.error("更新用户失败: userId={}", userId, e);
            return failure("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('user:delete')")
    public Map<String, Object> deleteUser(@PathVariable String userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return failure("用户不存在");
        }
        boolean ok = userService.removeById(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "删除成功" : "删除失败");
        return result;
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('user:update')")
    public Map<String, Object> toggleUserStatus(@PathVariable String userId, @RequestBody Map<String, Object> data) {
        Object val = data.get("isActive");
        if (!(val instanceof Boolean)) {
            return failure("缺少isActive参数");
        }
        boolean isActive = (Boolean) val;
        boolean ok = isActive ? userService.activateUser(userId) : userService.deactivateUser(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "状态更新成功" : "状态更新失败");
        return result;
    }

    @PutMapping("/{userId}/password")
    @PreAuthorize("hasAuthority('user:update')")
    public Map<String, Object> resetUserPassword(@PathVariable String userId, @RequestBody Map<String, Object> data) {
        String newPassword = str(data.get("newPassword"));
        if (!StringUtils.hasText(newPassword)) {
            return failure("新密码不能为空");
        }
        boolean ok = userService.resetPassword(userId, newPassword);
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "密码重置成功" : "密码重置失败");
        return result;
    }

    @GetMapping("/stats/overview")
    @PreAuthorize("hasAuthority('user:read')")
    public Map<String, Object> getUserStats() {
        try {
            long totalUsers = userService.count();
            long activeUsers = userService.count(new LambdaQueryWrapper<User>().eq(User::getStatus, UserStatus.ACTIVE));

            LocalDate today = LocalDate.now();
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L).atStartOfDay();
            LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

            long newUsersToday = userService.count(new LambdaQueryWrapper<User>().ge(User::getCreateTime, dayStart));
            long newUsersThisWeek = userService.count(new LambdaQueryWrapper<User>().ge(User::getCreateTime, weekStart));
            long newUsersThisMonth = userService.count(new LambdaQueryWrapper<User>().ge(User::getCreateTime, monthStart));

            List<Map<String, Object>> roleDistribution = Arrays.stream(UserRole.values()).map(role -> {
                long count = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, role));
                Map<String, Object> item = new HashMap<>();
                item.put("role", role.name().toLowerCase());
                item.put("count", count);
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("totalUsers", totalUsers);
            result.put("activeUsers", activeUsers);
            result.put("newUsersToday", newUsersToday);
            result.put("newUsersThisWeek", newUsersThisWeek);
            result.put("newUsersThisMonth", newUsersThisMonth);
            result.put("userGrowth", Collections.emptyList());
            result.put("roleDistribution", roleDistribution);
            return result;
        } catch (Exception e) {
            log.error("获取用户统计失败", e);
            return failure("获取用户统计失败: " + e.getMessage());
        }
    }

    private UserManagementDTO convertToDTO(User user) {
        UserManagementDTO dto = new UserManagementDTO();
        BeanUtils.copyProperties(user, dto);
        if (user.getRole() != null) {
            dto.setRole(user.getRole().name().toLowerCase());
        }
        if (user.getStatus() != null) {
            dto.setStatus(user.getStatus().name().toLowerCase());
        }
        return dto;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
    }

    private static UserRole parseRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String raw = role.trim().toUpperCase();
        if ("USER".equals(raw)) {
            return UserRole.BASIC;
        }
        try {
            return UserRole.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UserStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, Object> failure(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}

