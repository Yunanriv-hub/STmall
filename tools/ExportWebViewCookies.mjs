import { writeFile } from "node:fs/promises";

const wsUrl = process.argv[2];
const outputPath = process.argv[3];
if (!wsUrl || !outputPath) {
  console.error("usage: node ExportWebViewCookies.mjs <webSocketDebuggerUrl> <output.json>");
  process.exit(2);
}

const allowedDomains = ["confong.cn", "taobao.com", "tmall.com", "tmallcampus.com"];
const socket = new WebSocket(wsUrl);
const timeout = setTimeout(() => {
  console.error("timed out waiting for WebView DevTools");
  process.exit(1);
}, 15_000);

let nextId = 1;
const pending = new Map();

function call(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = nextId++;
    pending.set(id, { resolve, reject });
    socket.send(JSON.stringify({ id, method, params }));
  });
}

socket.addEventListener("message", event => {
  const message = JSON.parse(String(event.data));
  if (!message.id) return;
  const handler = pending.get(message.id);
  if (!handler) return;
  pending.delete(message.id);
  if (message.error) handler.reject(new Error(message.error.message));
  else handler.resolve(message.result);
});

socket.addEventListener("open", async () => {
  try {
    await call("Network.enable");
    let cookies = [];
    for (const request of [
      ["Network.getAllCookies", {}],
      ["Storage.getCookies", {}],
      ["Network.getCookies", {}],
    ]) {
      try {
        const result = await call(request[0], request[1]);
        const candidate = Array.isArray(result.cookies) ? result.cookies : [];
        if (candidate.length > cookies.length) cookies = candidate;
        if (cookies.length > 0) break;
      } catch {
        // Older WebView builds expose different cookie methods.
      }
    }
    const selected = cookies.filter(cookie =>
      allowedDomains.some(domain =>
        String(cookie.domain || "").replace(/^\./, "").endsWith(domain)
      )
    );

    const jar = {};
    for (const cookie of selected) {
      if (cookie.name && typeof cookie.value === "string" && cookie.value) {
        jar[cookie.name] = cookie.value;
      }
    }
    await writeFile(outputPath, JSON.stringify(jar), { mode: 0o600 });

    const names = Object.keys(jar).sort();
    const domains = [...new Set(selected.map(cookie => cookie.domain))].sort();
    console.log(`exported=${names.length} names=${names.join(",")}`);
    console.log(`domains=${domains.join(",")}`);
    clearTimeout(timeout);
    socket.close();
  } catch (error) {
    clearTimeout(timeout);
    console.error(`cookie export failed: ${error.message}`);
    process.exitCode = 1;
    socket.close();
  }
});
