let currentRepo = null;
let busy = false;
const $ = (id) => document.getElementById(id);

/* ---------- helpers ---------- */
function esc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

async function readJson(res) {
  const text = await res.text();
  try { return JSON.parse(text); }
  catch { throw new Error("Server returned " + res.status + " (not JSON). Is the Spring Boot app running on this port?"); }
}

// Safe mini-markdown: code blocks, inline code, bold, bullet lists
function render(text) {
  const blocks = [];
  let t = esc(text).replace(/```([a-zA-Z0-9+#-]*)\n?([\s\S]*?)```/g, (m, lang, code) => {
    blocks.push({ lang: lang || "code", code: code.replace(/\n$/, "") });
    return "@@BLOCK" + (blocks.length - 1) + "@@";
  });
  t = t
    .replace(/^[ \t]*[*-][ \t]+/gm, "\u2022 ")
    .replace(/`([^`\n]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*\n]+)\*\*/g, "<strong>$1</strong>");
  return t.replace(/@@BLOCK(\d+)@@/g, (m, i) => {
    const b = blocks[i];
    return '<div class="code"><div class="code-head"><span>' + b.lang +
      '</span><button class="copy" type="button">Copy</button></div><pre><code>' + b.code + "</code></pre></div>";
  });
}

function scrollDown() {
  const m = $("messages");
  m.scrollTop = m.scrollHeight;
}

function removeWelcome() {
  const w = $("welcome");
  if (w) w.remove();
}

function addUser(text) {
  removeWelcome();
  const row = document.createElement("div");
  row.className = "row user";
  row.innerHTML = '<div class="bubble">' + esc(text) + "</div>";
  $("messages").appendChild(row);
  scrollDown();
}

function addBot(html, isError) {
  removeWelcome();
  const row = document.createElement("div");
  row.className = "row bot";
  row.innerHTML = '<div class="avatar">AI</div><div class="bubble' + (isError ? " error" : "") + '">' + html + "</div>";
  $("messages").appendChild(row);
  scrollDown();
  return row.querySelector(".bubble");
}

/* ---------- status area ---------- */
function setStatus(type, msg) {
  const el = $("status");
  el.className = "status " + (type || "");
  if (type === "loading") {
    el.innerHTML = '<span class="spinner"></span><span>' + esc(msg) + "</span>";
  } else {
    el.textContent = msg || "";
  }
}

/* ---------- repository selection ---------- */
function setRepo(id) {
  currentRepo = id;
  $("repoLabel").textContent = id || "No repository selected";
  $("repoDot").classList.toggle("on", !!id);
  $("question").placeholder = id
    ? "Ask about " + id + "…"
    : "Ask about a file, class or function…";
  const wt = $("welcomeText");
  if (wt && id) wt.textContent = "Chatting with " + id + ". Pick a starter below or type your own question.";
}

async function loadRepos() {
  const ul = $("repoList");
  try {
    const res = await fetch("/api/repos");
    const repos = await readJson(res);
    ul.innerHTML = "";
    if (!repos.length) {
      ul.innerHTML = '<li class="empty">No repositories yet. Add one above.</li>';
      return;
    }
    repos.forEach((r) => {
      const li = document.createElement("li");
      if (r.id === currentRepo) li.classList.add("active");
      li.innerHTML = '<span class="repo-name">' + esc(r.id) + '</span><span class="repo-meta">' +
        r.files + " files · " + r.chunks + " chunks</span>";
      li.onclick = () => { setRepo(r.id); loadRepos(); closeMenu(); $("question").focus(); };
      ul.appendChild(li);
    });
  } catch (e) {
    ul.innerHTML = '<li class="empty">Could not load repositories.</li>';
  }
}

/* ---------- upload & analyze ---------- */
$("addBtn").onclick = async () => {
  const url = $("repoUrl").value.trim();
  if (!url) { setStatus("error", "Please paste a GitHub repository URL."); return; }

  $("addBtn").disabled = true;
  const started = Date.now();
  const tick = () => {
    const s = Math.floor((Date.now() - started) / 1000);
    setStatus("loading", "Cloning and indexing… " + Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0"));
  };
  tick();
  const timer = setInterval(tick, 1000);

  try {
    const res = await fetch("/api/repos", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url }),
    });
    const data = await readJson(res);
    if (!res.ok) throw new Error(data.error || "Request failed");
    setRepo(data.id);
    setStatus("ok", "Done: " + data.files + " files, " + data.chunks + " chunks indexed.");
    await loadRepos();
  } catch (e) {
    setStatus("error", "Error: " + e.message);
  } finally {
    clearInterval(timer);
    $("addBtn").disabled = false;
  }
};
$("repoUrl").addEventListener("keydown", (e) => { if (e.key === "Enter") $("addBtn").click(); });

/* ---------- chat ---------- */
async function send() {
  if (busy) return;
  const input = $("question");
  const q = input.value.trim();
  if (!q) return;
  if (!currentRepo) {
    addBot("Please add or select a repository from the sidebar first.", true);
    return;
  }

  busy = true;
  $("sendBtn").disabled = true;
  addUser(q);
  input.value = "";
  autosize();

  const bubble = addBot('<div class="typing"><span></span><span></span><span></span></div>');
  try {
    const res = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ repoId: currentRepo, question: q }),
    });
    const data = await readJson(res);
    if (!res.ok) throw new Error(data.error || "Request failed");

    let html = render(data.answer || "");
    if (data.sources && data.sources.length) {
      html += '<div class="sources"><span class="tag">Sources</span>' +
        data.sources.map((s) => '<span class="src-chip">' + esc(s) + "</span>").join("") + "</div>";
    }
    bubble.innerHTML = html;
  } catch (e) {
    bubble.classList.add("error");
    bubble.textContent = "Error: " + e.message;
  } finally {
    busy = false;
    $("sendBtn").disabled = false;
    scrollDown();
    input.focus();
  }
}

$("sendBtn").onclick = send;
$("question").addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
});

function autosize() {
  const t = $("question");
  t.style.height = "auto";
  t.style.height = Math.min(t.scrollHeight, 160) + "px";
}
$("question").addEventListener("input", autosize);

/* ---------- suggestion chips ---------- */
document.querySelectorAll(".chip").forEach((chip) => {
  chip.onclick = () => { $("question").value = chip.textContent; send(); };
});

/* ---------- copy button for code blocks ---------- */
$("messages").addEventListener("click", async (e) => {
  if (!e.target.classList.contains("copy")) return;
  const code = e.target.closest(".code").querySelector("code").textContent;
  try {
    await navigator.clipboard.writeText(code);
    e.target.textContent = "Copied";
  } catch {
    e.target.textContent = "Failed";
  }
  setTimeout(() => (e.target.textContent = "Copy"), 1400);
});

/* ---------- mobile menu ---------- */
function openMenu() { $("sidebar").classList.add("open"); $("scrim").classList.add("show"); }
function closeMenu() { $("sidebar").classList.remove("open"); $("scrim").classList.remove("show"); }
$("menuBtn").onclick = openMenu;
$("scrim").onclick = closeMenu;

/* ---------- start ---------- */
loadRepos();