package interview.guide.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth.jwt")
public class JwtProperties {

  private String secret = "interview-guide-jwt-secret-change-in-production-at-least-256bits!!";
  private long accessTokenExpiration = 3600000;
  private long refreshTokenExpiration = 604800000;
}
