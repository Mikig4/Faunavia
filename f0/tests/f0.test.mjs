import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";
import { test } from "node:test";
import {
  analyzeRoute,
  classifyEvidence,
  isAcceptedAnimalTaxon,
  normalizeGbifOccurrence,
  normalizeNnbOccurrence,
  routeFingerprint
} from "../scripts/f0-core.mjs";

const root = new URL("../", import.meta.url);

async function json(relativePath) {
  return JSON.parse(await readFile(new URL(relativePath, root), "utf8"));
}

test("le cinque tracce WGS84 hanno coordinate valide e una sola pilota", async () => {
  const routes = await json("fixtures/routes/pilot-routes.geojson");
  assert.equal(routes.features.length, 5);
  assert.equal(routes.features.filter((feature) => feature.properties.pilot).length, 1);
  for (const feature of routes.features) {
    for (const [longitude, latitude] of feature.geometry.coordinates) {
      assert.ok(longitude >= -180 && longitude <= 180);
      assert.ok(latitude >= -90 && latitude <= 90);
    }
  }
});

test("celle, chunk, metriche e fingerprint sono deterministici", async () => {
  const routes = await json("fixtures/routes/pilot-routes.geojson");
  const strategy = await json("fixtures/geo-strategy.json");
  const expected = await json("fixtures/expected-route-output.json");
  const actual = routes.features.map((feature) => {
    const result = analyzeRoute(feature, strategy);
    assert.equal(new Set(result.cellKeys).size, result.cellKeys.length);
    assert.ok(result.queryChunkCount <= strategy.queryLimits.maxQueryChunksPerPilotRoute);
    for (const chunk of result.queryChunks) {
      assert.ok(chunk.bbox[2] - chunk.bbox[0] <= strategy.queryLimits.maxQueryEnvelopeSpanDegrees + 1e-9);
      assert.ok(chunk.bbox[3] - chunk.bbox[1] <= strategy.queryLimits.maxQueryEnvelopeSpanDegrees + 1e-9);
    }
    return {
      routeId: result.routeId,
      fingerprint: result.fingerprint,
      lengthKm: result.lengthKm,
      estimatedCorridorAreaKm2: result.estimatedCorridorAreaKm2,
      sampleCount: result.sampleCount,
      cellCount: result.cellCount,
      queryChunkCount: result.queryChunkCount
    };
  });
  assert.deepEqual(actual, expected.routes);
  assert.deepEqual(
    routes.features.map((feature) => analyzeRoute(feature, strategy)),
    routes.features.map((feature) => analyzeRoute(feature, strategy))
  );
});

test("il fingerprint ignora rumore sotto la precisione contrattuale", async () => {
  const routes = await json("fixtures/routes/pilot-routes.geojson");
  const strategy = await json("fixtures/geo-strategy.json");
  const original = routes.features[0];
  const noisy = structuredClone(original);
  noisy.geometry.coordinates[0][0] += 0.0000001;
  assert.equal(routeFingerprint(original, strategy), routeFingerprint(noisy, strategy));
});

test("tutte le query rispettano il limite interno e quello noto del provider", async () => {
  const manifest = await json("fixtures/query-manifest.json");
  for (const query of manifest.queries) {
    assert.ok(query.limit <= manifest.maxRecordsPerRequest, query.id);
    if (query.providerKnownMaximum !== null) {
      assert.ok(query.limit <= query.providerKnownMaximum, query.id);
    }
  }
});

test("le fixture provider sono rileggibili offline e protette da hash", async () => {
  const manifest = await json("fixtures/provider-manifest.json");
  const providerDirectory = new URL("fixtures/providers/", root);
  const diskFiles = (await readdir(providerDirectory))
    .filter((name) => name.endsWith(".json"))
    .map((name) => `providers/${name}`)
    .sort();
  assert.deepEqual(diskFiles, manifest.files.map((entry) => entry.file));
  for (const entry of manifest.files) {
    const content = await readFile(new URL(`fixtures/${entry.file}`, root));
    const digest = createHash("sha256").update(content).digest("hex");
    assert.equal(digest, entry.sha256, entry.file);
    JSON.parse(content.toString("utf8"));
  }
});

test("i normalizzatori riproducono gli esempi GBIF e NNB", async () => {
  const gbifRaw = await json("fixtures/providers/gbif-occurrence.parco-nord.json");
  const nnbRaw = await json("fixtures/providers/nnb-wfs.parco-nord.json");
  assert.deepEqual(
    normalizeGbifOccurrence(gbifRaw.response.results[0], gbifRaw),
    await json("fixtures/normalized/occurrence-gbif.json")
  );
  assert.deepEqual(
    normalizeNnbOccurrence(nnbRaw.response.features[0], nnbRaw),
    await json("fixtures/normalized/occurrence-nnb.json")
  );
});

test("solo un taxon Animalia accettato supera il gate di persistenza", async () => {
  const accepted = await json("fixtures/normalized/taxon-turdus-merula.json");
  const unresolved = await json("fixtures/normalized/occurrence-nnb.json");
  assert.equal(isAcceptedAnimalTaxon(accepted.taxon), true);
  assert.equal(isAcceptedAnimalTaxon(unresolved.taxon), false);
  assert.equal(isAcceptedAnimalTaxon({ ...accepted.taxon, kingdom: "Plantae" }), false);
});

test("documented, plausible e insufficient restano semanticamente distinti", async () => {
  const fixture = await json("fixtures/evidence-cases.json");
  for (const evidenceCase of fixture.cases) {
    assert.deepEqual(classifyEvidence(evidenceCase.input), evidenceCase.expected, evidenceCase.id);
  }
});

test("la mancanza del crosswalk CLCplus-MAES produce insufficient", () => {
  assert.deepEqual(
    classifyEvidence({ occurrenceUsable: false, rangeCompatible: true, habitatCompatible: null, seasonCompatible: true }),
    { level: "insufficient", confidenceModifier: "not-applicable" }
  );
});

test("gli esiti reali conservano fallback, errori e non-equivalenza temporale", async () => {
  const smoke = await json("fixtures/smoke-results.json");
  const nnb = smoke.checks.find((check) => check.id === "nnb-occurrence");
  assert.deepEqual(nnb.attempts.map((attempt) => attempt.httpStatus), [503, 200]);
  assert.equal(nnb.selected, "NNB WFS");
  const clcplus = smoke.checks.find((check) => check.id === "clcplus");
  assert.deepEqual(clcplus.attempts.map((attempt) => attempt.httpStatus), [404, 200]);
  assert.equal(clcplus.equivalentFallback, false);
});

test("ogni record normalizzato mantiene provenienza completa e fixture grezza", async () => {
  const normalizedDirectory = new URL("fixtures/normalized/", root);
  const files = (await readdir(normalizedDirectory)).filter((name) => name.endsWith(".json"));
  assert.ok(files.length >= 6);
  for (const file of files) {
    const record = JSON.parse(await readFile(new URL(file, normalizedDirectory), "utf8"));
    assert.equal(record.taxon.kingdom, "Animalia");
    for (const field of ["provider", "dataset", "sourceVersion", "retrievedAt", "sourceRecordId", "queryId", "sourceUrl", "license", "attribution", "quality", "rawFixture"]) {
      assert.ok(record.provenance[field], `${file}:${field}`);
    }
    await readFile(new URL(record.provenance.rawFixture, root));
  }
});

test("un'occorrenza storica conserva la data e non dichiara presenza attuale", async () => {
  const occurrence = await json("fixtures/normalized/occurrence-nnb.json");
  assert.equal(occurrence.data.observedAt, "2014");
  assert.equal(Object.hasOwn(occurrence.data, "currentlyPresent"), false);
});

test("registro fonti rende leggibili licenza, attribuzione, uso e rischio", async () => {
  const registry = await json("fixtures/source-registry.json");
  assert.ok(registry.sources.length >= 6);
  for (const source of registry.sources) {
    for (const field of ["sourceVersion", "license", "attribution", "intendedUse", "sourceUrl", "risk"]) {
      assert.ok(source[field], `${source.id}:${field}`);
    }
  }
});
