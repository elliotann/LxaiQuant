package com.chain.ai.trade.member.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.member.entity.Permission;
import com.chain.ai.trade.member.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class PermissionRegistry implements CommandLineRunner {

    private final PermissionMapper permissionMapper;

    private static final List<PermissionDef> ALL_PERMISSIONS = Arrays.asList(
            new PermissionDef("user:create", "创建用户", "用户管理"),
            new PermissionDef("user:read", "查看用户", "用户管理"),
            new PermissionDef("user:update", "编辑用户", "用户管理"),
            new PermissionDef("user:delete", "删除用户", "用户管理"),
            new PermissionDef("user:assign-role", "分配角色", "用户管理"),

            new PermissionDef("strategy:create", "创建策略", "策略管理"),
            new PermissionDef("strategy:read", "查看策略", "策略管理"),
            new PermissionDef("strategy:update", "编辑策略", "策略管理"),
            new PermissionDef("strategy:delete", "删除策略", "策略管理"),
            new PermissionDef("strategy:ai-generate", "AI生成策略", "策略管理"),

            new PermissionDef("backtest:run", "执行回测", "回测管理"),
            new PermissionDef("backtest:advanced", "高级回测", "回测管理"),

            new PermissionDef("trade:execute", "执行交易", "交易管理"),
            new PermissionDef("trade:view-orderbook", "查看订单簿", "交易管理"),

            new PermissionDef("factor:run", "运行因子挖掘", "因子挖掘"),
            new PermissionDef("factor:create", "创建因子", "因子挖掘"),

            new PermissionDef("ml:train", "ML训练", "机器学习"),

            new PermissionDef("membership:view", "查看会员", "会员管理"),
            new PermissionDef("membership:manage", "管理会员", "会员管理"),

            new PermissionDef("system:config", "系统配置", "系统管理"),
            new PermissionDef("system:logs", "查看日志", "系统管理"),
            new PermissionDef("system:menu", "菜单维护", "系统管理"),

            new PermissionDef("permission:manage", "管理权限", "权限管理"),

            new PermissionDef("role:read", "查看角色", "权限管理"),
            new PermissionDef("role:assign", "分配角色", "权限管理")
    );

    @Override
    public void run(String... args) {
        for (PermissionDef def : ALL_PERMISSIONS) {
            Permission existing = permissionMapper.selectOne(
                    new LambdaQueryWrapper<Permission>()
                            .eq(Permission::getPermCode, def.code));
            if (existing == null) {
                Permission perm = new Permission();
                perm.setPermCode(def.code);
                perm.setPermName(def.name);
                perm.setModule(def.module);
                perm.setCreateTime(LocalDateTime.now());
                permissionMapper.insert(perm);
                log.debug("Registered permission: {}", def.code);
            }
        }
        log.info("PermissionRegistry completed, {} permissions registered", ALL_PERMISSIONS.size());
    }

    private record PermissionDef(String code, String name, String module) {
    }
}
