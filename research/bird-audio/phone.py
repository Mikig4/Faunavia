"""Paired BirdNET+ 3.0 comparison on annotated recordings from phones."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent


def handset_examples():
    rows = json.loads((HERE / "phone-xc.json").read_text(encoding="utf8"))
    for row in rows:
        row["file"] = row.get("member", row["id"])
    sonoma = json.loads((HERE / "phone-lg.json").read_text(encoding="utf8"))
    for item in sonoma["recordings"]:
        events = [[e["start_s"], e["end_s"]] for e in item["events"]]
        rows.append(dict(id=item["id"], author=item["recorder_id"], country="United States",
                         panel="LG" if item["overlap_s"] >= 0.1 else "LG-short",
                         file="lg/source/"+item["id"]+".wav", sha256=item["source_sha256"],
                         start_frame=item["start_frame"], focal=item["species"][0],
                         secondary=item["species"][1], focal_event_s=events[0],
                         secondary_event_s=events[1], overlap_s=item["overlap_s"]))
    return rows


def ranked_pair(labels, values, pair):
    if not np.isfinite(values).all():
        raise ValueError("Non-finite classification scores")
    ranking = np.argsort(-values, kind="stable")
    names = labels[ranking]
    ranks = []
    for name in pair:
        positions = np.flatnonzero(names == name)
        ranks.append(int(positions[0])+1 if len(positions) else None)
    return dict(top3=names[:3].tolist(), scores=values[ranking[:3]].tolist(), ranks=ranks)


def run(folder, model_folder, output, head=None):
    import soundfile as sf
    from evaluate import Classifier, mono, normalize, resample
    from separate import attenuate_floor, scores

    aliases = json.loads((HERE / "evaluation-names.json").read_text())["aliases"]
    def canonical(name):
        name = normalize(name)
        return aliases.get(name, name)

    model = Classifier(model_folder, "bn3")
    labels = np.array([canonical(n) for n in model.labels])
    if head:
        from adapt import features
        from scipy.special import expit, logit
        with np.load(head) as saved:
            columns, weights = saved["indices"], saved["augmented_weights"]
            assert np.array_equal(labels[columns], saved["labels"])
    results = []
    for row in handset_examples():
        path = folder / row["file"]
        with path.open("rb") as f:
            if hashlib.file_digest(f, "sha256").hexdigest() != row["sha256"]:
                raise ValueError(f"Audio checksum mismatch: {path}")
        with sf.SoundFile(path) as audio:
            rate = audio.samplerate
            if "start_frame" in row:
                start = row["start_frame"]
            elif "center_s" in row:
                start = min(max(0, round((row["center_s"]-2.5)*rate)), max(0, len(audio)-5*rate))
            else:
                assert row["panel"] == "Italy-clip" and len(audio) <= 5*rate
                start = 0
            audio.seek(start)
            wave = mono(audio.read(5*rate, dtype="float32", always_2d=True))
        lo, hi = start/rate, (start+len(wave))/rate
        pair = [canonical(row[k]) for k in ("focal", "secondary")]
        assert len(set(pair)) == 2
        if "overlap_s" in row:
            a, b = row["focal_event_s"], row["secondary_event_s"]
            overlap = min(hi,b[1],a[1]) - max(lo,b[0],a[0])
            assert overlap >= min(0.1, row["overlap_s"])-1e-5, (row["id"], overlap)
        sample = resample(wave, rate, 32000)[:160000]
        result = {key: row[key] for key in ("id","country","author","panel")}
        result.update(pair=pair, start_s=lo, end_s=hi)
        for method, view in (("raw", sample), ("filtered", attenuate_floor(sample))):
            padded = np.pad(view, (0, 160000-len(view)))
            if head and method == "raw":
                pred, emb = model.session.run([model.output, "embeddings"], {model.input: padded[None, :]})
                values = pred[0]
                adjusted = values.copy()
                offsets = logit(np.clip(values[columns], 1e-7, 1-1e-7))
                adjusted[columns] = expit(offsets + (features(emb) @ weights)[0])
                result["wab_head"] = ranked_pair(labels, adjusted, pair)
                result["adapted_targets"] = [name in labels[columns] for name in pair]
            else:
                values = scores(model, padded)
            result[method] = ranked_pair(labels, values, pair)
        results.append(result)
    output.write_text("".join(json.dumps(r, ensure_ascii=False)+"\n" for r in results), encoding="utf8")


def report(rows, comparison="filtered"):
    if {r["id"] for r in rows} != {r["id"] for r in handset_examples()} or len(rows) != len({r["id"] for r in rows}):
        raise ValueError("Missing, unexpected or duplicated recording")
    strong = [r for r in rows if r["panel"] in ("CEB-XC", "XC")]
    panels = {"paired": strong, "Italy": [r for r in strong if r["country"] == "Italy"]}
    panels.update({k: [r for r in rows if r["panel"] == k] for k in sorted({r["panel"] for r in rows})})
    for panel, part in panels.items():
        totals = {}
        for target in ("both", "secondary"):
            if target == "secondary" and panel.startswith("LG"):
                continue  # no focal/background designation in this corpus
            counts = Counter()
            groups = defaultdict(list)
            for r in part:
                hits = []
                for method in ("raw",comparison):
                    ranks = r[method]["ranks"]
                    ranks = ranks if target == "both" else ranks[1:]
                    hits.append(int(all(k is not None and k <= 3 for k in ranks)))
                counts[tuple(hits)] += 1
                groups[r["author"]].append(hits[1]-hits[0])
            stat = dict(raw=counts[1,1]+counts[1,0], gained=counts[0,1], lost=counts[1,0], n=len(part))
            stat[comparison] = counts[1,1]+counts[0,1]
            if panel == "paired":
                blocks = list(groups.values())
                idx = np.random.default_rng(42).integers(len(blocks), size=(10000,len(blocks)))
                deltas = np.array([sum(b) for b in blocks])[idx].sum(axis=1)
                sizes = np.array([len(b) for b in blocks])[idx].sum(axis=1)
                stat["delta_95_pp"] = np.percentile(100*deltas/sizes, [2.5,97.5]).round(2).tolist()
            totals[target] = stat
        print(panel, json.dumps(totals))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("predictions", type=Path)
    ap.add_argument("--audio", type=Path)
    ap.add_argument("--model", type=Path)
    ap.add_argument("--head", type=Path, help="Frozen augmented WABAD residual head from adapt.py")
    args = ap.parse_args()
    if bool(args.audio) != bool(args.model):
        ap.error("Provide both --audio and --model to run inference")
    if args.audio:
        run(args.audio, args.model, args.predictions, args.head)
    rows = [json.loads(line) for line in args.predictions.read_text(encoding="utf8").splitlines()]
    report(rows)
    if all("wab_head" in r for r in rows):
        report(rows, "wab_head")
