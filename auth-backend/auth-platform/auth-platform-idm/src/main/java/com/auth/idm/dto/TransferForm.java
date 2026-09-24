package com.auth.idm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 转岗表单（改组织，事件留痕）。
 */
@Data
public class TransferForm {

    /** 目标组织 ID */
    @NotNull(message = "目标组织不能为空")
    private Long targetOrgId;

    /** 转岗原因（写入事件明细） */
    @Size(max = 255, message = "转岗原因长度不能超过 255")
    private String reason;
}
