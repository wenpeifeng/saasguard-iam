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
    @NotBlank(message = "新口令不能为空")
    @Size(min = 8, max = 64, message = "口令长度须为 8-64 位")
    private String password;
}
