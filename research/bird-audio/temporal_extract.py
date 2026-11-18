"""Train and apply a two-output temporal mask estimator on 32 kHz mono audio."""
import argparse
from collections import defaultdict
import hashlib
import io
import json
from pathlib import Path
import tarfile

import numpy as np
import soundfile as sf
import torch
from torch import nn

from evaluate import mono, resample, table

HERE = Path(__file__).resolve().parent
RATE = 32000
NFFT = 512
HOP = 256
BINS = NFFT // 2 + 1
STEPS = 800
SEED = 90817


class TemporalExtractor(nn.Module):
    def __init__(self):
        super().__init__()
        self.register_buffer("window", torch.hann_window(NFFT))
        channels = 96
        self.encoder = nn.Conv1d(BINS, channels, 1)
        self.context = nn.ModuleList([
            nn.Sequential(nn.Conv1d(channels, channels, 3, dilation=d, padding=d),
                          nn.GroupNorm(8, channels), nn.SiLU())
            for d in (1, 2, 4, 8, 16)
        ])
        self.output = nn.Conv1d(channels, 2 * BINS, 1)

    def spectrum(self, wave):
        return torch.stft(wave, NFFT, HOP, window=self.window, return_complex=True)

    def forward(self, magnitude):
        scale = magnitude.square().mean((1, 2), keepdim=True).sqrt().clamp_min(1e-7)
        features = self.encoder(torch.log1p(magnitude / scale))
        for layer in self.context:
            features = features + layer(features)
        return self.output(features).unflatten(1, (2, BINS)).softmax(1)

    @torch.inference_mode()
    def extract(self, wave):
        spec = self.spectrum(torch.from_numpy(wave)[None])
        separated = self(spec.abs())[0] * spec
        return torch.istft(separated, NFFT, HOP, window=self.window, length=len(wave)).numpy()


def load_clips(audio):
    refs = json.loads((HERE / "target-references.json").read_text(encoding="utf8"))
    selected = {r["member"]: r for r in refs}
    events = defaultdict(list)
    for row in table(audio / "ceb-xc.csv"):
        if row["filepath"] in selected and row["label_status"] == "valid":
            events[row["filepath"]].append((float(row["start_time"]), float(row["end_time"])))
    clips = [defaultdict(list), defaultdict(list)]
    files = set()
    with tarfile.open(audio / "verified-ceb.tar.gz", "r|gz") as archive:
        for member in archive:
            if member.name not in selected:
                continue
            ref = selected[member.name]
            data, rate = sf.read(io.BytesIO(archive.extractfile(member).read()), always_2d=True, dtype="float32")
            wave = resample(mono(data), rate, RATE)
            for a, b in sorted(events[member.name], key=lambda x: x[0]-x[1])[:4]:
                clip = wave[max(0, round(a*RATE)):min(len(wave), round(b*RATE))]
                if len(clip) >= RATE//10 and np.linalg.norm(clip) > 1e-5:
                    clips[int(ref["probe"])][ref["species"]].append(clip.copy())
                    files.add(member.name)
    return clips, dict(files=len(files), train_clips=sum(map(len, clips[0].values())),
                       probe_clips=sum(map(len, clips[1].values())), species=len(clips[0]))


def batch(clips, rng, count):
    length = RATE * 2
    sources = np.zeros((count, 2, length), dtype="float32")
    names = sorted(clips)
    for pair in sources:
        for channel, species in enumerate(rng.choice(names, size=2, replace=False)):
            pool = clips[species]
            clip = pool[rng.integers(len(pool))]
            start = rng.integers(max(1, len(clip)-length+1))
            clip = clip[start:start+length]
            # Center the calls near each other while varying overlap and relative level.
            jitter = int(rng.uniform(-0.15, 0.15)*RATE)
            pos = int(np.clip((length-len(clip))//2 + jitter, 0, length-len(clip)))
            gain = 10**(rng.uniform(-12, 0)/20) if channel else 1
            pair[channel, pos:pos+len(clip)] = clip * (gain / max(np.linalg.norm(clip), 1e-6))
    return torch.from_numpy(sources)


def assignment_loss(model, sources):
    real = model.spectrum(sources.flatten(0, 1)).abs().unflatten(0, (-1, 2))
    mix = model.spectrum(sources.sum(1)).abs()
    estimates = model(mix) * mix[:, None]
    # Compressed magnitude, with equal relative weight per source.
    truth = torch.sqrt(real + 1e-8)
    predicted = torch.sqrt(estimates + 1e-8)
    weight = truth.square().mean((-2, -1)).clamp_min(1e-8)
    def error(pair):
        return ((pair-truth).square().mean((-2, -1)) / weight).mean(1)
    return torch.minimum(error(predicted), error(predicted.flip(1))).mean()


def train(audio, work):
    torch.manual_seed(SEED)
    rng = np.random.default_rng(SEED)
    (training, probes), history = load_clips(audio)
    model = TemporalExtractor()
    optimizer = torch.optim.AdamW(model.parameters(), lr=0.0007)
    validation = [batch(probes, np.random.default_rng(SEED+i+1), 8) for i in range(8)]
    history["validation"] = []
    for step in range(STEPS+1):
        if step % 200 == 0:
            with torch.no_grad():
                loss = np.mean([assignment_loss(model, sample).item() for sample in validation])
            history["validation"].append(dict(step=step, loss=float(loss)))
            print(step, loss, flush=True)
        if step < STEPS:
            optimizer.zero_grad(set_to_none=True)
            loss = assignment_loss(model, batch(training, rng, 10))
            if not torch.isfinite(loss):
                raise RuntimeError("Non-finite training loss")
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5)
            optimizer.step()
    work.mkdir(parents=True, exist_ok=True)
    weights = work / "temporal.pt"
    torch.save(model.state_dict(), weights)
    history.update(steps=STEPS, mixtures=STEPS*10, seed=SEED, torch=str(torch.__version__),
                   parameters=sum(p.numel() for p in model.parameters()),
                   weights_sha256=hashlib.sha256(weights.read_bytes()).hexdigest())
    (work / "training.json").write_text(json.dumps(history, indent=2), encoding="utf8")


def restore(path):
    model = TemporalExtractor()
    model.load_state_dict(torch.load(path, map_location="cpu", weights_only=True))
    return model.eval()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    training = sub.add_parser("train")
    training.add_argument("audio", type=Path)
    training.add_argument("work", type=Path)
    extract = sub.add_parser("extract")
    extract.add_argument("weights", type=Path)
    extract.add_argument("audio", type=Path)
    extract.add_argument("output", type=Path)
    extract.add_argument("--start", type=float, default=0)
    args = parser.parse_args()
    torch.set_num_threads(3)
    if args.command == "train":
        train(args.audio, args.work)
    else:
        with sf.SoundFile(args.audio) as f:
            start = round(args.start*f.samplerate)
            if not 0 <= start < len(f):
                parser.error("Start is outside the recording")
            f.seek(start)
            wave = resample(mono(f.read(5*f.samplerate, dtype="float32", always_2d=True)), f.samplerate, RATE)
        length = len(wave)
        stems = restore(args.weights).extract(np.pad(wave, (0, max(0, 160000-length))))
        args.output.mkdir(exist_ok=True, parents=True)
        for i, stem in enumerate(stems):
            sf.write(args.output / f"source-{i+1}.wav", stem[:length], RATE, subtype="FLOAT")
