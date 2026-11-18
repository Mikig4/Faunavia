"""Snapshot post-release, publicly licensed focal recordings for BirdNET head fine-tuning."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import time

import requests

HERE = Path(__file__).resolve().parent
END = "2026-11-18"
BOUND = datetime(2026, 11, 14, tzinfo=timezone.utc)


def get(url, **kwargs):
    for attempt in range(4):
        try:
            response = requests.get(url, timeout=(12, 50), **kwargs)
            response.raise_for_status()
            return response
        except requests.RequestException:
            if attempt == 3:
                raise
            time.sleep(2**attempt)


def licensed(value):
    return value in {"cc0", "cc-by", "cc-by-sa", "cc-by-nc", "cc-by-nc-sa"}


def inat():
    rows = []
    url = "https://api.inaturalist.org/v1/observations"
    params = dict(taxon_id=3, sounds="true", quality_grade="research", d1="2026-11-14", d2=END,
                  per_page=200, order_by="id", order="asc")
    while True:
        obs = get(url, params=params).json()["results"]
        for r in obs:
            utc = r.get("time_observed_at")
            if r["observed_on"] == "2026-11-14" and (not utc or datetime.fromisoformat(utc) < BOUND):
                continue
            taxon = r["taxon"]
            for sound in r["sounds"]:
                if not licensed(sound.get("license_code")) or not sound.get("file_url"):
                    continue
                rows.append(dict(id=f"IN{sound['id']}", page=r["uri"], name=taxon["name"],
                                 taxon_id=str(taxon["id"]), parent=str(taxon.get("parent_id","")),
                                 rank=taxon["rank"], date=r["observed_on"], author=r["user"]["login"],
                                 credit=sound["attribution"], license=sound["license_code"],
                                 url=sound["file_url"]))
        if len(obs)<200:
            break
        params["id_above"] = obs[-1]["id"]
    return rows


def xenocanto():
    from bs4 import BeautifulSoup
    rows = []
    page = 1
    while True:
        html = get("https://xeno-canto.org/explore", params=dict(
            query="grp:birds year:2026 month:11 since:2026-11-13", pg=page)).text
        if "class='results'" not in html:
            raise ValueError("Missing XC table")
        for block in re.split(r"<tr\s", html)[1:]:
            tr = BeautifulSoup("<tr "+block.split("</tr>",1)[0]+"</tr>","html.parser").tr
            species, audio = tr.select_one(".sci-name"), tr.select_one("audio[src]")
            cc = tr.select_one('a[href*="creativecommons.org/licenses/"]')
            fields = tr.find_all("td",recursive=False)
            if not (species and audio and cc and len(fields)>7) or tr.select_one('[title="ID under discussion"]'):
                continue
            date, hour = [f.get_text(strip=True) for f in fields[4:6]]
            if not ("2026-11-14" <= date <= END):
                continue
            if date=="2026-11-14" and not re.fullmatch(r"(1[4-9]|2[0-3]):[0-5]\d", hour):
                continue
            license_code = "cc-"+cc["href"].split("/licenses/")[1].split("/")[0]
            if not licensed(license_code):
                continue
            number = audio["id"].split("_")[2]
            author = fields[3].get_text(strip=True)
            rows.append(dict(id="XC"+number, page="https://xeno-canto.org/"+number,
                             name=" ".join(species.stripped_strings), date=date, author=author,credit=author,
                             license=cc["href"], url=audio["src"]))
        pages = [int(n) for n in re.findall(r"[&;]pg=(\d+)",html)]
        if not any(p>page for p in pages):
            break
        page += 1
    return rows


def collect(models, work, manifest=None):
    labels = list(csv.DictReader((models/"bn3.csv").open(encoding="utf8")))
    taxids = {r["inat_id"]:r["scientific_name"] for r in labels if r["taxon_group"]=="Aves" and r["inat_id"]}
    names = set(taxids.values()) | {r["scientific_name"] for r in labels if r["taxon_group"]=="Aves"}
    reserved = json.loads((HERE/"phone-xc.json").read_text(encoding="utf8")) + json.loads((HERE/"temporal-xc.json").read_text(encoding="utf8"))
    ids = {r["id"] for r in reserved}
    authors = {r["author"].strip().casefold() for r in reserved}
    media = work/"media"
    media.mkdir(parents=True,exist_ok=True)
    snapshot = work/"selected-v2.json"
    if not manifest and not snapshot.exists():
        inat_path = work/"inat.json"
        if not inat_path.exists():
            inat_path.write_text(json.dumps(inat(),ensure_ascii=False),encoding="utf8")
        xc_path = work/"xc.json"
        if not xc_path.exists():
            xc_path.write_text(json.dumps(xenocanto(),ensure_ascii=False),encoding="utf8")
        found = {r["id"]:r for r in json.loads(inat_path.read_text(encoding="utf8"))+
                 json.loads(xc_path.read_text(encoding="utf8"))}
        selected = []
        for r in sorted(found.values(),key=lambda v:v["id"]):
            if r["id"] in ids or r["author"].strip().casefold() in authors:
                continue
            label = taxids.get(r.get("taxon_id"))
            if not label and r.get("rank") in {"subspecies","variety","form"}:
                label = taxids.get(r.get("parent"))
            label = label or " ".join(r["name"].split()[:2])
            if label in names:
                selected.append(dict(r,label=label))
        snapshot.write_text(json.dumps(selected,ensure_ascii=False),encoding="utf8")
    rows = json.loads((manifest or snapshot).read_text(encoding="utf8"))
    print("Selected",len(rows),flush=True)
    def download(r):
        path = media/r["id"]
        if not path.exists():
            try:
                data = get(r["url"]).content
            except requests.RequestException as exc:
                return dict(r,error=type(exc).__name__)
            part = path.with_suffix(".partial")
            part.write_bytes(data)
            part.replace(path)
        measured = hashlib.sha256(path.read_bytes()).hexdigest()
        if r.get("sha256",measured)!=measured:
            raise ValueError(f"Unexpected content for {r['id']}")
        return dict(r,sha256=measured)
    with ThreadPoolExecutor(6) as pool:
        downloaded = []
        for i,row in enumerate(pool.map(download,rows)):
            downloaded.append(row)
            if (i+1)%100==0:
                print(i+1,flush=True)
    (work/"sources.json").write_text(json.dumps(downloaded,ensure_ascii=False),encoding="utf8")


if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("models",type=Path)
    parser.add_argument("work",type=Path)
    parser.add_argument("--manifest",type=Path,help="Replay the frozen source list and validate its hashes")
    args=parser.parse_args()
    collect(args.models,args.work,args.manifest)
