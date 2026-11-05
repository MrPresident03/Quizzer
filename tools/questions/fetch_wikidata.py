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
BUDGET_S = 26 * 60  # stop starting queries after this; step timeout is 30 min
MAX_YEAR = 2026
DISCARD_PREV = {"sports_people"}  # earlier output included non-athletes


class BudgetExceeded(Exception):
    pass


# --------------------------------------------------------------------------- helpers

def valid_label(s):
    return bool(s) and "http" not in s[:5].lower() and "genid" not in s and s == s.strip() and 0 < len(s) <= 80 and "\n" not in s and not re.fullmatch(r"Q\d+", s)


def clean_label(s):
    """Stricter than valid_label (used by the newer templates): also rejects comma qualifiers
    such as "Springfield, Illinois"."""
    return valid_label(s) and "," not in s


LEAK_STOP = {
    "united", "states", "kingdom", "republic", "island", "islands", "south", "north", "east", "west",
    "lake", "mount", "river", "saint", "city", "company", "group", "corporation", "limited",
    "international", "national", "democratic", "federal", "federation", "people", "holdings",
    "brand", "brands", "house", "fashion", "languages", "language", "system", "prize", "trial", "case",
    "park", "bay", "falls", "desert", "with", "from", "that", "this", "royal", "great", "grand",
}


def _stems(s):
    return {t[:4] for t in re.findall(r"[a-z]{4,}", s.lower()) if t not in LEAK_STOP}


def leaks(title, answer):
    """True when the answer shares a (4-letter-prefix) word with the question subject, which makes
    the question trivially guessable ("Mount Kenya" -> Kenya, "Hungarian goulash" -> Hungary)."""
    return bool(_stems(title) & _stems(answer))


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

    def run_scaled(self, build, scales=(1, 2, 4)):
        """build(scale) -> query. On failure retry with a stricter popularity threshold."""
        last = None
        for scale in scales:
            try:
                return self.run(build(scale))
            except BudgetExceeded:
                raise
            except Exception as e:
                last = e
                C.warn("query failed (scale %d): %s" % (scale, e))
        raise last


LABEL = 'SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }'


def pair_query(where, min_sl, limit, date_prop=None, limited=False, optional="", extra=""):
    """Cheap query shape: sitelinks filter first, no sorting/grouping server side (done in Python).

    `limited=True` appends LIMIT (the older templates rely on the sitelinks floor alone)."""
    date = "OPTIONAL { ?item %s ?d }" % date_prop if date_prop else ""
    year = "(YEAR(?d) AS ?year)" if date_prop else ""
    q = """SELECT ?item ?itemLabel ?ans ?ansLabel ?sl %s %s WHERE {
  ?item wikibase:sitelinks ?sl. FILTER(?sl >= %d)
  %s
  %s
  %s
  %s
}""" % (year, extra, min_sl, where, date, optional, LABEL)
    if limited:
        q += " LIMIT %d" % limit
    return q


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
        if val(b, "dis") or val(b, "dis2"):
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


OPT_COUNTRY = "OPTIONAL { ?ans wdt:P576 ?dis } OPTIONAL { ?ans wdt:P30 ?cont }"
EXTRA_COUNTRY = "?cont ?dis"
OPT_DEAD = "OPTIONAL { ?ans wdt:P576 ?dis } OPTIONAL { ?item wdt:P576 ?dis2 }"
EXTRA_DEAD = "?dis ?dis2"


def merge_entries(lists):
    """Merge collect() results from several queries: same item -> union of answers."""
    out = {}
    for lst in lists:
        for e in lst:
            m = out.get(e["id"])
            if m is None:
                out[e["id"]] = e
                continue
            m["answers"].update(e["answers"])
            m["sl"] = max(m["sl"], e["sl"])
            m["conts"] |= e["conts"]
            if e.get("dead"):
                m["dead"] = True
            if e["year"] is not None and (m["year"] is None or e["year"] < m["year"]):
                m["year"] = e["year"]
    return list(out.values())


def fetch_entries(sp, where, min_sl, limit=2500, optional=OPT_COUNTRY, extra=EXTRA_COUNTRY, kind=None,
                  scales=(1, 3), date_prop=None):
    rows = sp.run_scaled(lambda s: pair_query(where, min_sl * s, limit, date_prop, True, optional, extra), scales)
    ents = collect(rows)
    for e in ents:
        e["kind"] = kind
    return ents


def gather(sp, tag, jobs):
    """Run several (cheap) queries; a failing one is skipped so the others still count."""
    lists = []
    for j in jobs:
        try:
            ents = fetch_entries(sp, **j)
        except BudgetExceeded:
            raise
        except Exception as ex:
            C.warn("%s: query %s failed: %s" % (tag, j.get("kind") or "", ex))
            continue
        print("%s %s: %d items" % (tag, j.get("kind") or "", len(ents)), flush=True)
        lists.append(ents)
    return merge_entries(lists)


# --------------------------------------------------------------------------- question building

def build_pairs(entries, key, category, qfmt, efmt, prefer="era", need_year=False,
                ans_fmt=None, cap=CAP, pool_of=lambda e: "all", multi=1, leak=False,
                per_answer_cap=None, group_of=None, strict_labels=False):
    """Turn entries (item -> answers) into multiple-choice questions.

    multi: accept items with up to this many valid answers. One of them is asked; ALL of them are
      excluded from the distractors, so exactly one option is right. qfmt(e) sees e["n"] (answer
      count) and efmt(e, ans) sees e["alabels"] (all valid answers).
    leak: drop items whose answer shares a word with the subject.
    per_answer_cap: at most this many questions with the same answer (keeps the best-known).
    group_of: label -> group key; distractors never share a group with a valid answer (synonyms).
    strict_labels: also reject labels containing commas.
    """
    ok = clean_label if strict_labels else valid_label
    valid, by_text = [], {}
    for e in entries:
        n = len(e["answers"])
        if n < 1 or n > multi:
            continue  # no answer, or ambiguous: more values than we accept
        labels = sorted({(ans_fmt(l or "") if ans_fmt else (l or "")) for l in e["answers"].values()})
        if len(labels) != n:
            continue
        title = e["title"] or ""
        if not ok(title) or not all(ok(l) for l in labels) or e.get("dead"):
            continue
        if title[-1] in "?!." or title[0] in "'\"":
            continue  # reads badly inside "'...'?"
        if need_year and e["year"] is None:
            continue
        if any(len(l) >= 3 and l.lower() in title.lower() for l in labels):
            continue
        if leak and any(leaks(title, l) for l in labels):
            continue
        e["n"], e["alabels"] = n, labels
        ans = labels[0] if n == 1 else random.Random(e["id"] + key).choice(labels)
        text = qfmt(e)
        by_text.setdefault(text.lower(), []).append((e, ans, text, frozenset(l.lower() for l in labels)))
    for group in by_text.values():
        if len({g[3] for g in group}) > 1:
            continue  # same question, different answers
        group.sort(key=lambda g: (-g[0]["sl"], g[0]["id"]))
        valid.append(group[0])
    valid.sort(key=lambda g: (-g[0]["sl"], g[0]["id"]))
    if per_answer_cap:
        used, capped = {}, []
        for g in valid:
            k = g[1].lower()
            if used.get(k, 0) >= per_answer_cap:
                continue
            used[k] = used.get(k, 0) + 1
            capped.append(g)
        valid = capped

    pools = {}
    for e, _, _, _ in valid:
        p = pools.setdefault(pool_of(e), {})
        for lab in e["alabels"]:
            meta = p.setdefault(lab, {"years": [], "conts": set()})
            if e["year"] is not None:
                meta["years"].append(e["year"])
            meta["conts"] |= e["conts"]

    kept = valid[:cap]
    out = []
    for rank, (e, ans, text, _) in enumerate(kept):
        pool = pools[pool_of(e)]
        rng = random.Random(e["id"] + key)
        pf = None
        if prefer == "era" and e["year"] is not None:
            pf = lambda a, y=e["year"]: any(abs(y - v) <= 25 for v in pool[a]["years"])
        elif prefer == "cont" and e["conts"]:
            pf = lambda a, c=e["conts"]: bool(pool[a]["conts"] & c)
        cands = sorted(pool)
        if group_of:
            banned_groups = {group_of(l) for l in e["alabels"]} - {None}
            cands = [a for a in cands if group_of(a) not in banned_groups]
        d = pick_distractors(rng, e["alabels"], cands, pf)
        if len(d) < 3:
            continue
        opts, idx = assemble(rng, ans, d)
        out.append(C.make_question(text, opts, idx, category, tertile_difficulty(rank, len(kept)),
                                   efmt(e, ans), {"template": key}))
    return out


def first_word(label):
    return label.split()[0].lower() if label.split() else None


def join_and(labels):
    return labels[0] if len(labels) == 1 else ", ".join(labels[:-1]) + " and " + labels[-1]


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



# --------------------------------------------------------------------------- newer templates
# Conventions: sovereign states are `wd:Q3624078`; every query has a sitelinks floor and a LIMIT;
# one-to-many relations use build_pairs(multi=N) so every valid answer is excluded from distractors.

SOVEREIGN = "wd:Q3624078"
CO_CLASSES = "wd:Q4830453 wd:Q783794 wd:Q891723 wd:Q6881511 wd:Q18388277"
STRICT = dict(strict_labels=True)


def many(e, one, several):
    return one if e["n"] == 1 else several


# ---- Geography

GEO_KINDS = [  # (kind, class VALUES, sitelinks floor)
    ("mountain", "wd:Q8502", 80), ("lake", "wd:Q23397", 80), ("river", "wd:Q4022", 60),
    ("island", "wd:Q23442", 80), ("volcano", "wd:Q8072 wd:Q169358", 50), ("waterfall", "wd:Q34038", 40),
    ("desert", "wd:Q8514", 50), ("national park", "wd:Q46169", 60),
]


def geo_question(e):
    if e["kind"] == "river":
        return many(e, "Which country does the %s flow through?" % e["title"],
                    "Which of these countries does the %s flow through?" % e["title"])
    return many(e, "In which country is %s located?" % e["title"],
                "Which of these countries contains part of %s?" % e["title"])


def geo_expl(e, ans):
    verb = "flows through" if e["kind"] == "river" else "is in"
    return "%s %s %s." % (e["title"], verb, join_and(e["alabels"]))


def t_geography(sp):
    jobs = [dict(where="?item wdt:P31 ?cls. VALUES ?cls { %s } ?item wdt:P17 ?ans. ?ans wdt:P31 %s." % (cls, SOVEREIGN),
                 min_sl=sl, limit=900, kind=kind) for kind, cls, sl in GEO_KINDS]
    return build_pairs(gather(sp, "geography", jobs), "geography", "Geography", geo_question, geo_expl,
                       prefer="cont", multi=2, leak=True, cap=700, per_answer_cap=70, **STRICT)


def t_geo_heritage(sp):
    jobs = [dict(where="?item wdt:P1435 wd:Q9259; wdt:P17 ?ans. ?ans wdt:P31 %s." % SOVEREIGN,
                 min_sl=60, limit=1500, kind="heritage")]
    return build_pairs(gather(sp, "geo_heritage", jobs), "geo_heritage", "Geography",
                       lambda e: many(e, "In which country is %s located?" % e["title"],
                                      "Which of these countries is %s located in?" % e["title"]),
                       lambda e, a: "%s is a UNESCO World Heritage Site in %s." % (e["title"], join_and(e["alabels"])),
                       prefer="cont", multi=2, leak=True, cap=350, per_answer_cap=25, **STRICT)


def t_geo_cities(sp):
    # capitals are excluded on purpose: the app has a separate capitals mode
    where = ("?item wdt:P31 ?cls. VALUES ?cls { wd:Q515 wd:Q1549591 } ?item wdt:P17 ?ans. ?ans wdt:P31 %s. "
             "FILTER NOT EXISTS { ?ans wdt:P36 ?item }" % SOVEREIGN)
    jobs = [dict(where=where, min_sl=100, limit=2000, kind="city")]
    return build_pairs(gather(sp, "geo_cities", jobs), "geo_cities", "Geography",
                       lambda e: "In which country is the city of %s?" % e["title"],
                       lambda e, a: "%s is a city in %s." % (e["title"], a),
                       prefer="cont", leak=True, cap=300, per_answer_cap=12, **STRICT)


CONTINENTS = {"Africa", "Asia", "Europe", "North America", "South America", "Oceania"}


def t_geo_continents(sp):
    jobs = [dict(where="?item wdt:P31 %s; wdt:P30 ?ans." % SOVEREIGN, min_sl=100, limit=1000, kind="country",
                 optional="", extra="")]
    ents = [e for e in gather(sp, "geo_continents", jobs)
            if all(l in CONTINENTS for l in e["answers"].values())]
    return build_pairs(ents, "geo_continents", "Geography",
                       lambda e: "On which continent is %s located?" % e["title"],
                       lambda e, a: "%s is in %s." % (e["title"], a), prefer=None, leak=True, cap=200, **STRICT)


# ---- Sports & Recreation

SPORTS = [  # (sport QID, answer label, occupation QIDs, sitelinks floor)
    ("Q2736", "Football (soccer)", ["Q937857"], 80), ("Q847", "Tennis", ["Q10833314"], 50),
    ("Q5372", "Basketball", ["Q3665646"], 60), ("Q1968", "Formula One", ["Q10841764"], 40),
    ("Q5375", "Cricket", ["Q12299841"], 50), ("Q5377", "Golf", ["Q13365117"], 40),
    ("Q32112", "Boxing", ["Q11338576"], 40), ("Q542", "Athletics", ["Q11513337"], 60),
    ("Q31920", "Swimming", ["Q10843402"], 50), ("Q5369", "Baseball", ["Q10871364"], 60),
    ("Q41466", "Ice hockey", ["Q11774891"], 60), ("Q41323", "American football", ["Q19204627"], 60),
    ("Q5849", "Rugby union", ["Q13141064"], 40),
]
ATHLETE = "Q2066131"


def sport_query(sport, occs, min_sl, limit=400):
    """One cheap query per sport: athletes (by occupation) of that sport above a sitelinks floor."""
    return """SELECT ?item ?itemLabel ?sl ?oth ?othLabel WHERE {
  ?item wdt:P641 wd:%s; wdt:P106 ?occ. VALUES ?occ { %s }
  ?item wdt:P31 wd:Q5; wikibase:sitelinks ?sl. FILTER(?sl >= %d)
  OPTIONAL { ?item wdt:P641 ?oth. FILTER(?oth != wd:%s) }
  %s
} LIMIT %d""" % (sport, " ".join("wd:" + o for o in occs + [ATHLETE]), min_sl, sport, LABEL, limit)


def sport_entries(rows, sport, label):
    """Entries for one sport; a second P641 value ("oth") is kept so the person is dropped as ambiguous."""
    items = {}
    for b in rows:
        if "item" not in b:
            continue
        iq = qid(val(b, "item"))
        e = items.setdefault(iq, {"id": iq, "title": val(b, "itemLabel"), "answers": {sport: label},
                                  "year": None, "sl": int(float(val(b, "sl") or 0)), "conts": set()})
        if val(b, "oth"):
            e["answers"][qid(val(b, "oth"))] = val(b, "othLabel") or "other sport"
    return list(items.values())


def t_sports_people(sp):
    lists = []
    for sport, label, occs, sl in SPORTS:
        try:
            rows = sp.run_scaled(lambda s: sport_query(sport, occs, sl * s), (1, 3))
        except BudgetExceeded:
            raise
        except Exception as ex:
            C.warn("sports_people: %s failed: %s" % (label, ex))
            continue
        print("sports_people %s: %d rows" % (label, len(rows)), flush=True)
        lists.append(sport_entries(rows, sport, label))
    return build_pairs(merge_entries(lists), "sports_people", "Sports & Recreation",
                       lambda e: "Which sport is %s known for?" % e["title"],
                       lambda e, a: "%s is best known for %s." % (e["title"], a[:1].lower() + a[1:]),
                       prefer=None, cap=1000, per_answer_cap=90, **STRICT)


CANCELLED = (1916, 1940, 1944)  # cancelled Olympic editions


def event_specs():
    summer = [y for y in range(1896, 2025, 4) if y not in CANCELLED]  # 2028+ are future editions
    winter = [y for y in list(range(1924, 1993, 4)) + list(range(1994, MAX_YEAR + 1, 4)) if y not in CANCELLED]
    world_cup = [1930, 1934, 1938] + list(range(1950, MAX_YEAR + 1, 4))  # no 1942/1946
    return [  # (series, labels, host properties, noun)
        ("Summer Olympics", ["%d Summer Olympics" % y for y in summer], "wdt:P276 wdt:P131", "city"),
        ("Winter Olympics", ["%d Winter Olympics" % y for y in winter], "wdt:P276 wdt:P131", "city"),
        ("FIFA World Cup", ["%d FIFA World Cup" % y for y in world_cup], "wdt:P17", "country"),
        ("FIFA Women's World Cup", ["%d FIFA Women's World Cup" % y for y in range(1991, 2024, 4)], "wdt:P17", "country"),
        ("UEFA Euro", ["UEFA Euro %d" % y for y in range(1960, 2025, 4)], "wdt:P17", "country"),
    ]


def event_query(labels, props):
    """Editions are looked up by exact English label (an index lookup), not through a class hierarchy."""
    return """SELECT ?item ?itemLabel ?ans ?ansLabel ?sl ?pp WHERE {
  VALUES ?lab { %s }
  ?item rdfs:label ?lab. ?item wikibase:sitelinks ?sl. FILTER(?sl >= 5)
  VALUES ?pp { %s }
  ?item ?pp ?ans.
  %s
}""" % (" ".join('"%s"@en' % l for l in labels), props, LABEL)


def prefer_city_property(rows):
    """If an edition has a P276 (location) value use only that, otherwise fall back to the rest."""
    has = {val(b, "item") for b in rows if (val(b, "pp") or "").endswith("/P276")}
    return [b for b in rows if val(b, "item") not in has or (val(b, "pp") or "").endswith("/P276")]


def event_year(title):
    m = re.search(r"\b(1[89]\d\d|20\d\d)\b", title or "")
    return int(m.group(1)) if m else None


def t_events(sp):
    entries = []
    for series, labels, props, noun in event_specs():
        try:
            rows = sp.run_scaled(lambda s: event_query(labels, props), (1,))
        except BudgetExceeded:
            raise
        except Exception as ex:
            C.warn("events: %s failed: %s" % (series, ex))
            continue
        print("events %s: %d rows" % (series, len(rows)), flush=True)
        for e in collect(prefer_city_property(rows)):
            year = event_year(e["title"])
            if year is None or year > MAX_YEAR or year in CANCELLED:
                continue
            e["year"], e["series"], e["noun"] = year, series, noun
            entries.append(e)

    def q(e):
        if e["n"] == 1:
            return "Which %s hosted the %s?" % (e["noun"], e["title"])
        if e["noun"] == "city":
            return "Which of these cities hosted events at the %s?" % e["title"]
        return "Which of these countries co-hosted the %s?" % e["title"]

    def ex(e, a):
        if e["n"] == 1:
            return "The %s %s." % (e["title"], "were held in %s" % a if "Olympics" in e["title"] else "was hosted by %s" % a)
        return "The %s involved %s." % (e["title"], join_and(e["alabels"]))
    return build_pairs(entries, "events", "Sports & Recreation", q, ex, multi=3,
                       pool_of=lambda e: e["series"], cap=300, **STRICT)


# ---- Politics & World Leaders

ORDINAL_OFFICES = [  # (office QID, name) - numbering is checked by ordinal_office_ok before use
    ("wd:Q11696", "President of the United States"), ("wd:Q14211", "Prime Minister of the United Kingdom"),
    ("wd:Q839078", "Prime Minister of Canada"), ("wd:Q319145", "Prime Minister of Australia"),
    ("wd:Q11699", "Vice President of the United States"), ("wd:Q4970706", "Chancellor of Germany"),
]


def ordinal_office_ok(ords, min_items=12, max_missing=0.2):
    """Ordinals are only trusted when they look like a clean 1..N sequence (few gaps)."""
    ords = set(ords)
    if len(ords) < min_items:
        return False
    return len(ords) / max(ords) >= 1 - max_missing


def ordinal_rows(rows):
    """ordinal -> {person qid: (name, sitelinks)} for finished terms with a plain numeric ordinal."""
    by_ord = {}
    for b in rows:
        if not val(b, "end") or not re.fullmatch(r"\d{1,3}", val(b, "ord") or ""):
            continue  # skip serving office holders and odd ordinals
        if not clean_label(val(b, "pLabel") or ""):
            continue
        by_ord.setdefault(int(val(b, "ord")), {})[qid(val(b, "p"))] = (val(b, "pLabel"), int(float(val(b, "sl"))))
    return by_ord


def t_leaders(sp):
    out_entries = []
    for office, label in ORDINAL_OFFICES:
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
        by_ord = ordinal_rows(rows)
        ok = ordinal_office_ok(by_ord)
        print("leaders %s: %d ordinals%s" % (label, len(by_ord), "" if ok else " (numbering unreliable, skipped)"), flush=True)
        if not ok:
            continue
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


def t_leaders_current(sp):
    """Current head of state / government (open-ended statements only; may go stale between refreshes)."""
    out = []
    for prop, office in (("P35", "head of state"), ("P6", "head of government")):
        where = ("?item wdt:P31 %s. ?item p:%s ?st. ?st ps:%s ?ans. FILTER NOT EXISTS { ?st pq:P582 ?e } "
                 "?st wikibase:rank ?rk. FILTER(?rk != wikibase:DeprecatedRank) ?ans wdt:P31 wd:Q5. "
                 "OPTIONAL { ?ans wdt:P570 ?dis }" % (SOVEREIGN, prop, prop))
        ents = gather(sp, "leaders_current " + office, [dict(where=where, min_sl=100, limit=600, kind=office,
                                                              optional="", extra="?dis")])
        out += build_pairs(ents, "leaders_current", "Politics & World Leaders",
                           lambda e, o=office: "Who is the current %s of %s?" % (o, e["title"]),
                           lambda e, a, o=office: "%s is the current %s of %s." % (a, o, e["title"]),
                           prefer=None, cap=200, pool_of=lambda e, o=office: o, **STRICT)
    return out


def t_leaders_country(sp):
    where = ("?ans wdt:P31 %s; (wdt:P1906|wdt:P1313) ?pos. ?item wdt:P39 ?pos; wdt:P31 wd:Q5." % SOVEREIGN)
    ents = [e for e in gather(sp, "leaders_country", [dict(where=where, min_sl=90, limit=2500, kind="leader")])
            if not any("Vatican" in l or "Holy See" in l for l in e["answers"].values())]
    return build_pairs(ents, "leaders_country", "Politics & World Leaders",
                       lambda e: "%s served as head of state or head of government of which country?" % e["title"],
                       lambda e, a: "%s led %s." % (e["title"], a),
                       prefer="cont", cap=300, per_answer_cap=14, **STRICT)


# ---- Food & Drink

FOOD_KINDS = [  # (kind, class, noun phrase, sitelinks floor)
    ("dish", "wd:Q746549", "the dish", 25), ("cheese", "wd:Q10675", "the cheese", 15),
    ("cocktail", "wd:Q134768", "the cocktail", 15), ("drink", "wd:Q40050", "the drink", 30),
]


def t_food_origin(sp):
    jobs = [dict(where="?item wdt:P495 ?ans. ?ans wdt:P31 %s. ?item wdt:P31/wdt:P279* %s." % (SOVEREIGN, cls),
                 min_sl=sl, limit=1500, kind=noun) for kind, cls, noun, sl in FOOD_KINDS]
    return build_pairs(gather(sp, "food_origin", jobs), "food_origin", "Food & Drink",
                       lambda e: "Which country does %s %s originate from?" % (e["kind"], e["title"]),
                       lambda e, a: "%s %s originates from %s." % (cap_first(e["kind"]), e["title"], a),
                       prefer="cont", leak=True, cap=450, per_answer_cap=25, **STRICT)


# ---- Business & Economics

def founder_q(e):
    return many(e, "Who founded %s?" % e["title"], "Which of these people co-founded %s?" % e["title"])


def t_company_founders(sp):
    where = ("?item wdt:P112 ?ans. ?ans wdt:P31 wd:Q5. ?item wdt:P31 ?cls. VALUES ?cls { %s }" % CO_CLASSES)
    jobs = [dict(where=where, min_sl=40, limit=3000, kind="company", optional="", extra="")]
    return build_pairs(gather(sp, "company_founders", jobs), "company_founders", "Business & Economics",
                       founder_q, lambda e, a: "%s was founded by %s." % (e["title"], join_and(e["alabels"])),
                       prefer=None, multi=3, leak=True, cap=400, **STRICT)


def t_company_hq(sp):
    where = ("?item wdt:P159 ?hq. ?hq wdt:P17 ?ans. ?ans wdt:P31 %s. ?item wdt:P31 ?cls. VALUES ?cls { %s }"
             % (SOVEREIGN, CO_CLASSES))
    jobs = [dict(where=where, min_sl=60, limit=3000, kind="company")]
    return build_pairs(gather(sp, "company_hq", jobs), "company_hq", "Business & Economics",
                       lambda e: "In which country is %s headquartered?" % e["title"],
                       lambda e, a: "%s is headquartered in %s." % (e["title"], a),
                       prefer="cont", leak=True, cap=400, per_answer_cap=40, **STRICT)


def t_currency(sp):
    where = ("?item wdt:P31 %s. ?item p:P38 ?st. ?st ps:P38 ?ans. FILTER NOT EXISTS { ?st pq:P582 ?e }" % SOVEREIGN)
    jobs = [dict(where=where, min_sl=100, limit=1000, kind="country", optional="", extra="")]
    return build_pairs(gather(sp, "currency", jobs), "currency", "Business & Economics",
                       lambda e: many(e, "What is the official currency of %s?" % e["title"],
                                      "Which of these is an official currency of %s?" % e["title"]),
                       lambda e, a: "%s uses the %s." % (e["title"], join_and(e["alabels"])),
                       ans_fmt=cap_first, prefer="cont", multi=2, leak=True, cap=250, per_answer_cap=5, **STRICT)


def t_company_parent(sp):
    brand_cls = "wd:Q431289 " + CO_CLASSES
    parent = ("?item p:P749 ?st. ?st ps:P749 ?ans. FILTER NOT EXISTS { ?st pq:P582 ?e } "
              "?item wdt:P31 ?cls. VALUES ?cls { %s } ?ans wdt:P31 ?ac. VALUES ?ac { %s }" % (brand_cls, CO_CLASSES))
    owner = ("?item wdt:P127 ?ans. ?item wdt:P31 wd:Q431289. ?ans wdt:P31 ?ac. VALUES ?ac { %s }" % CO_CLASSES)
    jobs = [dict(where=parent, min_sl=40, limit=2500, kind="parent", optional=OPT_DEAD, extra=EXTRA_DEAD),
            dict(where=owner, min_sl=30, limit=2500, kind="owner", optional=OPT_DEAD, extra=EXTRA_DEAD)]
    return build_pairs(gather(sp, "company_parent", jobs), "company_parent", "Business & Economics",
                       lambda e: ("Which company is the parent company of %s?" if e["kind"] == "parent"
                                  else "Which company owns the brand %s?") % e["title"],
                       lambda e, a: "%s is owned by %s." % (e["title"], a),
                       prefer=None, leak=True, cap=300, **STRICT)


# ---- Health & Medicine

def t_disease_cause(sp):
    where = ("?item wdt:P828 ?ans. ?ans wdt:P31 wd:Q16521. ?item wdt:P31 ?dc. ?dc wdt:P279* wd:Q12136.")
    jobs = [dict(where=where, min_sl=20, limit=1500, kind="disease", optional=OPT_DEAD, extra=EXTRA_DEAD)]
    return build_pairs(gather(sp, "disease_cause", jobs), "disease_cause", "Health & Medicine",
                       lambda e: many(e, "Which pathogen or organism causes %s?" % e["title"],
                                      "Which of these organisms can cause %s?" % e["title"]),
                       lambda e, a: "%s is caused by %s." % (cap_first(e["title"]), join_and(e["alabels"])),
                       prefer=None, multi=3, leak=True, group_of=first_word, cap=300, **STRICT)


def t_drug_discoverer(sp):
    where = ("?item wdt:P31/wdt:P279* ?cls. VALUES ?cls { wd:Q12140 wd:Q8386 wd:Q134808 } "
             "?item (wdt:P61|wdt:P178) ?ans. ?ans wdt:P31 wd:Q5.")
    jobs = [dict(where=where, min_sl=15, limit=1500, kind="drug", optional="", extra="")]
    return build_pairs(gather(sp, "drug_discoverer", jobs), "drug_discoverer", "Health & Medicine",
                       lambda e: many(e, "Who is credited with discovering or developing %s?" % e["title"],
                                      "Which of these people is credited with discovering or developing %s?" % e["title"]),
                       lambda e, a: "%s is credited to %s." % (cap_first(e["title"]), join_and(e["alabels"])),
                       prefer=None, multi=3, leak=True, cap=300, **STRICT)


def nobel_entries(rows):
    """Group laureates by award year: one entry per year with all that year's laureates as answers."""
    years = {}
    for b in rows:
        y = val(b, "year")
        if not y or "ans" not in b:
            continue
        year = int(float(y))
        e = years.setdefault(year, {"id": "nobel%d" % year, "title": str(year), "answers": {}, "year": year,
                                    "sl": 0, "conts": set()})
        e["answers"][qid(val(b, "ans"))] = val(b, "ansLabel")
        e["sl"] = max(e["sl"], int(float(val(b, "sl") or 0)))
    return list(years.values())


def t_nobel_medicine(sp):
    q = """SELECT ?ans ?ansLabel ?sl ?year WHERE {
  ?ans p:P166 ?st. ?st ps:P166 wd:Q80061. ?st pq:P585 ?d. BIND(YEAR(?d) AS ?year)
  ?ans wikibase:sitelinks ?sl.
  %s
} LIMIT 600""" % LABEL
    ents = nobel_entries(sp.run(q))
    print("nobel_medicine years:", len(ents), flush=True)
    return build_pairs(ents, "nobel_medicine", "Health & Medicine",
                       lambda e: "Which of these people received the %s Nobel Prize in Physiology or Medicine?" % e["title"],
                       lambda e, a: "The %s Nobel Prize in Physiology or Medicine went to %s." % (e["title"], join_and(e["alabels"])),
                       multi=4, cap=200, **STRICT)


def t_organ_systems(sp):
    where = "?item wdt:P31 wd:Q712378; wdt:P361 ?ans."
    ents = gather(sp, "organ_systems", [dict(where=where, min_sl=15, limit=800, kind="organ", optional="", extra="")])
    for e in ents:  # keep only "... system" parents (not e.g. "head" or "thorax")
        e["answers"] = {k: v for k, v in e["answers"].items() if (v or "").lower().endswith("system")}
    return build_pairs([e for e in ents if e["answers"]], "organ_systems", "Health & Medicine",
                       lambda e: "Which body system does the %s belong to?" % e["title"],
                       lambda e, a: "The %s is part of the %s." % (e["title"], a.lower()),
                       prefer=None, ans_fmt=cap_first, leak=True, cap=150, **STRICT)


# ---- Language & Linguistics

LANG_CLASSES = "wd:Q34770 wd:Q1288568 wd:Q33742"


def t_official_language(sp):
    where = ("?item wdt:P31 %s. ?item p:P37 ?st. ?st ps:P37 ?ans. FILTER NOT EXISTS { ?st pq:P582 ?e }" % SOVEREIGN)
    jobs = [dict(where=where, min_sl=100, limit=1500, kind="country", optional="", extra="")]
    return build_pairs(gather(sp, "official_language", jobs), "official_language", "Language & Linguistics",
                       lambda e: many(e, "What is the official language of %s?" % e["title"],
                                      "Which of these is an official language of %s?" % e["title"]),
                       lambda e, a: "%s: official language%s %s." % (e["title"], "" if e["n"] == 1 else "s", join_and(e["alabels"])),
                       prefer=None, multi=2, leak=True, cap=250, per_answer_cap=8, **STRICT)


def t_writing_system(sp):
    where = "?item wdt:P31 ?lc. VALUES ?lc { %s } ?item wdt:P282 ?ans." % LANG_CLASSES
    jobs = [dict(where=where, min_sl=50, limit=1500, kind="language", optional="", extra="")]
    return build_pairs(gather(sp, "writing_system", jobs), "writing_system", "Language & Linguistics",
                       lambda e: many(e, "Which writing system is used for the %s language?" % e["title"],
                                      "Which of these writing systems is used for the %s language?" % e["title"]),
                       lambda e, a: "%s is written in %s." % (e["title"], join_and(e["alabels"])),
                       prefer=None, multi=2, leak=True, group_of=first_word, cap=250, per_answer_cap=30, **STRICT)


FAMILY_BLOCK = re.compile(r"altaic|nostratic|eurasiatic|proto|macro|isolate|creole|pidgin|sign", re.I)


def top_families(fams, has_family_parent):
    """Keep only top-level families: a family that is under another typed family is dropped."""
    return {k: v for k, v in fams.items() if k not in has_family_parent and not FAMILY_BLOCK.search(v or "")}


def t_language_family(sp):
    where = ("?item wdt:P31 ?lc. VALUES ?lc { %s } ?item wdt:P279+ ?ans. ?ans wdt:P31 wd:Q25295." % LANG_CLASSES)
    ents = gather(sp, "language_family", [dict(where=where, min_sl=60, limit=5000, kind="language",
                                               optional="", extra="")])
    parents = set()
    try:
        rows = sp.run("SELECT DISTINCT ?f WHERE { ?f wdt:P31 wd:Q25295; wdt:P279+ ?p. ?p wdt:P31 wd:Q25295 } LIMIT 3000")
        parents = {qid(val(b, "f")) for b in rows}
    except BudgetExceeded:
        raise
    except Exception as ex:
        C.warn("language_family: family hierarchy query failed: %s" % ex)
        return []  # without the hierarchy the answers would be wrong
    for e in ents:
        e["answers"] = top_families(e["answers"], parents)
    ents = [e for e in ents if not re.search(r"\bsign\b", e["title"] or "", re.I)]
    return build_pairs(ents, "language_family", "Language & Linguistics",
                       lambda e: "To which language family does %s belong?" % e["title"],
                       lambda e, a: "%s belongs to the %s." % (e["title"], a), prefer=None, cap=250,
                       per_answer_cap=60, **STRICT)


# ---- Fashion & Design

FASHION_INDUSTRY = ('?item wdt:P452 ?ind. ?ind rdfs:label ?il. FILTER(LANG(?il) = "en" && '
                    'REGEX(?il, "fashion|clothing|luxury|apparel|jewel|footwear|leather", "i"))')


def t_fashion_founders(sp):
    jobs = [dict(where="?item wdt:P31 wd:Q3661265; wdt:P112 ?ans. ?ans wdt:P31 wd:Q5.", min_sl=15, limit=1500,
                 kind="house", optional="", extra=""),
            dict(where="?item wdt:P112 ?ans. ?ans wdt:P31 wd:Q5. " + FASHION_INDUSTRY, min_sl=25, limit=1500,
                 kind="brand", optional="", extra="")]
    return build_pairs(gather(sp, "fashion_founders", jobs), "fashion_founders", "Fashion & Design",
                       founder_q, lambda e, a: "%s was founded by %s." % (e["title"], join_and(e["alabels"])),
                       prefer=None, multi=3, leak=True, cap=300, **STRICT)


def t_fashion_country(sp):
    place = "?item (wdt:P17|wdt:P495|(wdt:P159/wdt:P17)) ?ans. ?ans wdt:P31 %s. " % SOVEREIGN
    jobs = [dict(where="?item wdt:P31 wd:Q3661265. " + place, min_sl=15, limit=1500, kind="house"),
            dict(where=place + FASHION_INDUSTRY, min_sl=25, limit=1500, kind="brand")]
    return build_pairs(gather(sp, "fashion_country", jobs), "fashion_country", "Fashion & Design",
                       lambda e: "Which country is the fashion brand %s from?" % e["title"],
                       lambda e, a: "%s is a brand from %s." % (e["title"], a),
                       prefer="cont", leak=True, cap=300, per_answer_cap=40, **STRICT)


def t_fashion_designers(sp):
    where = ("?item wdt:P31 wd:Q5; wdt:P106 wd:Q3501317; wdt:P27 ?ans. ?ans wdt:P31 %s." % SOVEREIGN)
    jobs = [dict(where=where, min_sl=40, limit=1500, kind="designer")]
    return build_pairs(gather(sp, "fashion_designers", jobs), "fashion_designers", "Fashion & Design",
                       lambda e: "%s is a fashion designer from which country?" % e["title"],
                       lambda e, a: "%s is a fashion designer from %s." % (e["title"], a),
                       prefer="cont", leak=True, cap=250, per_answer_cap=40, **STRICT)


# ---- True Crime & Law

VICTIM_TITLE = re.compile(r"\b(murder|killing|death|disappearance|rape|assassination|massacre|abduction) of\b", re.I)
CRIME_BLOCK = re.compile(r"rape|sexual|child|abuse|indecen|molest|pedoph|incest|sodomy|prostitut|traffick|assault|kidnap", re.I)
CRIME_GROUPS = [
    ("homicide", re.compile(r"murder|homicide|manslaughter|killing|assassin", re.I)),
    ("fraud", re.compile(r"fraud|embezzl|forger|swindl|deception|counterfeit", re.I)),
    ("treason", re.compile(r"treason|sedition|espionage|spy|spying|subversi|betray", re.I)),
    ("theft", re.compile(r"theft|robbery|larceny|burglar|steal|stolen|heist", re.I)),
    ("tax", re.compile(r"tax|evasion", re.I)),
    ("terror", re.compile(r"terror|bomb|hijack", re.I)),
    ("conspiracy", re.compile(r"conspir|racketeer|organi[sz]ed crime", re.I)),
    ("heresy", re.compile(r"heresy|blasphem|witch", re.I)),
]


def crime_group(label):
    """Synonym group of a crime label, so a distractor is never a near-synonym of the answer."""
    for name, rx in CRIME_GROUPS:
        if rx.search(label):
            return name
    return first_word(label)


def t_crimes(sp):
    # deceased people only (historical figures); never anything about victims or sexual offences
    where = "?item wdt:P31 wd:Q5; wdt:P1399 ?ans; wdt:P570 ?dod."
    ents = gather(sp, "crimes", [dict(where=where, min_sl=50, limit=1500, kind="person", optional="", extra="")])
    ents = [e for e in ents if not any(CRIME_BLOCK.search(l or "") for l in e["answers"].values())]
    return build_pairs(ents, "crimes", "True Crime & Law",
                       lambda e: "What was %s convicted of?" % e["title"],
                       lambda e, a: "%s was convicted of %s." % (e["title"], a.lower()),
                       prefer=None, ans_fmt=cap_first, group_of=crime_group, cap=200, per_answer_cap=30, **STRICT)


def t_legal_bodies(sp):
    jobs = [dict(where="?ans wdt:P31 %s; wdt:P194 ?item." % SOVEREIGN, min_sl=15, limit=800, kind="legislature"),
            dict(where="?ans wdt:P31 %s; wdt:P209 ?item." % SOVEREIGN, min_sl=10, limit=800, kind="court")]
    return build_pairs(gather(sp, "legal_bodies", jobs), "legal_bodies", "True Crime & Law",
                       lambda e: ("The %s is the national legislature of which country?" if e["kind"] == "legislature"
                                  else "The %s is the highest court of which country?") % e["title"],
                       lambda e, a: "The %s belongs to %s." % (e["title"], a),
                       prefer="cont", leak=True, cap=250, per_answer_cap=3, **STRICT)


def t_trials(sp):
    where = ("?item wdt:P31 ?c. ?c wdt:P279* wd:Q2334719. ?item (wdt:P17|wdt:P1001) ?ans. ?ans wdt:P31 %s." % SOVEREIGN)
    ents = [e for e in gather(sp, "trials", [dict(where=where, min_sl=30, limit=1500, kind="case")])
            if not VICTIM_TITLE.search(e["title"] or "")]
    return build_pairs(ents, "trials", "True Crime & Law",
                       lambda e: "In which country did the legal case %s take place?" % e["title"],
                       lambda e, a: "%s took place in %s." % (e["title"], a),
                       prefer="cont", leak=True, cap=250, per_answer_cap=30, **STRICT)


# --------------------------------------------------------------------------- probe

DISCOVER = [  # exact English labels whose QIDs we want to see (to fix guessed class ids)
    "fashion house", "luxury brand", "organ system", "legal case", "trial", "supreme court", "legislature",
    "parliament", "cheese", "cocktail", "vaccine", "language family", "sovereign state",
    "Nobel Prize in Physiology or Medicine", "Prime Minister of Canada", "Prime Minister of Australia",
    "Prime Minister of New Zealand", "Prime Minister of India", "Prime Minister of Japan",
    "Vice President of the United States", "Chancellor of Germany", "Secretary-General of the United Nations",
    "President of France", "Prime Minister of Italy",
]
DUMP = ["1996 Summer Olympics", "2018 FIFA World Cup", "Brown v. Board of Education", "Chanel", "tuberculosis",
        "Bundestag", "heart"]


def probe(sp, source_path=None):
    """Print `QID -> English label` for every id used in this file, plus a few discovery lookups."""
    src = open(source_path or __file__, encoding="utf-8").read()
    ids = sorted(set(re.findall(r"\b([QP]\d{2,})\b", src)), key=lambda s: (s[0], int(s[1:])))
    for i in range(0, len(ids), 60):
        chunk = ids[i:i + 60]
        q = 'SELECT ?x ?l WHERE { VALUES ?x { %s } ?x rdfs:label ?l. FILTER(LANG(?l) = "en") }' % " ".join("wd:" + c for c in chunk)
        try:
            got = {qid(val(b, "x")): val(b, "l") for b in sp.run(q)}
        except BudgetExceeded:
            raise
        except Exception as ex:
            C.warn("probe labels failed: %s" % ex)
            continue
        for c in chunk:
            print("PROBE %s = %s" % (c, got.get(c, "<<MISSING>>")), flush=True)
    for lab in DISCOVER:
        q = ('SELECT ?x ?sl ?d WHERE { ?x rdfs:label "%s"@en; wikibase:sitelinks ?sl. FILTER(?sl >= 3) '
             'OPTIONAL { ?x schema:description ?d. FILTER(LANG(?d) = "en") } } ORDER BY DESC(?sl) LIMIT 5' % lab)
        try:
            for b in sp.run(q):
                print("PROBE-FIND '%s' -> %s (sl %s) %s" % (lab, qid(val(b, "x")), val(b, "sl"), val(b, "d") or ""), flush=True)
        except BudgetExceeded:
            raise
        except Exception as ex:
            C.warn("probe find %s failed: %s" % (lab, ex))
    for lab in DUMP:
        q = ('SELECT ?p ?pl ?o ?ol WHERE { ?x rdfs:label "%s"@en; wikibase:sitelinks ?sl. FILTER(?sl > 20) '
             '?x ?p ?o. FILTER(STRSTARTS(STR(?p), "http://www.wikidata.org/prop/direct/")) FILTER(isIRI(?o)) '
             '?prop wikibase:directClaim ?p. ?prop rdfs:label ?pl. FILTER(LANG(?pl) = "en") '
             'OPTIONAL { ?o rdfs:label ?ol. FILTER(LANG(?ol) = "en") } } LIMIT 80' % lab)
        try:
            for b in sp.run(q):
                print("PROBE-DUMP '%s': %s %s = %s %s" % (lab, qid(val(b, "p")), val(b, "pl"), qid(val(b, "o")), val(b, "ol") or ""), flush=True)
        except BudgetExceeded:
            raise
        except Exception as ex:
            C.warn("probe dump %s failed: %s" % (lab, ex))


# Newer / fixed templates run first so the time budget never starves them; older templates that run
# out of budget simply keep their previous items.
TEMPLATES = [
    ("events", t_events), ("sports_people", t_sports_people),
    ("geography", t_geography), ("geo_heritage", t_geo_heritage), ("geo_cities", t_geo_cities),
    ("geo_continents", t_geo_continents),
    ("leaders", t_leaders), ("leaders_current", t_leaders_current), ("leaders_country", t_leaders_country),
    ("food_origin", t_food_origin),
    ("company_founders", t_company_founders), ("company_hq", t_company_hq), ("currency", t_currency),
    ("company_parent", t_company_parent),
    ("disease_cause", t_disease_cause), ("drug_discoverer", t_drug_discoverer),
    ("nobel_medicine", t_nobel_medicine), ("organ_systems", t_organ_systems),
    ("official_language", t_official_language), ("writing_system", t_writing_system),
    ("language_family", t_language_family),
    ("fashion_founders", t_fashion_founders), ("fashion_country", t_fashion_country),
    ("fashion_designers", t_fashion_designers),
    ("crimes", t_crimes), ("legal_bodies", t_legal_bodies), ("trials", t_trials),
    ("literature", t_literature), ("films", t_films), ("albums", t_albums),
    ("paintings", t_paintings), ("architecture", t_architecture),
    ("elements", t_elements), ("animals", t_animals),
]


def sample_lines(items, k=3):
    if not items:
        return []
    step = max(1, len(items) // k)
    out = []
    for q in items[::step][:k]:
        out.append("  SAMPLE %s -> %s" % (q["text"], q["options"][q["correctIndex"]]))
    return out


def main():
    sp = Sparql()
    try:
        probe(sp)
    except BudgetExceeded:
        pass
    except Exception as ex:
        C.warn("probe failed: %s" % ex)
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
        print("%-18s %5d questions%s" % (key, len(chosen), " (kept previous)" if new is None else ""), flush=True)
        for line in sample_lines(chosen):
            print(line, flush=True)
        final += chosen
    final.sort(key=lambda q: (q["template"], q["category"], q["text"]))
    if final or prev:
        C.write_lines(OUT, final)
    return 0


if __name__ == "__main__":
    sys.exit(main())
