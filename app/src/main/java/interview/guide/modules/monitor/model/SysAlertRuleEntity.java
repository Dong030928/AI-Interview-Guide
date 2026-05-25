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
@Table(name = "sys_alert_rule")
public class SysAlertRuleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String ruleName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OperationEventType eventType;

  @Column(length = 10)
  private String level;

  @Column(nullable = false)
  private Integer threshold;

  @Column(nullable = false)
  private Integer windowMinutes;

  @Column(nullable = false)
  @Builder.Default
  private Boolean enabled = true;

  @Column(nullable = false, length = 20)
  @Builder.Default
  private String notifyChannel = "CONSOLE";

  @Column(length = 500)
  private String notifyTarget;

  @Column(nullable = false)
  @Builder.Default
  private Integer cooldownMinutes = 30;

  private LocalDateTime lastTriggeredAt;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime updatedAt = LocalDateTime.now();
}
