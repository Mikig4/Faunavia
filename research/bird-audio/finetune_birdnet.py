"""Fine-tune BirdNET+ 12K's native global classification head on post-release recordings."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

import av
import numpy as np
import onnx
from onnx import numpy_helper
import onnxruntime as ort
from scipy.special import expit, logit
import soundfile as sf
import torch
from torch.nn import functional as F

from adapt import canon
from evaluate import Classifier, mono, normalize, resample, table
from phone import HERE, handset_examples

RATE = 32000
SAMPLES = RATE * 5
BASE = 0.25


def digest(path):
    with path.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()


def save_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+"\n", encoding="utf8")


def decode(path):
    dropped = 0
    with av.open(str(path)) as container:
        converter = av.AudioResampler(format="fltp", layout="mono", rate=RATE)
        frames = []
        for packet in container.demux(audio=0):
            try:
                decoded = packet.decode()
            except av.error.InvalidDataError:
                dropped += 1
                continue
            for frame in decoded:
                frames.extend(f.to_ndarray().ravel() for f in converter.resample(frame))
        frames.extend(f.to_ndarray().ravel() for f in converter.resample(None))
    return (np.concatenate(frames) if frames else np.empty(0, dtype="float32")), dropped


def forward(net, wave):
    s,e = net.session.run([net.output,"embeddings"],{net.input:wave[None,:]})
    if not np.isfinite(s).all() or not np.isfinite(e).all():
        raise ValueError("Non-finite BirdNET outputs")
    return s[0],e[0]


def train_features(net, work, sources):
    cache = work/"features"
    cache.mkdir(exist_ok=True)
    rows = []
    seen = set()
    for i, source in enumerate(sources):
        r = dict(source, label=normalize(source["label"]))
        if "sha256" not in r:
            rows.append(r)
            continue
        key = r["sha256"]
        target = cache/(key+".npz")
        path = work/"media"/r["id"]
        assert digest(path)==key
        try:
            if not target.exists():
                wave, dropped = decode(path)
                if not len(wave) or not np.isfinite(wave).all() or np.std(wave)<1e-7:
                    raise ValueError("No usable audio")
                pcm = hashlib.sha256(wave.tobytes()).hexdigest()
                # One bag per recording, three evenly distributed candidate windows at most.
                last = max(0,len(wave)-SAMPLES)
                starts = np.unique(np.linspace(0,last,min(3, int(np.ceil(len(wave)/SAMPLES))),dtype=int))
                start_scores, embeddings, valid = [], [], []
                for start in starts:
                    view = wave[start:start+SAMPLES]
                    if np.std(view)<1e-7:
                        continue
                    score, emb = forward(net,np.pad(view,(0,SAMPLES-len(view))))
                    valid.append(start)
                    start_scores.append(score)
                    embeddings.append(emb)
                if not valid:
                    raise ValueError("Only silence in sampled windows")
                tmp = target.with_suffix(".partial.npz")
                np.savez(tmp, scores=start_scores, emb=embeddings, starts=valid, pcm=pcm,dropped=dropped)
                tmp.replace(target)
            with np.load(target) as archive:
                pcm = str(archive["pcm"])
                label = int(np.flatnonzero(net.labels == r["label"])[0])
                choice = int(archive["scores"][:,label].argmax())
                example = (pcm, r["label"])
                if example in seen:
                    rows.append(dict(r, status="duplicate PCM and species",pcm=pcm))
                    continue
                seen.add(example)
                rows.append(dict(r,status="used",choice=choice,start=int(archive["starts"][choice])/RATE,pcm=pcm,
                                 dropped_packets=int(archive["dropped"]) if "dropped" in archive else 0))
        except (av.FFmpegError, ValueError) as err:
            rows.append(dict(r,status=str(err)[:160]))
        if i%100==0:
            print("Training features",i,len(sources),flush=True)
    return rows


def phone_features(net, work, phones, older, fresh=False):
    path = work/("new-phone-features.npz" if fresh else "phone-features.npz")
    if path.exists():
        return
    rows, raw, vectors = [], [], []
    def add(audio, metadata):
        score, emb = forward(net,audio)
        raw.append(score)
        vectors.append(emb)
        rows.append(metadata)
    phones_manifest = json.loads((HERE/"temporal-phones.json").read_text(encoding="utf8"))["recordings"]
    assert {r["device"] for r in phones_manifest if r["split"]=="test"}.isdisjoint(
        r["device"] for r in phones_manifest if r["split"]=="cal")
    panel = phones_manifest
    if fresh:
        panel = json.loads((HERE/"finetune-new-phones.json").read_text(encoding="utf8"))
        old = json.loads((HERE/"phone-lg.json").read_text(encoding="utf8"))["recordings"]
        deployments = {r["deployment"] for r in phones_manifest + old}
        assert deployments.isdisjoint(r["deployment"] for r in panel)
    for rec in panel:
        file = phones/rec["file"]
        assert digest(file)==rec["sha256"]
        data, rate = sf.read(file, always_2d=True, dtype="float32")
        audio = resample(mono(data),rate,RATE)
        for j, truth in enumerate(rec["windows"]):
            row = dict(file=rec["file"],sha256=rec["sha256"],panel="fresh" if fresh else rec["split"],start=5*j,truth=truth)
            if j==rec["second"]//5:
                row.update(second=rec["secondary"],other=rec["companion"])
            add(audio[j*SAMPLES:(j+1)*SAMPLES],row)
    reference = {r["id"]:r for r in map(json.loads,(HERE/"phone-predictions.jsonl").read_text(encoding="utf8").splitlines())}
    supplemental = json.loads((HERE/"temporal-xc.json").read_text(encoding="utf8"))
    extra = {r["id"]:r for r in json.loads((HERE/"temporal-xc-results.json").read_text())}
    for rec in ([] if fresh else handset_examples()+supplemental):
        newer = rec["id"] in extra
        file = phones/rec["id"] if newer else older/rec["file"]
        assert digest(file)==rec["sha256"]
        baseline = extra[rec["id"]] if newer else reference[rec["id"]]
        start = baseline["start"] if newer else baseline["start_s"]
        with sf.SoundFile(file) as f:
            f.seek(round(start*f.samplerate))
            x = resample(mono(f.read(5*f.samplerate,always_2d=True,dtype="float32")),f.samplerate,RATE)[:SAMPLES]
        pair = [rec["companion"],rec["secondary"]] if newer else baseline["pair"]
        row=dict(file=rec["id"], panel="extra" if newer else rec["panel"],start=start,sha256=rec["sha256"],
                 country=rec["country"],truth=pair,second=pair[1],other=pair[0])
        add(np.pad(x,(0,SAMPLES-len(x))),row)
    np.savez(path,raw=np.array(raw),emb=np.array(vectors),rows=json.dumps(rows))


def fit(net, work, models, records):
    rows = [r for r in records if r.get("status")=="used"]
    indices = np.array(sorted({int(np.flatnonzero(net.labels==r["label"])[0]) for r in rows}))
    x, offset, y = [], [], []
    for r in rows:
        with np.load(work/"features"/(r["sha256"]+".npz")) as f:
            x.append(f["emb"][r["choice"]])
            offset.append(logit(np.clip(f["scores"][r["choice"],indices],1e-7,1-1e-7)))
        y.append(int(np.flatnonzero(net.labels[indices]==r["label"])[0]))
    x, logits, labels = torch.tensor(np.stack(x)),torch.tensor(np.stack(offset)),torch.tensor(y)
    # Native Gemm receives the raw embedding. Its contribution to the global logit is 0.4.
    delta = torch.nn.Parameter(torch.zeros(len(indices),x.shape[1]))
    bias = torch.nn.Parameter(torch.zeros(len(indices)))
    optimizer = torch.optim.AdamW([delta,bias],lr=0.0004)
    teacher = torch.sigmoid(logits)
    seed = torch.Generator().manual_seed(14026)
    curve = []
    for epoch in range(8):
        total = 0
        for ix in torch.randperm(len(labels),generator=seed).split(48):
            pred = logits[ix]+0.4*F.linear(x[ix],delta,bias)
            # Only the focal bird is supervised. Retain the teacher's other predictions.
            positive = F.softplus(-pred.gather(1,labels[ix,None])).mean()
            anchor = F.binary_cross_entropy_with_logits(pred,teacher[ix],reduction="none").sum(1).mean()
            loss = positive + anchor + 0.01*delta.square().sum()/len(indices)
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
            total+=positive.item()*len(ix)
        curve.append(total/len(labels))
    dw,db = delta.detach().numpy(),bias.detach().numpy()
    graph = onnx.load(models/"bn3.onnx")
    assert any(n.op_type=="Gemm" and list(n.input)==["embeddings","head.weight","head.bias"] for n in graph.graph.node)
    tensors = {w.name:w for w in graph.graph.initializer}
    for key,change in [("head.weight",dw),("head.bias",db)]:
        arr=numpy_helper.to_array(tensors[key]).copy()
        arr[indices]+=change
        tensors[key].CopyFrom(numpy_helper.from_array(arr,name=key))
    export=work/"birdnet-finetuned.onnx"
    onnx.save(graph,export)
    np.savez_compressed(work/"head-delta.npz",indices=indices,weight=dw,bias=db)
    return indices,dw,db,dict(examples=len(rows),classes=len(indices),positive_loss=curve,
                               model_sha256=digest(export),base_sha256=digest(models/"bn3.onnx"))


def validate(net,work,models,indices,dw,db):
    with np.load(work/"phone-features.npz") as cache:
        base,emb,rows = cache["raw"],cache["emb"],json.loads(str(cache["rows"]))
    with np.load(work/"new-phone-features.npz") as cache:
        base=np.concatenate((base,cache["raw"]))
        emb=np.concatenate((emb,cache["emb"]))
        rows+=json.loads(str(cache["rows"]))
    new = base.copy()
    new[:,indices] = expit(logit(np.clip(base[:,indices],1e-7,1-1e-7)) + 0.4*(emb@dw.T+db))
    birds = np.array([r["taxon_group"]=="Aves" for r in table(models/"bn3.csv")])
    names=np.array([canon(s) for s in net.labels])
    cal = [i for i,r in enumerate(rows) if r["panel"]=="cal"]
    def errors(values):
        return np.concatenate([values[i,birds & ~np.isin(names,rows[i]["truth"])] for i in cal])
    budget = int(sum(errors(base)>=BASE))
    err=np.sort(errors(new))[::-1]
    threshold=float(np.nextafter(float(err[budget]),np.inf))
    # Emit all actual suggestions, including unknown classes, for audit.
    for i,r in enumerate(rows):
        for method,values,cutoff in [("raw",base[i],BASE),("tuned",new[i],threshold),("fixed",new[i],BASE)]:
            keep=np.flatnonzero(birds & (values.astype("float64")>=cutoff))
            order=np.argsort(-values,kind="stable")
            r[method]=dict(proposed=names[keep].tolist(),top3=names[order[:3]].tolist())
    result={}
    for panel in sorted({r["panel"] for r in rows} | {"overlap39"}):
        selected=[r for r in rows if r["panel"]==panel or (panel=="overlap39" and r["panel"] in ("XC","CEB-XC"))]
        pairs=[r for r in selected if "second" in r]
        metrics={}
        for method in ("raw","tuned","fixed"):
            metrics[method]=dict(second=sum(r["second"] in r[method]["proposed"] for r in pairs),
                                 both=sum({r["second"],r["other"]}<=set(r[method]["proposed"]) for r in pairs),
                                 top3_second=sum(r["second"] in r[method]["top3"] for r in pairs))
            if panel in ("cal","test","fresh"):
                metrics[method]["fp"]=sum(len(set(r[method]["proposed"])-set(r["truth"])) for r in selected)
                metrics[method]["tp"]=sum(len(set(r[method]["proposed"])&set(r["truth"])) for r in selected)
        result[panel]=dict(windows=len(selected),pairs=len(pairs),methods=metrics,
                           gained=sum(r["second"] not in r["raw"]["proposed"] and r["second"] in r["tuned"]["proposed"] for r in pairs),
                           lost=sum(r["second"] in r["raw"]["proposed"] and r["second"] not in r["tuned"]["proposed"] for r in pairs))
    assert result["cal"]["methods"]["raw"]["fp"]==150 and result["test"]["methods"]["raw"]["fp"]==263
    assert result["cal"]["methods"]["tuned"]["fp"]<=budget
    (work/"predictions.jsonl").write_text("".join(json.dumps(r)+"\n" for r in rows),encoding="utf8")
    return dict(threshold=threshold,panels=result)


def verify_export(net, work, phones, indices, dw, db):
    session = ort.InferenceSession(str(work/"birdnet-finetuned.onnx"), net.session.get_session_options(),
                                   providers=["CPUExecutionProvider"])
    manifest = json.loads((HERE/"temporal-phones.json").read_text(encoding="utf8"))["recordings"]
    devices = set()
    error = 0.0
    for r in manifest:
        if r["device"] in devices:
            continue
        devices.add(r["device"])
        with sf.SoundFile(phones/r["file"]) as f:
            wave = resample(mono(f.read(5*f.samplerate, always_2d=True, dtype="float32")),f.samplerate,RATE)
        base, emb = forward(net,wave)
        base[indices] = expit(logit(np.clip(base[indices],1e-7,1-1e-7)) + 0.4*(dw@emb+db))
        got = session.run([net.output], {net.input:wave[None,:]})[0][0]
        np.testing.assert_allclose(got,base,rtol=1e-4,atol=3e-5)
        error=max(error,float(np.max(np.abs(got-base))))
    return dict(devices=len(devices),max_absolute_score_difference=error)


if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("mode",choices=["phones","new-phones","fit"])
    for arg in ("models","work","phones","older"):
        p.add_argument(arg,type=Path)
    args=p.parse_args()
    torch.set_num_threads(3)
    model=Classifier(args.models,"bn3")
    if args.mode in ("phones","new-phones"):
        phone_features(model,args.work,args.phones,args.older,fresh=args.mode=="new-phones")
    else:
        sources=json.loads((args.work/"sources.json").read_text(encoding="utf8"))
        records=train_features(model,args.work,sources)
        for cache in ("phone-features.npz","new-phone-features.npz"):
            with np.load(args.work/cache) as holdout:
                reserved = {r["sha256"] for r in json.loads(str(holdout["rows"]))}
                assert reserved.isdisjoint(r.get("sha256") for r in records)
        save_json(args.work/"audited.json",records)
        ids,dw,db,training=fit(model,args.work,args.models,records)
        result=validate(model,args.work,args.models,ids,dw,db)
        result["onnx_verification"]=verify_export(model,args.work,args.phones,ids,dw,db)
        save_json(args.work/"results.json",dict(training=training,**result))
        print(training,result["panels"]["test"],flush=True)
