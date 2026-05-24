# 登录认证模块实现详解

> 本文档详细介绍 AI Interview Platform 中基于 Spring Security + JWT 的无状态认证模块的实现原理、业务链路和涉及的核心知识点。

---

## 目录

- [1. 整体架构](#1-整体架构)
- [2. 技术选型](#2-技术选型)
- [3. 数据库设计（RBAC 模型）](#3-数据库设计rbac-模型)
- [4. 核心业务链路](#4-核心业务链路)
  - [4.1 登录流程](#41-登录流程)
  - [4.2 注册流程](#42-注册流程)
  - [4.3 请求认证流程](#43-请求认证流程)
  - [4.4 Token 刷新流程](#44-token-刷新流程)
  - [4.5 登出流程](#45-登出流程)
- [5. 后端核心实现](#5-后端核心实现)
  - [5.1 SecurityConfig — 安全配置中枢](#51-securityconfig--安全配置中枢)
  - [5.2 JwtAuthenticationFilter — 请求过滤器](#52-jwtauthenticationfilter--请求过滤器)
  - [5.3 JwtService — Token 服务](#53-jwtservice--token-服务)
  - [5.4 AuthService — 业务编排](#54-authservice--业务编排)
  - [5.5 AdminDataInitializer — 数据初始化](#55-admindatainitializer--数据初始化)
- [6. 前端核心实现](#6-前端核心实现)
  - [6.1 AuthContext — 状态管理](#61-authcontext--状态管理)
  - [6.2 Axios 拦截器 — 自动刷新](#62-axios-拦截器--自动刷新)
  - [6.3 ProtectedRoute — 路由守卫](#63-protectedroute--路由守卫)
- [7. 知识点总结](#7-知识点总结)
- [8. 文件清单](#8-文件清单)

---

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端 (React)                            │
│  ┌──────────┐  ┌──────────────┐  ┌─────────────┐               │
│  │LoginPage │  │ AuthContext  │  │ProtectedRoute│               │
│  └────┬─────┘  └──────┬───────┘  └──────┬──────┘               │
│       │               │                 │                       │
│       │    ┌──────────┴──────────┐      │                       │
│       │    │  Axios Interceptor  │      │                       │
│       │    │ (Bearer Token注入)  │      │                       │
│       │    └──────────┬──────────┘      │                       │
└───────┼───────────────┼─────────────────┼───────────────────────┘
        │               │                 │
        ▼               ▼                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                      后端 (Spring Boot)                         │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              SecurityFilterChain                         │   │
│  │  ┌───────────────┐    ┌──────────────────────────────┐  │   │
│  │  │   CORS Filter  │───▶│  JwtAuthenticationFilter     │  │   │
│  │  └───────────────┘    └──────────────┬───────────────┘  │   │
│  │                                      │                  │   │
│  │                              ┌───────▼───────┐          │   │
│  │                              │SecurityContext │          │   │
│  │                              │(设置认证信息)  │          │   │
│  │                              └───────┬───────┘          │   │
│  └──────────────────────────────────────┼──────────────────┘   │
│                                         ▼                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │AuthController│─▶│ AuthService  │─▶│  JwtService   │         │
│  └──────────────┘  └──────┬───────┘  └──────┬───────┘         │
│                           │                 │                  │
│                           ▼                 ▼                  │
│                   ┌──────────────┐  ┌──────────────┐          │
│                   │   JPA/DB     │  │    Redis      │          │
│                   │ (用户/角色)  │  │(Token黑名单) │          │
│                   └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 技术选型

| 组件 | 技术 | 作用 |
|------|------|------|
| 认证框架 | Spring Security 7.x | 提供完整的认证/授权基础设施 |
| Token 方案 | JWT (JJWT 0.12.x) | 无状态 Token 生成与验证 |
| 密码加密 | BCrypt | 单向哈希，自带盐值 |
| Token 存储 | Redis (Redisson) | Access Token 黑名单 + Refresh Token 存储 |
| 权限模型 | RBAC (Role-Based Access Control) | 用户-角色-权限三级模型 |

### 为什么选择 JWT + 双 Token？

**Access Token（短期）**：
- 有效期 1 小时，携带用户信息和角色
- 每次请求携带在 `Authorization: Bearer <token>` header 中
- 服务端无需查询数据库即可验证身份
- 代价是无法主动失效，因此需要黑名单机制

**Refresh Token（长期）**：
- 有效期 7 天，仅存储在 Redis 中
- 仅用于换取新的 Access Token，不参与业务请求
- 存储在 Redis 中可以主动失效（登出、改密码时）
- 实现了"无状态认证 + 有状态续期"的平衡

---

## 3. 数据库设计（RBAC 模型）

### ER 图

```
┌─────────────┐       ┌─────────────────┐       ┌─────────────┐
│  sys_user   │       │  sys_user_role  │       │  sys_role   │
├─────────────┤       ├─────────────────┤       ├─────────────┤
│ id (PK)     │──┐    │ user_id (FK)    │    ┌──│ id (PK)     │
│ username    │  └───▶│ role_id (FK)    │◀───┘  │ roleCode    │
│ password    │       └─────────────────┘       │ roleName    │
│ email       │                                 │ description │
│ nickname    │       ┌───────────────────┐     │ enabled     │
│ avatarUrl   │       │sys_role_permission│     └──────┬──────┘
│ enabled     │       ├───────────────────┤            │
│ createdAt   │       │ role_id (FK)      │◀───────────┘
│ updatedAt   │       │ permission_id(FK) │◀──────────┐
│ lastLoginAt │       └───────────────────┘            │
└─────────────┘                                  ┌─────┴──────────┐
                                                 │ sys_permission │
                                                 ├────────────────┤
                                                 │ id (PK)        │
                                                 │ permissionCode │
                                                 │ permissionName │
                                                 │ description    │
                                                 └────────────────┘
```

### 三表关系说明

- **sys_user**：用户表，存储用户名、BCrypt 密码、邮箱等基本信息
- **sys_role**：角色表，如 `ROLE_ADMIN`（管理员）、`ROLE_USER`（普通用户）
- **sys_permission**：权限表，如 `resume:read`、`interview:create`
- **sys_user_role**：用户-角色关联表（多对多）
- **sys_role_permission**：角色-权限关联表（多对多）

### JPA 实体关键注解

```java
// 用户实体 - 多对多关系
@ManyToMany(fetch = FetchType.LAZY)  // LAZY 避免 N+1 查询
@JoinTable(
    name = "sys_user_role",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"})
)
private Set<SysRoleEntity> roles = new HashSet<>();
```

**知识点：`FetchType.LAZY` vs `FetchType.EAGER`**
- `LAZY`：延迟加载，只在真正访问关联对象时才执行 SQL 查询
- `EAGER`：立即加载，查询主对象时一并加载所有关联对象
- 这里用 `LAZY` 是因为并非每次都需要加载角色和权限信息

---

## 4. 核心业务链路

### 4.1 登录流程

```
前端                          后端                              Redis
 │                             │                                │
 │  POST /api/auth/login       │                                │
 │  {username, password}       │                                │
 │────────────────────────────▶│                                │
 │                             │                                │
 │                     ┌───────▼───────┐                        │
 │                     │ 1.查找用户    │                        │
 │                     │ findByUsername│                        │
 │                     └───────┬───────┘                        │
 │                             │                                │
 │                     ┌───────▼───────┐                        │
 │                     │ 2.验证密码    │                        │
 │                     │ BCrypt.matches│                        │
 │                     └───────┬───────┘                        │
 │                             │                                │
 │                     ┌───────▼───────┐                        │
 │                     │ 3.生成Token   │                        │
 │                     │ Access+Refresh│                        │
 │                     └───────┬───────┘                        │
 │                             │                                │
 │                             │   SET jwt:refresh:{userId}     │
 │                             │   = {jti} (TTL 7天)            │
 │                             │───────────────────────────────▶│
 │                             │                                │
 │  {accessToken, refreshToken,│                                │
 │   expiresIn, user}          │                                │
 │◀────────────────────────────│                                │
 │                             │                                │
 │  localStorage.set(tokens)   │                                │
 └─────────────────────────────┘                                │
```

**关键代码路径**：
1. `AuthController.login()` → `AuthService.login()`
2. `passwordEncoder.matches()` 验证 BCrypt 哈希
3. `JwtService.generateAccessToken()` 生成 Access Token
4. `JwtService.generateRefreshToken()` 生成 Refresh Token 并存入 Redis

### 4.2 注册流程

```
1. 校验用户名/邮箱唯一性
2. BCrypt 加密密码
3. 分配默认角色 ROLE_USER
4. 保存用户到数据库
5. 生成双 Token 返回（注册即登录）
```

**知识点：BCrypt 密码加密**

```java
// 加密（注册时）
String hashed = passwordEncoder.encode("plainPassword");
// 结果示例: $2a$10$N9qo8uLOickgx2ZMRZoMye...

// 验证（登录时）
boolean matches = passwordEncoder.matches("plainPassword", hashed);
```

BCrypt 的特点：
- 自带随机盐值（嵌入在哈希结果中）
- 每次加密同一明文得到不同结果
- 计算成本可调（work factor），默认 10 轮
- Java 21 中使用 `BCryptPasswordEncoder`

### 4.3 请求认证流程

```
前端                    JwtAuthenticationFilter        SecurityContext
 │                              │                          │
 │  GET /api/resume/list        │                          │
 │  Authorization: Bearer xxx   │                          │
 │─────────────────────────────▶│                          │
 │                              │                          │
 │                     ┌────────▼────────┐                 │
 │                     │ 1.提取Token     │                 │
 │                     │ header.substring│                 │
 │                     └────────┬────────┘                 │
 │                              │                          │
 │                     ┌────────▼────────┐                 │
 │                     │ 2.验证Token     │                 │
 │                     │ 签名+过期+黑名单 │                 │
 │                     └────────┬────────┘                 │
 │                              │                          │
 │                     ┌────────▼────────┐                 │
 │                     │ 3.解析Claims    │                 │
 │                     │ userId, roles   │                 │
 │                     └────────┬────────┘                 │
 │                              │                          │
 │                              │  设置Authentication       │
 │                              │─────────────────────────▶│
 │                              │                          │
 │                              │  request.setAttribute    │
 │                              │  ("userId", userId)      │
 │                              │                          │
 │                     ┌────────▼────────┐                 │
 │                     │ 4.继续FilterChain│                │
 │                     └────────┬────────┘                 │
 │                              │                          │
 │                              ▼                          │
 │                     SecurityFilterChain                 │
 │                     .authorizeHttpRequests()             │
 │                     → .authenticated()                   │
 │                              │                          │
 │  200 OK (业务数据)            │                          │
 │◀─────────────────────────────┘                          │
```

**关键代码**：`JwtAuthenticationFilter.doFilterInternal()`

```java
// 1. 提取 Token
String token = authHeader.substring(7);

// 2. 验证 Token（签名 + 过期 + 黑名单）
if (!jwtService.isTokenValid(token)) { return; }

// 3. 解析 Claims
Claims claims = jwtService.parseToken(token);
String userId = claims.getSubject();  // JWT sub 字段存储 userId

// 4. 构建 Authentication 对象
UsernamePasswordAuthenticationToken authToken =
    new UsernamePasswordAuthenticationToken(userId, null, authorities);

// 5. 设置到 SecurityContext
SecurityContextHolder.getContext().setAuthentication(authToken);

// 6. 设置 request attribute（供 RateLimitAspect 使用）
request.setAttribute("userId", userId);
```

**知识点：SecurityContextHolder 的线程模型**

Spring Security 使用 `SecurityContextHolder` 存储当前用户的认证信息，默认使用 `ThreadLocal` 策略：
- 每个请求线程有独立的 `SecurityContext`
- 本项目启用了 Java 21 虚拟线程（`spring.threads.virtual.enabled=true`）
- 虚拟线程也支持 `ThreadLocal`，因此无需额外配置

### 4.4 Token 刷新流程

```
前端                         后端                            Redis
 │                            │                               │
 │  401 Unauthorized          │                               │
 │◀───────────────────────────│                               │
 │                            │                               │
 │  POST /api/auth/refresh    │                               │
 │  {refreshToken}            │                               │
 │───────────────────────────▶│                               │
 │                            │                               │
 │                    ┌───────▼───────┐                       │
 │                    │ 1.解析Refresh │                       │
 │                    │ Token的Claims │                       │
 │                    └───────┬───────┘                       │
 │                            │                               │
 │                            │  GET jwt:refresh:{userId}     │
 │                            │──────────────────────────────▶│
 │                            │                               │
 │                            │  返回存储的 jti               │
 │                            │◀──────────────────────────────│
 │                            │                               │
 │                    ┌───────▼───────┐                       │
 │                    │ 2.比对jti     │                       │
 │                    │ 防止重放攻击  │                       │
 │                    └───────┬───────┘                       │
 │                            │                               │
 │                    ┌───────▼───────┐                       │
 │                    │ 3.生成新Token │                       │
 │                    │ 新Access+     │                       │
 │                    │ 新Refresh     │                       │
 │                    └───────┬───────┘                       │
 │                            │                               │
 │  {accessToken,             │  SET jwt:refresh:{userId}     │
 │   refreshToken, expiresIn} │  = 新jti                      │
 │◀───────────────────────────│──────────────────────────────▶│
```

**知识点：JTI（JWT ID）机制**

每个 Token 都有唯一的 JTI（UUID），用于：
1. **Access Token**：黑名单标识，登出时将 JTI 加入黑名单
2. **Refresh Token**：存储在 Redis 中，刷新时比对 JTI 防止重放攻击

**前端自动刷新机制**（`request.ts`）：
```typescript
// 401 时自动刷新 Token
if (error.response?.status === 401) {
  // 1. 如果正在刷新，将请求加入队列
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({ resolve, reject });
    });
  }

  // 2. 用 Refresh Token 换取新 Token
  const response = await axios.post('/api/auth/refresh', { refreshToken });

  // 3. 用新 Token 重试原请求
  originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
  return instance(originalRequest);
}
```

### 4.5 登出流程

```
1. 解析 Access Token，获取 JTI 和剩余有效期
2. 将 JTI 加入 Redis 黑名单（TTL = 剩余有效期）
3. 删除 Redis 中的 Refresh Token
4. 前端清除 localStorage 中的 Token
```

**为什么黑名单需要 TTL？**
- Access Token 本身有过期时间
- 黑名单只需在 Token 有效期内存在
- Token 过期后自然失效，黑名单记录自动删除
- 避免 Redis 中积累过多无用数据

---

## 5. 后端核心实现

### 5.1 SecurityConfig — 安全配置中枢

**文件**：`modules/auth/security/SecurityConfig.java`

这是整个认证模块的配置中心，定义了 Spring Security 的行为：

```java
@Configuration
@EnableWebSecurity      // 启用 Spring Security
@EnableMethodSecurity   // 启用方法级安全（@PreAuthorize）
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. CORS 配置（复用 CorsProperties）
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 2. 禁用 CSRF（无状态 JWT 不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 3. 无状态会话（不使用 HttpSession）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 4. URL 授权规则
            .authorizeHttpRequests(auth -> {
                // 公开路径（登录、注册、Swagger）
                securityProperties.getPublicPaths().forEach(path -> ...);
                // OPTIONS 预检请求放行
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                // 其他请求需要认证
                auth.anyRequest().authenticated();
            })
            // 5. 异常处理
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(...)  // 401
                .accessDeniedHandler(...)       // 403
            )
            // 6. 添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

**知识点：SecurityFilterChain 的执行顺序**

```
请求进入
  │
  ▼
CorsFilter（处理 OPTIONS 预检请求）
  │
  ▼
JwtAuthenticationFilter（解析 Token，设置 SecurityContext）
  │
  ▼
UsernamePasswordAuthenticationFilter（本项目未使用，但位置保留）
  │
  ▼
AuthorizationFilter（检查权限）
  │
  ▼
Controller（业务处理）
```

### 5.2 JwtAuthenticationFilter — 请求过滤器

**文件**：`modules/auth/security/JwtAuthenticationFilter.java`

继承 `OncePerRequestFilter`，保证每个请求只执行一次：

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // 1. 提取 Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);  // 无 Token，继续（由后续拦截器处理）
            return;
        }

        // 2. 验证 Token
        String token = authHeader.substring(7);
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);  // Token 无效，继续（由授权层拒绝）
            return;
        }

        // 3. 解析并设置认证信息
        Claims claims = jwtService.parseToken(token);
        String userId = claims.getSubject();
        List<String> roles = claims.get("roles", List.class);

        // 4. 构建 Authentication 对象
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(userId, null, authorities);

        // 5. 设置到 SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // 6. 设置 request attribute（供 RateLimitAspect 使用）
        request.setAttribute("userId", userId);

        filterChain.doFilter(request, response);
    }
}
```

**关键设计决策**：
- Token 无效时不抛异常，而是放行请求，由 Spring Security 的授权层统一处理 401
- `request.setAttribute("userId", userId)` 是与现有 `RateLimitAspect` 的集成点

### 5.3 JwtService — Token 服务

**文件**：`modules/auth/service/JwtService.java`

封装所有 JWT 相关操作：

```java
@Service
public class JwtService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String REFRESH_TOKEN_PREFIX = "jwt:refresh:";

    // 生成 Access Token
    public String generateAccessToken(Long userId, String username, Set<String> roles) {
        return Jwts.builder()
            .subject(userId.toString())      // sub = userId
            .claim("username", username)     // 自定义声明
            .claim("roles", roles)           // 角色列表
            .id(UUID.randomUUID().toString()) // JTI
            .issuedAt(new Date())
            .expiration(new Date(now + accessTokenExpiration))
            .signWith(getSigningKey())       // HMAC-SHA256 签名
            .compact();
    }

    // 生成 Refresh Token（存入 Redis）
    public String generateRefreshToken(Long userId) {
        String jti = UUID.randomUUID().toString();
        // ... 构建 Token ...

        // 存入 Redis，TTL = 7天
        redisService.set(REFRESH_TOKEN_PREFIX + userId, jti, Duration.ofMillis(...));
        return token;
    }

    // 验证 Token
    public boolean isTokenValid(String token) {
        Claims claims = parseToken(token);    // 验证签名和过期
        String jti = claims.getId();
        return !isTokenBlacklisted(jti);      // 检查黑名单
    }

    // Token 加入黑名单
    public void blacklistToken(String jti, long remainingMs) {
        redisService.set(BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(remainingMs));
    }
}
```

**知识点：JWT 结构**

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsInJvbGVzIjpbIlJPTEVfQURNSU4iXSwianRpIjoiYTJiM2M0ZDUtZTZmNy00OGhiLWlqMWwtN2s4bTVvNnAiLCJpYXQiOjE2...xxxxx

│         Header          │              Payload              │    Signature   │
│ {alg: "HS256", typ: "JWT"} │ {sub: "1", username: "admin", │ HMAC-SHA256(   │
│                         │   roles: ["ROLE_ADMIN"],        │   header +     │
│                         │   jti: "a2b3c4d5-...",          │   payload,     │
│                         │   iat: 1620000000,              │   secret)      │
│                         │   exp: 1620003600}              │                │
```

**知识点：HMAC-SHA256 签名**

```java
private SecretKey getSigningKey() {
    byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
    // 使用 SHA-256 确保密钥长度 >= 256 位
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    keyBytes = digest.digest(keyBytes);
    return Keys.hmacShaKeyFor(keyBytes);
}
```

- HMAC = Hash-based Message Authentication Code
- 使用同一密钥进行签名和验证
- 服务端持有密钥，客户端无法伪造 Token

### 5.4 AuthService — 业务编排

**文件**：`modules/auth/service/AuthService.java`

编排登录、注册、刷新、登出等业务逻辑：

```java
@Service
public class AuthService {

    // 登录
    public LoginResponse login(LoginRequest request) {
        // 1. 查找用户
        var user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2. 验证密码（BCrypt）
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 4. 生成双 Token
        String accessToken = jwtService.generateAccessToken(...);
        String refreshToken = jwtService.generateRefreshToken(...);

        return new LoginResponse(accessToken, refreshToken, expiresIn, userResponse);
    }

    // 登出
    public void logout(String accessToken) {
        Claims claims = jwtService.parseToken(accessToken);
        String jti = claims.getId();
        long remainingMs = jwtService.getRemainingExpiration(claims);

        // 1. Access Token 加入黑名单
        jwtService.blacklistToken(jti, remainingMs);

        // 2. 删除 Refresh Token
        jwtService.invalidateRefreshToken(Long.parseLong(claims.getSubject()));
    }
}
```

**知识点：`@Transactional` 的使用边界**

```java
@Transactional
public LoginResponse register(RegisterRequest request) {
    // 数据库操作在同一事务中
    // 注意：不在事务中调用外部 API（如 Redis）
}

public LoginResponse login(LoginRequest request) {
    // 登录不加 @Transactional
    // 因为 Redis 操作（生成 Refresh Token）不应在数据库事务中
}
```

### 5.5 AdminDataInitializer — 数据初始化

**文件**：`modules/auth/security/AdminDataInitializer.java`

实现 `CommandLineRunner`，应用启动时自动初始化基础数据：

```java
@Component
public class AdminDataInitializer implements CommandLineRunner {

    @Override
    @Transactional
    public void run(String... args) {
        initPermissions();  // 创建 22 个权限
        initRoles();        // 创建 ROLE_ADMIN、ROLE_USER
        initAdminUser();    // 创建管理员账号
    }
}
```

**知识点：`CommandLineRunner` vs `@PostConstruct`**

| 特性 | CommandLineRunner | @PostConstruct |
|------|-------------------|----------------|
| 执行时机 | Spring 容器完全初始化后 | Bean 初始化完成后 |
| 事务支持 | 支持 `@Transactional` | 不支持 |
| 依赖注入 | 完全可用 | 可能有顺序问题 |
| 适用场景 | 数据初始化、迁移 | 简单的 Bean 初始化 |

---

## 6. 前端核心实现

### 6.1 AuthContext — 状态管理

**文件**：`frontend/src/contexts/AuthContext.tsx`

使用 React Context + useState 管理全局认证状态：

```typescript
export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [state, setState] = useState<AuthState>({
        user: null,
        accessToken: null,
        refreshToken: null,
        isAuthenticated: false,
        isLoading: true,  // 初始加载状态
    });

    // 应用启动时检查 Token 有效性
    useEffect(() => {
        const initAuth = async () => {
            if (tokenStorage.hasTokens()) {
                try {
                    const user = await authApi.getCurrentUser();
                    setState({ user, isAuthenticated: true, ... });
                } catch {
                    tokenStorage.clearTokens();  // Token 无效，清除
                }
            }
            setState(prev => ({ ...prev, isLoading: false }));
        };
        initAuth();
    }, []);

    // 登录方法
    const login = useCallback(async (data: LoginRequest) => {
        const response = await authApi.login(data);
        tokenStorage.setTokens(response.accessToken, response.refreshToken);
        setState({ user: response.user, isAuthenticated: true, ... });
    }, []);

    return (
        <AuthContext.Provider value={{ ...state, login, register, logout }}>
            {children}
        </AuthContext.Provider>
    );
}
```

**知识点：React Context 的作用**

```
┌─────────────────────────────────────────┐
│ App                                      │
│ ┌─────────────────────────────────────┐ │
│ │ AuthProvider (Context)              │ │
│ │  - user, isAuthenticated            │ │
│ │  - login(), logout()                │ │
│ │ ┌─────────────────────────────────┐ │ │
│ │ │ ProtectedRoute                  │ │ │
│ │ │  useAuth() → isAuthenticated    │ │ │
│ │ │ ┌─────────────────────────────┐ │ │ │
│ │ │ │ Layout                      │ │ │ │
│ │ │ │  useAuth() → user, logout   │ │ │ │
│ │ │ └─────────────────────────────┘ │ │ │
│ │ └─────────────────────────────────┘ │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### 6.2 Axios 拦截器 — 自动刷新

**文件**：`frontend/src/api/request.ts`

请求拦截器和响应拦截器的配合：

```typescript
// 请求拦截器：自动附加 Token
instance.interceptors.request.use((config) => {
    const token = tokenStorage.getAccessToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// 响应拦截器：401 时自动刷新
instance.interceptors.response.use(
    (response) => { /* 正常响应处理 */ },
    async (error) => {
        if (error.response?.status === 401) {
            // 队列化并发请求，避免多次刷新
            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({ resolve, reject });
                });
            }

            // 用 Refresh Token 换取新 Token
            isRefreshing = true;
            const response = await axios.post('/api/auth/refresh', { refreshToken });
            tokenStorage.setTokens(response.data.data.accessToken, ...);

            // 重试所有等待中的请求
            processQueue(null, accessToken);
            return instance(originalRequest);
        }
    }
);
```

**知识点：请求队列化解决并发问题**

```
请求A (401) ──▶ 开始刷新 Token
请求B (401) ──▶ 加入队列等待
请求C (401) ──▶ 加入队列等待
                    │
                    ▼
            Token 刷新成功
                    │
        ┌───────────┼───────────┐
        ▼           ▼           ▼
    重试请求A   重试请求B   重试请求C
```

### 6.3 ProtectedRoute — 路由守卫

**文件**：`frontend/src/components/ProtectedRoute.tsx`

```typescript
export default function ProtectedRoute({ children }: ProtectedRouteProps) {
    const { isAuthenticated, isLoading } = useAuth();
    const location = useLocation();

    // 加载中显示 spinner
    if (isLoading) {
        return <LoadingSpinner />;
    }

    // 未认证重定向到登录页
    if (!isAuthenticated) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    return <>{children}</>;
}
```

**知识点：`state={{ from: location }}` 的作用**

登录成功后可以跳转回用户原本想访问的页面：
```typescript
// LoginPage.tsx
const from = location.state?.from?.pathname || '/history';
navigate(from, { replace: true });
```

---

## 7. 知识点总结

### Spring Security 核心概念

| 概念 | 说明 | 本项目实现 |
|------|------|-----------|
| `SecurityFilterChain` | 安全过滤器链 | `SecurityConfig.securityFilterChain()` |
| `Authentication` | 认证信息 | `UsernamePasswordAuthenticationToken` |
| `SecurityContext` | 存储认证信息的容器 | `SecurityContextHolder.getContext()` |
| `UserDetailsService` | 加载用户信息的服务 | `CustomUserDetailsService` |
| `PasswordEncoder` | 密码编码器 | `BCryptPasswordEncoder` |
| `AuthenticationEntryPoint` | 未认证处理 | 返回 401 + Result JSON |
| `AccessDeniedHandler` | 权限不足处理 | 返回 403 + Result JSON |

### JWT 核心概念

| 概念 | 说明 | 本项目实现 |
|------|------|-----------|
| `Claims` | JWT 中的声明 | sub(userId), username, roles, jti |
| `JTI` | JWT 唯一标识 | UUID，用于黑名单和 Refresh Token 比对 |
| `Signature` | 签名 | HMAC-SHA256 |
| `Blacklist` | Token 黑名单 | Redis 存储，TTL = Token 剩余有效期 |
| `Refresh Token` | 续期 Token | Redis 存储，可主动失效 |

### RBAC 核心概念

| 概念 | 说明 | 本项目实现 |
|------|------|-----------|
| User | 系统用户 | `sys_user` 表 |
| Role | 角色 | `ROLE_ADMIN`, `ROLE_USER` |
| Permission | 权限 | `resume:read`, `interview:create` 等 |
| 多对多关系 | 用户-角色-权限 | `sys_user_role`, `sys_role_permission` |

### 与现有系统的集成点

| 集成点 | 机制 | 说明 |
|--------|------|------|
| `RateLimitAspect` | `request.setAttribute("userId", userId)` | JWT 过滤器设置，限流切面读取 |
| `GlobalExceptionHandler` | Spring Security 异常处理 | 统一返回 Result 格式 |
| `CorsProperties` | 复用现有 CORS 配置 | 移入 SecurityFilterChain |
| `ErrorCode` | 12xxx 错误码域 | 认证授权专用错误码 |

---

## 8. 文件清单

### 后端文件（20 个新文件）

```
app/src/main/java/interview/guide/
├── common/config/
│   ├── JwtProperties.java              # JWT 配置属性
│   └── SecurityProperties.java         # 安全配置属性（公开路径、管理员）
├── modules/auth/
│   ├── model/
│   │   ├── SysUserEntity.java          # 用户实体
│   │   ├── SysRoleEntity.java          # 角色实体
│   │   ├── SysPermissionEntity.java    # 权限实体
│   │   ├── LoginRequest.java           # 登录请求 DTO
│   │   ├── RegisterRequest.java        # 注册请求 DTO
│   │   ├── LoginResponse.java          # 登录响应 DTO
│   │   ├── TokenRefreshRequest.java    # 刷新请求 DTO
│   │   ├── TokenRefreshResponse.java   # 刷新响应 DTO
│   │   ├── UserResponse.java           # 用户信息 DTO
│   │   └── ChangePasswordRequest.java  # 改密码请求 DTO
│   ├── repository/
│   │   ├── SysUserRepository.java      # 用户 Repository
│   │   ├── SysRoleRepository.java      # 角色 Repository
│   │   └── SysPermissionRepository.java # 权限 Repository
│   ├── service/
│   │   ├── JwtService.java             # JWT 生成/验证/黑名单
│   │   ├── CustomUserDetailsService.java # Spring Security 用户加载
│   │   └── AuthService.java            # 认证业务逻辑
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java # JWT 过滤器
│   │   ├── SecurityConfig.java         # 安全配置
│   │   └── AdminDataInitializer.java   # 初始化管理员
│   └── controller/
│       └── AuthController.java         # 认证 API 端点
```

### 后端修改文件（7 个）

- `gradle/libs.versions.toml` — 添加 JJWT 依赖
- `app/build.gradle` — 添加 Spring Security + JJWT
- `app/src/main/resources/application.yml` — 添加 auth 配置
- `.env` — 添加 JWT_SECRET、ADMIN_* 变量
- `ErrorCode.java` — 添加 12xxx 错误码
- `GlobalExceptionHandler.java` — 添加 Security 异常处理
- `OpenApiConfig.java` — 添加 JWT Bearer scheme
- ~~`CorsConfig.java`~~ — 已删除（移入 SecurityConfig）

### 前端文件（7 个新文件）

```
frontend/src/
├── types/auth.ts           # 认证相关类型定义
├── utils/token.ts          # Token 存储工具
├── api/auth.ts             # 认证 API 客户端
├── contexts/AuthContext.tsx # 认证状态 Context
├── components/
│   └── ProtectedRoute.tsx  # 路由守卫
└── pages/
    ├── LoginPage.tsx       # 登录页面
    └── RegisterPage.tsx    # 注册页面
```

### 前端修改文件（3 个）

- `frontend/src/api/request.ts` — 添加 auth 拦截器
- `frontend/src/App.tsx` — 路由重构
- `frontend/src/components/Layout.tsx` — 用户信息 + 退出按钮

---

## 附录：API 端点速查

| 方法 | 路径 | 说明 | 认证 | 限流 |
|------|------|------|------|------|
| POST | `/api/auth/register` | 用户注册 | 否 | IP 5次/60分钟 |
| POST | `/api/auth/login` | 用户登录 | 否 | IP 10次/分钟 |
| POST | `/api/auth/refresh` | 刷新 Token | 否 | 无 |
| POST | `/api/auth/logout` | 用户登出 | 是 | 无 |
| GET | `/api/auth/me` | 获取当前用户 | 是 | 无 |
| PUT | `/api/auth/password` | 修改密码 | 是 | 无 |
