package interview.guide.modules.auth.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "用户名不能为空") @Size(min = 3, max = 50, message = "用户名长度3-50个字符") String username,
    @NotBlank(message = "密码不能为空") @Size(min = 6, max = 100, message = "密码长度6-100个字符") String password,
    @Email(message = "邮箱格式不正确") String email,
    String nickname
) {
}
