package interview.guide.modules.auth.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.auth.model.*;
import interview.guide.modules.auth.repository.SysRoleRepository;
import interview.guide.modules.auth.repository.SysUserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final SysUserRepository userRepository;
  private final SysRoleRepository roleRepository;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public LoginResponse register(RegisterRequest request) {
    if (userRepository.existsByUsername(request.username())) {
      throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "用户名已存在");
    }
    if (request.email() != null && !request.email().isEmpty()
        && userRepository.existsByEmail(request.email())) {
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "邮箱已被注册");
    }

    var defaultRole = roleRepository.findByRoleCode("ROLE_USER")
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "默认角色未初始化"));

    var user = SysUserEntity.builder()
        .username(request.username())
        .password(passwordEncoder.encode(request.password()))
        .email(request.email())
        .nickname(request.nickname())
        .enabled(true)
        .roles(new HashSet<>(Set.of(defaultRole)))
        .build();

    user = userRepository.save(user);

    Set<String> roles = user.getRoles().stream()
        .map(r -> r.getRoleCode())
        .collect(Collectors.toSet());

    String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
    String refreshToken = jwtService.generateRefreshToken(user.getId());

    return new LoginResponse(
        accessToken,
        refreshToken,
        3600000,
        toUserResponse(user)
    );
  }

  public LoginResponse login(LoginRequest request) {
    var user = userRepository.findByUsername(request.username())
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误"));

    if (!user.getEnabled()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
    }

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
    }

    user.setLastLoginAt(LocalDateTime.now());
    userRepository.save(user);

    Set<String> roles = user.getRoles().stream()
        .map(r -> r.getRoleCode())
        .collect(Collectors.toSet());

    String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
    String refreshToken = jwtService.generateRefreshToken(user.getId());

    return new LoginResponse(
        accessToken,
        refreshToken,
        3600000,
        toUserResponse(user)
    );
  }

  public TokenRefreshResponse refreshToken(String refreshToken) {
    try {
      Claims claims = jwtService.parseToken(refreshToken);
      String userId = claims.getSubject();
      String jti = claims.getId();

      if (!jwtService.isRefreshTokenValid(Long.parseLong(userId), jti)) {
        throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "刷新Token无效或已过期");
      }

      var user = userRepository.findById(Long.parseLong(userId))
          .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));

      Set<String> roles = user.getRoles().stream()
          .map(r -> r.getRoleCode())
          .collect(Collectors.toSet());

      String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
      String newRefreshToken = jwtService.generateRefreshToken(user.getId());

      return new TokenRefreshResponse(
          newAccessToken,
          newRefreshToken,
          3600000
      );
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "刷新Token无效或已过期");
    }
  }

  public void logout(String accessToken) {
    try {
      Claims claims = jwtService.parseToken(accessToken);
      String jti = claims.getId();
      long remainingMs = jwtService.getRemainingExpiration(claims);

      jwtService.blacklistToken(jti, remainingMs);

      String userId = claims.getSubject();
      jwtService.invalidateRefreshToken(Long.parseLong(userId));
    } catch (Exception e) {
      log.debug("登出时token解析失败: {}", e.getMessage());
    }
  }

  public UserResponse getCurrentUser(Long userId) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));
    return toUserResponse(user);
  }

  @Transactional
  public void changePassword(Long userId, ChangePasswordRequest request) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));

    if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.PASSWORD_MISMATCH, "旧密码不正确");
    }

    user.setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
  }

  private UserResponse toUserResponse(SysUserEntity user) {
    Set<String> roles = user.getRoles().stream()
        .map(r -> r.getRoleCode())
        .collect(Collectors.toSet());

    Set<String> permissions = user.getRoles().stream()
        .flatMap(r -> r.getPermissions().stream())
        .map(p -> p.getPermissionCode())
        .collect(Collectors.toSet());

    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getNickname(),
        user.getAvatarUrl(),
        roles,
        permissions
    );
  }
}
