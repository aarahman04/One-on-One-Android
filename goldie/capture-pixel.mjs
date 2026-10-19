import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

// Goldie's capture command requires a Pixel 10 Pro; reuse the owner's Pixel 9a without reinstalling or clearing app data.
assert(process.env.GOLDIE_PACKAGE_ROOT, "Set GOLDIE_PACKAGE_ROOT to the installed goldie package directory.");
const packageRoot = resolve(process.env.GOLDIE_PACKAGE_ROOT);
const { loadConfig } = await import(pathToFileURL(resolve(packageRoot, "dist/index.js")));
const { createArgentClient } = await import(pathToFileURL(resolve(packageRoot, "../@swmansion/argent/dist/client.js")));
const device = process.argv[2];
assert(/^emulator-\d+$/.test(device ?? ""), "Pass the existing emulator's adb serial.");
const cfg = await loadConfig(fileURLToPath(new URL("./goldie.config.ts", import.meta.url)));
const client = createArgentClient();
const rawDir = resolve(cfg.outDir, "raw/pixel-10-pro");
await mkdir(rawDir, { recursive: true });
const manifest = { device: "pixel-10-pro", udid: device, capturedAt: new Date().toISOString(), screenshots: [], preview: null };
for (const scene of cfg.scenes.filter(scene => scene.kind === "screenshot")) {
  console.log(`Capturing ${scene.id}`);
  const { data: report } = await client.callTool("flow-execute", {
    name: scene.flow, project_root: cfg.appRoot, device,
  });
  await writeFile(resolve(rawDir, `${scene.id}-flow.json`), JSON.stringify(report, null, 2));
  assert(report.ok, `Flow ${scene.flow} failed; see ${scene.id}-flow.json.`);
  await new Promise(resolve => setTimeout(resolve, 1500));
  const file = resolve(rawDir, `${scene.id}.png`);
  const png = execFileSync("adb", ["-s", device, "exec-out", "screencap", "-p"], { maxBuffer: 16 * 1024 * 1024, timeout: 30000, windowsHide: true });
  assert.equal(png.subarray(1, 4).toString(), "PNG");
  assert(png.readUInt32BE(16) >= 1080 && png.readUInt32BE(20) >= 1920, "Capture must be full resolution.");
  await writeFile(file, png);
  manifest.screenshots.push({ sceneId: scene.id, file });
  await writeFile(resolve(rawDir, "manifest.json"), JSON.stringify(manifest, null, 2));
}
