package com.chain.ai.trade.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.member.entity.Permission;
import com.chain.ai.trade.member.entity.Role;
import com.chain.ai.trade.member.entity.RolePermission;
import com.chain.ai.trade.member.entity.UserRoleRel;
import com.chain.ai.trade.member.mapper.PermissionMapper;
import com.chain.ai.trade.member.mapper.RoleMapper;
import com.chain.ai.trade.member.mapper.RolePermissionMapper;
import com.chain.ai.trade.member.mapper.UserRoleRelMapper;
import com.chain.ai.trade.member.service.IRbacService;
import com.chain.ai.trade.common.utils.RedisCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements IRbacService {

    private static final String PERM_CACHE_PREFIX = "permissions:";

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RedisCache redisCache;

    @Override
    public List<Role> getRolesByUserId(String userId) {
        return roleMapper.selectByUserId(userId);
    }

    @Override
    public List<Permission> getPermissionsByUserId(String userId) {
        return permissionMapper.selectByUserId(userId);
    }

    @Override
    public List<Permission> getPermissionsByRoleId(Integer roleId) {
        return permissionMapper.selectByRoleId(roleId);
    }

    @Override
    public List<String> getPermissionCodesByUserId(String userId) {
        String cacheKey = PERM_CACHE_PREFIX + userId;
        Set<String> cached = (Set<String>) redisCache.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return List.copyOf(cached);
        }
        List<String> codes = getPermissionsByUserId(userId).stream()
                .map(Permission::getPermCode)
                .toList();
        redisCache.put(cacheKey, new HashSet<>(codes));
        return codes;
    }

    @Override
    @Transactional
    public void assignRoleToUser(String userId, Integer roleId) {
        long count = userRoleRelMapper.selectCount(
                new LambdaQueryWrapper<UserRoleRel>()
                        .eq(UserRoleRel::getUserId, userId)
                        .eq(UserRoleRel::getRoleId, roleId));
        if (count == 0) {
            UserRoleRel rel = new UserRoleRel();
            rel.setUserId(userId);
            rel.setRoleId(roleId);
            userRoleRelMapper.insert(rel);
        }
        evictUserPermCache(userId);
    }

    @Override
    @Transactional
    public void removeRoleFromUser(String userId, Integer roleId) {
        userRoleRelMapper.delete(new LambdaQueryWrapper<UserRoleRel>()
                .eq(UserRoleRel::getUserId, userId)
                .eq(UserRoleRel::getRoleId, roleId));
        evictUserPermCache(userId);
    }

    @Override
    @Transactional
    public void assignPermissionToRole(Integer roleId, Integer permissionId) {
        long count = rolePermissionMapper.selectCount(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId)
                        .eq(RolePermission::getPermissionId, permissionId));
        if (count == 0) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permissionId);
            rolePermissionMapper.insert(rp);
        }
        evictRolePermCache(roleId);
    }

    @Override
    @Transactional
    public void removePermissionFromRole(Integer roleId, Integer permissionId) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId)
                .eq(RolePermission::getPermissionId, permissionId));
        evictRolePermCache(roleId);
    }

    @Override
    public List<Role> listAllRoles() {
        return roleMapper.selectList(null);
    }

    @Override
    public List<Permission> listAllPermissions() {
        return permissionMapper.selectList(null);
    }

    @Override
    @Transactional
    public void createRole(Role role) {
        roleMapper.insert(role);
    }

    @Override
    @Transactional
    public void updateRole(Role role) {
        roleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void deleteRole(Integer roleId) {
        long userCount = userRoleRelMapper.selectCount(
                new LambdaQueryWrapper<UserRoleRel>().eq(UserRoleRel::getRoleId, roleId));
        if (userCount > 0) {
            throw new IllegalStateException("该角色下存在关联用户，无法删除");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        roleMapper.deleteById(roleId);
    }

    @Override
    @Transactional
    public void assignPermissionsToRole(Integer roleId, List<Integer> permissionIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Integer permId : permissionIds) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permId);
                rolePermissionMapper.insert(rp);
            }
        }
        evictRolePermCache(roleId);
    }

    @Override
    @Transactional
    public void assignUserRoles(String userId, List<Integer> roleIds) {
        userRoleRelMapper.delete(new LambdaQueryWrapper<UserRoleRel>()
                .eq(UserRoleRel::getUserId, userId));
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Integer roleId : roleIds) {
                UserRoleRel rel = new UserRoleRel();
                rel.setUserId(userId);
                rel.setRoleId(roleId);
                userRoleRelMapper.insert(rel);
            }
        }
        evictUserPermCache(userId);
    }

    private void evictUserPermCache(String userId) {
        redisCache.remove(PERM_CACHE_PREFIX + userId);
    }

    private void evictRolePermCache(Integer roleId) {
        List<UserRoleRel> rels = userRoleRelMapper.selectList(
                new LambdaQueryWrapper<UserRoleRel>().eq(UserRoleRel::getRoleId, roleId));
        for (UserRoleRel rel : rels) {
            redisCache.remove(PERM_CACHE_PREFIX + rel.getUserId());
        }
    }
}
