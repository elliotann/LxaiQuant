-- ============================================================
-- RBAC 数据初始化
-- 执行前确认已创建以下表：role、permission、role_permission、user_role_rel
-- 执行前确认已运行 PermissionRegistry 自动注册权限
-- ============================================================

-- 1) 为 ADMIN 角色分配所有已注册的权限
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 2) 将 ADMIN 用户关联到 ADMIN 角色
SET @admin_role_id = (SELECT id FROM role WHERE role_code = 'ADMIN');
INSERT INTO user_role_rel (user_id, role_id)
SELECT u.user_id, @admin_role_id
FROM user u
WHERE u.role COLLATE utf8mb4_unicode_ci = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM user_role_rel ur
    WHERE ur.user_id = u.user_id COLLATE utf8mb4_unicode_ci AND ur.role_id = @admin_role_id
  );

-- 3) 清理 Redis 权限缓存（在 Redis CLI 或应用 Redis 客户端执行）
--    DEL permissions:U000000000000001
--    或者在管理后台重新登录，登录操作会自动刷新缓存
