import { createServer } from "node:http";

const routes = {
  "/": { message: "Hello World" },
  "/health": { status: "ok" },
};

export function createApp() {
  return createServer((req, res) => {
    const body = routes[req.url];
    res.writeHead(body ? 200 : 404, { "content-type": "application/json" });
    res.end(JSON.stringify(body ?? { error: "Not Found" }));
  });
}
