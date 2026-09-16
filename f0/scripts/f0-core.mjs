import { createHash } from "node:crypto";

const EARTH_RADIUS_M = 6_371_008.8;
const METERS_PER_DEGREE_LAT = 111_320;

export function stableStringify(value) {
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(",")}]`;
  }
  if (value && typeof value === "object") {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value);
}

export function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function toRadians(degrees) {
  return (degrees * Math.PI) / 180;
}

export function haversineMeters([lonA, latA], [lonB, latB]) {
  const lat1 = toRadians(latA);
  const lat2 = toRadians(latB);
  const deltaLat = toRadians(latB - latA);
  const deltaLon = toRadians(lonB - lonA);
  const h =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
}

export function lineLengthMeters(coordinates) {
  let length = 0;
  for (let index = 1; index < coordinates.length; index += 1) {
    length += haversineMeters(coordinates[index - 1], coordinates[index]);
  }
  return length;
}

export function sampleLine(coordinates, intervalM) {
  const samples = [coordinates[0]];
  for (let index = 1; index < coordinates.length; index += 1) {
    const start = coordinates[index - 1];
    const end = coordinates[index];
    const segmentLength = haversineMeters(start, end);
    const steps = Math.max(1, Math.ceil(segmentLength / intervalM));
    for (let step = 1; step <= steps; step += 1) {
      const ratio = step / steps;
      samples.push([
        start[0] + (end[0] - start[0]) * ratio,
        start[1] + (end[1] - start[1]) * ratio
      ]);
    }
  }
  return samples;
}

function cellIndices([lon, lat], grid) {
  return {
    x: Math.floor((lon - grid.originLongitude) / grid.cellSizeDegrees),
    y: Math.floor((lat - grid.originLatitude) / grid.cellSizeDegrees)
  };
}

function cellKey(x, y, grid) {
  return `${grid.id}/${x}/${y}`;
}

function parseCellKey(key) {
  const parts = key.split("/");
  return { x: Number(parts[1]), y: Number(parts[2]) };
}

export function corridorCellKeys(samples, radiusM, grid) {
  const keys = new Set();
  for (const [lon, lat] of samples) {
    const latitudeRadius = radiusM / METERS_PER_DEGREE_LAT;
    const longitudeRadius = radiusM / (METERS_PER_DEGREE_LAT * Math.cos(toRadians(lat)));
    const min = cellIndices([lon - longitudeRadius, lat - latitudeRadius], grid);
    const max = cellIndices([lon + longitudeRadius, lat + latitudeRadius], grid);
    const halfDiagonalM =
      0.5 *
      Math.hypot(
        grid.cellSizeDegrees * METERS_PER_DEGREE_LAT,
        grid.cellSizeDegrees * METERS_PER_DEGREE_LAT * Math.cos(toRadians(lat))
      );

    for (let y = min.y; y <= max.y; y += 1) {
      for (let x = min.x; x <= max.x; x += 1) {
        const center = [
          grid.originLongitude + (x + 0.5) * grid.cellSizeDegrees,
          grid.originLatitude + (y + 0.5) * grid.cellSizeDegrees
        ];
        if (haversineMeters([lon, lat], center) <= radiusM + halfDiagonalM) {
          keys.add(cellKey(x, y, grid));
        }
      }
    }
  }
  return [...keys].sort((left, right) => left.localeCompare(right, "en"));
}

export function queryChunks(cellKeys, grid) {
  const size = grid.queryChunkCellsPerAxis;
  const chunks = new Map();
  for (const key of cellKeys) {
    const { x, y } = parseCellKey(key);
    const chunkX = Math.floor(x / size);
    const chunkY = Math.floor(y / size);
    const keyName = `${grid.queryChunkId}/${chunkX}/${chunkY}`;
    if (!chunks.has(keyName)) {
      chunks.set(keyName, []);
    }
    chunks.get(keyName).push(key);
  }

  return [...chunks.entries()]
    .sort(([left], [right]) => left.localeCompare(right, "en"))
    .map(([key, cells]) => {
      const [, rawX, rawY] = key.split("/");
      const chunkX = Number(rawX);
      const chunkY = Number(rawY);
      const minLon = grid.originLongitude + chunkX * size * grid.cellSizeDegrees;
      const minLat = grid.originLatitude + chunkY * size * grid.cellSizeDegrees;
      return {
        key,
        bbox: [
          Number(minLon.toFixed(6)),
          Number(minLat.toFixed(6)),
          Number((minLon + size * grid.cellSizeDegrees).toFixed(6)),
          Number((minLat + size * grid.cellSizeDegrees).toFixed(6))
        ],
        cellCount: cells.length
      };
    });
}

function normalizedCoordinates(coordinates, decimals) {
  return coordinates.map(([lon, lat]) => [
    Number(lon.toFixed(decimals)),
    Number(lat.toFixed(decimals))
  ]);
}

export function routeFingerprint(feature, strategy) {
  const input = {
    contract: strategy.schemaVersion,
    routeId: feature.properties.id,
    crs: strategy.crs,
    coordinates: normalizedCoordinates(
      feature.geometry.coordinates,
      strategy.coordinatePrecisionDecimals
    ),
    corridorRadiusM: strategy.defaultCorridorRadiusM,
    samplingIntervalM: strategy.samplingIntervalM,
    gridId: strategy.grid.id,
    queryChunkId: strategy.grid.queryChunkId
  };
  return sha256(stableStringify(input));
}

export function analyzeRoute(feature, strategy) {
  const coordinates = feature.geometry.coordinates;
  const lengthM = lineLengthMeters(coordinates);
  const samples = sampleLine(coordinates, strategy.samplingIntervalM);
  const cells = corridorCellKeys(samples, strategy.defaultCorridorRadiusM, strategy.grid);
  const chunks = queryChunks(cells, strategy.grid);
  const radiusKm = strategy.defaultCorridorRadiusM / 1000;
  return {
    routeId: feature.properties.id,
    fingerprint: routeFingerprint(feature, strategy),
    lengthKm: Number((lengthM / 1000).toFixed(3)),
    estimatedCorridorAreaKm2: Number(
      (2 * radiusKm * (lengthM / 1000) + Math.PI * radiusKm ** 2).toFixed(3)
    ),
    sampleCount: samples.length,
    cellCount: cells.length,
    queryChunkCount: chunks.length,
    cellKeys: cells,
    queryChunks: chunks
  };
}

export function classifyEvidence({ occurrenceUsable, rangeCompatible, habitatCompatible, seasonCompatible }) {
  if (occurrenceUsable) {
    return {
      level: "documented",
      confidenceModifier: seasonCompatible === false ? "season-reduces" : "none"
    };
  }
  if (rangeCompatible === true && habitatCompatible === true) {
    return {
      level: "plausible",
      confidenceModifier:
        seasonCompatible === true
          ? "season-supports"
          : seasonCompatible === false
            ? "season-reduces"
            : "season-unknown"
    };
  }
  return { level: "insufficient", confidenceModifier: "not-applicable" };
}

export function isAcceptedAnimalTaxon(taxon) {
  return (
    taxon?.kingdom === "Animalia" &&
    taxon?.status === "ACCEPTED" &&
    typeof taxon?.id === "string" &&
    taxon.id.length > 0 &&
    typeof taxon?.scientificName === "string" &&
    taxon.scientificName.length > 0
  );
}

export function normalizeGbifOccurrence(raw, fixture) {
  return {
    schemaVersion: "f0.normalized.v1",
    kind: "occurrence",
    id: `gbif:${raw.key}`,
    taxon: {
      source: "GBIF",
      id: String(raw.acceptedTaxonKey ?? raw.taxonKey),
      scientificName: raw.acceptedScientificName ?? raw.scientificName,
      kingdom: raw.kingdom,
      rank: "SPECIES",
      status: "ACCEPTED"
    },
    provenance: {
      provider: "GBIF",
      dataset: raw.datasetName,
      sourceVersion: "GBIF occurrence index 2026-09",
      retrievedAt: fixture.retrievedAt,
      sourceRecordId: String(raw.key),
      queryId: fixture.query.id,
      sourceUrl: raw.references,
      license: raw.license,
      attribution: `${raw.datasetName}; GBIF occurrence ${raw.key}`,
      quality: raw.issues.length === 0 ? "high" : "medium",
      rawFixture: "fixtures/providers/gbif-occurrence.parco-nord.json"
    },
    data: {
      occurrenceStatus: raw.occurrenceStatus,
      observedAt: raw.eventDate,
      position: {
        latitude: raw.decimalLatitude,
        longitude: raw.decimalLongitude,
        crs: "EPSG:4326"
      },
      coordinateUncertaintyM: raw.coordinateUncertaintyInMeters,
      basisOfRecord: raw.basisOfRecord,
      issues: raw.issues
    }
  };
}

export function normalizeNnbOccurrence(raw, fixture) {
  return {
    schemaVersion: "f0.normalized.v1",
    kind: "occurrence",
    id: `nnb:${raw.properties.id_osservazione}`,
    taxon: {
      source: "NNB",
      id: `unresolved:${raw.properties.nome_scientifico}`,
      scientificName: raw.properties.nome_scientifico,
      kingdom: "Animalia",
      rank: "SPECIES",
      status: "REQUIRES_GBIF_MATCH"
    },
    provenance: {
      provider: "NNB",
      dataset: raw.properties.banca_dati,
      sourceVersion: "NNB WFS 2026-09-14",
      retrievedAt: fixture.retrievedAt,
      sourceRecordId: String(raw.properties.id_osservazione),
      queryId: fixture.query.id,
      sourceUrl: fixture.query.url,
      license: "NNB per-record license not exposed; personal/non-commercial default only",
      attribution: `${raw.properties.banca_dati}; Network Nazionale della Biodiversità (ISPRA)`,
      quality: "unknown",
      rawFixture: "fixtures/providers/nnb-wfs.parco-nord.json"
    },
    data: {
      occurrenceStatus: "PRESENT",
      observedAt: raw.properties.anno ? `${raw.properties.anno}` : null,
      position: {
        latitude: raw.geometry.coordinates[1],
        longitude: raw.geometry.coordinates[0],
        crs: "EPSG:4326"
      },
      coordinateUncertaintyM: null,
      basisOfRecord: "OBSERVATION",
      issues: ["LICENSE_NOT_EXPOSED", "COORDINATE_UNCERTAINTY_NOT_EXPOSED", "TAXON_NOT_RESOLVED"]
    }
  };
}
