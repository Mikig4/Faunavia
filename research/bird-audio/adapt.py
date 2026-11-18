"""Train a residual multilabel head on earlier WABAD days; test later days and CEB."""
import argparse
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy.optimize import minimize
from scipy.signal import butter, sosfilt
from scipy.special import expit, logit
from threadpoolctl import threadpool_limits

from evaluate import Classifier, mixed, mono, recordings, resample, seed, sf, table

HERE = Path(__file__).resolve().parent
HP = butter(4, 800, fs=32000, btype="highpass", output="sos")
ALIASES = json.loads((HERE / "evaluation-names.json").read_text())["aliases"]


def canon(name):
    return ALIASES.get(name, name)


def split_days(keys):
    by_site = defaultdict(set)
    for key in keys:
        site, day, _ = key.split("_")
        by_site[site].add(day)
    return {site: sorted(days)[:-math.ceil(len(days) / 3)] for site, days in by_site.items()}


def extract(folder, cache, augmentation=False):
    manifest = defaultdict(list)
    for line in (HERE / "predictions-v3.jsonl").read_text().splitlines():
        row = json.loads(line)
        manifest[row["dataset"], row["file"]].append(row)
    days = split_days(key for dataset, key in manifest if dataset == "WABAD")
    net = Classifier(folder, "bn3")
    noises = {}
    if augmentation:
        cache = cache / "augmentation"
        hashes = json.loads((HERE / "training-noises.json").read_text())
        paths = [folder / name for name in sorted(hashes)]
        for path in paths:
            assert hashlib.sha256(path.read_bytes()).hexdigest() == hashes[path.name]
    else:
        paths = folder.glob("1-*.wav")
    for path in paths:
        data, sr = sf.read(path, dtype="float32", always_2d=True)
        noises[path.name] = (mono(data), sr)
    cache.mkdir(parents=True, exist_ok=True)
    for i, (dataset, key, events, buf) in enumerate(recordings(folder)):
        train = dataset == "WABAD" and key.split("_")[1] in days[key.split("_")[0]]
        if augmentation and not train:
            continue
        destination = cache / f"{dataset}-{seed(key)}.npz"
        if destination.exists():
            continue
        data, sr = sf.read(buf, dtype="float32", always_2d=True)
        if train:
            grid = range(0, len(data) // sr - 4, 5)
            starts = sorted(grid, key=lambda s: seed(f"train/{key}/{s}"))[:12]
            selections = [dict(start=s, truth=sorted({name for a, b, name in events
                          if min(s+5, b) > max(s, a)})) for s in starts]
        else:
            selections = manifest[dataset, key]
        rows, scores, embeddings = [], [], []
        for item in selections:
            start = item["start"]
            audio = mono(data[start*sr:(start+5)*sr])
            samples = {"original": audio}
            if augmentation:
                noise_name = sorted(noises)[seed(f"train-noise/{key}/{start}") % len(noises)]
                noise, nsr = noises[noise_name]
                samples = {"augmented": mixed(audio, resample(noise, nsr, sr)[:len(audio)])}
            elif not train:
                noise, nsr = noises[item["noise_file"]]
                samples["noise"] = mixed(audio, resample(noise, nsr, sr)[:len(audio)])
            for condition, sample in samples.items():
                raw = resample(sample, sr, 32000)
                views = [raw] if train else [raw, sosfilt(HP, raw).astype("float32")]
                view_scores, view_embeddings = [], []
                for view in views:
                    s, emb = net.session.run([net.output, "embeddings"], {net.input: view[None, :]})
                    if not np.isfinite(emb).all() or not ((0 <= s) & (s <= 1)).all():
                        raise ValueError(f"Invalid inference: {key}, {start}")
                    view_scores.append(s[0])
                    view_embeddings.append(emb[0])
                if not train:
                    original_top = net.labels[np.argsort(-view_scores[0])[:3]].tolist()
                    assert original_top == item[condition]["bn"]["all"], (key, start, condition)
                scores.append(view_scores)
                embeddings.append(view_embeddings)
                rows.append(dict(dataset=dataset, file=key, start=start, condition=condition,
                                 train=train, truth=sorted({canon(s) for s in item["truth"]}),
                                 stratified=item.get("stratified"), uniform=item.get("uniform"),
                                 group=item.get("group")))
        tmp = destination.with_suffix(".partial.npz")
        np.savez(tmp, rows=json.dumps(rows), scores=scores, embeddings=embeddings)
        tmp.replace(destination)
        if i % 25 == 0:
            print("Extracted", i+1, dataset, flush=True)
    expected = sum(1 for dataset, key in manifest if not augmentation or
                   (dataset == "WABAD" and key.split("_")[1] in days[key.split("_")[0]]))
    assert len(list(cache.glob("*.npz"))) == expected


def features(emb):
    scaled = 8 * emb / np.maximum(np.linalg.norm(emb, axis=-1, keepdims=True), 1e-9)
    return np.column_stack((scaled, np.ones(len(scaled))))


def fit_head(inputs, baseline, target, penalty=1):
    # Residual in log odds, L2 penalty including intercept. C = 1 throughout.
    def objective(params):
        logits = baseline + inputs @ params
        loss = np.logaddexp(0, logits).sum() - target @ logits + 0.5 * penalty * (params @ params)
        return loss, inputs.T @ (expit(logits) - target) + penalty * params
    fit = minimize(objective, np.zeros(inputs.shape[1]), jac=True, method="L-BFGS-B")
    if not fit.success:
        raise RuntimeError(fit.message)
    return fit.x


def train_and_score(folder, cache, output):
    net = Classifier(folder, "bn3")
    names = np.array([canon(label) for label in net.labels])
    bird = np.array([r["taxon_group"] == "Aves" for r in table(folder / "bn3.csv")])
    train_rows, vectors, scores, tests, aug_vectors, aug_scores = [], [], [], [], [], []
    for path in sorted(cache.glob("*.npz")):
        with np.load(path) as archive:
            rows = json.loads(archive["rows"].item())
            if rows[0]["train"]:
                train_rows.extend(rows)
                vectors.extend(archive["embeddings"][:, 0])
                scores.extend(archive["scores"][:, 0])
                with np.load(cache / "augmentation" / path.name) as aug:
                    aug_rows = json.loads(aug["rows"].item())
                    assert [(r["start"], r["truth"], r["file"]) for r in rows] == [
                        (r["start"], r["truth"], r["file"]) for r in aug_rows]
                    aug_vectors.extend(aug["embeddings"][:, 0])
                    aug_scores.extend(aug["scores"][:, 0])
            else:
                tests.append(path)
    support = defaultdict(list)
    for i, row in enumerate(train_rows):
        for species in row["truth"]:
            support[species].append(i)
    eligible = [i for i, name in enumerate(names) if len(support[name]) >= 5 and
                len({train_rows[j]["file"] for j in support[name]}) >= 2 and bird[i]]
    x = features(np.asarray(vectors))
    offsets = logit(np.clip(np.array(scores)[:, eligible], 1e-7, 1-1e-7))
    x_aug = np.vstack((x, features(np.asarray(aug_vectors))))
    offsets_aug = np.vstack((offsets, logit(np.clip(np.array(aug_scores)[:, eligible], 1e-7, 1-1e-7))))
    weights, biases, aug_weights = [], [], []
    for j, k in enumerate(eligible):
        y = np.zeros(len(train_rows))
        y[support[names[k]]] = 1
        weights.append(fit_head(x, offsets[:, j], y))
        biases.append(fit_head(x[:, -1:], offsets[:, j], y)[0])
        aug_weights.append(fit_head(x_aug, offsets_aug[:, j], np.tile(y, 2), penalty=2))
    weights = np.stack(weights, axis=1)
    aug_weights = np.stack(aug_weights, axis=1)
    model_dir = cache / "head"
    model_dir.mkdir(exist_ok=True)
    np.savez_compressed(model_dir / "wab-residual.npz", weights=weights.astype("float32"),
                        indices=eligible, labels=names[eligible], biases=biases,
                        augmented_weights=aug_weights.astype("float32"))
    print("Training:", len(train_rows), "windows,", len({r["file"] for r in train_rows}),
          "recordings,", len(eligible), "adapted species", flush=True)
    with output.open("w", encoding="utf-8") as out:
        for path in tests:
            with np.load(path) as archive:
                rows = json.loads(archive["rows"].item())
                raw, hp = archive["scores"].transpose(1, 0, 2)
                emb, hp_emb = archive["embeddings"].transpose(1, 0, 2)
            def corrected(base, residual):
                result = base.copy()
                result[:, eligible] = expit(logit(np.clip(base[:, eligible], 1e-7, 1-1e-7))
                                            + residual)
                return result
            adapted = corrected(raw, features(emb) @ weights)
            adapted_hp = corrected(hp, features(hp_emb) @ weights)
            augmented = corrected(raw, features(emb) @ aug_weights)
            augmented_hp = corrected(hp, features(hp_emb) @ aug_weights)
            methods = {"raw": raw, "hp": hp, "mean": (raw+hp)/2, "bias": corrected(raw, biases),
                       "adapted": adapted, "adapted_mean": (adapted+adapted_hp)/2,
                       "augmented": augmented, "augmented_mean": (augmented+augmented_hp)/2}
            for i, row in enumerate(rows):
                del row["train"]
                for method, arr in methods.items():
                    top = np.argsort(-arr[i])[:3]
                    row[method] = {"names": names[top].tolist(), "scores": arr[i, top].tolist(),
                                   "first_is_bird": bool(bird[top[0]])}
                out.write(json.dumps(row) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", type=Path)
    parser.add_argument("cache", type=Path, help="Dedicated temporary feature directory")
    parser.add_argument("output", type=Path)
    parser.add_argument("--augment-only", action="store_true", help="Only populate extra training features")
    args = parser.parse_args()
    with threadpool_limits(1, user_api="blas"):
        if args.augment_only:
            extract(args.folder, args.cache, augmentation=True)
        else:
            extract(args.folder, args.cache)
            extract(args.folder, args.cache, augmentation=True)
            train_and_score(args.folder, args.cache, args.output)
