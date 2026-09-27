#!/usr/bin/env node
// 用学号+密码登录 v.hbu.cn WebVPN，抓取本学期课表原始 JSON。零依赖，需 Node 18+。
//   node tools/capture-schedule.mjs --probe   只测连通性和登录页，不输入账号
//   node tools/capture-schedule.mjs           完整抓取，默认输出 schedule_raw.json
// 密码只在进程内存里，不写盘、不进日志。

process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';

import { createCipheriv } from 'node:crypto';
import { createInterface } from 'node:readline';
import fs from 'node:fs';

const VPN = 'https://v.hbu.cn';
const AES_KEY = 'wrdvpnisawesome!';

const args = process.argv.slice(2);
const has = (f) => args.includes(f);
const val = (f, d) => {
  const i = args.indexOf(f);
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith('--') ? args[i + 1] : d;
};

let rl = null;
const getRl = () => (rl ??= createInterface({ input: process.stdin, output: process.stdout }));
const ask = (q) => new Promise((r) => getRl().question(q, r));

function askSecret(q) {
  return new Promise((resolve) => {
    getRl().close();
    rl = null;
    const s = process.stdin;
    let buf = '';
    s.setRawMode(true);
    s.resume();
    s.setEncoding('utf8');
    process.stdout.write(q);
    const onData = (ch) => {
      if (ch === '\u0003') {
        s.setRawMode(false);
        process.stdout.write('\n已取消\n');
        process.exit(130);
      }
      if (ch === '\r' || ch === '\n') {
        s.setRawMode(false);
        s.removeListener('data', onData);
        s.pause();
        process.stdout.write('\n');
        resolve(buf);
        return;
      }
      if (ch === '\u007f' || ch === '\b') {
        if (buf) {
          buf = buf.slice(0, -1);
          process.stdout.write('\b \b');
        }
        return;
      }
      buf += ch;
      process.stdout.write('*');
    };
    s.on('data', onData);
  });
}

const jar = new Map();
const cookieHeader = () => [...jar].map(([k, v]) => `${k}=${v}`).join('; ');

async function req(url, opts = {}) {
  const res = await fetch(url, {
    method: opts.method || 'GET',
    body: opts.body || null,
    redirect: 'manual',
    headers: {
      'user-agent': 'Mozilla/5.0 (Linux; Android 13) HBUschedule-probe/1',
      cookie: cookieHeader(),
      ...(opts.headers || {}),
    },
  });
  for (const c of res.headers.getSetCookie?.() || []) {
    const pair = c.split(';')[0];
    const eq = pair.indexOf('=');
    if (eq > 0) jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
  }
  return res;
}

const text = (res) => res.text();

// 复现登录页里的 encrypt()：AES-128-CFB(128bit segment)，key 与 iv 同为硬编码串，
// 输出 hex(iv) + hex(密文) 截断到原密码长度。已用页面自带的 aes-js 对拍通过。
function encryptPassword(pwd) {
  const key = Buffer.from(AES_KEY, 'utf8');
  const iv = Buffer.from(AES_KEY, 'utf8');
  let padded = pwd;
  if (padded.length % 16 !== 0) padded += '0'.repeat(16 - (padded.length % 16));
  const c = createCipheriv('aes-128-cfb', key, iv);
  c.setAutoPadding(false);
  const ct = Buffer.concat([c.update(Buffer.from(padded, 'utf8')), c.final()]);
  return iv.toString('hex') + ct.subarray(0, pwd.length).toString('hex');
}

function form(obj) {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(obj)) if (v !== undefined && v !== null) p.set(k, String(v));
  return p.toString();
}

async function doLogin(csrf, captchaId, username, password, captcha) {
  const body = form({
    _csrf: csrf,
    auth_type: 'local',
    username,
    password: encryptPassword(password),
    captcha: captcha || '',
    needCaptcha: captcha ? 'true' : 'false',
    captcha_id: captchaId,
    remember_cookie: 'on',
  });
  const res = await req(`${VPN}/do-login`, {
    method: 'POST',
    body,
    headers: { 'content-type': 'application/x-www-form-urlencoded; charset=UTF-8', 'x-requested-with': 'XMLHttpRequest' },
  });
  return res.json().catch(() => ({ success: false, error: 'NOT_JSON', message: `HTTP ${res.status}` }));
}

async function main() {
  const loginPage = await text(await req(`${VPN}/login`));
  const csrf = loginPage.match(/name="_csrf"\s+value="([^"]+)"/)?.[1];
  const captchaId = loginPage.match(/name="captcha_id"\s+value="([^"]+)"/)?.[1];
  console.log(`✓ 登录页可达  _csrf=${csrf ? '有' : '没抓到'}  captcha_id=${captchaId || '无'}`);
  console.log(`  登录方式: ${[...loginPage.matchAll(/data-type="(\w+)"[^>]*>([^<]+)</g)].map((m) => m[2].trim()).join(' / ')}`);
  if (has('--probe')) {
    console.log('✓ --probe 完成，未提交任何账号信息');
    return;
  }
  if (!csrf) throw new Error('拿不到 _csrf，页面结构可能变了');

  const username = (await ask('学号: ')).trim();
  const password = await askSecret('密码(输入不回显): ');

  let res = await doLogin(csrf, captchaId, username, password);
  while (['CAPTCHA_FAILED', 'INVALID_ACCOUNT', 'TOO_MANY_ATTEMPTS', 'IP_FORBIDDEN'].includes(res.error)) {
    console.log(`! ${res.error}: ${res.message || ''}`);
    if (has('--no-captcha')) throw new Error('已按 --no-captcha 停止');
    const img = await req(`${VPN}/captcha/${captchaId}.png`);
    const ct = img.headers.get('content-type') || '';
    if (!ct.includes('image')) throw new Error('取验证码图片失败，可能需要人工在浏览器里过一次');
    fs.writeFileSync('captcha.png', Buffer.from(await img.arrayBuffer()));
    console.log('  验证码图片已存到 captcha.png，用它打开看图');
    const code = (await ask('图中验证码(回车跳过则退出): ')).trim();
    if (!code) throw new Error('未输入验证码');
    res = await doLogin(csrf, captchaId, username, password, code);
  }

  if (res.error === 'NEED_CONFIRM') {
    console.log('! 该账号已在别处登录，继续会踢掉对方 -> 确认继续');
    res = await (await req(`${VPN}/do-confirm-login`, {
      method: 'POST',
      body: form({ _csrf: csrf }),
      headers: { 'content-type': 'application/x-www-form-urlencoded; charset=UTF-8', 'x-requested-with': 'XMLHttpRequest' },
    })).json();
  }

  if (res.error === 'NEED_TWO_STEP' || (res.success === false && /two_step/i.test(res.error || ''))) {
    const phone = res.phone || '';
    console.log(`! 需要短信验证码，账号绑定手机号 ${phone.slice(0, 3)}****${phone.slice(7)}`);
    await req(`${VPN}/send-sms/`, { method: 'POST', body: form({ username: phone, auth_type: 'local' }), headers: { 'content-type': 'application/x-www-form-urlencoded' } });
    const code = (await ask('短信验证码: ')).trim();
    res = await (await req(`${VPN}/do-second-login`, {
      method: 'POST',
      body: form({ username, disable_phone: phone, code }),
      headers: { 'content-type': 'application/x-www-form-urlencoded', 'x-requested-with': 'XMLHttpRequest' },
    })).json();
  }

  if (!res.success) throw new Error(`登录失败: ${res.error || '未知'} ${res.message || ''}`);
  console.log(`✓ 登录成功 -> ${res.url}`);

  let token = null, proto = null;
  if (has('--token')) {
    token = val('--token');
    proto = val('--proto', 'https');
  } else {
    for (const u of [res.url, `${VPN}/`, `${VPN}/wengine-vpn/portal`]) {
      if (!u || token) continue;
      let html = '';
      try { html = await text(await req(u)); } catch { continue; }
      const hit = html.match(/v\.hbu\.cn\/(https?)\/([0-9a-zA-Z]{20,})\/[^\s"'<>]*(zhjw|教务|courseSelect)/i);
      if (hit) { proto = hit[1]; token = hit[2]; console.log(`✓ 在门户页找到教务代理前缀`); break; }
      fs.writeFileSync('portal_dump.html', html);
    }
  }
  if (!token) {
    console.log('✗ 没能自动找到教务系统的代理前缀(A 串)。门户页已存成 portal_dump.html。');
    console.log('  在浏览器里打开教务系统，把地址栏 https://v.hbu.cn/https/<A串>/... 里的 <A串> 复制出来，再跑:');
    console.log('    node tools/capture-schedule.mjs --token <A串> --proto https');
    return;
  }

  const base = `${VPN}/${proto}/${token}`;
  const curriculum = await text(await req(`${base}/student/courseSelect/thisSemesterCurriculum/index`));
  fs.writeFileSync('curriculum_page.html', curriculum);
  let callbackUrl = curriculum.match(/(["'])([^"']*ajaxStudentSchedule\/curr\/callback)\1/)?.[2];
  if (!callbackUrl) {
    const b = curriculum.match(/thisSemesterCurriculum\/([0-9a-zA-Z]{10,})/)?.[1];
    if (b) callbackUrl = `${base}/student/courseSelect/thisSemesterCurriculum/${b}/ajaxStudentSchedule/curr/callback`;
  }
  if (!callbackUrl) {
    console.log('✗ 课表页里没找到 callback 接口，已存 curriculum_page.html 供排查');
    return;
  }
  const abs = callbackUrl.startsWith('http') ? callbackUrl : new URL(callbackUrl, base).href;
  const raw = await text(await req(abs));
  if (!raw.trim().startsWith('{') && !raw.trim().startsWith('[')) {
    throw new Error(`接口返回不是 JSON（前 200 字）: ${raw.slice(0, 200)}`);
  }
  const out = val('--out', 'schedule_raw.json');
  fs.writeFileSync(out, JSON.stringify(JSON.parse(raw), null, 2));
  const data = JSON.parse(raw);
  const n = Array.isArray(data.xkxx) ? data.xkxx.length : 0;
  console.log(`✓ 已抓取 ${n} 门课，保存到 ${out}`);
  console.log('  把这份文件发我就能定死节次表、周次文案和学期起点。');
}

main().catch((e) => {
  console.error(`\n✗ ${e.message}`);
  process.exit(1);
});
