package com.chain.ai.trade.member.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.member.entity.Role;
import com.chain.ai.trade.member.entity.RolePermission;
import com.chain.ai.trade.member.entity.User;
import com.chain.ai.trade.member.entity.UserRole;
import com.chain.ai.trade.member.entity.UserRoleRel;
import com.chain.ai.trade.member.mapper.PermissionMapper;
import com.chain.ai.trade.member.mapper.RoleMapper;
import com.chain.ai.trade.member.mapper.RolePermissionMapper;
import com.chain.ai.trade.member.mapper.UserMapper;
import com.chain.ai.trade.member.mapper.UserRoleRelMapper;
import com.chain.ai.trade.common.utils.RedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RbacDataInitializer implements CommandLineRunner {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final UserMapper userMapper;
    private final RedisCache redisCache;

    @Override
    public void run(String... args) {
        Role adminRole = roleMapper.selectByCode("ADMIN");
        if (adminRole == null) {
            log.warn("ADMIN role not found, skipping RBAC initialization");
            return;
        }

        var allPerms = permissionMapper.selectList(null);
        if (allPerms.isEmpty()) {
            log.warn("No permissions found, skipping permission assignment");
        } else {
            List<Integer> existingPermIds = rolePermissionMapper.selectList(
                    new LambdaQueryWrapper<RolePermission>()
                            .eq(RolePermission::getRoleId, adminRole.getId()))
                    .stream()
                    .map(RolePermission::getPermissionId)
                    .toList();
            for (var perm : allPerms) {
                if (!existingPermIds.contains(perm.getId())) {
                    RolePermission rp = new RolePermission();
                    rp.setRoleId(adminRole.getId());
                    rp.setPermissionId(perm.getId());
                    rolePermissionMapper.insert(rp);
                    log.info("Assigned permission {} to ADMIN role", perm.getPermCode());
                }
            }
        }

        long userRoleCount = userRoleRelMapper.selectCount(
                new LambdaQueryWrapper<UserRoleRel>()
                        .eq(UserRoleRel::getRoleId, adminRole.getId()));
        if (userRoleCount > 0) {
            log.info("Admin users already linked to ADMIN role, skipping");
        } else {
            List<User> adminUsers = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getRole, UserRole.ADMIN));
            if (adminUsers.isEmpty()) {
                log.warn("No ADMIN users found, skipping user-role assignment");
            } else {
                for (var user : adminUsers) {
                    UserRoleRel rel = new UserRoleRel();
                    rel.setUserId(user.getUserId());
                    rel.setRoleId(adminRole.getId());
                    userRoleRelMapper.insert(rel);
                    log.info("Linked user {} to ADMIN role", user.getUserId());
                }
            }
        }

        List<User> allAdminUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, UserRole.ADMIN));
        for (var u : allAdminUsers) {
            redisCache.remove("permissions:" + u.getUserId());
        }
    }
}
