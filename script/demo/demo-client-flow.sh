#!/usr/bin/env bash
# ============================================================
# demo-client-flow.sh —— OIDC 授权码 + PKCE 全流程演示（模拟 demo-client）
#
# 前置：后端已在 8082 运行（仓库根执行
#   java -jar auth-backend/auth-bootstrap/target/auth-bootstrap-1.0.0-SNAPSHOT.jar）
# 种子数据：应用 demo-client（密钥 demo-secret，回调 http://localhost:9090/callback，
#   consent_required=1），admin/123456 已分配并持 demo.admin 标签。
#
# 流程：登录 → 授权（含 consent）→ 授权码换 access/id token（PKCE S256）
#   → userinfo → 登出 → userinfo 应失效
# ============================================================
set -euo pipefail

BASE=http://localhost:8082/api
CLIENT_ID=demo-client
CLIENT_SECRET=demo-secret
REDIRECT_URI=http://localhost:9090/callback
USERNAME=admin
PASSWORD='123456'

step() { echo; echo "===== $* ====="; }

step "1. 统一登录页登录（loginType=OIDC，登录日志单独留痕）"
LOGIN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"loginType\":\"OIDC\",\"clientId\":\"$CLIENT_ID\"}")
TOKEN=$(echo "$LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
echo "管理端 JWT: ${TOKEN:0:40}..."

step "2. 生成 PKCE verifier / challenge（S256）"
VERIFIER=$(python3 -c 'import secrets;print(secrets.token_urlsafe(48))')
CHALLENGE=$(python3 -c "import hashlib,base64;print(base64.urlsafe_b64encode(hashlib.sha256('$VERIFIER'.encode()).digest()).rstrip(b'=').decode())")
echo "verifier=$VERIFIER"

step "3. 授权请求（demo-client 配置了 consent，第一次不带 consent → needConsent）"
curl -s -X POST "$BASE/oauth/authorize" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"clientId\":\"$CLIENT_ID\",\"redirectUri\":\"$REDIRECT_URI\",\"responseType\":\"code\",\"scope\":\"openid profile\",\"state\":\"demo-state\",\"codeChallenge\":\"$CHALLENGE\",\"codeChallengeMethod\":\"S256\"}"
echo

step "4. 用户同意授权（consent=true）→ 拿到回调地址（含 code & state）"
RESP=$(curl -s -X POST "$BASE/oauth/authorize" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"clientId\":\"$CLIENT_ID\",\"redirectUri\":\"$REDIRECT_URI\",\"responseType\":\"code\",\"scope\":\"openid profile\",\"state\":\"demo-state\",\"codeChallenge\":\"$CHALLENGE\",\"codeChallengeMethod\":\"S256\",\"consent\":true}")
echo "$RESP"
REDIRECT=$(echo "$RESP" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["redirectUri"])')
CODE=$(python3 -c "import urllib.parse as u;q=u.urlparse('$REDIRECT');print(dict(u.parse_qsl(q.query))['code'])")
echo "code=$CODE"

step "5. 授权码 + PKCE 换令牌（Basic 认证 demo-client:demo-secret）"
curl -s -X POST "$BASE/oauth/token" -u "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=$CODE" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "code_verifier=$VERIFIER" > /tmp/demo-token.json
python3 - <<'EOF'
import json,base64
d=json.load(open('/tmp/demo-token.json'))
print("expires_in:",d["expires_in"],"scope:",d["scope"])
at=d["access_token"]
print("access_token claims:",json.loads(base64.urlsafe_b64decode(at.split('.')[1]+"==")))
print("id_token 已签发:", "id_token" in d)
EOF
AT=$(python3 -c 'import json;print(json.load(open("/tmp/demo-token.json"))["access_token"])')

step "6. userinfo（RS256 access token）"
curl -s "$BASE/oauth/userinfo" -H "Authorization: Bearer $AT"; echo

step "7. 单点登出（令牌拉黑）"
curl -s -X POST "$BASE/oauth/logout" -H "Authorization: Bearer $AT"; echo

step "8. 登出后 userinfo 应失效"
curl -s "$BASE/oauth/userinfo" -H "Authorization: Bearer $AT"; echo

echo
echo "===== 附：服务间 API（client_credentials，业务系统后端调用） ====="
ST=$(curl -s -X POST "$BASE/oauth/token" -u erp-web:demo-secret --data-urlencode "grant_type=client_credentials" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
echo "service token: ${ST:0:40}..."
curl -s "$BASE/service/apps/erp-web/users" -H "Authorization: Bearer $ST"; echo
echo "全流程演示完成 ✅"
