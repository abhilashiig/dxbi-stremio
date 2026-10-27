// Downloads every mod in registry.json and checks it against its sha256. Exits 1 on any mismatch.
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";

const registry = JSON.parse(readFileSync(new URL("../registry.json", import.meta.url), "utf8"));
const entries = [...registry.plugins, ...registry.themes];

const results = await Promise.all(entries.map(async ({ name, download, sha256 }) => {
  try {
    const res = await fetch(download);
    if (!res.ok) return `${name}: HTTP ${res.status}`;
    const actual = createHash("sha256").update(Buffer.from(await res.arrayBuffer())).digest("hex");
    return actual === sha256 ? null : `${name}: sha256 is ${actual}, registry says ${sha256}`;
  } catch (err) {
    return `${name}: ${err.message}`;
  }
}));

const failed = results.filter(Boolean);
failed.forEach((f) => console.error(f));
console.log(`${entries.length - failed.length}/${entries.length} match`);
process.exit(failed.length ? 1 : 0);
