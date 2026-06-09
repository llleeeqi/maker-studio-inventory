import { cp, mkdir, rm } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const out = resolve(root, "www");

await rm(out, { recursive: true, force: true });
await mkdir(out, { recursive: true });

await cp(resolve(root, "index.html"), resolve(out, "index.html"));
await cp(resolve(root, "app"), resolve(out, "app"), { recursive: true });
await cp(resolve(root, "core"), resolve(out, "core"), { recursive: true });
