package interview.guide.modules.auth.model;

import java.util.Set;

public record UserResponse(
    Long id,
    String username,
    String email,
    String nickname,
    String avatarUrl,
    Set<String> roles,
    Set<String> permissions
) {
}
