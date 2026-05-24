package interview.guide.modules.auth.repository;

import interview.guide.modules.auth.model.SysRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SysRoleRepository extends JpaRepository<SysRoleEntity, Long> {

  Optional<SysRoleEntity> findByRoleCode(String roleCode);

  boolean existsByRoleCode(String roleCode);
}
