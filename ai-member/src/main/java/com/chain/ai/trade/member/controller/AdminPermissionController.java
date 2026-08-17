package com.chain.ai.trade.member.controller;

import cn.hutool.core.lang.Assert;
import com.chain.ai.trade.member.entity.Permission;
import com.chain.ai.trade.member.entity.Role;
import com.chain.ai.trade.member.service.IRbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final IRbacService rbacService;

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('permission:manage')")
    public List<Permission> getAllPermissions() {
        return rbacService.listAllPermissions();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('permission:manage')")
    public List<Role> getAllRoles() {
        return rbacService.listAllRoles();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('permission:manage')")
    public void createRole(@RequestBody Role role) {
        Assert.notBlank(role.getRoleCode(), "角色编码不能为空");
        Assert.notBlank(role.getRoleName(), "角色名称不能为空");
        rbacService.createRole(role);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('permission:manage')")
    public void updateRole(@PathVariable Integer id, @RequestBody Role role) {
        role.setId(id);
        rbacService.updateRole(role);
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('permission:manage')")
    public void deleteRole(@PathVariable Integer id) {
        rbacService.deleteRole(id);
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('permission:manage')")
    public List<Permission> getRolePermissions(@PathVariable Integer roleId) {
        return rbacService.getPermissionsByRoleId(roleId);
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('permission:manage')")
    public void assignPermissionsToRole(@PathVariable Integer roleId, @RequestBody List<Integer> permissionIds) {
        rbacService.assignPermissionsToRole(roleId, permissionIds);
    }

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('user:assign-role')")
    public List<Role> getUserRoles(@PathVariable String userId) {
        return rbacService.getRolesByUserId(userId);
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('user:assign-role')")
    public void assignUserRoles(@PathVariable String userId, @RequestBody List<Integer> roleIds) {
        rbacService.assignUserRoles(userId, roleIds);
    }
}
