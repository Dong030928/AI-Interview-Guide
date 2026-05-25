package interview.guide.modules.monitor.controller;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.modules.monitor.model.AlertLogResponse;
import interview.guide.modules.monitor.model.AlertRuleRequest;
import interview.guide.modules.monitor.model.AlertRuleResponse;
import interview.guide.modules.monitor.model.MonitorStatsResponse;
import interview.guide.modules.monitor.model.OperationEventType;
import interview.guide.modules.monitor.model.OperationLogQueryRequest;
import interview.guide.modules.monitor.model.OperationLogResponse;
import interview.guide.modules.monitor.model.SysAlertLogEntity;
import interview.guide.modules.monitor.model.SysAlertRuleEntity;
import interview.guide.modules.monitor.repository.SysAlertLogRepository;
import interview.guide.modules.monitor.repository.SysAlertRuleRepository;
import interview.guide.modules.monitor.repository.SysOperationLogRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MonitorController {

  private final SysOperationLogRepository operationLogRepository;
  private final SysAlertRuleRepository alertRuleRepository;
  private final SysAlertLogRepository alertLogRepository;

  @GetMapping("/api/monitor/logs")
  public Result<Page<OperationLogResponse>> getOperationLogs(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) OperationEventType eventType,
      @RequestParam(required = false) String level,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate,
      @RequestParam(required = false) String keyword) {

    LocalDateTime start = startDate != null
        ? startDate.atStartOfDay()
        : LocalDate.now().minusDays(7).atStartOfDay();
    LocalDateTime end = endDate != null
        ? endDate.atTime(LocalTime.MAX)
        : LocalDateTime.now();

    PageRequest pageRequest = PageRequest.of(
        Math.max(0, page - 1), Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

    Page<OperationLogResponse> result;
    if (eventType != null && level != null && !level.isBlank()) {
      result = operationLogRepository
          .findByCreatedAtBetweenAndEventTypeAndLevel(start, end, eventType, level, pageRequest)
          .map(OperationLogResponse::from);
    } else if (eventType != null) {
      result = operationLogRepository
          .findByCreatedAtBetweenAndEventType(start, end, eventType, pageRequest)
          .map(OperationLogResponse::from);
    } else if (level != null && !level.isBlank()) {
      result = operationLogRepository
          .findByCreatedAtBetweenAndLevel(start, end, level, pageRequest)
          .map(OperationLogResponse::from);
    } else if (keyword != null && !keyword.isBlank()) {
      result = operationLogRepository
          .findByMessageContainingIgnoreCase(keyword, pageRequest)
          .map(OperationLogResponse::from);
    } else {
      result = operationLogRepository
          .findByCreatedAtBetween(start, end, pageRequest)
          .map(OperationLogResponse::from);
    }

    return Result.success(result);
  }

  @GetMapping("/api/monitor/stats")
  public Result<MonitorStatsResponse> getStats() {
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    long totalLogs = operationLogRepository.countByCreatedAtAfter(todayStart);
    long errorCount = operationLogRepository.countByLevelAndCreatedAtAfter("ERROR", todayStart);
    long alertCount = alertLogRepository.countByTriggeredAtAfter(todayStart);
    long activeRules = alertRuleRepository.findAllByEnabledTrue().size();

    return Result.success(
        new MonitorStatsResponse(totalLogs, errorCount, alertCount, activeRules));
  }

  @GetMapping("/api/monitor/alerts/rules")
  public Result<List<AlertRuleResponse>> getAlertRules() {
    List<AlertRuleResponse> rules = alertRuleRepository.findAll().stream()
        .map(AlertRuleResponse::from)
        .toList();
    return Result.success(rules);
  }

  @PostMapping("/api/monitor/alerts/rules")
  public Result<AlertRuleResponse> createAlertRule(@Valid @RequestBody AlertRuleRequest request) {
    if (alertRuleRepository.existsByRuleName(request.ruleName())) {
      throw new BusinessException(ErrorCode.MONITOR_RULE_ALREADY_EXISTS);
    }

    SysAlertRuleEntity rule = SysAlertRuleEntity.builder()
        .ruleName(request.ruleName())
        .eventType(request.eventType())
        .level(request.level())
        .threshold(request.threshold())
        .windowMinutes(request.windowMinutes())
        .enabled(true)
        .notifyChannel(request.notifyChannel())
        .notifyTarget(request.notifyTarget())
        .cooldownMinutes(request.cooldownMinutes())
        .build();

    return Result.success(AlertRuleResponse.from(alertRuleRepository.save(rule)));
  }

  @PutMapping("/api/monitor/alerts/rules/{id}")
  public Result<AlertRuleResponse> updateAlertRule(
      @PathVariable Long id, @Valid @RequestBody AlertRuleRequest request) {
    SysAlertRuleEntity rule = alertRuleRepository.findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.MONITOR_RULE_NOT_FOUND));

    rule.setRuleName(request.ruleName());
    rule.setEventType(request.eventType());
    rule.setLevel(request.level());
    rule.setThreshold(request.threshold());
    rule.setWindowMinutes(request.windowMinutes());
    rule.setNotifyChannel(request.notifyChannel());
    rule.setNotifyTarget(request.notifyTarget());
    rule.setCooldownMinutes(request.cooldownMinutes());
    rule.setUpdatedAt(LocalDateTime.now());

    return Result.success(AlertRuleResponse.from(alertRuleRepository.save(rule)));
  }

  @PatchMapping("/api/monitor/alerts/rules/{id}/toggle")
  public Result<AlertRuleResponse> toggleAlertRule(@PathVariable Long id) {
    SysAlertRuleEntity rule = alertRuleRepository.findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.MONITOR_RULE_NOT_FOUND));

    rule.setEnabled(!rule.getEnabled());
    rule.setUpdatedAt(LocalDateTime.now());

    return Result.success(AlertRuleResponse.from(alertRuleRepository.save(rule)));
  }

  @GetMapping("/api/monitor/alerts/history")
  public Result<Page<AlertLogResponse>> getAlertHistory(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate) {

    LocalDateTime start = startDate != null
        ? startDate.atStartOfDay()
        : LocalDate.now().minusDays(30).atStartOfDay();
    LocalDateTime end = endDate != null
        ? endDate.atTime(LocalTime.MAX)
        : LocalDateTime.now();

    PageRequest pageRequest = PageRequest.of(
        Math.max(0, page - 1), Math.min(size, 100), Sort.by(Sort.Direction.DESC, "triggeredAt"));

    Page<AlertLogResponse> result = alertLogRepository
        .findByTriggeredAtBetween(start, end, pageRequest)
        .map(AlertLogResponse::from);

    return Result.success(result);
  }
}
