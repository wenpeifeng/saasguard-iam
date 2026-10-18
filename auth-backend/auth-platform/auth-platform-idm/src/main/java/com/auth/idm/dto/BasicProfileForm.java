package com.auth.idm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工基础档案表单。
 * <p>不包含工号、账号、组织、状态等生命周期字段，供认证中心管理端与
 * 已授权子系统按同一边界维护联系信息。</p>
 */
@Data
public class BasicProfileForm {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64")
    private String realName;

    @Size(max = 64, message = "岗位长度不能超过 64")
    private String position;

    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Size(max = 32, message = "手机号长度不能超过 32")
    private String phone;
}
