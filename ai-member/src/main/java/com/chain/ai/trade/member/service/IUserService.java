package com.chain.ai.trade.member.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.member.entity.User;
import com.chain.ai.trade.member.entity.UserRole;
import com.chain.ai.trade.member.entity.UserStatus;

/**
 * 用户服务接口
 */
public interface IUserService extends IService<User> {

    User getByUsername(String username);

    User getByEmail(String email);

    User getByPhone(String phone);

    User register(String username, String email, String phone, String password);

    User login(String usernameOrEmail, String password);

    boolean updateUserRole(String userId, UserRole role);

    boolean updateUserStatus(String userId, UserStatus status);

    boolean updateProfile(String userId, String email, String phone);

    boolean changePassword(String userId, String oldPassword, String newPassword);

    boolean resetPassword(String userId, String newPassword);

    boolean isUsernameExists(String username);

    boolean isEmailExists(String email);

    boolean isPhoneExists(String phone);

    boolean activateUser(String userId);

    boolean deactivateUser(String userId);

    boolean suspendUser(String userId);
}