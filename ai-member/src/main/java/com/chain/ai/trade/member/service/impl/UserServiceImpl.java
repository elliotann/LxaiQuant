package com.chain.ai.trade.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chain.ai.trade.member.entity.User;
import com.chain.ai.trade.member.entity.UserRole;
import com.chain.ai.trade.member.entity.UserStatus;
import com.chain.ai.trade.member.mapper.UserMapper;
import com.chain.ai.trade.member.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User getByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username.trim());
        return userMapper.selectOne(wrapper);
    }

    @Override
    public User getByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email.trim().toLowerCase());
        return userMapper.selectOne(wrapper);
    }

    @Override
    public User getByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone.trim());
        return userMapper.selectOne(wrapper);
    }

    @Override
    @Transactional
    public User register(String username, String email, String phone, String password) {
        // 参数校验
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        // 检查用户名是否已存在
        if (isUsernameExists(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        // 检查邮箱是否已存在
        if (StringUtils.hasText(email) && isEmailExists(email)) {
            throw new IllegalArgumentException("邮箱已存在: " + email);
        }

        // 检查手机号是否已存在
        if (StringUtils.hasText(phone) && isPhoneExists(phone)) {
            throw new IllegalArgumentException("手机号已存在: " + phone);
        }

        // 创建用户
        User user = User.builder()
                .userId(generateUserId())
                .username(username.trim())
                .email(email != null ? email.trim().toLowerCase() : null)
                .phone(phone != null ? phone.trim() : null)
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.BASIC)
                .status(UserStatus.ACTIVE)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 保存到数据库
        userMapper.insert(user);

        log.info("用户注册成功: {}", username);
        return user;
    }

    @Override
    public User login(String usernameOrEmail, String password) {
        if (!StringUtils.hasText(usernameOrEmail) || !StringUtils.hasText(password)) {
            return null;
        }

        // 先按用户名查找，再按邮箱查找
        User user = getByUsername(usernameOrEmail);
        if (user == null) {
            user = getByEmail(usernameOrEmail);
        }

        if (user == null) {
            log.warn("用户不存在: {}", usernameOrEmail);
            return null;
        }

        // 检查密码
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("密码错误: {}", usernameOrEmail);
            return null;
        }

        // 检查用户状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("用户状态异常: {} - {}", usernameOrEmail, user.getStatus());
            return null;
        }

        // 更新最后登录时间
        user.recordLogin();
        userMapper.updateById(user);

        log.info("用户登录成功: {}", usernameOrEmail);
        return user;
    }

    @Override
    @Transactional
    public boolean updateUserRole(String userId, UserRole role) {
        if (!StringUtils.hasText(userId) || role == null) {
            return false;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        user.setRole(role);
        user.setUpdateTime(LocalDateTime.now());

        int result = userMapper.updateById(user);
        return result > 0;
    }

    @Override
    @Transactional
    public boolean updateUserStatus(String userId, UserStatus status) {
        if (!StringUtils.hasText(userId) || status == null) {
            return false;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());

        int result = userMapper.updateById(user);
        return result > 0;
    }

    @Override
    @Transactional
    public boolean updateProfile(String userId, String email, String phone) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        user.updateProfile(email, phone);
        int result = userMapper.updateById(user);
        return result > 0;
    }

    @Override
    @Transactional
    public boolean changePassword(String userId, String oldPassword, String newPassword) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            return false;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return false;
        }

        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());

        int result = userMapper.updateById(user);
        return result > 0;
    }

    @Override
    @Transactional
    public boolean resetPassword(String userId, String newPassword) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(newPassword)) {
            return false;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());

        int result = userMapper.updateById(user);
        return result > 0;
    }

    @Override
    public boolean isUsernameExists(String username) {
        return getByUsername(username) != null;
    }

    @Override
    public boolean isEmailExists(String email) {
        return getByEmail(email) != null;
    }

    @Override
    public boolean isPhoneExists(String phone) {
        return getByPhone(phone) != null;
    }

    @Override
    @Transactional
    public boolean activateUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        user.activate();
        int result = userMapper.updateById(user);
        return result > 0;
    }

    @Override
    @Transactional
    public boolean deactivateUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        user.deactivate();
        int result = userMapper.updateById(user);
        return result > 0;
    }

    @Override
    @Transactional
    public boolean suspendUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        user.suspend();
        int result = userMapper.updateById(user);
        return result > 0;
    }

    /**
     * 生成用户ID
     */
    private String generateUserId() {
        return "U" + UUID.randomUUID().toString().replace("-", "").substring(0, 15).toUpperCase();
    }
}
