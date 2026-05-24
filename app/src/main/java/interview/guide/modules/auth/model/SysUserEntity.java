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
@Table(name = "sys_user", indexes = {
    @Index(name = "idx_sys_user_username", columnList = "username", unique = true),
    @Index(name = "idx_sys_user_email", columnList = "email", unique = true)
})
public class SysUserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String username;

  @Column(nullable = false, length = 100)
  private String password;

  @Column(unique = true, length = 100)
  private String email;

  @Column(length = 50)
  private String nickname;

  @Column(length = 500)
  private String avatarUrl;

  @Column(nullable = false)
  @Builder.Default
  private Boolean enabled = true;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;

  private LocalDateTime lastLoginAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "sys_user_role",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"),
      uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"})
  )
  @Builder.Default
  private Set<SysRoleEntity> roles = new HashSet<>();

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
