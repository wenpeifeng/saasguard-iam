package com.auth.idm.entity;

import java.util.Set;

/** 组织类型决定新编码前缀；组织关系独立存储，禁止从编码解析层级。 */
public enum OrgType {
    GROUP("GRP"), COMPANY("CO"), FACTORY("FAC"), DEPARTMENT("DEPT"), WORKSHOP("WS"), TEAM("TEAM"), OTHER("ORG");

    private final String prefix;
    OrgType(String prefix) { this.prefix = prefix; }
    /** 新组织编码前缀。 */
    public String prefix() { return prefix; }
    /** 允许作为独立企业的根组织。 */
    public boolean isRootType() { return this == GROUP || this == COMPANY || this == FACTORY || this == OTHER; }
    /** 允许的上下级关系；OTHER 用于兼容尚未分类的历史节点。 */
    public boolean accepts(OrgType child) {
        if (child == GROUP) return false;
        return switch (this) {
            case GROUP, OTHER -> true;
            case COMPANY -> child != COMPANY;
            case FACTORY -> Set.of(DEPARTMENT, WORKSHOP, TEAM, OTHER).contains(child);
            case DEPARTMENT -> Set.of(DEPARTMENT, WORKSHOP, TEAM, OTHER).contains(child);
            case WORKSHOP -> child == TEAM;
            case TEAM -> false;
        };
    }
}
