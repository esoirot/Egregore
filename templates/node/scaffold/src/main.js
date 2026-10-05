import { createApp } from "./app.js";

const port = 3000;

createApp().listen(port, "0.0.0.0", () => {
  console.log(`Listening on http://localhost:${port}`);
});
