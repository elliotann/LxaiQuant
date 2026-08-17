package com.chain.ai.trade.member.controller;

import com.chain.ai.trade.member.config.JwtUtil;
import com.chain.ai.trade.member.config.TokenStore;
import com.chain.ai.trade.member.dto.LoginRequest;
import com.chain.ai.trade.member.dto.RefreshTokenRequest;
import com.chain.ai.trade.member.entity.User;
import com.chain.ai.trade.member.entity.UserRole;
import com.chain.ai.trade.member.entity.UserStatus;
import com.chain.ai.trade.member.service.EmailCodeService;
import com.chain.ai.trade.member.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final IUserService userService;
    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;
    private final Optional<EmailCodeService> emailCodeService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body) {
        String usernameOrEmail = body.getUsername();
        if (!StringUtils.hasText(usernameOrEmail)) {
            usernameOrEmail = body.getEmail();
        }
        if (!StringUtils.hasText(usernameOrEmail) || !StringUtils.hasText(body.getPassword())) {
            return failure("账号和密码不能为空");
        }

        User user = userService.login(usernameOrEmail, body.getPassword());
        if (user == null) {
            return failure("用户名/邮箱或密码错误");
        }

        boolean rememberMe = Boolean.TRUE.equals(body.getRememberMe());
        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getUsername(), user.getRole().name(), rememberMe);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());
        tokenStore.storeAccessToken(accessToken, user.getUserId());
        tokenStore.storeRefreshToken(refreshToken, user.getUserId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", toAuthUser(user));
        payload.put("token", accessToken);
        payload.put("refreshToken", refreshToken);
        payload.put("rememberMe", rememberMe);

        return success(payload);
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        try {
            String username = str(body.get("username"));
            String email = str(body.get("email"));
            String password = str(body.get("password"));
            String phone = str(body.get("phone"));
            String emailCode = str(body.get("emailCode"));
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                return failure("用户名和密码不能为空");
            }
            if (!StringUtils.hasText(email)) {
                return failure("邮箱不能为空");
            }
            if (!StringUtils.hasText(emailCode)) {
                return failure("邮箱验证码不能为空");
            }
            if (!emailCodeService.isPresent()) {
                return failure("邮件服务未配置");
            }
            if (!emailCodeService.get().verifyCode(email, emailCode)) {
                return failure("邮箱验证码错误或已过期");
            }
            User user = userService.register(username, email, phone, password);
            User latest = userService.getById(user.getUserId());

            String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getUsername(), user.getRole().name());
            String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());
            tokenStore.storeAccessToken(accessToken, user.getUserId());
            tokenStore.storeRefreshToken(refreshToken, user.getUserId());

            Map<String, Object> payload = new HashMap<>();
            payload.put("user", toAuthUser(latest != null ? latest : user));
            payload.put("token", accessToken);
            payload.put("refreshToken", refreshToken);
            return success(payload);
        } catch (IllegalArgumentException e) {
            return failure(e.getMessage());
        } catch (Exception e) {
            log.error("注册失败", e);
            return failure("注册失败: " + e.getMessage());
        }
    }

    @PostMapping("/send-email-code")
    public Map<String, Object> sendEmailCode(@RequestBody Map<String, Object> body) {
        String email = str(body.get("email"));
        if (!StringUtils.hasText(email)) {
            return failure("邮箱不能为空");
        }
        try {
            if (!emailCodeService.isPresent()) {
                return failure("邮件服务未配置");
            }
            emailCodeService.get().sendCode(email);
            return success("验证码已发送");
        } catch (Exception e) {
            log.error("发送验证码失败: {}", email, e);
            return failure("验证码发送失败，请稍后重试");
        }
    }

    @PostMapping("/login-by-email")
    public Map<String, Object> loginByEmail(@RequestBody Map<String, Object> body) {
        String email = str(body.get("email"));
        String emailCode = str(body.get("emailCode"));
        if (!StringUtils.hasText(email) || !StringUtils.hasText(emailCode)) {
            return failure("邮箱和验证码不能为空");
        }
        if (!emailCodeService.isPresent()) {
            return failure("邮件服务未配置");
        }
        if (!emailCodeService.get().verifyCode(email, emailCode)) {
            return failure("验证码错误或已过期");
        }
        User user = userService.getByEmail(email);
        if (user == null) {
            return failure("该邮箱未注册");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId());
        tokenStore.storeAccessToken(accessToken, user.getUserId());
        tokenStore.storeRefreshToken(refreshToken, user.getUserId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("user", toAuthUser(user));
        payload.put("token", accessToken);
        payload.put("refreshToken", refreshToken);

        return success(payload);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader("Authorization") String authorization) {
        String token = extractBearerToken(authorization);
        if (token == null) {
            return failure("未登录");
        }
        if (!jwtUtil.validateToken(token) || !tokenStore.isValidAccessToken(token)) {
            return failure("登录状态已失效");
        }
        String userId = jwtUtil.getUserIdFromToken(token);
        User user = userService.getById(userId);
        if (user == null) {
            return failure("用户不存在");
        }
        return success(toAuthUser(user));
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestBody RefreshTokenRequest body) {
        String refreshToken = body.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) {
            return failure("refreshToken不能为空");
        }
        if (!jwtUtil.validateToken(refreshToken) || !tokenStore.isValidRefreshToken(refreshToken)) {
            return failure("refreshToken无效");
        }
        String userId = jwtUtil.getUserIdFromToken(refreshToken);

        boolean rememberMe = false;
        if (StringUtils.hasText(body.getAccessToken()) && jwtUtil.validateToken(body.getAccessToken())) {
            try {
                rememberMe = Boolean.TRUE.equals(jwtUtil.parseToken(body.getAccessToken()).get("rememberMe", Boolean.class));
            } catch (Exception e) {
                log.debug("解析旧accessToken中的rememberMe失败", e);
            }
        }

        tokenStore.removeRefreshToken(refreshToken);

        String newAccessToken = jwtUtil.generateAccessToken(userId, "", "", rememberMe);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);
        tokenStore.storeAccessToken(newAccessToken, userId);
        tokenStore.storeRefreshToken(newRefreshToken, userId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", newAccessToken);
        payload.put("refreshToken", newRefreshToken);
        payload.put("rememberMe", rememberMe);
        return success(payload);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader("Authorization") String authorization) {
        String token = extractBearerToken(authorization);
        if (token != null) {
            tokenStore.blacklistAccessToken(token);
        }
        return success("退出成功");
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> body) {
        String token = extractBearerToken(authorization);
        if (token == null || !tokenStore.isValidAccessToken(token)) {
            return failure("未登录");
        }
        String userId = jwtUtil.getUserIdFromToken(token);
        String currentPassword = str(body.get("currentPassword"));
        String newPassword = str(body.get("newPassword"));
        boolean ok = userService.changePassword(userId, currentPassword, newPassword);
        return ok ? success("修改成功") : failure("密码修改失败");
    }

    private static String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Map<String, Object> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "ok");
        result.put("data", data);
        return result;
    }

    private static Map<String, Object> failure(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }

    private static Map<String, Object> toAuthUser(User user) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", user.getUserId());
        dto.put("username", user.getUsername());
        dto.put("email", user.getEmail());
        String role = user.getRole() == UserRole.ADMIN ? "admin" : "user";
        dto.put("role", role);
        dto.put("isActive", user.getStatus() == UserStatus.ACTIVE);
        dto.put("createdAt", toIso(user.getCreateTime()));
        dto.put("updatedAt", toIso(user.getUpdateTime()));
        dto.put("lastLoginAt", toIso(user.getLastLoginTime()));
        return dto;
    }

    private static String toIso(LocalDateTime t) {
        return t == null ? null : t.toString();
    }
}
