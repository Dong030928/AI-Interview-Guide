package interview.guide.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class SecurityProperties {

  private List<String> publicPaths = new ArrayList<>();
  private AdminSeed admin = new AdminSeed();

  @Data
  public static class AdminSeed {
    private String username = "admin";
    private String password = "admin123";
    private String email = "admin@example.com";
  }
}
