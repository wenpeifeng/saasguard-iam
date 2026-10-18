package com.auth.idm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置口令表单。
 */
@Data
public class ResetPwdForm {

    /** 新口令（BCrypt 加密后入库） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须为 6-64 位")
    private String password;
}
