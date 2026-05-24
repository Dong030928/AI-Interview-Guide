package interview.guide.modules.auth.controller;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.modules.auth.model.*;
import interview.guide.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "认证授权", description = "登录、注册、Token刷新、登出")
public class AuthController {

  private final AuthService authService;

  @PostMapping("/api/auth/register")
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 5, interval = 60, timeUnit = RateLimit.TimeUnit.MINUTES)
  @Operation(summary = "用户注册")
  public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
    return Result.success(authService.register(request));
  }

  @PostMapping("/api/auth/login")
  @RateLimit(dimension = RateLimit.Dimension.IP, count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
  @Operation(summary = "用户登录")
  public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return Result.success(authService.login(request));
  }

  @PostMapping("/api/auth/refresh")
  @Operation(summary = "刷新Token")
  public Result<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
    return Result.success(authService.refreshToken(request.refreshToken()));
  }

  @PostMapping("/api/auth/logout")
  @Operation(summary = "用户登出")
  public Result<Void> logout(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      authService.logout(authHeader.substring(7));
    }
    return Result.success();
  }

  @GetMapping("/api/auth/me")
  @Operation(summary = "获取当前用户信息")
  public Result<UserResponse> getCurrentUser() {
    Long userId = getCurrentUserId();
    return Result.success(authService.getCurrentUser(userId));
  }

  @PutMapping("/api/auth/password")
  @Operation(summary = "修改密码")
  public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    Long userId = getCurrentUserId();
    authService.changePassword(userId, request);
    return Result.success();
  }

  private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()
        || "anonymousUser".equals(auth.getPrincipal())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    return Long.parseLong(auth.getPrincipal().toString());
  }
}
