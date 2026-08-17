# 用户管理模块 (User Management Module)

## 概述

用户管理模块实现了完整的用户身份管理和权限控制功能，作为交易平台的用户聚合根。

## 核心功能

### 1. 用户实体 (User Aggregate Root)

#### 基本属性
- userId: 用户唯一标识
- username: 用户名（唯一标识）
- email: 邮箱地址（可选，唯一）
- phone: 手机号码（可选，唯一）
- passwordHash: 密码哈希（BCrypt加密）
- ole: 用户角色（ADMIN/PREMIUM/BASIC）
- status: 用户状态（ACTIVE/INACTIVE/SUSPENDED）

#### 时间戳
- createTime: 注册时间
- updateTime: 最后更新时间
- lastLoginTime: 最后登录时间

#### 配置存储
- preferences: 用户偏好配置（JSON存储）
- securityConfig: 用户安全配置（JSON存储）

#### 业务方法
- ctivate(): 激活用户
- deactivate(): 停用用户
- isActive(): 检查用户是否活跃

### 2. 枚举类

#### UserRole（用户角色）
- ADMIN: 管理员 - 完全权限
- PREMIUM: 高级用户 - 大部分功能权限
- BASIC: 基础用户 - 基础功能权限

#### UserStatus（用户状态）
- ACTIVE: 活跃状态 - 用户正常使用
- INACTIVE: 非活跃状态 - 用户已停用
- SUSPENDED: 暂停状态 - 用户被临时暂停

### 3. 服务接口 (IUserService)

#### 用户管理
- egister(): 用户注册
- login(): 用户登录
- updateUserRole(): 更新用户角色
- updateUserStatus(): 更新用户状态
- ctivateUser(): 激活用户
- deactivateUser(): 停用用户
- suspendUser(): 暂停用户

#### 资料管理
- updateProfile(): 更新个人资料
- changePassword(): 修改密码

#### 查询功能
- getByUsername(): 按用户名查询
- getByEmail(): 按邮箱查询
- getByPhone(): 按手机号查询
- isUsernameExists(): 检查用户名存在性
- isEmailExists(): 检查邮箱存在性
- isPhoneExists(): 检查手机号存在性

### 4. REST API 接口

#### 用户注册
`http
POST /api/users/register
{
    \"username\": \"john_doe\",
    \"email\": \"john@example.com\",
    \"phone\": \"13800000000\",
    \"password\": \"password123\"
}
`

#### 用户登录
`http
POST /api/users/login
{
    \"usernameOrEmail\": \"john_doe\",
    \"password\": \"password123\"
}
`

#### 用户管理
`http
GET /api/users?page=1&size=10           # 分页查询
GET /api/users/{userId}                # 详情查询
PUT /api/users/{userId}/role?role=PREMIUM # 更新角色
PUT /api/users/{userId}/status         # 更新状态
POST /api/users/{userId}/activate      # 激活用户
`

### 5. 安全特性

#### 密码加密
- 使用 BCrypt 算法加密密码
- 支持密码强度验证

#### 账户安全
- 密码错误验证
- 账户状态检查
- 登录时间记录

### 6. 数据库设计

#### 用户表结构
`sql
CREATE TABLE user (
    user_id VARCHAR(32) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    ole VARCHAR(20) DEFAULT 'BASIC',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    preferences TEXT,           -- JSON格式
    security_config TEXT,       -- JSON格式
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    last_login_time DATETIME,
    delete_flag TINYINT(1) DEFAULT 0
);
`

### 7. 初始化数据

系统会自动创建默认管理员账户：
- **用户名**: dmin
- **密码**: dmin123
- **角色**: ADMIN
- **状态**: ACTIVE

## 8. 使用示例

### 注册新用户
`java
@Autowired
private IUserService userService;

public void registerUser() {
    User user = userService.register(\"john_doe\", \"john@example.com\", null, \"password123\");
    System.out.println(\"注册成功: \" + user.getUsername());
}
`

### 用户登录
`java
public User loginUser() {
    User user = userService.login(\"john_doe\", \"password123\");
    if (user != null && user.isActive()) {
        return user;
    }
    return null;
}
`

### 更新用户角色
`java
public boolean promoteToPremium(String userId) {
    return userService.updateUserRole(userId, UserRole.PREMIUM);
}
`

## 9. 扩展计划

- [ ] JWT token 认证集成
- [ ] OAuth2 第三方登录
- [ ] 用户行为分析
- [ ] 用户权限细粒度控制
- [ ] 用户组/部门管理
- [ ] 用户操作日志记录
