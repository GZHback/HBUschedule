const PALETTE = ["#2f6fdb", "#c2410c", "#0f766e", "#7c3aed", "#a16207",
                 "#be123c", "#15803d", "#475569", "#0369a1", "#9333ea"];

const state = { data: null, week: null };
const $ = (id) => document.getElementById(id);

function weeksIn(m, total) {
  if (m.weeks) return m.weeks;
  if (m.odd_week) return range(1, total, 2);
  if (m.even_week) return range(2, total, 2);
  return range(1, total, 1);
}
const range = (a, b, s) => Array.from({ length: Math.max(0, Math.floor((b - a) / s) + 1) }, (_, i) => a + i * s);
const hue = (name) => PALETTE[[...name].reduce((s, c) => s + c.charCodeAt(0), 0) % PALETTE.length];
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

async function load() {
  const res = await fetch("/api/state");
  state.data = await res.json();
  if (!state.data.courses.length) {
    showBanner(state.data.source || "没有课表数据", "warn");
  }
  buildWeekOptions();
  render();
}

function buildWeekOptions() {
  const sel = $("weekSelect");
  const total = Number(state.data.settings.total_weeks);
  sel.innerHTML = "";
  for (let w = 1; w <= total; w++) {
    const opt = document.createElement("option");
    opt.value = w;
    const monday = new Date(state.data.week1_monday + "T00:00:00");
    monday.setDate(monday.getDate() + (w - 1) * 7);
    opt.textContent = `第 ${w} 周（${monday.getMonth() + 1}/${monday.getDate()} 起）` +
      (w === state.data.current_week ? " 本周" : "");
    sel.appendChild(opt);
  }
  state.week = state.data.current_week || 1;
  sel.value = state.week;
}

function maxSession() {
  let max = 8;
  for (const c of state.data.courses)
    for (const m of c.meetings) max = Math.max(max, m.last_session);
  return Math.min(max, 11);
}

function render() {
  renderGrid();
  renderToday();
  renderCourseList();
  $("week1Input").value = state.data.settings.week1_monday;
  $("weeksInput").value = state.data.settings.total_weeks;
}

function renderGrid() {
  const times = state.data.session_times;
  const total = Number(state.data.settings.total_weeks);
  const rows = maxSession();
  let html = "<thead><tr><th class='corner'>节次</th>" +
    state.data.week_names.map((n, i) =>
      `<th class='${i >= 5 ? "weekend" : ""}'>${n}</th>`).join("") + "</tr></thead><tbody>";

  for (let s = 1; s <= rows; s++) {
    const t = times[String(s)] || times[s] || ["", ""];
    html += `<tr><th class='session'>第${s}节<span>${esc(t[0])}</span></th>`;
    for (let day = 1; day <= 7; day++) {
      const hits = [];
      for (const c of state.data.courses) {
        for (const m of c.meetings) {
          if (m.day !== day || s < m.first_session || s > m.last_session) continue;
          if (!weeksIn(m, total).includes(state.week)) continue;
          hits.push({ c, m, spans: m.last_session - m.first_session + 1, first: s === m.first_session });
        }
      }
      hits.sort((a, b) => a.m.first_session - b.m.first_session);
      html += "<td>" + hits.map(({ c, m, spans, first }) => first ? `
        <div class="card" style="height:${spans * 100 - 6}%;border-left-color:${hue(c.name)}">
          <b>${esc(c.name)}</b>
          <span class="room">${esc(m.room || m.building || "")}</span>
          <span class="teacher">${esc(c.teacher || "")}</span>
        </div>` : "").join("") + "</td>";
    }
    html += "</tr>";
  }
  $("grid").innerHTML = html + "</tbody>";
}

function renderToday() {
  const total = Number(state.data.settings.total_weeks);
  const day = new Date().getDay() || 7;
  const inWeek = [];
  for (const c of state.data.courses)
    for (const m of c.meetings)
      if (m.day === day && weeksIn(m, total).includes(state.week)) inWeek.push({ c, m });
  if (!inWeek.length) {
    $("today").innerHTML = `<div class="today-empty">第 ${state.week} 周今天没课 🎉</div>`;
    return;
  }
  inWeek.sort((a, b) => a.m.first_session - b.m.first_session);
  $("today").innerHTML = `<div class="today-title">今天 · 第 ${state.week} 周</div>` +
    inWeek.map(({ c, m }) => {
      const t = state.data.session_times[String(m.first_session)] || ["", ""];
      return `<div class="today-item"><span class="time">${esc(t[0])}</span>
        <span class="name">${esc(c.name)}</span>
        <span class="meta">${esc(m.session_label)} · ${esc(m.location)}</span></div>`;
    }).join("");
}

function renderCourseList() {
  const list = state.data.courses.filter((c) => c.meetings.length);
  const noPlace = state.data.courses.filter((c) => !c.meetings.length);
  $("courseList").innerHTML =
    `<h2>本学期课程（${list.length}）</h2>` +
    list.map((c) => `
      <div class="course-row">
        <span class="dot" style="background:${hue(c.name)}"></span>
        <b>${esc(c.name)}</b>
        <span class="meta">${esc(c.teacher)} · ${esc(c.property)} · ${esc(c.credits)}学分</span>
        <div class="slots">${c.meetings.map((m) =>
          `<i>${esc(state.data.week_names[m.day - 1])} ${esc(m.session_label)} ${esc(m.week_description || "全学期")} ${esc(m.location)}</i>`).join("")}</div>
      </div>`).join("") +
    (noPlace.length ? `<h2>无排课地点（${noPlace.length}）</h2>` + noPlace.map((c) =>
      `<div class="course-row"><span class="dot gray"></span><b>${esc(c.name)}</b>
       <span class="meta">${esc(c.teacher) || "未安排教师"} · 实践环节，日历里不会出现</span></div>`).join("") : "");
}

function showBanner(text, kind = "info") {
  const b = $("banner");
  b.textContent = text;
  b.className = "banner " + kind;
  clearTimeout(showBanner.timer);
  showBanner.timer = setTimeout(() => b.classList.add("hidden"), 6000);
}

$("weekSelect").addEventListener("change", (e) => {
  state.week = Number(e.target.value);
  render();
});

$("btnSettings").addEventListener("click", () => $("settingsPanel").classList.toggle("hidden"));

$("btnSaveSettings").addEventListener("click", async () => {
  const res = await fetch("/api/settings", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      week1_monday: $("week1Input").value,
      total_weeks: Number($("weeksInput").value),
    }),
  });
  const body = await res.json();
  if (!res.ok) return showBanner(body.error || "保存失败", "warn");
  showBanner("学期设置已保存", "ok");
  await load();
});

$("btnIcs").addEventListener("click", async () => {
  const res = await fetch("/api/export.ics");
  if (!res.ok) {
    const body = await res.json();
    return showBanner(body.error || "导出失败", "warn");
  }
  const blob = await res.blob();
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "hbu-schedule.ics";
  a.click();
  URL.revokeObjectURL(a.href);
  showBanner("已下载 hbu-schedule.ics，打开手机日历导入即可", "ok");
});

$("btnRefresh").addEventListener("click", async () => {
  const res = await fetch("/api/refresh", { method: "POST" });
  const body = await res.json();
  if (body.error) return showBanner(body.error, "warn");
  showBanner("已弹出浏览器，请扫码登录教务系统…", "info");
  poll();
});

function poll() {
  const timer = setInterval(async () => {
    const res = await fetch("/api/refresh-status");
    const { status } = await res.json();
    if (status === "running") return;
    clearInterval(timer);
    if (status === "ok") { showBanner("课表已更新", "ok"); await load(); }
    else if (status === "failed") showBanner("抓取失败，可改用“载入 JSON”", "warn");
  }, 1500);
}

$("fileInput").addEventListener("change", async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const raw = await file.text();
  const res = await fetch("/api/load", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ raw }),
  });
  const body = await res.json();
  if (!res.ok) return showBanner(body.error || "载入失败", "warn");
  showBanner(`已载入 ${body.course_count} 门课`, "ok");
  await load();
  e.target.value = "";
});

load();
