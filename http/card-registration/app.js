/* SDK의 customer.customerId가 REST 조회 응답의 customer.id에 대응한다. */
"use strict";
// 브라우저 SDK용 상점·채널 식별자. API Secret과 웹훅 시크릿은 서버에서만 관리한다.
const PORTONE_CONFIG = Object.freeze({
  storeId: "store-fbb356be-457b-4d16-be79-bf007aedf6d6",
  channelKey: "channel-key-b9cf4e55-1656-4f0b-b83c-d10fa5e27b6a"
});
const $ = (id) => document.getElementById(id);
let user = null;
let pending = null;
let busy = false;
const attemptKey = "portone-card-attempt";
const status = (message) => { $("status").textContent = message; };

function refresh() {
  $("start").disabled = busy || !user || !!pending;
  $("authenticate").disabled = busy;
  $("retry").disabled = busy || !user;
  $("retry").hidden = !pending;
  for (const id of ["token"]) $(id).disabled = busy;
}

async function api(path, body) {
  const response = await fetch(path, {
    method: body ? "POST" : "GET",
    headers: { Authorization: `Bearer ${$("token").value.trim().replace(/^Bearer\s+/i, "")}`,
      ...(body ? { "Content-Type": "application/json" } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(70000),
    cache: "no-store"
  });
  if (!response.ok) throw new Error(`서버 요청 실패 (HTTP ${response.status}). 로그인 토큰과 서버 로그를 확인해주세요.`);
  return response.json();
}

$("token").addEventListener("input", () => {
  user = null;
  $("identity").textContent = "로그인 사용자를 다시 확인해주세요.";
  refresh();
});
$("authenticate").addEventListener("click", async () => {
  busy = true; refresh();
  try {
    user = null;
    if (!$("token").value.trim()) throw new Error("로그인 액세스 토큰을 입력해주세요.");
    const profile = await api("/api/v1/local/users/billing-customer");
    if (!profile.uuid) throw new Error("사용자 식별자를 확인할 수 없습니다.");
    if (![profile.fullName, profile.phoneNumber, profile.email].every(value => typeof value === "string" && value.trim())) throw new Error("회원 프로필에 이름·전화번호·이메일이 필요합니다.");
    user = profile;
    $("identity").textContent = `${profile.name} 님으로 등록합니다.`;
    status(pending ? "발급된 카드가 있습니다. 저장 재시도를 눌러주세요." : "카드 등록 창을 열 수 있습니다.");
  } catch (error) { status(error.message); }
  finally { busy = false; refresh(); }
});

async function saveCard() {
  if (!pending || !user || pending.owner !== user.uuid) throw new Error("빌링키를 발급받은 계정으로 로그인해주세요.");
  status("발급된 카드를 서버에서 검증하고 저장하고 있습니다.");
  const card = await api("/api/v1/payments/methods", { billingKey: pending.billingKey });
  pending = null;
  sessionStorage.removeItem(attemptKey);
  $("card").textContent = `${card.label || "등록된 카드"} · ${card.brand || "브랜드 미제공"}\n${card.maskedNumber || "카드번호 미제공"}\n결제수단 ID: ${card.id}`;
  $("result").hidden = false;
  status("카드 등록이 완료되었습니다. 결제는 실행하지 않았습니다.");
}

$("register").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (busy || !user || pending) return;
  busy = true; refresh(); $("result").hidden = true;
  try {
    const { storeId, channelKey } = PORTONE_CONFIG;
    if (!window.PortOne) throw new Error("포트원 SDK를 불러오지 못했습니다. 네트워크 연결 후 새로고침해주세요.");
    const state = crypto.randomUUID();
    sessionStorage.setItem(attemptKey, JSON.stringify({ state, owner: user.uuid, created: Date.now() }));
    status("포트원·PG 창에서 카드 등록을 진행해주세요.");
    const response = await window.PortOne.requestIssueBillingKey({
      storeId, channelKey, billingKeyMethod: "CARD",
      issueId: crypto.randomUUID(), issueName: "모여타 카드 등록",
      customer: { customerId: user.uuid, fullName: user.fullName,
        phoneNumber: user.phoneNumber, email: user.email },
      redirectUrl: `${location.origin}/?state=${encodeURIComponent(state)}`
    });
    if (!response) { status("카드 등록 창이 닫혔거나 다른 화면으로 이동 중입니다."); return; }
    if (response.code) throw new Error("카드 발급이 취소되었거나 실패했습니다. 입력 정보와 PG 채널 설정을 확인해주세요.");
    if (!response.billingKey || response.billingKey === "NEEDS_CONFIRMATION") throw new Error("사용 가능한 빌링키가 없습니다. 수동 승인 채널은 이 화면에서 지원하지 않습니다.");
    pending = { billingKey: response.billingKey, owner: user.uuid };
    await saveCard();
  } catch (error) { status(`${error.message}${pending ? " 발급은 완료됐으므로 다시 발급하지 말고 저장 재시도를 눌러주세요." : ""}`); }
  finally { busy = false; refresh(); }
});
$("retry").addEventListener("click", async () => {
  if (busy) return;
  busy = true; refresh();
  try { await saveCard(); } catch (error) { status(error.message); }
  finally { busy = false; refresh(); }
});

// 모바일 등 리디렉션 방식: URL에서 키를 제거하고 메모리에서만 보관한다.
const query = new URLSearchParams(location.search);
if (query.has("billingKey") || query.has("code")) {
  history.replaceState(null, "", location.pathname);
  try {
    const attempt = JSON.parse(sessionStorage.getItem(attemptKey) || "null");
    if (!attempt || attempt.state !== query.get("state") || Date.now() - attempt.created > 30 * 60 * 1000) throw new Error("카드 등록 요청을 확인할 수 없습니다. 같은 브라우저에서 다시 시작해주세요.");
    if (query.get("code")) throw new Error("카드 발급이 취소되었거나 실패했습니다.");
    const billingKey = query.get("billingKey");
    if (!billingKey || billingKey === "NEEDS_CONFIRMATION") throw new Error("사용 가능한 빌링키가 없습니다.");
    pending = { billingKey, owner: attempt.owner };
    status("빌링키 발급이 완료되었습니다. 같은 계정의 로그인 토큰을 입력하고 사용자 확인 후 저장해주세요.");
  } catch (error) { status(error.message); }
}
refresh();
