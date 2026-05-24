package interview.guide.modules.auth.model;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
    @NotBlank(message = "refreshToken不能为空") String refreshToken
) {
}
