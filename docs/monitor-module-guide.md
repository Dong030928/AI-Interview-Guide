# 日志监控与告警模块实现详解

> 本文档详细介绍 AI Interview Platform 中日志监控与告警模块的实现原理、架构设计和涉及的核心知识点。

---

## 目录

- [1. 模块概览](#1-模块概览)
- [2. 整体架构](#2-整体架构)
- [3. 技术选型](#3-技术选型)
- [4. 数据库设计](#4-数据库设计)
  - [4.1 操作日志表 sys_operation_log](#41-操作日志表-sys_operation_log)
  - [4.2 告警规则表 sys_alert_rule](#42-告警规则表-sys_alert_rule)
  - [4.3 告警日志表 sys_alert_log](#43-告警日志表-sys_alert_log)
- [5. 分层实现详解](#5-分层实现详解)
  - [5.1 结构化日志层](#51-结构化日志层)
  - [5.2 链路追踪层](#52-链路追踪层)
  - [5.3 事件采集层](#53-事件采集层)
  - [5.4 告警规则引擎](#54-告警规则引擎)
  - [5.5 通知推送层](#55-通知推送层)
  - [5.6 健康检查层](#56-健康检查层)
  - [5.7 REST API 层](#57-rest-api-层)
  - [5.8 前端监控面板](#58-前端监控面板)
- [6. 核心业务链路](#6-核心业务链路)
  - [6.1 错误事件采集链路](#61-错误事件采集链路)
  - [6.2 告警触发链路](#62-告警触发链路)
  - [6.3 通知推送链路](#63-通知推送链路)
- [7. 配置说明](#7-配置说明)
- [8. 知识点总结](#8-知识点总结)
- [9. 项目经历亮点写法](#9-项目经历亮点写法)
- [10. 文件清单](#10-文件清单)

---

## 1. 模块概览

本模块为 AI Interview Platform 提供完整的可观测性能力，覆盖三大支柱：

| 能力 | 说明 | 对应 Phase |
|------|------|-----------|
| **日志（Logging）** | 结构化 JSON 日志 + 请求链路追踪 | Phase 1 |
| **指标（Metrics）** | Spring Boot Actuator 健康检查 + Prometheus 指标 | Phase 2 |
| **追踪（Tracing）** | MDC traceId 贯穿请求全链路 | Phase 1 |
| **事件采集** | 关键业务异常持久化到数据库 | Phase 3 |
| **告警规则** | 基于阈值的定时评估引擎 | Phase 4 |
| **通知推送** | 控制台 + 钉钉/飞书 Webhook | Phase 5 |
| **管理 API** | 后端 REST 接口 | Phase 6 |
| **可视化面板** | React 前端监控页面 | Phase 7 |

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端监控面板                              │
│   MonitorPage.tsx                                               │
│   ├── MonitorOverview.tsx    (统计概览 + 趋势图)                 │
│   ├── OperationLogPanel.tsx  (操作日志查询)                      │
│   └── AlertRulePanel.tsx     (告警规则管理 + 告警历史)            │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP REST API
┌──────────────────────────▼──────────────────────────────────────┐
│                    MonitorController                             │
│              /api/monitor/* (ADMIN 权限)                         │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                      服务层 (Service)                            │
│  ┌──────────────────┐ ┌───────────────────┐ ┌────────────────┐  │
│  │ OperationLogService│ │AlertEvaluationService│ │NotificationService│ │
│  │  (事件写入 DB)     │ │ (定时评估告警规则)   │ │ (通知分发)       │  │
│  └──────────────────┘ └───────────────────┘ └────────────────┘  │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                      数据层 (Repository)                         │
│  SysOperationLogRepository  SysAlertRuleRepository               │
│  SysAlertLogRepository                                            │
└──────────────────────────┬──────────────────────────────────────┘
                           │ JPA
┌──────────────────────────▼──────────────────────────────────────┐
│                      PostgreSQL                                  │
│  sys_operation_log    sys_alert_rule    sys_alert_log            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      事件采集入口                                │
│  ┌─────────────────────────┐  ┌──────────────────────────────┐  │
│  │ GlobalExceptionHandler  │  │ OperationLogAspect (AOP)     │  │
│  │ 捕获业务异常/系统异常     │  │ 捕获异步任务失败              │  │
│  └─────────────────────────┘  └──────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      基础设施层                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │ Logback       │ │ TraceIdFilter │ │ Spring Boot Actuator     │ │
│  │ 结构化 JSON   │ │ MDC 链路追踪  │ │ Health / Prometheus      │ │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 技术选型

| 技术 | 用途 | 选型理由 |
|------|------|---------|
| **LogstashEncoder** | 结构化 JSON 日志 | ELK/EFK 技术栈标准格式，便于日志采集和检索 |
| **MDC + Servlet Filter** | 链路追踪 | 零侵入，自动为每个请求生成 traceId |
| **Spring Boot Actuator** | 健康检查 + 指标 | Spring 生态标准方案，开箱即用 |
| **JPA + PostgreSQL** | 事件持久化 | 与现有技术栈统一，支持复杂查询 |
| **@Scheduled** | 告警定时评估 | Spring 原生调度，轻量级，无需引入 Quartz |
| **RestTemplate** | Webhook 通知 | Spring 生态 HTTP 客户端，配置简单 |
| **React + TypeScript** | 前端监控面板 | 与现有前端技术栈统一 |

---

## 4. 数据库设计

### 4.1 操作日志表 sys_operation_log

记录系统中的关键业务事件，作为告警引擎的数据源。

```sql
CREATE TABLE sys_operation_log (
    id           BIGSERIAL PRIMARY KEY,
    event_type   VARCHAR(32)  NOT NULL,    -- 事件类型：ERROR / AUTH / AI_SERVICE / ASYNC_TASK
    level        VARCHAR(16)  NOT NULL,    -- 日志级别：WARN / ERROR
    source       VARCHAR(255),             -- 来源类名
    message      TEXT,                     -- 事件描述
    stack_trace  TEXT,                     -- 异常堆栈（截取前 2000 字符）
    user_id      BIGINT,                   -- 操作用户 ID
    ip_address   VARCHAR(64),              -- 客户端 IP
    trace_id     VARCHAR(64),              -- 链路追踪 ID
    metadata     JSONB,                    -- 扩展信息（sessionId, resumeId 等）
    created_at   TIMESTAMP DEFAULT NOW()   -- 创建时间
);

-- 告警评估常用查询索引
CREATE INDEX idx_oplog_event_type_created ON sys_operation_log(event_type, created_at);
CREATE INDEX idx_oplog_level_created ON sys_operation_log(level, created_at);
```

**事件类型枚举（OperationEventType）：**

| 类型 | 触发场景 |
|------|---------|
| `ERROR` | 系统未捕获异常、业务异常 |
| `AUTH` | 登录失败、认证异常 |
| `AI_SERVICE` | LLM 调用超时、AI 服务异常 |
| `ASYNC_TASK` | Redis Stream 异步任务失败（简历分析、文档向量化等） |

### 4.2 告警规则表 sys_alert_rule

定义告警的触发条件和通知方式。

```sql
CREATE TABLE sys_alert_rule (
    id               BIGSERIAL PRIMARY KEY,
    rule_name        VARCHAR(128) NOT NULL,  -- 规则名称
    event_type       VARCHAR(32)  NOT NULL,  -- 监控的事件类型
    level            VARCHAR(16),            -- 监控的日志级别（NULL = 全部）
    threshold        INTEGER      NOT NULL,  -- 触发阈值（事件次数）
    window_minutes   INTEGER      NOT NULL,  -- 时间窗口（分钟）
    enabled          BOOLEAN DEFAULT TRUE,   -- 是否启用
    notify_channel   VARCHAR(32)  NOT NULL,  -- 通知渠道：WEBHOOK / CONSOLE
    notify_target    VARCHAR(512),           -- Webhook URL（CONSOLE 为空）
    cooldown_minutes INTEGER DEFAULT 30,     -- 告警冷却期（分钟）
    last_triggered_at TIMESTAMP,             -- 上次触发时间
    created_at       TIMESTAMP DEFAULT NOW(),
    updated_at       TIMESTAMP DEFAULT NOW()
);
```

**默认初始化的 4 条规则：**

| 规则名 | 事件类型 | 阈值 | 窗口 | 说明 |
|--------|---------|------|------|------|
| AI 服务异常频繁 | AI_SERVICE | 5 次 | 10 分钟 | LLM 调用频繁失败 |
| 登录失败过多 | AUTH (WARN) | 10 次 | 5 分钟 | 可能的暴力破解 |
| 异步任务失败 | ASYNC_TASK | 3 次 | 10 分钟 | 简历分析/向量化异常 |
| 系统错误频发 | ERROR (ERROR) | 10 次 | 5 分钟 | 系统级错误频发 |

### 4.3 告警日志表 sys_alert_log

记录每次告警触发的历史。

```sql
CREATE TABLE sys_alert_log (
    id           BIGSERIAL PRIMARY KEY,
    rule_id      BIGINT       NOT NULL,     -- 关联规则 ID
    rule_name    VARCHAR(128) NOT NULL,      -- 规则名称（冗余，避免 JOIN）
    event_count  INTEGER      NOT NULL,      -- 触发时的事件计数
    triggered_at TIMESTAMP DEFAULT NOW(),    -- 触发时间
    resolved     BOOLEAN DEFAULT FALSE,      -- 是否已恢复
    resolved_at  TIMESTAMP                   -- 恢复时间
);
```

---

## 5. 分层实现详解

### 5.1 结构化日志层

**文件：** `app/src/main/resources/logback-spring.xml`

采用双 Appender 架构：

```
┌─────────────────────────────────────────────────────┐
│                    Root Logger                       │
│                     level: INFO                      │
│                    /           \                      │
│         ┌──────────┐     ┌──────────┐               │
│         │ CONSOLE   │     │   FILE    │               │
│         │ PatternLayout │ LogstashEncoder │           │
│         │ 人可读格式   │     │ JSON 格式  │             │
│         └──────────┘     └──────────┘               │
└─────────────────────────────────────────────────────┘
```

**Console Appender** — 输出到控制台，人可读格式：

```
2026-05-25 15:45:22.954 [INFO] [a1b2c3d4] [main] i.g.m.service - 消息内容
                     时间    级别   traceId  线程    类名     消息
```

**File Appender** — 输出到 `logs/app.log`，JSON 格式（LogstashEncoder）：

```json
{
  "@timestamp": "2026-05-25T15:45:22.954+08:00",
  "level": "INFO",
  "logger_name": "interview.guide.modules.monitor.service.AlertEvaluationService",
  "thread_name": "scheduling-1",
  "message": "告警评估完成",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "service": "ai-interview-platform"
}
```

**滚动策略：**
- 单文件最大 50MB
- 保留 30 天历史
- 总大小上限 2GB
- 超出后自动压缩为 `.gz`

**包级日志级别控制（通过 application.yml 环境变量）：**

```yaml
logging:
  level:
    interview.guide: ${LOG_LEVEL_APP:INFO}           # 业务日志
    org.springframework.security: ${LOG_LEVEL_SECURITY:WARN}  # 安全框架
    org.springframework.ai: ${LOG_LEVEL_AI:INFO}     # Spring AI
    org.hibernate.SQL: ${LOG_LEVEL_HIBERNATE:WARN}   # SQL 日志
```

### 5.2 链路追踪层

**文件：** `app/src/main/java/interview/guide/common/logging/TraceIdFilter.java`

核心思路：用 Servlet Filter 在请求入口生成 UUID，存入 Logback MDC（Mapped Diagnostic Context），日志框架自动将 traceId 注入每条日志。

```
客户端请求
    │
    ▼
┌──────────────────────┐
│  TraceIdFilter        │  ← 优先级最高（HIGHEST_PRECEDENCE + 10）
│  1. 从请求头取 traceId │
│  2. 没有则生成 UUID    │
│  3. MDC.put("traceId")│
│  4. 响应头加 X-Trace-Id│
│  5. finally MDC.clear()│
└──────────────────────┘
    │
    ▼
┌──────────────────────┐
│ JwtAuthenticationFilter│
│  ... 业务处理 ...      │
│  此期间所有日志都自动    │
│  携带 traceId          │
└──────────────────────┘
```

**关键设计点：**

```java
// 优先复用上游传递的 traceId（未来微服务间调用场景）
String traceId = request.getHeader("X-Trace-Id");
if (traceId == null || traceId.isBlank()) {
  traceId = UUID.randomUUID().toString().replace("-", "");
}
MDC.put("traceId", traceId);

try {
  filterChain.doFilter(request, response);
} finally {
  MDC.clear(); // 必须清理，虚拟线程环境下防止内存泄漏
}

// traceId 写入响应头，前端可用于日志关联
response.setHeader("X-Trace-Id", traceId);
```

**为什么用 Filter 而不是 Interceptor？**
- Filter 在 Servlet 容器层面执行，优先级更高
- 可以在 Spring Security 之前执行，确保认证链路也有 traceId
- `@Order(Ordered.HIGHEST_PRECEDENCE + 10)` 保证在所有 Filter 之前

### 5.3 事件采集层

**采集入口有两个：**

#### 5.3.1 GlobalExceptionHandler — 异常捕获

**文件：** `app/src/main/java/interview/guide/common/exception/GlobalExceptionHandler.java`

在全局异常处理器中注入 `OperationLogService`，捕获关键异常时自动记录：

```java
@ExceptionHandler(BusinessException.class)
public Result<?> handleBusinessException(BusinessException e) {
  // 业务异常 → 记录到 sys_operation_log
  operationLogService.recordEvent(
      OperationEventType.ERROR, "ERROR",
      e.getClass().getSimpleName(), e.getMessage(), ...);
  return Result.error(e.getCode(), e.getMessage());
}
```

**捕获的异常类型与事件类型映射：**

| 异常类型 | 事件类型 | 级别 |
|---------|---------|------|
| BusinessException | ERROR | ERROR |
| ResourceAccessException | ERROR | ERROR |
| RestClientException | AI_SERVICE | ERROR |
| AuthenticationException | AUTH | WARN |
| Exception（兜底） | ERROR | ERROR |

#### 5.3.2 OperationLogAspect — AOP 切面

**文件：** `app/src/main/java/interview/guide/modules/monitor/aspect/OperationLogAspect.java`

通过 AOP 拦截异步任务的失败方法：

```java
@Aspect
@Component
public class OperationLogAspect {

  @AfterThrowing(
    pointcut = "execution(* interview.guide.common.async.AbstractStreamConsumer.markFailed(..))",
    throwing = "ex")
  public void onAsyncTaskFailed(JoinPoint jp, Exception ex) {
    operationLogService.recordEvent(
        OperationEventType.ASYNC_TASK, "ERROR",
        jp.getTarget().getClass().getSimpleName(),
        ex.getMessage(), stackTrace, ...);
  }
}
```

**为什么需要两个采集入口？**
- GlobalExceptionHandler 只能捕获 Controller 层抛出的异常
- 异步任务（Redis Stream Consumer）的异常不经过 Controller，需要 AOP 拦截
- 两者互补，形成完整的事件采集覆盖

#### 5.3.3 OperationLogService — 写入服务

**文件：** `app/src/main/java/interview/guide/modules/monitor/service/OperationLogService.java`

```java
@Service
public class OperationLogService {

  public void recordEvent(OperationEventType eventType, String level,
      String source, String message, String stackTrace,
      Long userId, String traceId, String metadata) {

    SysOperationLogEntity entity = SysOperationLogEntity.builder()
        .eventType(eventType.name())
        .level(level)
        .source(source)
        .message(message)
        .stackTrace(truncate(stackTrace, 2000))  // 截断过长堆栈
        .userId(userId)
        .ipAddress(extractClientIp())             // 从 RequestContextHolder 获取
        .traceId(traceId != null ? traceId : MDC.get("traceId"))
        .metadata(metadata)
        .build();

    repository.save(entity);
  }
}
```

### 5.4 告警规则引擎

**文件：** `app/src/main/java/interview/guide/modules/monitor/service/AlertEvaluationService.java`

#### 5.4.1 调度机制

```java
@Scheduled(fixedRate = 60_000) // 每分钟执行一次
public void evaluate() {
  List<SysAlertRuleEntity> rules = ruleRepository.findAllByEnabledTrue();

  for (SysAlertRuleEntity rule : rules) {
    if (isInCooldown(rule)) continue;           // 冷却期内跳过

    LocalDateTime since = LocalDateTime.now()
        .minusMinutes(rule.getWindowMinutes());
    int count = logRepository.countByEventTypeAndCreatedAtAfter(
        rule.getEventType(), since);

    if (count >= rule.getThreshold()) {
      triggerAlert(rule, count);                 // 触发告警
    }
  }
}
```

#### 5.4.2 核心算法

```
每分钟执行：
  for each 启用的规则:
    1. 检查冷却期 → lastTriggeredAt + cooldownMinutes > now? → 跳过
    2. 查询时间窗口内的事件数 → countByEventTypeAndCreatedAtAfter
    3. 判断阈值 → count >= threshold?
    4. 触发告警:
       a. 写入 sys_alert_log
       b. 更新规则的 lastTriggeredAt
       c. 调用 NotificationService 发送通知
```

#### 5.4.3 冷却期机制

防止同一规则短时间内重复告警（告警风暴）：

```
时间线：
  ──┬──────────┬──────────┬──────────┬──→
    │ 触发告警  │ 冷却期    │ 冷却期    │
    │          │ (30min)   │ 结束      │
    │          │ 跳过评估   │ 恢复评估  │
```

### 5.5 通知推送层

#### 5.5.1 通知渠道接口

**文件：** `app/src/main/java/interview/guide/modules/monitor/notification/NotificationChannel.java`

```java
public interface NotificationChannel {
  void send(AlertEvent alert, String target);  // target = Webhook URL 或空
  String channelName();                         // "CONSOLE" / "WEBHOOK"
}
```

#### 5.5.2 Console 通知

**文件：** `app/src/main/java/interview/guide/modules/monitor/notification/ConsoleNotificationChannel.java`

始终可用，输出到日志：

```
[ALERT] 规则[系统错误频发]触发: ERROR事件在5分钟内发生12次(阈值10)
```

#### 5.5.3 Webhook 通知

**文件：** `app/src/main/java/interview/guide/modules/monitor/notification/WebhookNotificationChannel.java`

发送钉钉/飞书兼容格式：

```json
{
  "msgtype": "text",
  "text": {
    "content": "[告警] 规则名: 系统错误频发\n事件类型: ERROR\n触发次数: 12\n时间窗口: 5分钟\n阈值: 10\n触发时间: 2026-05-25 15:45:00"
  }
}
```

**关键实现细节：**
- 超时 5 秒（connect + read）
- 验证钉钉响应 `errcode == 0`，失败打印具体错误信息
- 发送失败不抛异常，不阻塞告警流程

#### 5.5.4 通知分发

**文件：** `app/src/main/java/interview/guide/modules/monitor/service/NotificationService.java`

```java
@Service
public class NotificationService {
  private final Map<String, NotificationChannel> channelMap;

  // Spring 自动注入所有 NotificationChannel 实现，按 channelName 索引
  public NotificationService(List<NotificationChannel> channels) {
    this.channelMap = channels.stream()
        .collect(Collectors.toMap(NotificationChannel::channelName, c -> c));
  }

  public void notify(AlertEvent event, String channelName, String target) {
    NotificationChannel channel = channelMap.get(channelName);
    if (channel != null) {
      channel.send(event, target);
    }
  }
}
```

### 5.6 健康检查层

**文件：** `app/src/main/java/interview/guide/modules/monitor/health/AiServiceHealthIndicator.java`

Spring Boot 4.0 的 Health Indicator 接口（注意包名变化）：

```java
// Boot 3.x: org.springframework.boot.actuate.health.HealthIndicator
// Boot 4.0: org.springframework.boot.health.contributor.HealthIndicator
@Component
public class AiServiceHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    ChatClient client = llmProviderRegistry.getChatClientOrDefault(null);
    if (client == null) {
      return Health.down()
          .withDetail("reason", "No default ChatClient available")
          .build();
    }
    // 用简单 prompt 验证 AI 服务连通性
    String response = client.prompt().user("ping").call().content();
    return (response != null && !response.isBlank())
        ? Health.up().withDetail("provider", "default").build()
        : Health.down().withDetail("reason", "Empty response").build();
  }
}
```

**Actuator 端点：**

| 端点 | 说明 |
|------|------|
| `GET /actuator/health` | 整体健康状态（db, redis, aiService） |
| `GET /actuator/info` | 应用信息 |
| `GET /actuator/metrics` | JVM / HTTP 指标 |
| `GET /actuator/prometheus` | Prometheus 格式指标（可接入 Grafana） |

### 5.7 REST API 层

**文件：** `app/src/main/java/interview/guide/modules/monitor/controller/MonitorController.java`

所有端点需要 ADMIN 角色：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/monitor/logs` | GET | 分页查询操作日志（支持事件类型、级别、日期、关键词筛选） |
| `/api/monitor/stats` | GET | 统计概览（今日日志数、错误数、告警数、活跃规则数） |
| `/api/monitor/alerts/rules` | GET | 查询所有告警规则 |
| `/api/monitor/alerts/rules` | POST | 创建告警规则 |
| `/api/monitor/alerts/rules/{id}` | PUT | 更新告警规则 |
| `/api/monitor/alerts/rules/{id}/toggle` | PATCH | 启用/禁用规则 |
| `/api/monitor/alerts/history` | GET | 分页查询告警历史 |

### 5.8 前端监控面板

#### 5.8.1 页面结构

```
MonitorPage.tsx (主页面，Tab 切换)
├── MonitorOverview.tsx      ← Tab 1: 统计概览
│   ├── 4 个统计卡片（日志数/错误数/告警数/规则数）
│   └── 错误趋势折线图（Recharts）
├── OperationLogPanel.tsx    ← Tab 2: 操作日志
│   ├── 筛选栏（事件类型、级别、日期范围、关键词）
│   ├── 日志表格（时间、级别标签、事件类型、来源、消息）
│   └── 展开行（堆栈、traceId、metadata JSON）
└── AlertRulePanel.tsx       ← Tab 3: 告警管理
    ├── 规则列表（名称、事件类型、阈值/窗口、通知渠道、状态开关）
    ├── 新建/编辑规则 Modal
    └── 告警历史子表格
```

#### 5.8.2 路由与权限

```tsx
// App.tsx
<Route path="admin/monitor" element={<MonitorPage />} />

// Layout.tsx — 仅 ADMIN 可见
...(user?.roles?.includes('ROLE_ADMIN')
  ? [{ id: 'monitor', path: '/admin/monitor', label: '系统监控',
       icon: Activity, description: '日志、告警、健康检查' }]
  : []),
```

---

## 6. 核心业务链路

### 6.1 错误事件采集链路

```
用户操作触发异常
    │
    ▼
GlobalExceptionHandler.handleBusinessException()
    │
    ├─→ 返回 Result.error() 给前端
    │
    └─→ operationLogService.recordEvent()
            │
            ├─→ 提取客户端 IP（RequestContextHolder）
            ├─→ 提取 traceId（MDC）
            ├─→ 构建 SysOperationLogEntity
            └─→ repository.save() → 写入 sys_operation_log 表
```

### 6.2 告警触发链路

```
@Scheduled(fixedRate = 60_000)  ← 每分钟触发
    │
    ▼
AlertEvaluationService.evaluate()
    │
    ├─→ 查询所有启用规则
    │
    └─→ for each rule:
            │
            ├─→ 检查冷却期 → isInCooldown()? → 跳过
            │
            ├─→ 查询时间窗口内事件数
            │   SELECT COUNT(*) FROM sys_operation_log
            │   WHERE event_type = ? AND created_at > ?
            │
            ├─→ count >= threshold?
            │       │
            │       ▼ YES
            │   triggerAlert(rule, count)
            │       │
            │       ├─→ 写入 sys_alert_log
            │       ├─→ 更新 rule.lastTriggeredAt
            │       └─→ notificationService.notify(...)
            │
            └─→ count < threshold → 跳过
```

### 6.3 通知推送链路

```
NotificationService.notify(alertEvent, "WEBHOOK", webhookUrl)
    │
    ▼
channelMap.get("WEBHOOK")
    │
    ▼
WebhookNotificationChannel.send(alert, target)
    │
    ├─→ 构建钉钉/飞书格式 payload
    ├─→ RestTemplate.postForEntity(target, payload)
    ├─→ 检查响应 errcode == 0
    │       │
    │       ├─→ 成功 → log.info("已发送")
    │       └─→ 失败 → log.warn("发送被拒绝: errcode=xxx")
    │
    └─→ catch Exception → log.warn("发送失败: error=xxx")
```

---

## 7. 配置说明

### 7.1 application.yml 配置项

```yaml
# 日志级别控制
logging:
  level:
    interview.guide: ${LOG_LEVEL_APP:INFO}
    org.springframework.security: ${LOG_LEVEL_SECURITY:WARN}
    org.springframework.ai: ${LOG_LEVEL_AI:INFO}
    org.hibernate.SQL: ${LOG_LEVEL_HIBERNATE:WARN}
  file:
    name: logs/app.log

# Actuator 端点暴露
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized

# 监控告警配置
app:
  monitor:
    notification:
      enabled: ${MONITOR_NOTIFICATION_ENABLED:true}
      webhook-url: ${ALERT_WEBHOOK_URL:}
```

### 7.2 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `LOG_LEVEL_APP` | 业务日志级别 | INFO |
| `LOG_LEVEL_SECURITY` | 安全框架日志级别 | WARN |
| `LOG_LEVEL_AI` | Spring AI 日志级别 | INFO |
| `LOG_LEVEL_HIBERNATE` | Hibernate SQL 日志级别 | WARN |
| `MONITOR_NOTIFICATION_ENABLED` | 是否启用通知 | true |
| `ALERT_WEBHOOK_URL` | 钉钉/飞书 Webhook URL | 空（不发送 Webhook） |

### 7.3 钉钉机器人配置步骤

1. 打开钉钉群 → 群设置 → 智能群助手 → 添加机器人
2. 选择「自定义（通过 Webhook 接入）」
3. 安全设置选择「自定义关键词」，填入 `告警`
4. 复制 Webhook 地址，配置到 `ALERT_WEBHOOK_URL` 环境变量

---

## 8. 知识点总结

### 8.1 日志体系

| 知识点 | 说明 |
|--------|------|
| **Logback 架构** | Logger → Appender → Encoder 三层结构 |
| **LogstashEncoder** | 输出结构化 JSON，兼容 ELK/EFK 技术栈 |
| **MDC (Mapped Diagnostic Context)** | 线程级上下文存储，日志框架自动读取 |
| **滚动策略** | SizeAndTimeBasedRollingPolicy，按大小 + 时间双重滚动 |
| **springProfile** | Logback 中按 Spring Profile 条件加载配置 |

### 8.2 链路追踪

| 知识点 | 说明 |
|--------|------|
| **Servlet Filter vs Interceptor** | Filter 在 Servlet 容器层，优先级更高 |
| **@Order 注解** | 控制 Filter 执行顺序 |
| **MDC + 虚拟线程** | 虚拟线程需注意 MDC.clear() 防止内存泄漏 |
| **X-Trace-Id 响应头** | 前端可关联后端日志，便于问题排查 |

### 8.3 事件采集

| 知识点 | 说明 |
|--------|------|
| **GlobalExceptionHandler** | @RestControllerAdvice 全局异常处理 |
| **AOP @AfterThrowing** | 方法抛出异常后执行通知 |
| **RequestContextHolder** | 获取当前请求的 HttpServletRequest |
| **堆栈截断** | 避免超长异常堆栈撑爆数据库字段 |

### 8.4 告警引擎

| 知识点 | 说明 |
|--------|------|
| **@Scheduled** | Spring 原生定时任务，fixedRate 固定频率 |
| **滑动时间窗口** | `WHERE created_at > now() - windowMinutes` |
| **冷却期** | 防止告警风暴，`lastTriggeredAt + cooldownMinutes > now()` |
| **阈值评估** | `count >= threshold` 触发告警 |

### 8.5 通知推送

| 知识点 | 说明 |
|--------|------|
| **策略模式** | NotificationChannel 接口 + 多实现（Console/Webhook） |
| **Spring 自动注入** | `List<NotificationChannel>` 自动收集所有实现 |
| **钉钉 Webhook 协议** | msgtype: text 格式，errcode: 0 表示成功 |
| **容错设计** | 通知失败不抛异常，不影响告警流程 |

### 8.6 健康检查

| 知识点 | 说明 |
|--------|------|
| **Spring Boot 4.0 变化** | HealthIndicator 从 `actuate.health` 移到 `health.contributor` |
| **自定义 Health Indicator** | 实现 `HealthIndicator` 接口，返回 `Health.up()/down()` |
| **Actuator 端点暴露** | `management.endpoints.web.exposure.include` 配置 |

---

## 9. 项目经历亮点写法

### 9.1 简历描述（中文版）

> **日志监控与告警系统**
>
> 设计并实现了基于 Spring Boot 的全链路可观测性模块，覆盖日志、指标、追踪三大支柱：
> - 基于 LogstashEncoder 构建结构化 JSON 日志体系，通过 Servlet Filter + MDC 实现请求级链路追踪（traceId 贯穿 Filter → Service → Repository 全链路），支持与 ELK/EFK 日志平台无缝对接
> - 集成 Spring Boot Actuator 实现多维度健康检查（数据库、Redis、AI 服务），暴露 Prometheus 指标端点
> - 设计事件驱动的告警引擎：AOP 切面 + 全局异常处理器自动采集关键业务事件到 PostgreSQL，定时任务基于滑动时间窗口 + 阈值 + 冷却期机制评估告警规则
> - 实现策略模式的多渠道通知推送（钉钉/飞书 Webhook + 控制台），支持告警规则的 CRUD 管理和运行时动态启停
> - 前端基于 React + TypeScript 实现管理员监控面板，包含统计概览、操作日志查询、告警规则管理三个功能模块

### 9.2 面试话术参考

**开场（30 秒）：**

> 我在项目中负责了日志监控与告警模块的设计和实现。这个模块的背景是，系统上线后缺少可观测性能力——没有结构化日志、没有链路追踪、出了问题只能翻控制台。所以我从零搭建了一套完整的监控告警体系。

**技术深入（按面试官兴趣展开）：**

**Q: 日志是怎么做的？**

> 我用了双 Appender 架构：Console 用 PatternLayout 输出人可读格式给开发看，File 用 LogstashEncoder 输出结构化 JSON 给日志采集系统用。每个请求通过 Servlet Filter 生成 UUID 作为 traceId 存入 MDC，Logback 日志模板里用 `%X{traceId}` 自动注入，这样同一个请求的所有日志都能串起来。

**Q: 告警是怎么实现的？**

> 事件采集有两个入口：全局异常处理器捕获 Controller 层异常，AOP 切面拦截异步任务失败。采集到的事件写入 PostgreSQL。告警引擎是每分钟执行的定时任务，遍历所有启用的规则，用滑动时间窗口查询事件数，超过阈值就触发告警。为了避免告警风暴，加了冷却期机制——触发后一段时间内不再重复告警。

**Q: 怎么保证通知一定能送达？**

> 用了策略模式，NotificationChannel 接口有 Console 和 Webhook 两个实现。Console 始终可用作为兜底，Webhook 通过 RestTemplate 发 HTTP POST。发送失败只打日志不抛异常，不影响告警流程本身。另外会校验钉钉返回的 errcode，确保不是静默失败。

**Q: 有什么设计上的权衡？**

> 告警评估我选了拉模式（定时轮询）而不是推模式（事件驱动），因为场景是低频评估（每分钟一次），推模式需要额外的消息队列基础设施，成本更高。冷却期选了 30 分钟作为默认值，太短会告警风暴，太长会漏掉持续性问题。这些都可以通过 API 运行时调整。

---

## 10. 文件清单

### 后端新增文件

| 文件路径 | 说明 |
|---------|------|
| `common/logging/TraceIdFilter.java` | 链路追踪过滤器 |
| `common/config/MonitorProperties.java` | 监控配置属性 |
| `modules/monitor/model/OperationEventType.java` | 事件类型枚举 |
| `modules/monitor/model/SysOperationLogEntity.java` | 操作日志实体 |
| `modules/monitor/model/SysAlertRuleEntity.java` | 告警规则实体 |
| `modules/monitor/model/SysAlertLogEntity.java` | 告警日志实体 |
| `modules/monitor/model/OperationLogQueryRequest.java` | 日志查询请求 DTO |
| `modules/monitor/model/OperationLogResponse.java` | 日志响应 DTO |
| `modules/monitor/model/AlertRuleRequest.java` | 告警规则请求 DTO |
| `modules/monitor/model/AlertRuleResponse.java` | 告警规则响应 DTO |
| `modules/monitor/model/AlertLogResponse.java` | 告警日志响应 DTO |
| `modules/monitor/model/MonitorStatsResponse.java` | 统计概览 DTO |
| `modules/monitor/repository/SysOperationLogRepository.java` | 操作日志 Repository |
| `modules/monitor/repository/SysAlertRuleRepository.java` | 告警规则 Repository |
| `modules/monitor/repository/SysAlertLogRepository.java` | 告警日志 Repository |
| `modules/monitor/service/OperationLogService.java` | 事件记录服务 |
| `modules/monitor/service/AlertEvaluationService.java` | 告警评估调度 |
| `modules/monitor/service/NotificationService.java` | 通知分发服务 |
| `modules/monitor/aspect/OperationLogAspect.java` | AOP 事件采集切面 |
| `modules/monitor/notification/NotificationChannel.java` | 通知渠道接口 |
| `modules/monitor/notification/AlertEvent.java` | 告警事件记录 |
| `modules/monitor/notification/ConsoleNotificationChannel.java` | 控制台通知 |
| `modules/monitor/notification/WebhookNotificationChannel.java` | Webhook 通知 |
| `modules/monitor/controller/MonitorController.java` | 监控 REST API |
| `modules/monitor/config/MonitorDataInitializer.java` | 默认规则初始化 |
| `modules/monitor/health/AiServiceHealthIndicator.java` | AI 服务健康检查 |

### 后端修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `logback-spring.xml` | 重写为双 Appender 结构化日志配置 |
| `application.yml` | 添加 logging / management / app.monitor 配置 |
| `build.gradle` | 添加 actuator + logstash-logback-encoder 依赖 |
| `gradle/libs.versions.toml` | 添加 logstash-logback 版本声明 |
| `GlobalExceptionHandler.java` | 注入 OperationLogService 记录异常事件 |
| `ErrorCode.java` | 添加 13xxx 监控域错误码 |
| `SecurityProperties.java` | publicPaths 添加 `/actuator/**` |
| `ResumeController.java` | 删除 `/api/resumes/health` stub |

### 前端新增文件

| 文件路径 | 说明 |
|---------|------|
| `frontend/src/types/monitor.ts` | TypeScript 类型定义 |
| `frontend/src/api/monitor.ts` | API 客户端 |
| `frontend/src/pages/MonitorPage.tsx` | 监控主页面 |
| `frontend/src/components/monitor/MonitorOverview.tsx` | 统计概览组件 |
| `frontend/src/components/monitor/OperationLogPanel.tsx` | 操作日志面板 |
| `frontend/src/components/monitor/AlertRulePanel.tsx` | 告警规则面板 |

### 前端修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `frontend/src/App.tsx` | 添加 `/admin/monitor` 路由 |
| `frontend/src/components/Layout.tsx` | 添加监控导航项（ADMIN 可见） |
