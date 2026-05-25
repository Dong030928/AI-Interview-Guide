package interview.guide.modules.monitor.repository;

import interview.guide.modules.monitor.model.SysAlertLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SysAlertLogRepository extends JpaRepository<SysAlertLogEntity, Long> {

  Page<SysAlertLogEntity> findByTriggeredAtBetween(
      LocalDateTime start, LocalDateTime end, Pageable pageable);

  long countByTriggeredAtAfter(LocalDateTime since);
}
