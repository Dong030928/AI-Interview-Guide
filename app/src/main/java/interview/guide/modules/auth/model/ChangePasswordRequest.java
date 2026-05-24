package interview.guide.modules.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "旧密码不能为空") String oldPassword,
    @NotBlank(message = "新密码不能为空") @Size(min = 6, max = 100, message = "新密码长度6-100个字符") String newPassword
) {
}
