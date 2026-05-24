package interview.guide.modules.auth.service;

import interview.guide.common.config.JwtProperties;
import interview.guide.infrastructure.redis.RedisService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

  private final JwtProperties jwtProperties;
  private final RedisService redisService;

  private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
  private static final String REFRESH_TOKEN_PREFIX = "jwt:refresh:";

  public String generateAccessToken(Long userId, String username, Set<String> roles) {
    Date now = new Date();
    Date expiration = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

    return Jwts.builder()
        .subject(userId.toString())
        .claim("username", username)
        .claim("roles", roles)
        .id(UUID.randomUUID().toString())
        .issuedAt(now)
        .expiration(expiration)
        .signWith(getSigningKey())
        .compact();
  }

  public String generateRefreshToken(Long userId) {
    Date now = new Date();
    Date expiration = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration());
    String jti = UUID.randomUUID().toString();

    String token = Jwts.builder()
        .subject(userId.toString())
        .claim("type", "refresh")
        .id(jti)
        .issuedAt(now)
        .expiration(expiration)
        .signWith(getSigningKey())
        .compact();

    redisService.set(
        REFRESH_TOKEN_PREFIX + userId,
        jti,
        Duration.ofMillis(jwtProperties.getRefreshTokenExpiration())
    );

    return token;
  }

  public Claims parseToken(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public boolean isTokenValid(String token) {
    try {
      Claims claims = parseToken(token);
      String jti = claims.getId();
      return !isTokenBlacklisted(jti);
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("Token验证失败: {}", e.getMessage());
      return false;
    }
  }

  public boolean isTokenBlacklisted(String jti) {
    return redisService.exists(BLACKLIST_PREFIX + jti);
  }

  public void blacklistToken(String jti, long remainingMs) {
    if (remainingMs > 0) {
      redisService.set(BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(remainingMs));
    }
  }

  public void invalidateRefreshToken(Long userId) {
    redisService.delete(REFRESH_TOKEN_PREFIX + userId);
  }

  public boolean isRefreshTokenValid(Long userId, String jti) {
    String storedJti = redisService.get(REFRESH_TOKEN_PREFIX + userId);
    return jti.equals(storedJti);
  }

  public long getRemainingExpiration(Claims claims) {
    Date expiration = claims.getExpiration();
    return Math.max(0, expiration.getTime() - System.currentTimeMillis());
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      keyBytes = digest.digest(keyBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256不可用", e);
    }
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
