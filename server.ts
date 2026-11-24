import "dotenv/config";
import express from "express";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { GoogleGenAI, Type } from "@google/genai";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT) || 3000;
const ANDROID_PROJECT_DIR = path.resolve(__dirname, "android-jarvis");

const ALLOWED_MODELS = new Set(["gemini-4-hydrogen", "gemini-3.1-flash-lite"]);

const ACTION_ALLOWLIST = [
  {
    id: "get_device_status",
    phase: 1,
    implemented: true,
    confirmation: "NONE",
    description: "Read device battery percentage, charging state, network status, and runtime Android/MagicOS info.",
    params: [],
  },
  {
    id: "save_memory_note",
    phase: 1,
    implemented: true,
    confirmation: "REQUIRED",
    description: "Save a user-approved preference or personal note locally (params: key, value).",
    params: ["key", "value"],
  },
  {
    id: "open_application",
    phase: 3,
    implemented: false,
    confirmation: "NONE",
    description: "Open an installed Android app like Anghami, WhatsApp, or Spotify (params: appName).",
    params: ["appName"],
  },
  {
    id: "initiate_phone_call",
    phase: 3,
    implemented: false,
    confirmation: "REQUIRED",
    description: "Call a contact by name (params: contactName).",
    params: ["contactName"],
  },
  {
    id: "read_notifications",
    phase: 3,
    implemented: false,
    confirmation: "NONE",
    description: "Read latest accessible notifications from messaging apps.",
    params: [],
  },
  {
    id: "schedule_reminder",
    phase: 5,
    implemented: false,
    confirmation: "NONE",
    description: "Schedule a reminder notification (params: title, timeText).",
    params: ["title", "timeText"],
  },
];

function getGeminiClient(): GoogleGenAI | null {
  const apiKey = process.env.GEMINI_API_KEY?.trim();
  if (!apiKey) return null;
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        "User-Agent": "aistudio-build",
      },
    },
  });
}

function collectAndroidFiles(dir: string, baseDir: string): Array<{ path: string; content: string; size: number }> {
  if (!fs.existsSync(dir)) return [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const results: Array<{ path: string; content: string; size: number }> = [];

  for (const entry of entries) {
    const fullPath = path.resolve(dir, entry.name);
    if (!fullPath.startsWith(baseDir + path.sep)) {
      continue;
    }
    if (entry.isDirectory()) {
      results.push(...collectAndroidFiles(fullPath, baseDir));
    } else if (entry.isFile()) {
      const relPath = path.relative(baseDir, fullPath).replace(/\\/g, "/");
      const content = fs.readFileSync(fullPath, "utf-8");
      results.push({ path: relPath, content, size: Buffer.byteLength(content, "utf-8") });
    }
  }
  return results.sort((a, b) => a.path.localeCompare(b.path));
}

async function startServer() {
  const app = express();
  app.use(express.json({ limit: "1mb" }));

  app.get("/api/jarvis/health", (_req, res) => {
    const hasGeminiKey = Boolean(process.env.GEMINI_API_KEY?.trim());
    res.json({
      status: "ok",
      geminiConfigured: hasGeminiKey,
      defaultModel: "gemini-4-hydrogen",
      phase: "Phase 1 — Foundation",
    });
  });

  app.get("/api/android-project", (_req, res) => {
    try {
      const files = collectAndroidFiles(ANDROID_PROJECT_DIR, ANDROID_PROJECT_DIR);
      res.json({
        projectName: "JARVIS",
        targetDevice: "HONOR 400 Pro (DNP-NX9)",
        fileCount: files.length,
        files,
      });
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Failed to read Android project files";
      res.status(500).json({ error: msg });
    }
  });

  app.post("/api/jarvis/chat", async (req, res) => {
    const ai = getGeminiClient();
    if (!ai) {
      res.status(503).json({
        error: "GEMINI_API_KEY is not configured on the server. Add it in Settings > Secrets.",
      });
      return;
    }

    const body = req.body as {
      message?: unknown;
      model?: unknown;
      history?: Array<{ role?: string; text?: string }>;
      memories?: Record<string, string>;
    };

    const userMessage = typeof body?.message === "string" ? body.message.trim() : "";
    if (!userMessage || userMessage.length > 4000) {
      res.status(400).json({ error: "Please provide a valid message (1 to 4000 characters)." });
      return;
    }

    const requestedModel = typeof body?.model === "string" ? body.model.trim() : "gemini-4-hydrogen";
    const model = ALLOWED_MODELS.has(requestedModel) ? requestedModel : "gemini-4-hydrogen";

    const memories = body?.memories && typeof body.memories === "object" ? body.memories : {};
    const memoryEntries = Object.entries(memories)
      .slice(0, 20)
      .map(([k, v]) => `- ${String(k).slice(0, 80)}: ${String(v).slice(0, 240)}`)
      .join("\n");

    const historyLines = Array.isArray(body?.history)
      ? body.history
          .slice(-8)
          .map((h) => `${h.role === "assistant" ? "JARVIS" : "User"}: ${String(h.text ?? "").slice(0, 500)}`)
          .join("\n")
      : "";

    const allowlistText = ACTION_ALLOWLIST.map(
      (a) =>
        `- ${a.id} (phase=${a.phase}, implemented=${a.implemented}, confirmation=${a.confirmation}): ${a.description}`
    ).join("\n");

    const systemInstruction = `You are J.A.R.V.I.S. (Just A Rather Very Intelligent System), a personal AI assistant running in Phase 1 on Android (HONOR 400 Pro) and tested via its AI Brain backend.
Speak naturally in Egyptian Arabic (اللهجة المصرية) with a calm, intelligent, confident, concise tone (e.g. "تحت أمرك يا فندم", "أمرك يا فندم", "حاضر").
Never claim to have executed a phone call, SMS, WhatsApp action, app launch, or reminder—those device actions are scheduled for Phases 3-5 in the native Android ActionRegistry.
If the user asks for an action in the allowlist below, populate plannedAction with its exact actionId and parameters, and honestly tell the user if it belongs to a later phase or requires confirmation.
If no device action is needed, omit plannedAction or leave actionId empty.

User-approved local memories:
${memoryEntries || "None"}

Explicit Action Allowlist:
${allowlistText}`;

    const fullPrompt = historyLines
      ? `Recent conversation:\n${historyLines}\n\nUser now says: ${userMessage}`
      : userMessage;

    const generateWithRetry = async (targetModel: string) => {
      return ai.models.generateContent({
        model: targetModel,
        contents: fullPrompt,
        config: {
          systemInstruction,
          responseMimeType: "application/json",
          responseSchema: {
            type: Type.OBJECT,
            properties: {
              reply: {
                type: Type.STRING,
                description: "Natural, concise Egyptian Arabic response from JARVIS.",
              },
              plannedAction: {
                type: Type.OBJECT,
                nullable: true,
                description: "Optional structured action selected strictly from the allowlist.",
                properties: {
                  actionId: { type: Type.STRING },
                  paramKey: { type: Type.STRING },
                  paramValue: { type: Type.STRING },
                  requiresConfirmation: { type: Type.BOOLEAN },
                },
              },
            },
            required: ["reply"],
          },
        },
      });
    };

    try {
      let activeModel = model;
      let response;
      try {
        response = await generateWithRetry(activeModel);
      } catch (firstErr) {
        const msg = firstErr instanceof Error ? firstErr.message : String(firstErr);
        if (msg.includes("503") || msg.includes("UNAVAILABLE")) {
          await new Promise((r) => setTimeout(r, 800));
          activeModel = activeModel === "gemini-4-hydrogen" ? "gemini-3.1-flash-lite" : activeModel;
          response = await generateWithRetry(activeModel);
        } else {
          throw firstErr;
        }
      }

      const rawText = (response.text ?? "{}").trim();
      const parsed = JSON.parse(rawText) as {
        reply?: string;
        plannedAction?: {
          actionId?: string;
          paramKey?: string;
          paramValue?: string;
          requiresConfirmation?: boolean;
        } | null;
      };

      const actionId = parsed.plannedAction?.actionId?.trim();
      const validAction = actionId ? ACTION_ALLOWLIST.find((a) => a.id === actionId) : undefined;

      const parameters: Record<string, string> = {};
      if (validAction && parsed.plannedAction?.paramKey && parsed.plannedAction?.paramValue) {
        parameters[parsed.plannedAction.paramKey] = parsed.plannedAction.paramValue;
      }

      res.json({
        reply: parsed.reply?.trim() || "تحت أمرك يا فندم.",
        plannedAction: validAction
          ? {
              actionId: validAction.id,
              parameters,
              requiresConfirmation:
                validAction.confirmation === "REQUIRED" ||
                Boolean(parsed.plannedAction?.requiresConfirmation),
              implementedInPhase1: validAction.implemented,
              scheduledPhase: validAction.phase,
            }
          : null,
        modelUsed: activeModel,
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Gemini request failed";
      res.status(502).json({ error: message });
    }
  });

  if (process.env.NODE_ENV !== "production") {
    const { createServer: createViteServer } = await import("vite");
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distDir = path.resolve(__dirname, "dist");
    app.use(express.static(distDir));
    app.get("*", (_req, res) => {
      res.sendFile(path.resolve(distDir, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`JARVIS Studio & AI Brain server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
