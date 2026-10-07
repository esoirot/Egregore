// The trip planner's map in a real browser (run by templates/eonax/test in the Playwright image):
// every label stays clear of the other labels, of every marker and of the bus line, and inside the drawing.
// The page gets the template's own sample data: /gtfs (gtfs-backend) and /pois.geojson (poi-backend).
import { readFileSync } from "node:fs";
import { chromium } from "playwright-core";

const files = {
  "/": ["/app/index.html", "text/html"],
  "/data/stops.txt": ["/gtfs/stops.txt", "text/plain"],
  "/data/stop_times.txt": ["/gtfs/stop_times.txt", "text/plain"],
  "/data/pois.geojson": ["/pois.geojson", "application/geo+json"],
};
const browser = await chromium.launch();
const page = await browser.newPage();
await page.route("http://app.test/**", route => {
  const [file, type] = files[new URL(route.request().url()).pathname] ?? [];
  return file ? route.fulfill({ body: readFileSync(file), contentType: type }) : route.fulfill({ status: 404 });
});
await page.goto("http://app.test/");
await page.waitForSelector("#map text");
const problems = await page.evaluate(() => {
  const svg = document.getElementById("map");
  const box = el => { const b = el.getBBox(); return { name: el.textContent || el.tagName, x0: b.x, y0: b.y, x1: b.x + b.width, y1: b.y + b.height }; };
  const hit = (a, b) => a.x0 < b.x1 && b.x0 < a.x1 && a.y0 < b.y1 && b.y0 < a.y1;
  const labels = [...svg.querySelectorAll("text")].map(box);
  const markers = [...svg.querySelectorAll("circle, rect")].map(box);
  const line = svg.querySelector("polyline"), half = 2.5; // stroke-width 5
  const route = [];
  for (let at = 0; at <= line.getTotalLength(); at += 2) {
    const p = line.getPointAtLength(at);
    route.push({ x0: p.x - half, y0: p.y - half, x1: p.x + half, y1: p.y + half });
  }
  const view = svg.viewBox.baseVal;
  const found = [];
  labels.forEach((a, i) => {
    labels.slice(i + 1).forEach(b => { if (hit(a, b)) found.push(`label "${a.name}" overlaps label "${b.name}"`); });
    markers.forEach(m => { if (hit(a, m)) found.push(`label "${a.name}" covers a marker`); });
    if (route.some(r => hit(a, r))) found.push(`label "${a.name}" crosses the bus line`);
    if (a.x0 < 0 || a.y0 < 0 || a.x1 > view.width || a.y1 > view.height) found.push(`label "${a.name}" leaves the map`);
  });
  return found;
});
await browser.close();
if (problems.length) {
  console.log(problems.join("\n"));
  process.exit(1);
}
