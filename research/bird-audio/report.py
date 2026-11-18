"""Summarize the paired predictions, without rerunning inference."""
import argparse
import json
from collections import Counter
from pathlib import Path

import numpy as np


def paired(rows, condition, ranking):
    results = []
    for row in rows:
        truth = set(row["truth"])
        scores = []
        for model in ("bn", "perch"):
            top = row[condition][model][ranking]
            scores.extend([top[0] in truth, len(truth.intersection(top)) / len(truth)])
        results.append(scores)
    data = np.array(results, dtype=float)
    # Each stratum contains at most one window per recording.
    assert len({r["file"] for r in rows}) == len(rows)
    diff = data[:, 2:] - data[:, :2]
    indices = np.random.default_rng(41).integers(len(rows), size=(4000, len(rows)))
    ci = np.percentile(diff[indices].mean(axis=1), [2.5, 97.5], axis=0).T
    return {"n": len(rows), "mean_percent_BN1_BNrec3_P1_Prec3": (data.mean(axis=0)*100).round(2).tolist(),
            "paired_difference_95ci_pp_hit1_recall3": (100*ci).round(2).tolist()}


def operating_point(rows, condition, model):
    # One proposal per window, ties always included or rejected as a block.
    predictions = [(r[condition][model]["shared_score"],
                    r[condition][model]["shared"][0] in r["truth"]) for r in rows]
    budget = int(len(rows)*5/60)  # matched budget of one false suggestion / minute
    counts = Counter()
    for score, correct in predictions:
        counts[(score, correct)] += 1
    tp = fp = 0
    for score in sorted({s for s,_ in predictions}, reverse=True):
        if fp + counts[score, False] > budget:
            break
        tp += counts[score, True]
        fp += counts[score, False]
    return {"correct": tp, "wrong": fp, "windows": len(rows), "budget": budget}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("predictions", type=Path)
    args = parser.parse_args()
    rows = [json.loads(line) for line in args.predictions.read_text().splitlines()]
    assert len(rows) == len({(r["dataset"], r["file"], r["start"]) for r in rows})
    aliases = json.loads(Path(__file__).with_name("evaluation-names.json").read_text())["aliases"]
    def canonical(names):
        return [aliases.get(n, n) for n in names]
    for row in rows:
        row["truth"] = sorted(set(canonical(row["truth"])))
        row["shared_truth"] = sorted(set(canonical(row["shared_truth"])))
        for condition in ("noise","original"):
            for model in ("bn","perch"):
                for ranking in ("shared","all"):
                    result = row[condition][model]
                    result[ranking] = canonical(result[ranking])
    report = {}
    for name in ("CEB", "WABAD"):
        dataset = [r for r in rows if r["dataset"] == name]
        uniform = [r for r in dataset if r["uniform"]]
        cohort = [r for r in dataset if r["stratified"]]
        report[name] = {"files": len({r["file"] for r in dataset}), "unique_windows": len(dataset),
                        "uniform_windows": len(uniform), "uniform_empty": sum(not r["truth"] for r in uniform),
                        "species_in_sample": len({s for r in dataset for s in r["truth"]}),
                        "groups": {}, "both_species_in_first_three": {}, "noise_category": {},
                        "one_wrong_per_minute": {}}
        for condition in ("original","noise"):
            report[name]["one_wrong_per_minute"][condition] = {
                m: operating_point(uniform, condition, m) for m in ("bn","perch")}
            two = [r for r in cohort if r["group"] == "overlap" and len(r["truth"]) == 2]
            report[name]["both_species_in_first_three"][condition] = {
                "windows": len(two), **{m: sum(set(r["truth"]) <= set(r[condition][m]["all"])
                                               for r in two) for m in ("bn", "perch")}}
            for group in ("single","overlap"):
                grp = [r for r in cohort if r["group"] == group]
                # Include unsupported labels in the native model comparison.
                matched = [r for r in grp if r["truth"] == r["shared_truth"]]
                report[name]["groups"][f"{group}_{condition}"] = {
                    "native": paired(grp, condition, "all"), "matched": paired(matched, condition, "shared")}
        for suffix, label in [("16","wind"),("10","rain"),("44","engine")]:
            subset = [r for r in cohort if r["noise_file"].endswith(f"-{suffix}.wav")]
            report[name]["noise_category"][label] = {
                "windows": len(subset), **{m: sum(r["noise"][m]["all"][0] in r["truth"] for r in subset)
                                           for m in ("bn","perch")}}
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
