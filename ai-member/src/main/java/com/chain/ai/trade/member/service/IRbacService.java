package com.chain.ai.trade.member.service;

import com.chain.ai.trade.member.entity.Permission;
import com.chain.ai.trade.member.entity.Role;

import java.util.List;

public interface IRbacService {

    List<Role> getRolesByUserId(String userId);

    List<Permission> getPermissionsByUserId(String userId);

    List<Permission> getPermissionsByRoleId(Integer roleId);

    List<String> getPermissionCodesByUserId(String userId);

    void assignRoleToUser(String userId, Integer roleId);

    void removeRoleFromUser(String userId, Integer roleId);

    void assignPermissionToRole(Integer roleId, Integer permissionId);

    void removePermissionFromRole(Integer roleId, Integer permissionId);

    List<Role> listAllRoles();

    List<Permission> listAllPermissions();

    void createRole(Role role);

    void updateRole(Role role);

    void deleteRole(Integer roleId);

    void assignPermissionsToRole(Integer roleId, List<Integer> permissionIds);

    void assignUserRoles(String userId, List<Integer> roleIds);
}
