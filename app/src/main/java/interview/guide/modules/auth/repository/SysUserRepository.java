package interview.guide.modules.auth.repository;

import interview.guide.modules.auth.model.SysUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUserEntity, Long> {

  Optional<SysUserEntity> findByUsername(String username);

  Optional<SysUserEntity> findByEmail(String email);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);
}
