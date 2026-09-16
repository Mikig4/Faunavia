import { createHash } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";

const directory = new URL("../fixtures/providers/", import.meta.url);
const files = (await readdir(directory)).filter((name) => name.endsWith(".json")).sort();
const output = [];
for (const file of files) {
  const content = await readFile(new URL(file, directory));
  output.push({ file: `providers/${file}`, sha256: createHash("sha256").update(content).digest("hex") });
}
process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);
