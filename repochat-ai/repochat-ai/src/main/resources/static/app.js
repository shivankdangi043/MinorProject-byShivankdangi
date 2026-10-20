let currentRepo = null;
const $ = (id) => document.getElementById(id);

function esc(s) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// turn ```code``` blocks into <pre><code>
function render(text) {
  return esc(text).replace(/```[a-zA-Z]*\n?([\s\S]*?)```/g, "<pre><code>$1</code></pre>");
}

function addMsg(html, cls) {
  const div = document.createElement("div");
  div.className = "msg " + cls;
  div.innerHTML = html;
  $("messages").appendChild(div);
  $("messages").scrollTop = $("messages").scrollHeight;
  return div;
}

async function loadRepos() {
  const res = await fetch("/api/repos");
  const repos = await res.json();
  const ul = $("repoList");
  ul.innerHTML = "";
  repos.forEach((r) => {
    const li = document.createElement("li");
    li.innerHTML = `${esc(r.id)}<small>${r.files} files, ${r.chunks} chunks</small>`;
    if (r.id === currentRepo) li.classList.add("active");
    li.onclick = () => { currentRepo = r.id; loadRepos(); addMsg("Selected <b>" + esc(r.id) + "</b>. Ask away!", "bot"); };
    ul.appendChild(li);
  });
}

$("addBtn").onclick = async () => {
  const url = $("repoUrl").value.trim();
  if (!url) return;
  $("addBtn").disabled = true;
  $("status").textContent = "Cloning and indexing... this can take a minute.";
  try {
    const res = await fetch("/api/repos", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error);
    currentRepo = data.id;
    $("status").textContent = `Done: ${data.files} files, ${data.chunks} chunks indexed.`;
    loadRepos();
  } catch (e) {
    $("status").textContent = "Error: " + e.message;
  }
  $("addBtn").disabled = false;
};

async function send() {
  const q = $("question").value.trim();
  if (!q) return;
  if (!currentRepo) { addMsg("Please select or upload a repository first.", "bot"); return; }
  addMsg(esc(q), "user");
  $("question").value = "";
  const thinking = addMsg("Thinking...", "bot");
  try {
    const res = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ repoId: currentRepo, question: q }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error);
    thinking.innerHTML = render(data.answer) +
      `<div class="src">Sources: ${data.sources.map(esc).join(", ")}</div>`;
  } catch (e) {
    thinking.textContent = "Error: " + e.message;
  }
}

$("sendBtn").onclick = send;
$("question").addEventListener("keydown", (e) => { if (e.key === "Enter") send(); });
loadRepos();
