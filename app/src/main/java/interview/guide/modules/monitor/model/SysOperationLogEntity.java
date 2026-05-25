package interview.guide.modules.monitor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_operation_log")
public class SysOperationLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OperationEventType eventType;

  @Column(nullable = false, length = 10)
  private String level;

  @Column(length = 200)
  private String source;

  @Column(length = 2000)
  private String message;

  @Column(columnDefinition = "TEXT")
  private String stackTrace;

  private Long userId;

  @Column(length = 50)
  private String ipAddress;

  @Column(length = 64)
  private String traceId;

  @Column(columnDefinition = "TEXT")
  private String metadata;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
