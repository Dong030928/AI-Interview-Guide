package interview.guide.modules.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_role", indexes = {
    @Index(name = "idx_sys_role_code", columnList = "roleCode", unique = true)
})
public class SysRoleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String roleCode;

  @Column(length = 100)
  private String roleName;

  @Column(length = 255)
  private String description;

  @Column(nullable = false)
  @Builder.Default
  private Boolean enabled = true;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "sys_role_permission",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"),
      uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "permission_id"})
  )
  @Builder.Default
  private Set<SysPermissionEntity> permissions = new HashSet<>();

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
