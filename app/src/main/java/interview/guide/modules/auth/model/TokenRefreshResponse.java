package interview.guide.modules.auth.model;

public record TokenRefreshResponse(
    String accessToken,
    String refreshToken,
    long expiresIn
) {
}
