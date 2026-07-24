import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";

async function startServer() {
  const app = express();
  const PORT = 3000;

  // API Route to bypass CORS
  app.get("/api/proxy", async (req, res) => {
    try {
      const targetUrl = req.query.url as string;
      if (!targetUrl) {
        res.status(400).json({ error: "Missing url parameter" });
        return;
      }
      
      const response = await fetch(targetUrl, {
        headers: {
          "User-Agent": "SoundCloud/3.2.0 (Linux; Android 4.3; BlackBerry Q10)",
          "Accept": "application/json",
          "Referer": "https://soundcloud.com/"
        }
      });
      const data = await response.text();
      res.status(response.status).send(data);
    } catch (e: any) {
      res.status(500).json({ error: e.message });
    }
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
