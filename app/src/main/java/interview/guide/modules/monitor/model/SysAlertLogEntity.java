package interview.guide.modules.monitor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "sys_alert_log")
public class SysAlertLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long ruleId;

  @Column(nullable = false, length = 100)
  private String ruleName;

  @Column(nullable = false)
  private Integer eventCount;

  @Column(nullable = false)
  @Builder.Default
  private LocalDateTime triggeredAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  private Boolean resolved = false;

  private LocalDateTime resolvedAt;
}
