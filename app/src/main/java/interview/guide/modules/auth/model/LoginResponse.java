package interview.guide.modules.auth.model;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserResponse user
) {
}
