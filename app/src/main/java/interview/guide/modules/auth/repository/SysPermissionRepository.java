package interview.guide.modules.auth.repository;

import interview.guide.modules.auth.model.SysPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SysPermissionRepository extends JpaRepository<SysPermissionEntity, Long> {

  Optional<SysPermissionEntity> findByPermissionCode(String permissionCode);

  boolean existsByPermissionCode(String permissionCode);
}
