import { readFile } from "node:fs/promises";
import { analyzeRoute } from "./f0-core.mjs";

const routes = JSON.parse(
  await readFile(new URL("../fixtures/routes/pilot-routes.geojson", import.meta.url), "utf8")
);
const strategy = JSON.parse(
  await readFile(new URL("../fixtures/geo-strategy.json", import.meta.url), "utf8")
);

const output = routes.features.map((feature) => analyzeRoute(feature, strategy));
process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);
