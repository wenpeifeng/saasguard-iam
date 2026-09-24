package com.auth.idm.service;

import com.auth.common.result.PageResult;
import com.auth.idm.dto.ResetPwdForm;
import com.auth.idm.dto.TransferForm;
import com.auth.idm.dto.UserForm;
import com.auth.idm.dto.UserQuery;
import com.auth.idm.dto.UserVO;

/**
 * 主账号服务 —— 入职建号 / 转岗 / 离职 / 全局停用恢复 / 口令重置。
 * <p>
 * 生命周期状态机约束（全部收口在本服务，业务系统只能经服务间 API 间接影响）：
 * <ul>
 *   <li>离职是最强状态、是终态：离职后一切应用级启用/禁用操作被中心接口拒绝；</li>
 *   <li>转岗仅对在职员工可操作；</li>
 *   <li>全局停用可恢复在职；离职不可逆。</li>
 * </ul>
 */
public interface IdmUserService {

    /**
     * 主账号分页查询。
     *
     * @param query 查询条件（关键字/组织/状态 + 分页排序）
     * @return 分页结果
     */
    PageResult<UserVO> page(UserQuery query);

    /**
     * 用户详情。
     *
     * @param id 用户 ID
     * @return 用户 VO
     */
    UserVO get(Long id);

    /**
     * 入职建号。
     *
     * @param form 建号表单
     * @return 新用户 ID
     * @throws com.auth.common.exception.BusinessException 工号已存在 / 组织不存在或停用
     */
    Long create(UserForm form);

    /**
     * 修改基本信息（姓名/组织外的档案字段；工号不可改）。
     *
     * @param id   用户 ID
     * @param form 建号表单（employeeNo 被忽略）
     */
    void update(Long id, UserForm form);

    /**
     * 转岗：修改组织并写事件流水。
     *
     * @param id   用户 ID
     * @param form 转岗表单
     * @throws com.auth.common.exception.BusinessException 用户非在职 / 目标组织不存在或停用
     */
    void transfer(Long id, TransferForm form);

    /**
     * 离职：全局状态置为离职（终态），写事件流水，并下线全部管理端会话。
     *
     * @param id 用户 ID
     * @throws com.auth.common.exception.BusinessException 用户已离职
     */
    void leave(Long id);

    /**
     * 全局状态变更：在职 ⇄ 全局停用（离职终态不可达）。
     *
     * @param id     用户 ID
     * @param status 目标状态（1-在职 3-全局停用）
     * @throws com.auth.common.exception.BusinessException 非法状态迁移 / 用户已离职
     */
    void changeStatus(Long id, Integer status);

    /**
     * 重置口令。
     *
     * @param id   用户 ID
     * @param form 新口令表单
     * @throws com.auth.common.exception.BusinessException 用户已离职
     */
    void resetPassword(Long id, ResetPwdForm form);
}
