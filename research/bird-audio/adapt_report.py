"""Recompute the held-out metrics from the saved predictions, no models required."""
import json
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

METHODS = ("raw", "hp", "mean", "bias", "adapted", "adapted_mean", "augmented", "augmented_mean")


def recall(row, method):
    truth = set(row["truth"])
    return len(truth.intersection(row[method]["names"])) / len(truth)


def overlap_metrics(rows):
    # Sample recording days within each WABAD site, or individual recordings for CEB.
    strata = defaultdict(lambda: defaultdict(list))
    for i, row in enumerate(rows):
        if row["dataset"] == "WABAD":
            site, day, _ = row["file"].split("_")
            strata[site][day].append(i)
        else:
            strata["CEB"][row["file"]].append(i)
    rng = np.random.default_rng(20261118)
    recalls = np.array([[recall(r, m) for m in METHODS] for r in rows])
    diffs = recalls - recalls[:, :1]
    numerator = np.zeros((4000, len(METHODS)))
    denominator = np.zeros(4000)
    for groups in strata.values():
        blocks = list(groups.values())
        draw = rng.integers(len(blocks), size=(4000, len(blocks)))
        numerator += np.array([diffs[idx].sum(axis=0) for idx in blocks])[draw].sum(axis=1)
        denominator += np.array([len(idx) for idx in blocks])[draw].sum(axis=1)
    ci = np.percentile(100*numerator/denominator[:, None], [2.5, 97.5], axis=0)
    pairs = [r for r in rows if len(r["truth"]) == 2]
    return {"n": len(rows), "n_pairs": len(pairs),
            "blocks": sum(len(g) for g in strata.values()), "methods": {
                m: {"recall3_pct": round(100*recalls[:, j].mean(), 2),
                    "delta_95_pp": ci[:, j].round(2).tolist(),
                    "hit1_pct": round(100*np.mean([r[m]["names"][0] in r["truth"] for r in rows]), 2),
                    "both_in_three": sum(recall(r, m) == 1 for r in pairs)}
                for j, m in enumerate(METHODS)}}


def uniform_metrics(rows, method, budget):
    # Illustrative fixed native threshold; suppress top-ranked non-avian proposals.
    proposals = [r for r in rows if r[method]["first_is_bird"] and r[method]["scores"][0] >= 0.25]
    correct = sum(r[method]["names"][0] in r["truth"] for r in proposals)
    candidates = sorted((r for r in rows if r[method]["first_is_bird"]),
                        key=lambda r: (-r[method]["scores"][0], r["file"], r["start"]))
    matched = candidates[:budget]
    return {"correct": correct, "wrong": len(proposals)-correct, "abstained": len(rows)-len(proposals),
            "matched_offers": len(matched),
            "matched_correct": sum(r[method]["names"][0] in r["truth"] for r in matched)}


def summarize(rows):
    index = {(r["dataset"], r["file"], r["start"], r["condition"]) for r in rows}
    assert len(index) == len(rows)
    results = {}
    for dataset in ("CEB", "WABAD"):
        dataset_rows = [r for r in rows if r["dataset"] == dataset]
        result = {"files": len({r["file"] for r in dataset_rows})}
        for cond in ("original", "noise"):
            selected = [r for r in dataset_rows if r["condition"] == cond]
            assert all((dataset, r["file"], r["start"], "noise" if cond == "original" else "original")
                       in index for r in selected)
            metrics = {}
            for group in ("single", "overlap"):
                stratified = [r for r in selected if r["stratified"] and r["group"] == group]
                metrics[group] = overlap_metrics(stratified)
            uniform = [r for r in selected if r["uniform"]]
            budget = sum(r["raw"]["first_is_bird"] and r["raw"]["scores"][0] >= 0.25 for r in uniform)
            metrics["uniform"] = {m: uniform_metrics(uniform, m, budget) for m in METHODS}
            metrics["uniform"]["n"] = len(uniform)
            result[cond] = metrics
        results[dataset] = result
    return results


if __name__ == "__main__":
    inputs = [json.loads(line) for line in Path(sys.argv[1]).read_text().splitlines()]
    print(json.dumps(summarize(inputs), indent=2))
