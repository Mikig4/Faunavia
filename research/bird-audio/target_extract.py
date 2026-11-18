"""Reference-guided monaural bird extraction. See ESTRAZIONE.md."""
import argparse
import hashlib
import io
import json
import tarfile
from collections import defaultdict
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import istft, stft
from sklearn.cluster import MiniBatchKMeans
from threadpoolctl import threadpool_limits

from evaluate import Classifier, mono, normalize, resample, seed, table
from phone import HERE, handset_examples
from separate import scores

STFT = dict(fs=32000, nperseg=1024, noverlap=768)
ALIASES = json.loads((HERE / "evaluation-names.json").read_text())["aliases"]


def name(label):
    return ALIASES.get(normalize(label), normalize(label))


def unit_columns(x):
    return x / np.maximum(np.linalg.norm(x, axis=0, keepdims=True), 1e-10)


def build(audio, work):
    excluded = handset_examples()
    ids = {r["id"] for r in excluded}
    authors = {r["author"].casefold().strip() for r in excluded}
    grouped = defaultdict(list)
    for r in table(audio / "ceb-xc.csv"):
        if r["label_status"] == "valid":
            grouped[r["filepath"]].append(r)
    choices = defaultdict(list)
    for member, rows in grouped.items():
        r = rows[0]
        if "XC"+r["xc_id"] in ids or r["xc_recordist"].strip().casefold() in authors:
            continue
        focal = name(" ".join(r["xc_original_scientific_name"].split()[:2]))
        if any(name(s["scientific_name"]) != focal for s in rows):
            continue
        boxes = sorted(rows, key=lambda s: -(float(s["end_time"])-float(s["start_time"])))
        boxes = [s for s in boxes if float(s["end_time"])-float(s["start_time"]) >= .1]
        if boxes:
            choices[focal].append((member, boxes[:4]))
    selected = {}
    for species, options in sorted(choices.items()):
        if len(options) < 4:
            continue
        for i, (member, boxes) in enumerate(sorted(options,key=lambda v:seed(v[0]))[:9]):
            selected[member] = species, boxes, i == 0
    frames, probes, sources = defaultdict(list), {}, []
    with tarfile.open(audio / "verified-ceb.tar.gz","r|gz") as archive:
        for item in archive:
            if item.name not in selected:
                continue
            species, boxes, probe = selected[item.name]
            data, sr = sf.read(io.BytesIO(archive.extractfile(item).read()),dtype="float32",always_2d=True)
            wave = resample(mono(data),sr,32000)
            collected = []
            for b in boxes:
                clip = wave[int(float(b["start_time"])*32000):int(float(b["end_time"])*32000)]
                if len(clip) < 1024 or np.max(np.abs(clip)) < 1e-6:
                    continue
                if probe:
                    probes[species] = clip[:64000].copy()
                    break
                freq, _, spec = stft(clip, **STFT)
                mag = np.abs(spec)
                mag[(freq<float(b["low_freq"])) | (freq>float(b["high_freq"]))] = 0
                valid = mag[:,np.linalg.norm(mag,axis=0)>1e-7]
                if valid.shape[1]:
                    collected.append(unit_columns(valid[:,::max(1,valid.shape[1]//24)]).T)
            if collected:
                frames[species].extend(collected)
            if collected or (probe and species in probes):
                sources.append(dict(member=item.name, species=species, probe=probe, xc=boxes[0]["xc_id"],
                                    author=boxes[0]["xc_recordist"], license=boxes[0]["license"]))
    bank = {}
    for species, items in sorted(frames.items()):
        data = np.vstack(items)
        km = MiniBatchKMeans(n_clusters=min(12,len(data)),random_state=41,n_init=1,batch_size=128)
        bank[species] = unit_columns(km.fit(data).cluster_centers_.T).astype("float32")
    np.savez_compressed(work/"bank.npz", **bank)
    np.savez_compressed(work/"probes.npz", **probes)
    (work/"refs.json").write_text(json.dumps(sources,ensure_ascii=False,indent=2),encoding="utf8")


def isolate(wave, targets, bank):
    """Joint non-negative fit; target-specific soft masks reuse mixture phase."""
    targets = list(dict.fromkeys(n for n in targets if n in bank))
    if not targets:
        return {}
    length = len(wave)
    if not length:
        raise ValueError("Empty audio window")
    if length < STFT["nperseg"]:
        wave = np.pad(wave, (0, STFT["nperseg"] - length))
    _, _, z = stft(wave, **STFT)
    v = np.abs(z)
    floor = np.quantile(v, .2, axis=1).astype("float32")
    # Broad, overlapping frequency bands can absorb interference outside the learned calls.
    f = np.arange(len(floor))
    noise = [floor]+[np.exp(-.5*((f-center)/24)**2) for center in range(0,len(floor),32)]
    blocks = [bank[n] for n in targets]+[unit_columns(np.array(noise,dtype="float32").T)]
    w = np.concatenate(blocks,axis=1)
    cross, gram = w.T @ v, w.T @ w
    h = np.maximum(cross,1e-9)
    for _ in range(50):
        h *= cross / np.maximum(gram @ h,1e-12)
    if not np.isfinite(h).all():
        raise ValueError("Invalid dictionary fit")
    boundaries = np.cumsum([b.shape[1] for b in blocks[:-1]])
    parts = [b @ activation for b,activation in zip(blocks,np.split(h,boundaries))]
    denom = np.maximum(np.sum(np.square(parts),axis=0),1e-16)
    out = {}
    for target, part in zip(targets,parts):
        _, stem = istft(z * (part**2/denom), **STFT)
        out[target] = stem[:length].astype("float32")
    return out


def classify(wave, model, labels, bank):
    raw = scores(model,wave)
    shortlist = np.argsort(-raw,kind="stable")[:12]
    stems = isolate(wave, labels[shortlist], bank)
    fused = raw.copy()
    best = raw.copy()
    extracted = {}
    for i in shortlist:
        n = labels[i]
        if n in stems:
            if np.max(np.abs(stems[n])) <= 1e-8:
                fused[i] = 0
                continue
            view = scores(model,stems[n])
            score = float(view[i])
            extracted[n] = dict(score=score, original=float(raw[i]),
                                stem_top1=str(labels[np.argmax(view)]), rank=1+int(np.sum(view>score)))
            fused[i] = np.sqrt(raw[i]*score)
            best[i] = max(raw[i], score)
    return raw, fused, best, stems, extracted


def evaluate(audio, models, work, output):
    model = Classifier(models,"bn3")
    labels = np.array([name(n) for n in model.labels])
    baseline = {r["id"]:r for r in map(json.loads,(HERE/"phone-predictions.jsonl").read_text(encoding="utf8").splitlines())}
    with np.load(work/"bank.npz") as saved:
        bank = dict(saved)
    rows = []
    for r in handset_examples():
        ref = baseline[r["id"]]
        path = audio/r["file"]
        with path.open("rb") as f:
            assert hashlib.file_digest(f,"sha256").hexdigest() == r["sha256"]
        with sf.SoundFile(path) as f:
            sr = f.samplerate
            f.seek(round(ref["start_s"]*sr))
            wave = resample(mono(f.read(5*sr,dtype="float32",always_2d=True)),sr,32000)[:160000]
        wave = np.pad(wave,(0,160000-len(wave)))
        raw, fused, best, stems, extracted = classify(wave,model,labels,bank)
        order = np.argsort(-raw,kind="stable")
        assert labels[order[:3]].tolist() == ref["raw"]["top3"]
        row = dict(id=r["id"], panel=r["panel"], country=r["country"], pair=ref["pair"],
                   shortlist=labels[order[:12]].tolist(), extracted=extracted, raw=ref["raw"]["top3"],
                   fused=labels[np.argsort(-fused,kind="stable")[:3]].tolist(),
                   best=labels[np.argsort(-best,kind="stable")[:3]].tolist())
        if r["country"]=="Italy" and r["panel"]=="CEB-XC":
            for n,stem in stems.items():
                sf.write(work/f"{r['id']}-{n}.wav",stem,32000,subtype="FLOAT")
            sf.write(work/f"{r['id']}-mix.wav",wave,32000,subtype="FLOAT")
        rows.append(row)
    output.write_text("".join(json.dumps(r,ensure_ascii=False)+"\n" for r in rows),encoding="utf8")


def sisdr(target, estimate):
    target = target.astype("float64")-target.mean()
    estimate = estimate.astype("float64")-estimate.mean()
    projected = target*((estimate @ target)/max(target @ target,1e-16))
    return 10*np.log10((projected @ projected+1e-16)/(np.sum((estimate-projected)**2)+1e-16))


def field_control(models, work, output):
    from evaluate import recordings
    pool = [json.loads(s) for s in (HERE/"predictions-v3.jsonl").read_text().splitlines()]
    chosen = {}
    for dataset in ("CEB","WABAD"):
        unique = {}
        for r in sorted((r for r in pool if r["dataset"]==dataset and r["uniform"]),
                        key=lambda r:seed(f"target-field/{r['file']}/{r['start']}")):
            unique.setdefault(r["file"],r)
        chosen.update({(dataset,r["file"]):r for r in list(unique.values())[:48]})
    model = Classifier(models,"bn3")
    labels = np.array([name(n) for n in model.labels])
    birds = np.array([r["taxon_group"]=="Aves" for r in table(models/"bn3.csv")])
    rows = []
    with np.load(work/"bank.npz") as data:
        bank = dict(data)
    for ds,key,_,buf in recordings(models):
        if (ds,key) not in chosen:
            continue
        r = chosen[ds,key]
        with sf.SoundFile(buf) as f:
            f.seek(r["start"]*f.samplerate)
            wave=resample(mono(f.read(5*f.samplerate,dtype="float32",always_2d=True)),f.samplerate,32000)
        raw,fused,best,_,_ = classify(wave,model,labels,bank)
        result = dict(dataset=ds,file=key,start=r["start"],truth=[name(n) for n in r["truth"]])
        for method,values in (("raw",raw),("fused",fused),("best",best)):
            i = np.argmax(values)
            result[method] = dict(label=labels[i], score=float(values[i]), bird=bool(birds[i]))
        rows.append(result)
    assert len(rows)==len(chosen)==96
    output.write_text("".join(json.dumps(r)+"\n" for r in rows),encoding="utf8")


def simulated(work, output):
    with np.load(work/"bank.npz") as data, np.load(work/"probes.npz") as probes:
        bank = dict(data)
        keys = sorted(set(bank)&set(probes),key=seed)
        rows = []
        for first,second in zip(keys[::2],keys[1::2]):
            clips = []
            for n in (first,second):
                clip = probes[n]
                wave = np.zeros(160000,dtype="float32")
                start = (160000-len(clip))//2
                wave[start:start+len(clip)] = clip/max(np.linalg.norm(clip),1e-9)
                clips.append(wave)
            for db in (0,-6,-12):
                target = clips[1]*(10**(db/20))
                mix = clips[0]+target
                stem = isolate(mix,[first,second],bank)[second]
                rows.append(dict(first=first,second=second,db=db,
                                 improvement_db=float(sisdr(target,stem)-sisdr(target,mix))))
            # Query the same target in a clip from the interfering species alone.
            absent = isolate(clips[0],[first,second],bank)[second]
            residual = float(20*np.log10(max(np.linalg.norm(absent),1e-12)/np.linalg.norm(clips[0])))
            for row in rows[-3:]:
                row["absent_stem_relative_db"] = residual
    output.write_text(json.dumps(rows,indent=2),encoding="utf8")


if __name__=="__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("mode",choices=["build","test","sim","extract","field"])
    ap.add_argument("audio",type=Path)
    ap.add_argument("work",type=Path)
    ap.add_argument("--model",type=Path)
    ap.add_argument("--output",type=Path)
    ap.add_argument("--input-wav",type=Path)
    ap.add_argument("--start",type=float,default=0)
    ap.add_argument("--species",nargs="+",help="Quoted scientific names to extract jointly")
    args=ap.parse_args()
    if args.mode in ("test", "field") and not args.model:
        ap.error(f"{args.mode} requires --model")
    if args.mode in ("test", "field", "sim") and not args.output:
        ap.error(f"{args.mode} requires --output")
    args.work.mkdir(exist_ok=True,parents=True)
    with threadpool_limits(1):
        if args.mode=="build":
            build(args.audio,args.work)
        elif args.mode=="sim":
            simulated(args.work,args.output)
        elif args.mode=="field":
            field_control(args.model,args.work,args.output)
        elif args.mode=="extract":
            if not args.input_wav or not args.species or not args.output or args.start<0:
                ap.error("extract requires --input-wav, --species and --output (directory)")
            with sf.SoundFile(args.input_wav) as f:
                start = round(args.start*f.samplerate)
                if start >= len(f):
                    ap.error("--start is outside the recording")
                f.seek(start)
                wave=resample(mono(f.read(5*f.samplerate,dtype="float32",always_2d=True)),f.samplerate,32000)
            with np.load(args.work/"bank.npz") as data:
                targets=[name(n) for n in args.species]
                missing=set(targets)-set(data)
                if missing:
                    ap.error(f"No references for {sorted(missing)}")
                stems=isolate(wave,targets,dict(data))
            args.output.mkdir(parents=True,exist_ok=True)
            for species,stem in stems.items():
                sf.write(args.output/f"{species}.wav",stem,32000,subtype="FLOAT")
        else:
            evaluate(args.audio,args.model,args.work,args.output)
