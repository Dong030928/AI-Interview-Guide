package interview.guide.modules.auth.security;

import interview.guide.common.config.SecurityProperties;
import interview.guide.modules.auth.model.SysPermissionEntity;
import interview.guide.modules.auth.model.SysRoleEntity;
import interview.guide.modules.auth.model.SysUserEntity;
import interview.guide.modules.auth.repository.SysPermissionRepository;
import interview.guide.modules.auth.repository.SysRoleRepository;
import interview.guide.modules.auth.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminDataInitializer implements CommandLineRunner {

  private final SysUserRepository userRepository;
  private final SysRoleRepository roleRepository;
  private final SysPermissionRepository permissionRepository;
  private final PasswordEncoder passwordEncoder;
  private final SecurityProperties securityProperties;

  @Override
  @Transactional
  public void run(String... args) {
    initPermissions();
    initRoles();
    initAdminUser();
  }

  private void initPermissions() {
    List<String> permissionCodes = List.of(
        "resume:read", "resume:write", "resume:delete",
        "interview:create", "interview:read", "interview:delete",
        "knowledgebase:read", "knowledgebase:write", "knowledgebase:delete",
        "schedule:read", "schedule:write", "schedule:delete",
        "voice:read", "voice:write", "voice:delete",
        "llmprovider:read", "llmprovider:write",
        "user:read", "user:write", "user:delete",
        "role:read", "role:write"
    );

    for (String code : permissionCodes) {
      if (!permissionRepository.existsByPermissionCode(code)) {
        var permission = SysPermissionEntity.builder()
            .permissionCode(code)
            .permissionName(code.replace(":", " "))
            .description(code + " permission")
            .build();
        permissionRepository.save(permission);
        log.info("创建权限: {}", code);
      }
    }
  }

  private void initRoles() {
    if (!roleRepository.existsByRoleCode("ROLE_ADMIN")) {
      var allPermissions = new HashSet<>(permissionRepository.findAll());
      var adminRole = SysRoleEntity.builder()
          .roleCode("ROLE_ADMIN")
          .roleName("管理员")
          .description("系统管理员，拥有所有权限")
          .permissions(allPermissions)
          .build();
      roleRepository.save(adminRole);
      log.info("创建角色: ROLE_ADMIN");
    }

    if (!roleRepository.existsByRoleCode("ROLE_USER")) {
      var userRole = SysRoleEntity.builder()
          .roleCode("ROLE_USER")
          .roleName("普通用户")
          .description("普通用户，拥有基本权限")
          .permissions(new HashSet<>())
          .build();
      roleRepository.save(userRole);
      log.info("创建角色: ROLE_USER");
    }
  }

  private void initAdminUser() {
    SecurityProperties.AdminSeed adminConfig = securityProperties.getAdmin();
    if (userRepository.existsByUsername(adminConfig.getUsername())) {
      return;
    }

    var adminRole = roleRepository.findByRoleCode("ROLE_ADMIN")
        .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN 未初始化"));

    var admin = SysUserEntity.builder()
        .username(adminConfig.getUsername())
        .password(passwordEncoder.encode(adminConfig.getPassword()))
        .email(adminConfig.getEmail())
        .nickname("管理员")
        .enabled(true)
        .roles(new HashSet<>(Set.of(adminRole)))
        .build();

    userRepository.save(admin);
    log.info("创建初始管理员账号: {}", adminConfig.getUsername());
  }
}
