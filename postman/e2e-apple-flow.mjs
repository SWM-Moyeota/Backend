// 실행 중인 로컬 백엔드와 로컬 Apple 모의 서버를 연결한 종단 점검.
// 실제 Apple 계정 인증을 증명하지 않는다. 운영·개발 서버를 대상으로 실행하지 않는다.
// 준비: postman/README.md의 "백엔드 연결 종단 점검" 절 참고. 연결 SMS 쿨다운(1분) 대기 때문에 약 2분 걸린다.
const API=process.env.API_URL || 'http://127.0.0.1:18080/api/v1', MOCK=process.env.APPLE_MOCK_URL || 'http://127.0.0.1:4517/apple';
async function call(base,method,path,body,token){
  const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});
  const t=await r.text(); let j=null; try{j=t?JSON.parse(t):null}catch{j=t}
  return {status:r.status,body:j};
}
const results=[];
function check(name,cond,detail){results.push((cond?'통과':'실패')+' | '+name+(cond?'':' | '+JSON.stringify(detail)));}
function sub(jwt){if(!jwt)return null;return JSON.parse(Buffer.from(jwt.split('.')[1],'base64url')).sub;}
async function appleCreds(chPath,token,subject,email){
  const ch=await call(API,'POST',chPath,undefined,token);
  const id=await call(MOCK,'POST','/__test/identity',{nonce:ch.body.nonce,subject,email});
  return {ch,identityToken:id.body.identityToken,authorizationCode:id.body.authorizationCode,challengeToken:ch.body.challengeToken};
}
const uniq=Date.now().toString().slice(-7), subject='e2e-apple-'+uniq, phone='0109'+uniq;
await call(MOCK,'POST','/__test/mode',{mode:'healthy'});
// 1. 신규 Apple 로그인 → 가입 필요
let c=await appleCreds('/auth/apple/challenge',null,subject);
check('challenge 발급(200, 300초)',c.ch.status===200&&c.ch.body.expiresInSeconds===300,c.ch);
let login=await call(API,'POST','/auth/login/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken,name:'홍길동'});
check('신규 Apple 로그인 → SIGNUP_REQUIRED',login.status===200&&login.body.status==='SIGNUP_REQUIRED'&&!!login.body.signupToken,login);
// 2. challenge 재사용 거절
let replay=await call(API,'POST','/auth/login/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken});
check('challenge 재사용 → 401 USER407',replay.status===401&&replay.body.code==='USER407',replay);
// 3. nonce 불일치 거절
let c2=await appleCreds('/auth/apple/challenge',null,subject); const other=await call(MOCK,'POST','/__test/identity',{nonce:'wrong-nonce',subject});
let bad=await call(API,'POST','/auth/login/apple',{identityToken:other.body.identityToken,authorizationCode:other.body.authorizationCode,challengeToken:c2.challengeToken});
check('nonce 불일치 → 401 USER401',bad.status===401&&bad.body.code==='USER401',bad);
// 4. SMS 인증 후 가입
await call(API,'POST','/auth/sms/send',{phoneNumber:phone});
const v=await call(API,'POST','/auth/sms/verify',{phoneNumber:phone,code:'000000'});
const reg=await call(API,'POST','/auth/social/register',{signupToken:login.body.signupToken,verificationToken:v.body.verificationToken,phoneNumber:phone,nickname:'종단'+uniq.slice(-5),name:'홍길동',birthDate:'1995-01-01T00:00:00Z',gender:'UNDISCLOSED'});
check('소셜 가입 → 201 토큰',reg.status===201&&!!reg.body.accessToken,reg);
const access=reg.body.accessToken, publicId=sub(access);
// 5. 로그인 수단 조회
let m=await call(API,'GET','/users/me/login-methods',undefined,access);
check('로그인 수단: hasPassword=false, APPLE ACTIVE',m.status===200&&m.body.hasPassword===false&&m.body.methods.length===1&&m.body.methods[0].provider==='APPLE'&&m.body.methods[0].status==='ACTIVE',m);
// 6. 같은 Apple 계정 재로그인 → 같은 회원
c=await appleCreds('/auth/apple/challenge',null,subject);
login=await call(API,'POST','/auth/login/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken});
check('기존 Apple 재로그인 → LOGIN, 같은 UUID',login.status===200&&login.body.status==='LOGIN'&&sub(login.body.accessToken)===publicId,login.status);
// 7. 연결용 SMS 재인증 + 연결 challenge로 같은 Apple 재연결(멱등)
await new Promise(r=>setTimeout(r,62000));
const ls=await call(API,'POST','/users/me/login-methods/sms/send',undefined,access);
check('연결 SMS 발송 → 204',ls.status===204,ls);
const lv=await call(API,'POST','/users/me/login-methods/sms/verify',{code:'000000'},access);
check('연결 SMS 확인 → verificationToken',lv.status===200&&!!lv.body.verificationToken&&lv.body.expiresInSeconds===300,lv);
// 로그인용 challenge를 연결에 쓰면 거절, 증표는 유지
c=await appleCreds('/auth/apple/challenge',null,subject);
let cross=await call(API,'POST','/users/me/login-methods/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken,verificationToken:lv.body.verificationToken},access);
check('로그인용 challenge로 연결 → 401 USER407',cross.status===401&&cross.body.code==='USER407',cross);
c=await appleCreds('/users/me/login-methods/apple/challenge',access,subject);
let link=await call(API,'POST','/users/me/login-methods/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken,verificationToken:lv.body.verificationToken},access);
check('연결 challenge + SMS 증표로 Apple 연결 → 204',link.status===204,link);
c=await appleCreds('/users/me/login-methods/apple/challenge',access,subject);
link=await call(API,'POST','/users/me/login-methods/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken,verificationToken:lv.body.verificationToken},access);
check('소진된 SMS 증표 재사용 → 401 USER406',link.status===401&&link.body.code==='USER406',link);
// 8. 다른 회원이 같은 Apple 계정 연결 시도 → 충돌
const phone2='0108'+uniq, subject2=subject+'-b';
c=await appleCreds('/auth/apple/challenge',null,subject2,'b@example.invalid');
const l2=await call(API,'POST','/auth/login/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken});
await call(API,'POST','/auth/sms/send',{phoneNumber:phone2});
const v2=await call(API,'POST','/auth/sms/verify',{phoneNumber:phone2,code:'000000'});
const reg2=await call(API,'POST','/auth/social/register',{signupToken:l2.body.signupToken,verificationToken:v2.body.verificationToken,phoneNumber:phone2,nickname:'둘째'+uniq.slice(-5),name:'김둘째',birthDate:'1995-01-01T00:00:00Z',gender:'UNDISCLOSED'});
const access2=reg2.body?.accessToken;
await new Promise(r=>setTimeout(r,62000));
const ls2=await call(API,'POST','/users/me/login-methods/sms/send',undefined,access2);check('둘째 회원 연결 SMS 발송 → 204',ls2.status===204,ls2);
const lv2=await call(API,'POST','/users/me/login-methods/sms/verify',{code:'000000'},access2);
c=await appleCreds('/users/me/login-methods/apple/challenge',access2,subject);
const conflict=await call(API,'POST','/users/me/login-methods/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken,verificationToken:lv2.body?.verificationToken},access2);
check('다른 회원의 Apple 계정 연결 → 409 USER405',conflict.status===409&&conflict.body.code==='USER405',{reg2:reg2.status,conflict});
// 9. Apple 장애 시 503
await call(MOCK,'POST','/__test/mode',{mode:'invalid-grant'});
c=await appleCreds('/auth/apple/challenge',null,subject);
const ig=await call(API,'POST','/auth/login/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken});
check('Apple 코드 교환 invalid_grant → 401 USER401',ig.status===401&&ig.body.code==='USER401',ig);
await call(MOCK,'POST','/__test/mode',{mode:'healthy'});
// 10. 재발급, 탈퇴
const re=await call(API,'POST','/auth/reissue',{refreshToken:reg.body.refreshToken});
check('재발급 → 200, 같은 UUID',re.status===200&&sub(re.body.accessToken)===publicId,re.status);
const del=await call(API,'DELETE','/users/me',undefined,re.body.accessToken);
check('소셜 전용 회원 탈퇴(비밀번호 없이) → 204',del.status===204,del);
const after=await call(API,'POST','/auth/reissue',{refreshToken:re.body.refreshToken});
check('탈퇴 후 재발급 → 401',after.status===401,after);
c=await appleCreds('/auth/apple/challenge',null,subject);
const again=await call(API,'POST','/auth/login/apple',{identityToken:c.identityToken,authorizationCode:c.authorizationCode,challengeToken:c.challengeToken});
check('탈퇴 후 같은 Apple 로그인 → SIGNUP_REQUIRED(신규 취급)',again.status===200&&again.body.status==='SIGNUP_REQUIRED',again.status);
const stats=await call(MOCK,'GET','/__test/stats');
console.log(results.join('\n')); console.log('모의 서버 통계',JSON.stringify(stats.body)); console.log('subject',subject);
if (results.some(r => r.startsWith('실패'))) process.exit(1);
