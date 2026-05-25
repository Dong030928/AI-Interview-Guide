package interview.guide.modules.monitor.repository;

import interview.guide.modules.monitor.model.SysAlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysAlertRuleRepository extends JpaRepository<SysAlertRuleEntity, Long> {

  List<SysAlertRuleEntity> findAllByEnabledTrue();

  boolean existsByRuleName(String ruleName);
}
