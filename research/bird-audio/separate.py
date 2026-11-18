"""Evaluate four-source Bird MixIT against the unprocessed audio on paired windows."""
import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path

import numpy as np


HERE = Path(__file__).parent
WEIGHTS = "bird_mixit_4sources.safetensors"
SHA256 = "2d6a57db5884e078953ffb9f2a9e2d00a2d1986fefb02115c677b6565e2088ff"


def select(rows):
    from evaluate import seed

    selected = {}
    for dataset in ("CEB", "WABAD"):
        dataset_rows = [r for r in rows if r["dataset"] == dataset]
        overlap = [r for r in dataset_rows if r["stratified"] and r["group"] == "overlap"]
        groups = [
            ("two", [r for r in overlap if len(r["truth"]) == 2], 16),
            ("many", [r for r in overlap if len(r["truth"]) > 2], 16),
            ("uniform", [r for r in dataset_rows if r["uniform"]], 12),
        ]
        for name, group, count in groups:
            ranked = sorted(group, key=lambda r: seed(f"mixit/{name}/{r['file']}/{r['start']}"))
            for row in ranked[:count]:
                key = (dataset, row["file"], row["start"])
                if key not in selected:
                    selected[key] = {k: row[k] for k in ("dataset", "file", "start", "truth", "noise_file")}
                    selected[key]["cohorts"] = []
                selected[key]["cohorts"].append(name)
    return selected


def scores(model, audio):
    values = model.session.run([model.output], {model.input: audio[None, :]})[0][0]
    if values.shape != model.labels.shape or not np.isfinite(values).all():
        raise ValueError(f"Invalid {model.model} output")
    return values


def best(labels, values):
    top = np.argsort(-values)[:3]
    return {"labels": labels[top].tolist(), "scores": values[top].astype(float).tolist()}


def run(folder, output, baseline):
    import soundfile as sf
    import torch
    from bird_mixit_pytorch import BirdMixIT
    from evaluate import Classifier, mixed, mono, recordings, resample

    with (folder / WEIGHTS).open("rb") as weights:
        if hashlib.file_digest(weights, "sha256").hexdigest() != SHA256:
            raise ValueError("Unexpected separator weights")
    torch.set_num_threads(4)
    separator = BirdMixIT.from_pretrained(str(folder.resolve()), num_sources=4).eval()
    classifiers = [Classifier(folder, model) for model in ("bn3", "perch")]
    base_rows = [json.loads(line) for line in baseline.read_text(encoding="utf8").splitlines()]
    by_window = {(r["dataset"], r["file"], r["start"]): r for r in base_rows}
    scheduled = select(base_rows)
    by_recording = defaultdict(list)
    for (dataset, recording, start), row in scheduled.items():
        by_recording[dataset, recording].append((start, row))
    noises = {}
    for path in folder.glob("1-*.wav"):
        data, rate = sf.read(path, dtype="float32", always_2d=True)
        noises[path.name] = mono(data), rate
    done = 0
    staging = output.with_suffix(".partial")
    with staging.open("w", encoding="utf8") as stream, torch.inference_mode():
        for dataset, recording, _, source in recordings(folder):
            if (dataset, recording) not in by_recording:
                continue
            with sf.SoundFile(source) as wav:
                rate = wav.samplerate
                for start, row in by_recording[dataset, recording]:
                    wav.seek(start * rate)
                    original = mono(wav.read(5 * rate, dtype="float32", always_2d=True))
                    noise, noise_rate = noises[row["noise_file"]]
                    noisy = mixed(original, resample(noise, noise_rate, rate)[:len(original)])
                    reference = by_window[dataset, recording, start]
                    for condition, sample in (("original", original), ("noise", noisy)):
                        sep_input = resample(sample, rate, 22050)
                        stems = separator(torch.from_numpy(sep_input))[0].numpy()
                        if stems.shape != (4, 110250) or not np.allclose(stems.sum(0), sep_input, atol=1e-5, rtol=1e-4):
                            raise ValueError("Separator failed its reconstruction check")
                        unprocessed = resample(sample, rate, 32000)
                        stems = [resample(stem, 22050, 32000) for stem in stems]
                        row[condition] = {}
                        for classifier in classifiers:
                            raw = scores(classifier, unprocessed)
                            fused = np.maximum.reduce([raw] + [scores(classifier, stem) for stem in stems])
                            raw_top = best(classifier.labels, raw)
                            baseline_key = "bn" if classifier.model == "bn3" else "perch"
                            if raw_top["labels"] != reference[condition][baseline_key]["all"]:
                                raise ValueError(f"Raw ranking differs from baseline at {recording}:{start}")
                            row[condition][classifier.model] = {
                                "raw": raw_top, "mixit": best(classifier.labels, fused)}
                    stream.write(json.dumps(row, ensure_ascii=False) + "\n")
                    done += 1
                    if done % 8 == 0:
                        stream.flush()
                        print(f"{done}/{len(scheduled)} windows", flush=True)
    if done != len(scheduled):
        raise ValueError(f"Processed only {done}/{len(scheduled)} windows")
    staging.replace(output)


def summarize(output):
    aliases = json.loads((HERE / "evaluation-names.json").read_text())["aliases"]
    rows = [json.loads(line) for line in output.read_text(encoding="utf8").splitlines()]
    keys = {(r["dataset"], r["file"], r["start"]) for r in rows}
    if len(keys) != len(rows):
        raise ValueError("Duplicate prediction window")
    for dataset in ("CEB", "WABAD"):
        for name, expected in (("two", 16), ("many", 16), ("uniform", 12)):
            count = sum(r["dataset"] == dataset and name in r["cohorts"] for r in rows)
            if count != expected:
                raise ValueError(f"{dataset}/{name}: found {count} windows, expected {expected}")
        for condition in ("original", "noise"):
            for model in ("bn3", "perch"):
                paired = {}
                for method in ("raw", "mixit"):
                    recalls, complete, controls = [], [], []
                    strata = defaultdict(list)
                    for row in rows:
                        if row["dataset"] != dataset:
                            continue
                        truth = {aliases.get(n, n) for n in row["truth"]}
                        prediction = row[condition][model][method]
                        labels = [aliases.get(n, n) for n in prediction["labels"]]
                        if {"two", "many"}.intersection(row["cohorts"]):
                            recall = len(truth.intersection(labels)) / len(truth)
                            recalls.append(recall)
                            stratum = "two" if "two" in row["cohorts"] else "many"
                            strata[stratum].append(recall)
                        if "two" in row["cohorts"]:
                            complete.append(truth.issubset(labels))
                        if "uniform" in row["cohorts"]:
                            controls.append(labels[0] not in truth)
                    print(dataset, condition, model, method,
                          f"recall@3 {100 * np.mean(recalls):.1f}% (n={len(recalls)}),",
                          f"both {sum(complete)}/{len(complete)},",
                          f"wrong top1 {sum(controls)}/{len(controls)}")
                    paired[method] = strata
                rng = np.random.default_rng(41)
                sampled = []
                for group in ("two", "many"):
                    diff = np.array(paired["mixit"][group]) - paired["raw"][group]
                    indices = rng.integers(len(diff), size=(4000, len(diff)))
                    sampled.append(diff[indices])
                interval = np.percentile(np.hstack(sampled).mean(axis=1) * 100, [2.5, 97.5])
                print(f"  paired recall@3 difference, 95% CI: [{interval[0]:.1f}, {interval[1]:.1f}] pp")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--inputs", type=Path, help="Run inference using this input and weights directory")
    parser.add_argument("--baseline", type=Path, default=HERE / "predictions-v3.jsonl")
    args = parser.parse_args()
    if args.inputs:
        run(args.inputs, args.output, args.baseline)
    summarize(args.output)
