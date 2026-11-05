#!/usr/bin/env python3
"""Generate templated trivia from Wikidata (SPARQL) into sources/wikidata.json.

Every question carries an extra "template" key (stripped by build_bank.py) so a failing
template can keep its previous items.
"""
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import qb_common as C

ENDPOINT = "https://query.wikidata.org/sparql"
OUT = os.path.join(C.SOURCES_DIR, "wikidata.json")
CAP = 1500
MIN_ITEMS = 20
SHRINK_LIMIT = 0.9
BUDGET_S = 17 * 60  # stop starting queries after this; step timeout is 20 min
MAX_YEAR = 2026
DISCARD_PREV = {"sports_people"}  # earlier output included non-athletes


class BudgetExceeded(Exception):
    pass


# --------------------------------------------------------------------------- helpers

def valid_label(s):
    return bool(s) and "http" not in s[:5].lower() and "genid" not in s and s == s.strip() and 0 < len(s) <= 80 and "\n" not in s and not re.fullmatch(r"Q\d+", s)


def cap_first(s):
    return s[:1].upper() + s[1:]


def ordinal(n):
    if 10 <= n % 100 <= 20:
        suffix = "th"
    else:
        suffix = {1: "st", 2: "nd", 3: "rd"}.get(n % 10, "th")
    return "%d%s" % (n, suffix)


def pick_distractors(rng, valid_answers, pool, prefer=None, n=3):
    """Choose n distinct options from pool, never equal (case-insensitive) to any valid answer.

    `prefer(candidate)` marks candidates to use first (same era / continent / first letter).
    """
    banned = {a.lower() for a in valid_answers}
    seen, cands = set(), []
    for a in pool:
        k = a.lower()
        if k in banned or k in seen:
            continue
        seen.add(k)
        cands.append(a)
    preferred = [a for a in cands if prefer and prefer(a)]
    rest = [a for a in cands if not (prefer and prefer(a))]
    rng.shuffle(preferred)
    rng.shuffle(rest)
    return (preferred + rest)[:n]


def tertile_difficulty(rank, total):
    if rank < total / 3:
        return "easy"
    if rank < 2 * total / 3:
        return "medium"
    return "hard"


def assemble(rng, correct, distractors):
    opts = [correct] + list(distractors)
    rng.shuffle(opts)
    return opts, opts.index(correct)


def qid(uri):
    return uri.rsplit("/", 1)[-1]


def val(b, k):
    return b.get(k, {}).get("value")


# --------------------------------------------------------------------------- SPARQL client

class Sparql:
    def __init__(self):
        self.spacer = C.Spacer(2.0)
        self.started = time.monotonic()

    def run(self, query):
        for attempt in range(2):
            if time.monotonic() - self.started > BUDGET_S:
                raise BudgetExceeded()
            self.spacer.wait()
            data = urllib.parse.urlencode({"query": query, "format": "json"}).encode()
            req = urllib.request.Request(ENDPOINT, data=data, headers={
                "User-Agent": C.USER_AGENT, "Accept": "application/sparql-results+json"})
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    return json.load(r)["results"]["bindings"]
            except urllib.error.HTTPError as e:
                if e.code in (429, 503) and attempt == 0:
                    time.sleep(30)
                    continue
                raise
        raise RuntimeError("unreachable")

    def run_scaled(self, build):
        """build(scale) -> query. On failure retry once with a stricter popularity threshold."""
        last = None
        for scale in (1, 2, 4):
            try:
                return self.run(build(scale))
            except BudgetExceeded:
                raise
            except Exception as e:
                last = e
                C.warn("query failed (scale %d): %s" % (scale, e))
        raise last


LABEL = 'SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }'


def pair_query(where, min_sl, limit, date_prop=None):
    """Cheap query shape: sitelinks filter first, no sorting/grouping server side (done in Python)."""
    date = "OPTIONAL { ?item %s ?d }" % date_prop if date_prop else ""
    year = "(YEAR(?d) AS ?year)" if date_prop else ""
    return """SELECT ?item ?itemLabel ?ans ?ansLabel ?sl %s WHERE {
  ?item wikibase:sitelinks ?sl. FILTER(?sl >= %d)
  %s
  %s
  %s
}""" % (year, min_sl, where, date, LABEL)


def collect(rows):
    items = {}
    for b in rows:
        if "item" not in b or "ans" not in b:
            continue
        iq = qid(val(b, "item"))
        e = items.setdefault(iq, {"id": iq, "title": val(b, "itemLabel"), "answers": {},
                                  "year": None, "sl": 0, "conts": set()})
        e["answers"][qid(val(b, "ans"))] = val(b, "ansLabel")
        e["sl"] = max(e["sl"], int(float(val(b, "sl") or 0)))
        if val(b, "dis"):
            e["dead"] = True
        y = val(b, "year")
        if y:
            try:
                yy = int(float(y))
                if yy > 0 and (e["year"] is None or yy < e["year"]):
                    e["year"] = yy
            except ValueError:
                pass
        if val(b, "cont"):
            e["conts"].add(val(b, "cont"))
    return list(items.values())


# --------------------------------------------------------------------------- question building

def build_pairs(entries, key, category, qfmt, efmt, prefer="era", need_year=False,
                ans_fmt=None, cap=CAP, pool_of=lambda e: "all"):
    valid, by_text = [], {}
    for e in entries:
        if len(e["answers"]) != 1:
            continue  # ambiguous: more than one value
        alabel = next(iter(e["answers"].values()))
        if not valid_label(e["title"]) or not valid_label(alabel) or e.get("dead"):
            continue
        if e["title"][-1] in "?!." or e["title"][0] in "'\"":
            continue  # reads badly inside "'...'?"
        if need_year and e["year"] is None:
            continue
        if len(alabel) >= 3 and alabel.lower() in e["title"].lower():
            continue
        ans = ans_fmt(alabel) if ans_fmt else alabel
        text = qfmt(e)
        by_text.setdefault(text.lower(), []).append((e, ans, text))
    for group in by_text.values():
        if len({g[1].lower() for g in group}) > 1:
            continue  # same question, different answers
        group.sort(key=lambda g: (-g[0]["sl"], g[0]["id"]))
        valid.append(group[0])
    valid.sort(key=lambda g: (-g[0]["sl"], g[0]["id"]))

    pools = {}
    for e, ans, _ in valid:
        p = pools.setdefault(pool_of(e), {})
        meta = p.setdefault(ans, {"years": [], "conts": set()})
        if e["year"] is not None:
            meta["years"].append(e["year"])
        meta["conts"] |= e["conts"]

    kept = valid[:cap]
    out = []
    for rank, (e, ans, text) in enumerate(kept):
        pool = pools[pool_of(e)]
        rng = random.Random(e["id"] + key)
        pf = None
        if prefer == "era" and e["year"] is not None:
            pf = lambda a, y=e["year"]: any(abs(y - v) <= 25 for v in pool[a]["years"])
        elif prefer == "cont" and e["conts"]:
            pf = lambda a, c=e["conts"]: bool(pool[a]["conts"] & c)
        d = pick_distractors(rng, [ans], sorted(pool), pf)
        if len(d) < 3:
            continue
        opts, idx = assemble(rng, ans, d)
        out.append(C.make_question(text, opts, idx, category, tertile_difficulty(rank, len(kept)),
                                   efmt(e, ans), {"template": key}))
    return out


# --------------------------------------------------------------------------- templates

def yr(e):
    return " (%d)" % e["year"] if e["year"] is not None else ""


def t_literature(sp):
    where = "VALUES ?cls { wd:Q8261 wd:Q7725634 } ?item wdt:P31 ?cls; wdt:P50 ?ans."
    rows = sp.run_scaled(lambda s: pair_query(where, 30 * s, 8000 // s, "wdt:P577|wdt:P571"))
    return build_pairs(collect(rows), "literature", "Literature & Books",
                       lambda e: "Who wrote '%s'?" % e["title"],
                       lambda e, a: "'%s'%s was written by %s." % (e["title"], yr(e), a))


def t_films(sp):
    where = "?item wdt:P31 wd:Q11424; wdt:P57 ?ans."
    rows = sp.run_scaled(lambda s: pair_query(where, 40 * s, 8000 // s, "wdt:P577"))
    return build_pairs(collect(rows), "films", "Entertainment",
                       lambda e: "Who directed the film '%s' (%d)?" % (e["title"], e["year"]),
                       lambda e, a: "'%s' (%d) was directed by %s." % (e["title"], e["year"], a),
                       need_year=True)


def t_albums(sp):
    where = "?item wdt:P31 wd:Q482994; wdt:P175 ?ans."
    rows = sp.run_scaled(lambda s: pair_query(where, 30 * s, 8000 // s, "wdt:P577"))
    return build_pairs(collect(rows), "albums", "Entertainment",
                       lambda e: "Which artist released the album '%s'?" % e["title"],
                       lambda e, a: "'%s'%s is an album by %s." % (e["title"], yr(e), a))


def t_paintings(sp):
    where = "?item wdt:P31 wd:Q3305213; wdt:P170 ?ans."
    rows = sp.run_scaled(lambda s: pair_query(where, 15 * s, 8000 // s, "wdt:P571"))
    return build_pairs(collect(rows), "paintings", "Art & Architecture",
                       lambda e: "Who painted '%s'?" % e["title"],
                       lambda e, a: "'%s' was painted by %s." % (e["title"], a))


def t_architecture(sp):
    where = "?item wdt:P84 ?ans."
    rows = sp.run_scaled(lambda s: pair_query(where, 30 * s, 8000 // s, "wdt:P571"))
    return build_pairs(collect(rows), "architecture", "Art & Architecture",
                       lambda e: "Which architect designed %s?" % e["title"],
                       lambda e, a: "%s was designed by the architect %s." % (e["title"], a))


def t_geography(sp):
    def build(s):
        return """SELECT ?item ?itemLabel ?ans ?ansLabel ?sl ?cont ?dis WHERE {
  ?item wikibase:sitelinks ?sl. FILTER(?sl >= %d)
  VALUES ?cls { wd:Q8502 wd:Q23397 wd:Q570116 wd:Q41176 }
  ?item wdt:P31 ?cls; wdt:P17 ?ans.
  OPTIONAL { ?ans wdt:P576 ?dis }
  OPTIONAL { ?ans wdt:P30 ?cont }
  %s
}""" % (120 * s, LABEL)
    rows = sp.run_scaled(build)
    print("geography rows:", len(rows), flush=True)
    return build_pairs(collect(rows), "geography", "Geography",
                       lambda e: "In which country is %s?" % e["title"],
                       lambda e, a: "%s is located in %s." % (e["title"], a), prefer="cont")


def t_elements(sp):
    q = """SELECT ?item ?itemLabel ?sym ?num WHERE {
  ?item wdt:P31 wd:Q11344; wdt:P1086 ?num; wdt:P246 ?sym. FILTER(?num <= 100)
  %s
}""" % LABEL
    rows = sp.run(q)
    by = {}
    for b in rows:
        by.setdefault(qid(val(b, "item")), []).append(b)
    els = []
    for iq, bs in by.items():
        syms = {val(b, "sym") for b in bs}
        nums = {int(float(val(b, "num"))) for b in bs}
        name = cap_first(val(bs[0], "itemLabel") or "")
        if len(syms) != 1 or len(nums) != 1 or not valid_label(name):
            continue
        sym = syms.pop()
        if not valid_label(sym):
            continue
        els.append({"id": iq, "name": name, "sym": sym, "num": nums.pop()})
    els.sort(key=lambda e: e["num"])
    out = []
    for e in els:
        diff = "easy" if e["num"] <= 20 else "medium" if e["num"] <= 56 else "hard"
        expl = "%s has the chemical symbol %s and atomic number %d." % (e["name"], e["sym"], e["num"])
        rng = random.Random(e["id"] + "elements")
        d = pick_distractors(rng, [e["sym"]], [x["sym"] for x in els],
                             lambda a, f=e["sym"][0]: a[0] == f)
        if len(d) == 3:
            o, i = assemble(rng, e["sym"], d)
            out.append(C.make_question("What is the chemical symbol for %s?" % e["name"], o, i,
                                       "Science & Nature", diff, expl, {"template": "elements"}))
        d = pick_distractors(rng, [e["name"]], [x["name"] for x in els],
                             lambda a, f=e["name"][0]: a[0] == f)
        if len(d) == 3:
            o, i = assemble(rng, e["name"], d)
            out.append(C.make_question("Which element has the chemical symbol %s?" % e["sym"], o, i,
                                       "Science & Nature", diff, expl, {"template": "elements"}))
    return out


ANIMAL_CLASSES = [  # (SPARQL taxon term, answer label)
    ("wd:Q7377", "Mammal"), ("wd:Q5113", "Bird"), ("wd:Q10811", "Reptile"), ("wd:Q10908", "Amphibian"),
    ("wd:Q1390", "Insect"), ('?c wdt:P225 "Actinopterygii"', "Fish"), ('?c wdt:P225 "Chondrichthyes"', "Fish"),
]
ANIMAL_OPTIONS = ["Mammal", "Bird", "Reptile", "Amphibian", "Insect", "Fish"]


def animal_query(term, min_sl=60):
    if term.startswith("wd:"):
        link = "?item wdt:P171+ %s." % term
    else:
        link = "?item wdt:P171+ ?c. %s." % term
    return """SELECT ?item ?cn ?sl WHERE {
  ?item wikibase:sitelinks ?sl. FILTER(?sl >= %d)
  ?item wdt:P105 wd:Q7432; wdt:P1843 ?cn. FILTER(LANG(?cn) = "en")
  %s
}""" % (min_sl, link)


def t_animals(sp):
    rows = []
    for term, label in ANIMAL_CLASSES:
        got = None
        for min_sl in (60, 100):
            try:
                got = sp.run(animal_query(term, min_sl))
                break
            except BudgetExceeded:
                raise
            except Exception as e:
                C.warn("animals: class %s failed (sitelinks>=%d): %s" % (label, min_sl, e))
        if got is None:
            continue
        print("animals %s rows: %d" % (label, len(got)), flush=True)
        for b in got:
            b["lbl"] = {"value": label}
        rows += got
    by = {}
    for b in rows:
        e = by.setdefault(qid(val(b, "item")), {"id": qid(val(b, "item")), "names": set(),
                                                "sl": int(float(val(b, "sl"))), "cls": set()})
        e["names"].add(val(b, "cn"))
        e["cls"].add(val(b, "lbl"))
    cands, seen = [], set()
    for e in by.values():
        if "Bird" in e["cls"]:
            e["cls"].discard("Reptile")  # birds also sit under Reptilia in some taxonomies
        e["name"] = sorted(e["names"], key=lambda n: (len(n), n))[0]
        if len(e["cls"]) != 1 or not valid_label(e["name"]) or e["name"].lower() in seen:
            continue
        seen.add(e["name"].lower())
        e["cls"] = next(iter(e["cls"]))
        cands.append(e)
    cands.sort(key=lambda e: (-e["sl"], e["id"]))
    cands = cands[:CAP]
    out = []
    for rank, e in enumerate(cands):
        rng = random.Random(e["id"] + "animals")
        d = pick_distractors(rng, [e["cls"]], ANIMAL_OPTIONS)
        o, i = assemble(rng, e["cls"], d)
        art = "an" if e["cls"][0] in "AEIOU" else "a"
        out.append(C.make_question("What kind of animal is the %s?" % e["name"], o, i, "Animals & Wildlife",
                                   tertile_difficulty(rank, len(cands)),
                                   "The %s is %s %s." % (e["name"], art, e["cls"].lower()),
                                   {"template": "animals"}))
    return out


def t_sports_people(sp):
    def build(s):
        return """SELECT ?item ?itemLabel ?ans ?ansLabel ?sl WHERE {
  ?item wikibase:sitelinks ?sl. FILTER(?sl >= %d)
  ?item wdt:P641 ?ans; wdt:P31 wd:Q5; wdt:P106 ?occ.
  VALUES ?occ { wd:Q937857 wd:Q3665646 wd:Q10833314 wd:Q2066131 wd:Q10871364 wd:Q10843402 wd:Q11338576 wd:Q12299841 }
  %s
}""" % (120 * s, LABEL)
    rows = sp.run_scaled(build)
    print("sports_people rows:", len(rows), flush=True)
    return build_pairs(collect(rows), "sports_people", "Sports & Recreation",
                       lambda e: "Which sport is %s known for?" % e["title"],
                       lambda e, a: "%s is best known for %s." % (e["title"], a[:1].lower() + a[1:]),
                       prefer=None, ans_fmt=cap_first)


def t_events(sp):
    specs = [
        ("wd:Q159821", "wdt:P276|wdt:P131", r"^(\d{4}) (Summer Olympics)$", "Which city hosted the %d %s?",
         "The %d %s were held in %s."),
        ("wd:Q82414", "wdt:P276|wdt:P131", r"^(\d{4}) (Winter Olympics)$", "Which city hosted the %d %s?",
         "The %d %s were held in %s."),
        ("wd:Q19317", "wdt:P17", r"^(\d{4}) (FIFA World Cup)$", "Which country hosted the %d %s?",
         "The %d %s was hosted by %s."),
    ]
    entries, fmt = [], {}
    for cls, prop, pattern, qf, ef in specs:
        city = ""
        q = """SELECT ?item ?itemLabel ?ans ?ansLabel ?sl WHERE {
  ?item wdt:P31|wdt:P179 ?kind. ?kind wdt:P279* %s.
  ?item %s ?ans; wikibase:sitelinks ?sl. %s
  %s
}""" % (cls, prop, city, LABEL)
        try:
            rows = sp.run(q)
        except BudgetExceeded:
            raise
        except Exception as e:
            C.warn("events: %s failed: %s" % (cls, e))
            continue
        print("events %s rows: %d" % (cls, len(rows)), flush=True)
        for e in collect(rows):
            m = re.match(pattern, e["title"] or "")
            if not m:
                continue
            year = int(m.group(1))
            if year > MAX_YEAR or year in (1916, 1940, 1944):  # later or cancelled editions
                continue
            e["year"], e["series"] = year, m.group(2)
            fmt[e["id"]] = (qf % (year, m.group(2)), ef, year, m.group(2))
            entries.append(e)
    return build_pairs(entries, "events", "Sports & Recreation",
                       lambda e: fmt[e["id"]][0],
                       lambda e, a: fmt[e["id"]][1] % (fmt[e["id"]][2], fmt[e["id"]][3], a),
                       pool_of=lambda e: e["series"])


def t_leaders(sp):
    out_entries = []
    for office, label in (("wd:Q11696", "President of the United States"),
                          ("wd:Q14211", "Prime Minister of the United Kingdom")):
        q = """SELECT ?p ?pLabel ?ord ?sl ?end WHERE {
  ?p p:P39 ?st. ?st ps:P39 %s; pq:P1545 ?ord.
  OPTIONAL { ?st pq:P582 ?end }
  ?p wikibase:sitelinks ?sl.
  %s
}""" % (office, LABEL)
        try:
            rows = sp.run(q)
        except BudgetExceeded:
            raise
        except Exception as e:
            C.warn("leaders: %s failed: %s" % (office, e))
            continue
        by_ord = {}
        for b in rows:
            if not val(b, "end") or not re.fullmatch(r"\d{1,3}", val(b, "ord") or ""):
                continue  # skip serving office holders and odd ordinals
            if not valid_label(val(b, "pLabel")):
                continue
            by_ord.setdefault(int(val(b, "ord")), {})[qid(val(b, "p"))] = (val(b, "pLabel"), int(float(val(b, "sl"))))
        for n, people in by_ord.items():
            if len(people) != 1:
                continue
            pq_, (name, sl) = next(iter(people.items()))
            out_entries.append({"office": label, "n": n, "name": name, "sl": sl, "id": "%s-%d" % (pq_, n)})
    out = []
    ranked = {}
    for e in sorted(out_entries, key=lambda e: (-e["sl"], e["id"])):
        ranked.setdefault(e["office"], []).append(e)
    for office, items in ranked.items():
        by_n = {e["n"]: e for e in out_entries if e["office"] == office}
        for rank, e in enumerate(items):
            rng = random.Random(e["id"] + "leaders")
            names = None
            for window in (5, 10, 1000):
                names = [x["name"] for k, x in sorted(by_n.items()) if 0 < abs(k - e["n"]) <= window]
                d = pick_distractors(rng, [e["name"]], names)
                if len(d) == 3:
                    break
            if len(d) < 3:
                continue
            o, i = assemble(rng, e["name"], d)
            out.append(C.make_question("Who was the %s %s?" % (ordinal(e["n"]), office), o, i,
                                       "Politics & World Leaders", tertile_difficulty(rank, len(items)),
                                       "%s was the %s %s." % (e["name"], ordinal(e["n"]), office),
                                       {"template": "leaders"}))
    return out


TEMPLATES = [
    ("literature", t_literature), ("films", t_films), ("albums", t_albums),
    ("paintings", t_paintings), ("architecture", t_architecture), ("geography", t_geography),
    ("elements", t_elements), ("animals", t_animals), ("sports_people", t_sports_people),
    ("events", t_events), ("leaders", t_leaders),
]


def main():
    sp = Sparql()
    prev = C.load_json(OUT, []) or []
    prev_by = {}
    for q in prev:
        if q.get("template") in DISCARD_PREV:
            continue
        prev_by.setdefault(q.get("template"), []).append(q)
    final = []
    for key, fn in TEMPLATES:
        old = prev_by.get(key, [])
        try:
            new = fn(sp)
        except BudgetExceeded:
            C.warn("time budget exhausted before/during template %s; keeping previous items" % key)
            new = None
        except Exception as e:
            C.warn("template %s failed: %s; keeping previous items" % (key, e))
            new = None
        if new is not None and len(new) < MIN_ITEMS:
            C.warn("template %s produced only %d questions (< %d); keeping previous items" % (key, len(new), MIN_ITEMS))
            new = None
        if new is not None and old and len(new) < SHRINK_LIMIT * len(old):
            C.warn("template %s shrank from %d to %d; keeping previous items" % (key, len(old), len(new)))
            new = None
        chosen = old if new is None else new
        print("%-15s %5d questions%s" % (key, len(chosen), " (kept previous)" if new is None else ""), flush=True)
        final += chosen
    final.sort(key=lambda q: (q["template"], q["category"], q["text"]))
    if final or prev:
        C.write_lines(OUT, final)
    return 0


if __name__ == "__main__":
    sys.exit(main())
