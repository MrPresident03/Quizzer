#!/usr/bin/env python3
"""Fetch every mappable Open Trivia DB question into sources/opentdb.json (app schema)."""
import json
import math
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import qb_common as C

BASE = "https://opentdb.com/"
GAP = 5.5
DEADLINE_S = 27 * 60
SHRINK_LIMIT = 0.9
OUT = os.path.join(C.SOURCES_DIR, "opentdb.json")


def convert(item, topic):
    """Convert one url3986-encoded API result to an app-schema question (or None)."""
    d = urllib.parse.unquote
    text = d(item["question"]).strip()
    correct = d(item["correct_answer"]).strip()
    incorrect = [d(x).strip() for x in item.get("incorrect_answers", [])]
    difficulty = d(item.get("difficulty", "")).lower()
    if item.get("type") == "boolean":
        options = ["True", "False"]
        idx = 0 if correct.lower() == "true" else 1
    else:
        options = sorted(set(incorrect + [correct]))
        idx = options.index(correct)
    if not text:
        return None
    return C.make_question(text, options, idx, topic, difficulty, "")


class Client:
    def __init__(self, started):
        self.spacer = C.Spacer(GAP)
        self.started = started

    def out_of_time(self):
        return time.monotonic() - self.started > DEADLINE_S

    def get(self, path):
        """GET json with spacing; on HTTP 429 / code 5 wait 10s and retry (max 3)."""
        for attempt in range(4):
            self.spacer.wait()
            req = urllib.request.Request(BASE + path, headers={"User-Agent": C.USER_AGENT})
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    data = json.load(r)
            except urllib.error.HTTPError as e:
                if e.code != 429:
                    C.warn("HTTP %s for %s" % (e.code, path))
                data = None
            except Exception as e:  # network hiccup
                C.warn("request failed (%s) for %s" % (e, path))
                data = None
            if data is not None and data.get("response_code") != 5:
                return data
            if attempt < 3:
                time.sleep(10)
        return None


def new_token(client):
    d = client.get("api_token.php?command=request")
    return d.get("token") if d and d.get("response_code") == 0 else None


def fetch_all():
    client = Client(time.monotonic())
    token = new_token(client)
    cats = client.get("api_category.php")
    if not cats:
        C.warn("could not load category list")
        return []
    results = []
    for cat in cats["trivia_categories"]:
        topic = C.app_topic_for_open_tdb_label(cat["name"])
        if topic is None:
            print("skip unmapped category:", cat["name"])
            continue
        if client.out_of_time():
            C.warn("deadline reached before category %s" % cat["name"])
            break
        cnt = client.get("api_count.php?category=%d" % cat["id"])
        try:
            total = cnt["category_question_count"]["total_question_count"]
        except (TypeError, KeyError):
            C.warn("no count for %s" % cat["name"])
            continue
        remaining, got, amount = total, 0, min(50, total)
        while remaining > 0 and not client.out_of_time():
            amount = min(amount, remaining)
            q = "api.php?amount=%d&category=%d&encode=url3986" % (amount, cat["id"])
            if token:
                q += "&token=" + token
            d = client.get(q)
            if d is None:
                break
            code = d.get("response_code")
            if code == 3:
                token = new_token(client)
                continue
            if code == 1 and amount > 5:
                amount //= 2  # fewer left than asked for
                continue
            if code != 0:
                break
            for item in d["results"]:
                conv = convert(item, topic)
                if conv:
                    results.append(conv)
            got += len(d["results"])
            remaining -= len(d["results"]) or amount
        print("%-40s %5d / %5d" % (cat["name"], got, total), flush=True)
    return results


def main():
    results = fetch_all()
    results.sort(key=lambda q: (q["category"], q["text"], q["options"]))
    prev = C.load_json(OUT, [])
    if prev and len(results) < SHRINK_LIMIT * len(prev):
        C.warn("fetched %d questions but existing file has %d; keeping existing file" % (len(results), len(prev)))
        return 0
    if not results:
        C.warn("nothing fetched; not writing")
        return 0
    C.write_lines(OUT, results)
    print("wrote %d questions" % len(results))
    return 0


if __name__ == "__main__":
    sys.exit(main())
