"""Paired phone evaluation with a separate recorder-level calibration split."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

import numpy as np
import soundfile as sf
import torch

from evaluate import Classifier, mono, resample, table
from separate import scores
from temporal_extract import HERE, RATE, restore

BASE = 0.25


def classify(wave, birdnet, extractor, birds, labels):
    raw = scores(birdnet, wave)
    best = raw.copy()
    for stem in extractor.extract(wave):
        best = np.maximum(best, scores(birdnet, stem))
    output = {}
    for method, values in (("raw", raw), ("temporal", best)):
        order = np.argsort(-values[birds], kind="stable")
        ranked = birds[order]
        output[method] = {
            "top3": labels[ranked[:3]].tolist(),
            "scores": {str(labels[i]): float(values[i]) for i in ranked if values[i] >= BASE}
        }
    return output


def summarize(rows, method, threshold):
    count = Counter()
    for row in rows:
        found = {n for n,v in row[method]["scores"].items() if v >= threshold}
        truth = set(row["truth"])
        count["fp"] += len(found - truth)
        count["correct"] += len(found & truth)
        count["fp_absent_in_minute"] += len(found - set(row["minute_species"]))
        count["windows"] += 1
        if "secondary" in row:
            count["pairs"] += 1
            count["second"] += row["secondary"] in found
            count["both"] += {row["secondary"], row["companion"]} <= found
            count["second_top3"] += row["secondary"] in row[method]["top3"]
    return dict(count)


def calibrated_threshold(rows):
    budget = summarize(rows, "raw", BASE)["fp"]
    errors = sorted((v for row in rows for n,v in row["temporal"]["scores"].items()
                     if n not in row["truth"]), reverse=True)
    if len(errors) <= budget:
        return BASE
    return float(np.nextafter(float(errors[budget]), np.inf))


def evaluate(phones, weights, models, output):
    manifest = json.loads((HERE/"temporal-phones.json").read_text(encoding="utf8"))["recordings"]
    historical = json.loads((HERE/"phone-lg.json").read_text(encoding="utf8"))["recordings"]
    assert not {r["file"] for r in manifest} & {r["id"]+".wav" for r in historical}
    assert not {r["device"] for r in manifest if r["split"]=="test"} & {
        r["device"] for r in manifest if r["split"]=="cal"}
    extractor = restore(weights)
    birdnet = Classifier(models, "bn3")
    labels = birdnet.labels
    birds = np.array([i for i,r in enumerate(table(models/"bn3.csv")) if r["taxon_group"]=="Aves"])
    assert set(n for rec in manifest for w in rec["windows"] for n in w) <= set(labels[birds])
    rows = []
    metrics = {}
    for split in ("cal", "test"):
        if split == "test":
            threshold = calibrated_threshold(rows)
            metrics["thresholds"] = dict(raw=BASE, temporal=threshold)
        for r in manifest:
            if r["split"] != split:
                continue
            path = phones / r["file"]
            with path.open("rb") as src:
                if hashlib.file_digest(src, "sha256").hexdigest() != r["sha256"]:
                    raise ValueError(f"Unexpected audio: {path}")
            data, rate = sf.read(path, dtype="float32", always_2d=True)
            audio = resample(mono(data), rate, RATE)
            minute = sorted({n for w in r["windows"] for n in w})
            for index, truth in enumerate(r["windows"]):
                start = index*5
                wave = audio[start*RATE:(start+5)*RATE]
                assert len(wave) == 5*RATE
                result = classify(wave, birdnet, extractor, birds, labels)
                result.update(file=r["file"], split=split, start=start, truth=truth, minute_species=minute)
                if index == r["second"]//5:
                    result.update(secondary=r["secondary"], companion=r["companion"])
                rows.append(result)
        print(split, len(rows), flush=True)
    for split in ("cal", "test"):
        selected = [r for r in rows if r["split"]==split]
        metrics[split] = {m: summarize(selected, m, t) for m,t in metrics["thresholds"].items()}
        metrics[split]["temporal_at_raw_threshold"] = summarize(selected, "temporal", BASE)
        pair_rows = [r for r in selected if "secondary" in r]
        changes = Counter()
        for r in pair_rows:
            hits = tuple(r[m]["scores"].get(r["secondary"], 0) >= t for m,t in metrics["thresholds"].items())
            changes[{(True,False):"lost",(False,True):"gained",(True,True):"both",(False,False):"neither"}[hits]] += 1
        metrics[split]["paired_second"] = dict(changes)
    output.write_text("".join(json.dumps(r)+"\n" for r in rows),encoding="utf8")
    print(json.dumps(metrics, indent=2),flush=True)
    output.with_suffix(".summary.json").write_text(json.dumps(metrics, indent=2)+"\n", encoding="utf8")


def evaluate_xc(phones, weights, models, output):
    examples = json.loads((HERE/"temporal-xc.json").read_text(encoding="utf8"))
    trained = json.loads((HERE/"target-references.json").read_text(encoding="utf8"))
    previous = json.loads((HERE/"phone-xc.json").read_text(encoding="utf8"))
    excluded = {r["id"] for r in previous} | {"XC"+r["xc"] for r in trained}
    extractor = restore(weights)
    birdnet = Classifier(models, "bn3")
    birds = np.array([i for i,r in enumerate(table(models/"bn3.csv")) if r["taxon_group"]=="Aves"])
    results = []
    for r in examples:
        path = phones/r["id"]
        assert r["id"] not in excluded
        with path.open("rb") as source:
            assert hashlib.file_digest(source,"sha256").hexdigest()==r["sha256"]
        with sf.SoundFile(path) as source:
            rate = source.samplerate
            start = min(max(0, round((r["center"]-2.5)*rate)), max(0, len(source)-5*rate))
            source.seek(start)
            wave = resample(mono(source.read(5*rate, dtype="float32", always_2d=True)),rate,RATE)
        wave = np.pad(wave[:5*RATE],(0,max(0,5*RATE-len(wave))))
        result = classify(wave, birdnet, extractor, birds, birdnet.labels)
        result.update(id=r["id"], start=start/rate, secondary=r["secondary"], companion=r["companion"],
                      truth=sorted({n for a,b,n in r["events"] if b>start/rate and a<start/rate+5}))
        results.append(result)
    output.write_text(json.dumps(results,indent=2)+"\n",encoding="utf8")


if __name__=="__main__":
    parser = argparse.ArgumentParser()
    for key in ("phones","weights","models","output"):
        parser.add_argument(key,type=Path)
    parser.add_argument("--xc", action="store_true", help="Run the independent two-iPhone supplement")
    args=parser.parse_args()
    torch.set_num_threads(3)
    run = evaluate_xc if args.xc else evaluate
    run(args.phones,args.weights,args.models,args.output)
