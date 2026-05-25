package interview.guide.modules.monitor.service;

import interview.guide.modules.monitor.model.OperationEventType;
import interview.guide.modules.monitor.model.SysOperationLogEntity;
import interview.guide.modules.monitor.repository.SysOperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationLogService {

  private final SysOperationLogRepository repository;

  public void recordEvent(
      OperationEventType eventType,
      String level,
      String source,
      String message,
      Throwable throwable,
      Long userId,
      String metadata) {
    try {
      String stackTrace = null;
      if (throwable != null) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        stackTrace = sw.toString();
        if (stackTrace.length() > 2000) {
          stackTrace = stackTrace.substring(0, 2000);
        }
      }

      String ipAddress = null;
      String traceId = MDC.get("traceId");

      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs != null) {
        HttpServletRequest request = attrs.getRequest();
        ipAddress = getClientIp(request);
      }

      SysOperationLogEntity entity = SysOperationLogEntity.builder()
          .eventType(eventType)
          .level(level)
          .source(source)
          .message(message)
          .stackTrace(stackTrace)
          .userId(userId)
          .ipAddress(ipAddress)
          .traceId(traceId)
          .metadata(metadata)
          .createdAt(LocalDateTime.now())
          .build();

      repository.save(entity);
    } catch (Exception e) {
      log.warn("Failed to record operation log: {}", e.getMessage());
    }
  }

  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip != null && !ip.isBlank()) {
      return ip.split(",")[0].trim();
    }
    ip = request.getHeader("X-Real-IP");
    if (ip != null && !ip.isBlank()) {
      return ip;
    }
    return request.getRemoteAddr();
  }
}
