#!/usr/bin/env bash
# ============================================================
# seed-demo-data.sh —— 演示数据种子（经真实 API 写入，连带产生
# 生命周期事件流水 + 登录/操作审计日志，比裸 SQL 更贴近真实运行态）。
#
# 前置：后端已运行（localhost:8082），种子超管 admin/123456 未改密。
# 可重复执行：工号已存在会报错跳过该用户（幂等性靠工号唯一键）。
#
# 演示密码统一：User@123456
# 覆盖场景：在职（普通/系统管理员标签）/ 离职终态 / 全局停用 / 应用级禁用（ERP 管理端视角）
# ============================================================
set -uo pipefail

BASE=http://localhost:8082/api
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
[ -n "$TOKEN" ] || { echo "登录失败：请确认后端已启动且种子超管口令未改"; exit 1; }
AUTH="Authorization: Bearer $TOKEN"

step() { echo; echo "===== $* ====="; }

# 组织：集团总部(id=1) > 华东制造基地/华南制造基地 > 车间
step "1. 组织架构"
org() { # name code parent -> orgId（重名则查询已有）
  local resp
  resp=$(curl -s -X POST "$BASE/idm/orgs" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"code\":\"$2\",\"name\":\"$1\",\"parentId\":$3}")
  local id=$(echo "$resp" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["data"] or "")')
  if [ -z "$id" ]; then
    id=$(curl -s "$BASE/idm/orgs/tree" -H "$AUTH" | python3 -c "
import sys,json
def walk(ns):
    for n in ns:
        if n['name']=='$1': print(n['id']); return True
        if walk(n.get('children') or []): return True
    return False
walk(json.load(sys.stdin)['data'])")
  fi
  echo "$id"
}
EAST=$(org 华东制造基地 ORG-EAST 1)
SOUTH=$(org 华南制造基地 ORG-SOUTH 1)
WS1=$(org 总装车间 ORG-EAST-ASSY "$EAST")
WS2=$(org 注塑车间 ORG-EAST-INJ "$EAST")
WS3=$(org 包装车间 ORG-SOUTH-PKG "$SOUTH")
echo "华东=$EAST 华南=$SOUTH 车间=$WS1/$WS2/$WS3"

step "2. 建号（统一口令 User@123456）"
mkuser() { # employeeNo realName orgId position -> userId
  local resp
  resp=$(curl -s -X POST "$BASE/idm/users" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"employeeNo\":\"$1\",\"realName\":\"$2\",\"orgId\":$3,\"position\":\"$4\",\"password\":\"User@123456\",\"entryDate\":\"2026-01-15\"}")
  local id=$(echo "$resp" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["data"] or "")')
  if [ -z "$id" ]; then
    id=$(curl -s "$BASE/idm/users?keyword=$1" -H "$AUTH" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["records"][0]["id"])')
    echo "  $1 已存在，复用 id=$id" >&2
  fi
  echo "$id"
}
U1=$(mkuser E2001 王建国 "$WS1" 计划员)
U2=$(mkuser E2002 陈国栋 "$WS2" 报工员)
U3=$(mkuser E2003 刘美玲 "$WS3" 车间主任)
U4=$(mkuser E2004 赵春霞 1 财务主管)
U5=$(mkuser E2005 钱有德 "$EAST" 质检员)
U6=$(mkuser E2006 孙丽华 "$SOUTH" 仓管员)
U7=$(mkuser E2007 周大军 "$WS1" 操作员)
U8=$(mkuser E2008 吴晓东 1 IT运维)
echo "用户ID: $U1 $U2 $U3 $U4 $U5 $U6 $U7 $U8"

step "3. 应用分配 + 角色标签"
assign() { curl -s -X POST "$BASE/apps/$1/assignments" -H "$AUTH" -H 'Content-Type: application/json' -d "{\"userIds\":[$2]}" >/dev/null; }
role()   { curl -s -X PUT "$BASE/apps/$1/assignments/$2/roles/$3" -H "$AUTH" >/dev/null; }
assign erp-web "$U1"; assign erp-web "$U4"; role erp-web "$U4" erp.admin; assign erp-web "$U7"
assign mes-web "$U1"; assign mes-web "$U2"; assign mes-web "$U3"; role mes-web "$U3" mes.admin; assign mes-web "$U6"
assign demo-client "$U8"; role demo-client "$U8" demo.admin
echo "分配完成（E2004=erp.admin，E2003=mes.admin，E2008=demo.admin）"

step "4. 状态覆盖场景"
# 离职（终态）：E2005
curl -s -X PUT "$BASE/idm/users/$U5/leave" -H "$AUTH" >/dev/null && echo "E2005 钱有德 → 离职"
# 全局停用：E2006
curl -s -X PUT "$BASE/idm/users/$U6/status/3" -H "$AUTH" >/dev/null && echo "E2006 孙丽华 → 全局停用"
# 应用级禁用（模拟 ERP 管理端经服务间 API 操作）：E2007
ST=$(curl -s -X POST "$BASE/oauth/token" -u erp-web:demo-secret \
  --data-urlencode "grant_type=client_credentials" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
curl -s -X PUT "$BASE/service/apps/erp-web/users/E2007/status" \
  -H "Authorization: Bearer $ST" -H 'Content-Type: application/json' \
  -d '{"enabled":false}' >/dev/null && echo "E2007 周大军 → ERP 应用级禁用（服务间 API 演示）"

step "5. 制造几条登录审计日志（成功/失败各一）"
curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"E2001","password":"User@123456","loginType":"OIDC","clientId":"erp-web"}' >/dev/null
curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"E2005","password":"User@123456"}' >/dev/null
echo "完成（E2001 成功登录 + E2005 离职拒绝登录，均已落审计）"

step "6. 结果核对"
echo "--- 用户（工号/姓名/状态/组织）---"
curl -s "$BASE/idm/users?pageSize=20" -H "$AUTH" | python3 -c '
import sys,json
M={1:"在职",2:"离职",3:"全局停用"}
for r in json.load(sys.stdin)["data"]["records"]:
    print("  %-7s %-5s %-6s %s" % (r["employeeNo"], r["realName"], M.get(r["status"], r["status"]), r["orgName"] or "-"))'
echo "--- erp-web 同步视图（服务间视角）---"
curl -s "$BASE/service/apps/erp-web/users" -H "Authorization: Bearer $ST" | python3 -c '
import sys,json
for u in json.load(sys.stdin)["data"]:
    print("  %-7s %-5s active=%-5s %-12s roles=%s" % (u["employeeNo"], u["realName"], u["active"], u["inactiveReason"] or "", u["roles"]))'
echo
echo "演示数据就绪 ✅  统一口令：User@123456（admin 除外）"
