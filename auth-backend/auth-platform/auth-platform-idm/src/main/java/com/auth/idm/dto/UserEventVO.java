package com.auth.idm.dto;

import java.time.LocalDateTime;

/** 员工生命周期记录，不暴露密码或内部租户字段。 */
public record UserEventVO(Long id, String eventType, String detail, LocalDateTime createTime) { }
