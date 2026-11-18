"""Paired, deterministic CPU benchmark. See README.md for inputs and invocation."""
import argparse
import csv
import hashlib
import io
import itertools
import json
import math
import tarfile
import zipfile
from collections import defaultdict
from pathlib import Path

import numpy as np
import onnxruntime as ort
import soundfile as sf
from scipy.signal import resample_poly

SITES = "COU DEVA EMP HUAP MILLAN OIO SD".split()
ALIAS = {"Phylloscopus sibillatrix": "Phylloscopus sibilatrix",
         "Coloeus monedula": "Corvus monedula", "Chrysococcyx lucidus": "Chalcites lucidus",
         "Corvinella corvina": "Lanius corvinus", "Accipiter gentilis": "Astur gentilis"}


def normalize(name):
    return ALIAS.get(name, name)


def seed(key):
    return int.from_bytes(hashlib.sha256(key.encode()).digest()[:8], "little")


def table(path):
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def events(folder):
    ceb, wab = defaultdict(list), defaultdict(list)
    for row in table(folder / "ceb.csv"):
        if row["label_status"] == "valid":
            ceb[row["filepath"]].append((float(row["start_time"]), float(row["end_time"]),
                                         normalize(row["scientific_name"])))
    for row in table(folder / "wab.csv"):
        if row["Site"] in SITES:
            wab[row["Recording"]].append((float(row["Begin_Time_(s)"]), float(row["End_Time_(s)"]),
                                           normalize(row["Species"])))
    return ceb, wab


def recordings(folder):
    ceb, wab = events(folder)
    seen = set()
    with tarfile.open(folder / "ceb.tar.gz", "r|gz") as archive:
        for item in archive:
            key = "/".join(item.name.split("/")[-2:])
            if key in ceb and item.isfile():
                seen.add(key)
                yield "CEB", key, ceb[key], io.BytesIO(archive.extractfile(item).read())
    if set(ceb) != seen:
        raise ValueError(f"Missing CEB audio: {set(ceb) - seen}")
    seen.clear()
    for site in SITES:
        with zipfile.ZipFile(folder / (site + ".zip")) as archive:
            for name in sorted(archive.namelist()):
                key = name.split("/")[-1]
                if key in wab and not name.startswith("__MACOSX/"):
                    seen.add(key)
                    yield "WABAD", key, wab[key], io.BytesIO(archive.read(name))
    if set(wab) != seen:
        raise ValueError(f"Missing WABAD audio: {set(wab) - seen}")


def choose_windows(key, annotations, seconds):
    # Select without scores, one single-species and one truly overlapping window per file.
    strata = defaultdict(list)
    grid = range(0, int(seconds) - 4, 5)
    uniform = set(sorted(grid, key=lambda start: seed(f"uniform/{key}/{start}"))[:3])
    selected = {}
    for start in grid:
        clipped = [(max(a, start), min(b, start+5), sp) for a,b,sp in annotations
                   if min(b, start+5) > max(a, start)]
        species = sorted({sp for _,_,sp in clipped})
        if len(species) == 1:
            group = "single"
        elif any(s != t and min(b,d)-max(a,c) >= 0.1
                 for (a,b,s),(c,d,t) in itertools.combinations(clipped, 2)):
            group = "overlap"
        else:
            group = "other" if species else "empty"
        if start in uniform:
            selected[start] = (group, species, False)
        if group in ("overlap", "single"):
            strata[group].append((start, species))
    for group, windows in sorted(strata.items()):
        start, species = min(windows, key=lambda w: seed(f"{key}/{w[0]}"))
        selected[start] = (group, species, True)
    for start, (group, species, stratified) in sorted(selected.items()):
        yield group, start, species, stratified, start in uniform


def resample(samples, sr, target):
    gcd = math.gcd(sr, target)
    return resample_poly(samples, target//gcd, sr//gcd).astype("float32") if sr != target else samples


def mono(data):
    return data.mean(axis=1, dtype="float32")


def mixed(samples, noise):
    gain = np.linalg.norm(samples) / max(np.linalg.norm(noise), 1e-9)
    combined = samples + gain*noise
    return combined / max(1., np.max(np.abs(combined)))


class Classifier:
    def __init__(self, folder, model):
        self.model = model
        opt = ort.SessionOptions()
        opt.intra_op_num_threads = 4
        opt.inter_op_num_threads = 1
        self.session = ort.InferenceSession(str(folder / (model+".onnx")), opt,
                                            providers=["CPUExecutionProvider"])
        self.input = self.session.get_inputs()[0].name
        if model == "bn3":
            rows = table(folder / "bn3.csv")
            assert [int(r["ix"]) for r in rows] == list(range(len(rows)))
            labels = [r["scientific_name"] or r["common_name"] for r in rows]
        else:
            labels = (folder / (model+".txt")).read_text(encoding="utf-8-sig").splitlines()
            if model == "perch":
                assert labels.pop(0) == "inat2024_fsd50k"
            else:
                labels = [n.split("_")[0] for n in labels]
        self.labels = np.asarray([normalize(n) for n in labels])
        self.output = next(t.name for t in self.session.get_outputs() if t.shape[-1] == len(labels))

    def predict(self, audio, sr, shared):
        if self.model == "bn":
            x = resample(audio, sr, 48000)
            batch = np.stack([x[:144000], x[-144000:]])
        else:
            batch = resample(audio, sr, 32000)[None, :]
        logits = self.session.run([self.output], {self.input: batch})[0].max(axis=0)
        if len(logits) != len(self.labels) or not np.isfinite(logits).all():
            raise ValueError(f"Invalid output from {self.model}")
        ranked = self.labels[np.argsort(-logits)]
        common = [idx for idx in np.argsort(-logits) if self.labels[idx] in shared][:3]
        return {"all": ranked[:3].tolist(), "shared": self.labels[common].tolist(),
                "shared_score": float(logits[common[0]])}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--birdnet", choices=("2.4","3.0"), default="2.4")
    args = parser.parse_args()
    nets = {"bn": Classifier(args.folder, "bn3" if args.birdnet == "3.0" else "bn"),
            "perch": Classifier(args.folder, "perch")}
    birds = {normalize(r["scientific_name"]) for r in table(args.folder / "taxonomy.csv")
             if r["category"] == "species"}
    shared = set(nets["bn"].labels) & set(nets["perch"].labels) & birds
    print("Shared bird species:", len(shared), flush=True)
    noises = {}
    for path in sorted(args.folder.glob("1-*.wav")):
        data, sr = sf.read(path, dtype="float32", always_2d=True)
        noises[path.name] = (mono(data), sr)
    assert len(noises) == 9
    with args.output.open("w", encoding="utf8") as out:
        for i, (dataset, key, annotations, buf) in enumerate(recordings(args.folder)):
            data, sr = sf.read(buf, dtype="float32", always_2d=True)
            for group, start, truth, stratified, uniform in choose_windows(key, annotations, len(data)/sr):
                audio = mono(data[start*sr:(start+5)*sr])
                noise_name = sorted(noises)[seed(f"noise/{key}/{start}") % len(noises)]
                noise, nsr = noises[noise_name]
                noisy = mixed(audio, resample(noise, nsr, sr)[:len(audio)])
                row = dict(dataset=dataset, file=key, start=start, group=group, truth=truth,
                           shared_truth=sorted(set(truth)&shared), noise_file=noise_name,
                           stratified=stratified, uniform=uniform)
                for condition, sample in [("original",audio),("noise",noisy)]:
                    row[condition] = {n:m.predict(sample,sr,shared) for n,m in nets.items()}
                out.write(json.dumps(row)+"\n")
            if i % 20 == 0:
                out.flush()
                print(dataset, i, key, flush=True)


if __name__ == "__main__":
    main()
