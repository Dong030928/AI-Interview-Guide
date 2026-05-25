package interview.guide.modules.monitor.repository;

import interview.guide.modules.monitor.model.OperationEventType;
import interview.guide.modules.monitor.model.SysOperationLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SysOperationLogRepository extends JpaRepository<SysOperationLogEntity, Long> {

  long countByEventTypeAndCreatedAtAfter(OperationEventType eventType, LocalDateTime since);

  long countByLevelAndCreatedAtAfter(String level, LocalDateTime since);

  long countByCreatedAtAfter(LocalDateTime since);

  Page<SysOperationLogEntity> findByCreatedAtBetween(
      LocalDateTime start, LocalDateTime end, Pageable pageable);

  Page<SysOperationLogEntity> findByCreatedAtBetweenAndEventType(
      LocalDateTime start, LocalDateTime end, OperationEventType eventType, Pageable pageable);

  Page<SysOperationLogEntity> findByCreatedAtBetweenAndLevel(
      LocalDateTime start, LocalDateTime end, String level, Pageable pageable);

  Page<SysOperationLogEntity> findByCreatedAtBetweenAndEventTypeAndLevel(
      LocalDateTime start, LocalDateTime end, OperationEventType eventType, String level,
      Pageable pageable);

  Page<SysOperationLogEntity> findByMessageContainingIgnoreCase(
      String keyword, Pageable pageable);
}
