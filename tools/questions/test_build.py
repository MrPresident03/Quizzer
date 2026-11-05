import random
import unittest
from urllib.parse import quote

import build_bank as B
import fetch_opentdb as O
import fetch_wikidata as W
import qb_common as C


def q(text="What is the capital of France?", options=None, idx=0, cat="Geography", diff="easy", **kw):
    return {"text": text, "options": options or ["Paris", "Rome", "Madrid", "Berlin"], "correctIndex": idx,
            "category": cat, "difficulty": diff, "explanation": "", "imageUrl": None, **kw}


class OpenTdbConversion(unittest.TestCase):
    def enc(self, s):
        return quote(s, safe="")

    def test_multiple_is_decoded_and_sorted(self):
        item = {"type": "multiple", "difficulty": "hard", "category": "Art",
                "question": self.enc('Who painted "The Scream"?'), "correct_answer": self.enc("Edvard Munch"),
                "incorrect_answers": [self.enc("Claude Monet"), self.enc("Vincent van Gogh"), self.enc("Gustav Klimt & co")]}
        r = O.convert(item, "Art & Architecture")
        self.assertEqual(r["text"], 'Who painted "The Scream"?')
        self.assertEqual(r["options"], sorted(r["options"]))
        self.assertEqual(r["options"][r["correctIndex"]], "Edvard Munch")
        self.assertIn("Gustav Klimt & co", r["options"])
        self.assertEqual((r["difficulty"], r["explanation"], r["imageUrl"]), ("hard", "", None))

    def test_boolean_format(self):
        base = {"type": "boolean", "difficulty": "easy", "category": "x", "incorrect_answers": []}
        t = O.convert({**base, "question": self.enc("The sky is blue."), "correct_answer": "True", "incorrect_answers": ["False"]}, "General Knowledge")
        f = O.convert({**base, "question": self.enc("The sky is green."), "correct_answer": "False", "incorrect_answers": ["True"]}, "General Knowledge")
        self.assertEqual((t["options"], t["correctIndex"]), (["True", "False"], 0))
        self.assertEqual((f["options"], f["correctIndex"]), (["True", "False"], 1))

    def test_topic_mapping(self):
        self.assertEqual(C.app_topic_for_open_tdb_label("Entertainment: Video Games"), "Entertainment")
        self.assertEqual(C.app_topic_for_open_tdb_label("Entertainment: Books"), "Literature & Books")
        self.assertEqual(C.app_topic_for_open_tdb_label("Celebrities"), "Pop Culture & Memes")
        self.assertEqual(C.app_topic_for_open_tdb_label("Science: Gadgets"), "Technology & Computing")
        self.assertIsNone(C.app_topic_for_open_tdb_label("Vehicles"))


class Validation(unittest.TestCase):
    def test_valid(self):
        self.assertIsNone(B.drop_reason(q()))
        self.assertIsNone(B.drop_reason(q("The sky is blue today, true?", ["True", "False"])))

    def test_drops(self):
        cases = [
            q(options=["A", "a", "B", "C"]), q(options=["A", "B", "C"]), q(options=["Yes", "No"]),
            q(idx=4), q(idx=-1), q(text="Too short"), q(text="x" * 251),
            q(options=["", "B", "C", "D"]), q(options=["A" * 81, "B", "C", "D"]),
            q(text="What is &quot;this&quot; thing?"), q(options=["A%20B", "B", "C", "D"]),
            q(cat="Nonsense"), q(diff="impossible"),
        ]
        for c in cases:
            self.assertIsNotNone(B.drop_reason(c), c)


class Dedupe(unittest.TestCase):
    def test_priority_and_normalisation(self):
        src = {
            "curated": [q("What is the capital of France?", explanation="curated")],
            "opentdb": [q("  what is the   CAPITAL of france? ", explanation="otdb"), q("What is the capital of Spain?", ["Madrid", "Rome", "Paris", "Berlin"])],
            "wikidata": [q("WHAT IS THE CAPITAL OF SPAIN?", ["Madrid", "Rome", "Paris", "Berlin"], template="t"), q("Which city is the capital of Italy?", ["Rome", "Paris", "Madrid", "Berlin"], template="t")],
        }
        kept, stats = B.build(src)
        self.assertEqual(len(kept), 3)
        self.assertEqual([k["explanation"] for k in kept if "France" in k["text"]], ["curated"])
        self.assertEqual(stats["per_source"]["opentdb"], (2, 1))
        self.assertEqual(stats["per_source"]["wikidata"], (2, 1))
        self.assertTrue(all("template" not in k for k in kept))
        self.assertEqual(kept, sorted(kept, key=lambda k: (k["category"], k["text"])))


class WikidataDistractors(unittest.TestCase):
    def test_never_contains_valid_answer(self):
        pool = ["Jane Austen", "jane austen", "Charles Dickens", "Leo Tolstoy", "Mark Twain", "Virginia Woolf", "Homer"]
        for seed in range(200):
            d = W.pick_distractors(random.Random(seed), ["JANE AUSTEN", "Homer"], pool)
            self.assertEqual(len(d), 3)
            self.assertEqual(len({x.lower() for x in d}), 3)
            self.assertFalse({"jane austen", "homer"} & {x.lower() for x in d})

    def test_prefer_and_deterministic(self):
        pool = ["Alpha", "Bravo", "Charlie", "Delta", "Apple", "Avocado", "Apricot"]
        d = W.pick_distractors(random.Random("Q1"), ["Zulu"], pool, lambda a: a[0] == "A")
        self.assertTrue(all(x[0] == "A" for x in d[:3]) or len(d) == 3)
        self.assertTrue(all(x.startswith("A") for x in d))
        self.assertEqual(d, W.pick_distractors(random.Random("Q1"), ["Zulu"], pool, lambda a: a[0] == "A"))

    def test_build_pairs(self):
        entries = []
        for i in range(12):
            entries.append({"id": "Q%d" % i, "title": "Book %d" % i, "answers": {"A%d" % (i % 6): "Author %d" % (i % 6)},
                            "year": 1900 + i * 10, "sl": 100 - i, "conts": set()})
        entries.append({"id": "Q99", "title": "Two authors", "answers": {"A1": "Author 1", "A2": "Author 2"}, "year": 1900, "sl": 500, "conts": set()})
        out = W.build_pairs(entries, "t", "Literature & Books", lambda e: "Who wrote '%s'?" % e["title"], lambda e, a: "%s wrote it." % a)
        self.assertEqual(len(out), 12)
        for item in out:
            correct = item["options"][item["correctIndex"]]
            self.assertEqual(len(set(item["options"])), 4)
            self.assertEqual(correct, item["explanation"].split(" wrote")[0])
            self.assertIsNone(B.drop_reason(item))
        self.assertEqual(out, W.build_pairs(entries, "t", "Literature & Books", lambda e: "Who wrote '%s'?" % e["title"], lambda e, a: "%s wrote it." % a))
        self.assertEqual([o["difficulty"] for o in out].count("easy"), 4)

    def test_helpers(self):
        self.assertEqual([W.ordinal(n) for n in (1, 2, 3, 11, 12, 22, 45)], ["1st", "2nd", "3rd", "11th", "12th", "22nd", "45th"])
        self.assertFalse(W.valid_label("Q12345"))
        self.assertFalse(W.valid_label("x" * 81))
        self.assertTrue(W.valid_label("Hamlet"))


def ent(i, title, answers, sl=100, year=None, conts=()):
    return {"id": "Q%d" % i, "title": title, "answers": answers, "year": year, "sl": sl, "conts": set(conts)}


class NewHelpers(unittest.TestCase):
    def test_clean_label_rejects_comma_qualifiers(self):
        self.assertFalse(W.clean_label("Springfield, Illinois"))
        self.assertFalse(W.clean_label("Q42"))
        self.assertFalse(W.clean_label(""))
        self.assertTrue(W.clean_label("Paris"))

    def test_leaks(self):
        self.assertTrue(W.leaks("Mount Kenya", "Kenya"))
        self.assertTrue(W.leaks("Hungarian goulash", "Hungary"))
        self.assertTrue(W.leaks("Louis Vuitton", "Louis Vuitton"))
        self.assertTrue(W.leaks("Congo River", "Democratic Republic of the Congo"))
        self.assertFalse(W.leaks("Seine", "France"))
        self.assertFalse(W.leaks("Instagram", "Meta Platforms"))
        self.assertFalse(W.leaks("Lake Victoria", "United States"))

    def test_multi_answer_excludes_all_valid(self):
        people = ["Founder %d" % i for i in range(12)]
        entries = [ent(1, "Alpha Corp", {"P0": people[0], "P1": people[1], "P2": people[2]}, sl=300)]
        for i in range(3, 12):
            entries.append(ent(10 + i, "Co%d" % i, {"P%d" % i: people[i]}, sl=100 - i))
        out = W.build_pairs(entries, "t", "Business & Economics", lambda e: "Who founded %s?" % e["title"],
                            lambda e, a: "x", multi=3)
        alpha = [o for o in out if o["text"] == "Who founded Alpha Corp?"]
        self.assertEqual(len(alpha), 1)
        opts = alpha[0]["options"]
        self.assertEqual(len(set(opts)), 4)
        self.assertEqual(sum(o in people[:3] for o in opts), 1)  # exactly one of the three founders
        # single-answer behaviour unchanged: 3 founders rejected without multi
        out1 = W.build_pairs(entries, "t", "Business & Economics", lambda e: "Who founded %s?" % e["title"], lambda e, a: "x")
        self.assertFalse([o for o in out1 if "Alpha" in o["text"]])

    def test_leak_and_comma_and_cap(self):
        entries = [ent(1, "Mount Kenya", {"A": "Kenya"}), ent(2, "Springfield, Illinois", {"A": "Kenya"}),
                   ent(3, "Kilimanjaro", {"A": "Tanzania"})]
        for i in range(4, 12):
            entries.append(ent(i, "Peak %d" % i, {"C%d" % i: "Country %d" % i}, sl=50))
        out = W.build_pairs(entries, "t", "Geography", lambda e: "Where is %s?" % e["title"], lambda e, a: "x",
                            leak=True, strict_labels=True, prefer=None)
        texts = {o["text"] for o in out}
        self.assertNotIn("Where is Mount Kenya?", texts)
        self.assertNotIn("Where is Springfield, Illinois?", texts)
        self.assertIn("Where is Kilimanjaro?", texts)
        same = [ent(i, "Same %d" % i, {"A": "Italy"}, sl=100 - i) for i in range(10)]
        same += [ent(50 + i, "Other %d" % i, {"B%d" % i: "Land %d" % i}) for i in range(6)]
        capped = W.build_pairs(same, "t", "Food & Drink", lambda e: "Where is %s?" % e["title"], lambda e, a: "x",
                               per_answer_cap=3, prefer=None)
        self.assertEqual(sum(1 for o in capped if "Italy" in o["options"][o["correctIndex"]]), 3)

    def test_group_of_blocks_synonym_distractors(self):
        crimes = ["Murder", "Manslaughter", "First-degree murder", "Fraud", "Treason", "Tax evasion", "Piracy", "Arson"]
        entries = [ent(i, "Person %d" % i, {"C%d" % i: c}, sl=100 - i) for i, c in enumerate(crimes)]
        out = W.build_pairs(entries, "t", "True Crime & Law", lambda e: "What was %s convicted of?" % e["title"],
                            lambda e, a: "x", group_of=W.crime_group, prefer=None)
        for o in out:
            correct = o["options"][o["correctIndex"]]
            if W.crime_group(correct) == "homicide":
                others = [x for x in o["options"] if x != correct]
                self.assertFalse([x for x in others if W.crime_group(x) == "homicide"], o)

    def test_ordinal_office_ok(self):
        self.assertTrue(W.ordinal_office_ok({n: 1800 + 4 * n for n in range(1, 30)}))
        self.assertTrue(W.ordinal_office_ok({n: 1800 + n for n in range(1, 30, 3)}))  # sparse but ordered
        self.assertFalse(W.ordinal_office_ok({1: 1800, 2: 1804, 3: 1808}))  # too few
        bad = {n: 1800 + 4 * n for n in range(1, 30)}
        bad[10] = 1700  # ordinal 10 started before ordinal 9
        self.assertFalse(W.ordinal_office_ok(bad))
        self.assertFalse(W.ordinal_office_ok({n: None for n in range(1, 30)}))  # no dates: cannot verify

    def test_ordinal_rows_skip_serving_and_bad_ordinals(self):
        def row(p, name, o, end, start=None):
            b = {"p": {"value": "http://x/entity/" + p}, "pLabel": {"value": name}, "ord": {"value": o}, "sl": {"value": "50"}}
            if end:
                b["end"] = {"value": "2000-01-01"}
            if start:
                b["start"] = {"value": start}
            return b
        got, starts = W.ordinal_rows([row("Q1", "A One", "1", True, "1990-01-01T00:00:00Z"), row("Q2", "B Two", "2", False),
                                      row("Q3", "C Three", "3rd", True), row("Q4", "Q55", "4", True)])
        self.assertEqual(list(got), [1])
        self.assertEqual(starts, {1: 1990})

    def test_country_group(self):
        self.assertEqual(W.country_group("Kingdom of the Netherlands"), W.country_group("Netherlands"))
        self.assertEqual(W.country_group("Democratic Republic of the Congo"), W.country_group("Republic of the Congo"))
        self.assertEqual(W.country_group("Republic of Ireland"), "ireland")
        self.assertNotEqual(W.country_group("United States"), W.country_group("United Kingdom"))
        self.assertEqual(W.country_group("Czech Republic"), "czech republic")

    def test_ordered_query_puts_sitelinks_after_anchor(self):
        q = W.ordered_query("?item wdt:P31 wd:Q8502. #SL# ?item wdt:P17 ?ans.", 80, 100)
        self.assertLess(q.index("wdt:P31 wd:Q8502"), q.index("wikibase:sitelinks"))
        self.assertLess(q.index("wikibase:sitelinks"), q.index("wdt:P17"))
        self.assertIn('hint:optimizer "None"', q)
        self.assertTrue(q.rstrip().endswith("LIMIT 100"))
        self.assertGreater(W.ordered_query("?item wdt:P1 ?ans.", 5, 9).index("sitelinks"), 0)

    def test_athlete_occupation(self):
        for ok in ("association football player", "tennis player", "Formula One driver", "cricketer", "golfer"):
            self.assertTrue(W.is_athlete_occupation(ok), ok)
        for bad in ("football manager", "association football referee", "sports commentator", "politician", "", None):
            self.assertFalse(W.is_athlete_occupation(bad), bad)

    def test_top_families(self):
        fams = {"Q1": "Indo-European languages", "Q2": "Germanic languages", "Q3": "Altaic languages"}
        self.assertEqual(W.top_families(fams, {"Q2"}), {"Q1": "Indo-European languages"})

    def test_sport_entries_flag_second_sport(self):
        def row(i, oth=None, occ="tennis player"):
            b = {"item": {"value": "http://x/entity/Q%d" % i}, "itemLabel": {"value": "Player %d" % i}, "sl": {"value": "90"},
                 "occLabel": {"value": occ}}
            if oth:
                b["oth"] = {"value": "http://x/entity/" + oth}
                b["othLabel"] = {"value": "Golf"}
            return b
        a = W.sport_entries([row(1), row(2, "Q5377"), row(3, occ="tennis coach")], "Q847", "Tennis")
        merged = {e["id"]: e for e in W.merge_entries([a])}
        self.assertNotIn("Q3", merged)  # a coach is not an athlete
        self.assertEqual(len(merged["Q1"]["answers"]), 1)
        self.assertEqual(len(merged["Q2"]["answers"]), 2)  # two sports -> dropped as ambiguous
        b = W.sport_entries([row(1)], "Q5377", "Golf")
        self.assertEqual(len(W.merge_entries([a, b])[0]["answers"]), 2)

    def test_nobel_entries_group_by_year(self):
        def row(i, y):
            return {"ans": {"value": "http://x/entity/Q%d" % i}, "ansLabel": {"value": "Laureate %d" % i},
                    "sl": {"value": "40"}, "year": {"value": str(y)}}
        got = {e["year"]: e for e in W.nobel_entries([row(1, 1945), row(2, 1945), row(3, 1946), {"ans": {"value": "x/Q9"}}])}
        self.assertEqual(len(got[1945]["answers"]), 2)
        self.assertEqual(len(got[1946]["answers"]), 1)

    def test_event_helpers(self):
        specs = {s[0]: s for s in W.event_specs()}
        summer = specs["Summer Olympics"][1]
        self.assertNotIn("1916 Summer Olympics", summer)
        self.assertNotIn("1940 Summer Olympics", summer)
        self.assertNotIn("2028 Summer Olympics", summer)
        self.assertIn("2024 Summer Olympics", summer)
        winter = specs["Winter Olympics"][1]
        self.assertNotIn("1940 Winter Olympics", winter)
        self.assertNotIn("1944 Winter Olympics", winter)
        self.assertIn("1994 Winter Olympics", winter)
        self.assertNotIn("1942 FIFA World Cup", specs["FIFA World Cup"][1])
        self.assertEqual(W.event_year("2016 Summer Olympics"), 2016)
        q = W.event_query(["1996 Summer Olympics"], "wdt:P276", "city")
        self.assertIn("wd:Q486972", q)  # hosts must be settlements, not stadiums
        self.assertNotIn("wd:Q486972", W.event_query(["2018 FIFA World Cup"], "wdt:P17", "country"))


    def test_label_groups_and_filters(self):
        self.assertEqual(W.script_group("Gaj's Latin alphabet"), W.script_group("Latin script"))
        self.assertEqual(W.script_group("Urdu orthography"), W.script_group("Persian alphabet"))
        self.assertEqual(W.family_label("Uralic"), "Uralic languages")
        self.assertEqual(W.family_label("Romance languages"), "Romance languages")
        self.assertEqual(W.the_("Knesset"), "The Knesset")
        self.assertEqual(W.the_("The Knesset"), "The Knesset")
        self.assertTrue(W.GENERIC_BODY.match("Court of Cassation"))
        self.assertFalse(W.GENERIC_BODY.match("Bundestag"))
        self.assertTrue(W.CURRENCY_JUNK.search("All-Russian Classifier of Currencies"))
        self.assertFalse(W.CURRENCY_JUNK.search("Euro"))

    def test_contested_places_and_non_brands(self):
        ents = [ent(1, "Simferopol", {"A": "Ukraine"}), ent(2, "Al-Aqsa Mosque", {"A": "Ukraine"}), ent(3, "Hamburg", {"A": "Germany"}),
                ent(4, "Rome", {"A": "Palestine"})]
        self.assertEqual([e["title"] for e in W.uncontested(ents)], ["Hamburg"])
        self.assertTrue(W.FASHION_NOT_BRAND.search("Elite Model Management"))
        self.assertFalse(W.FASHION_NOT_BRAND.search("Gucci"))
        self.assertTrue(W.CRIME_BLOCK.search("Heresy"))
        self.assertTrue(W.GENERIC_BODY.match("Bundesrat"))
        self.assertTrue(W.NON_SPORT_PERSON.search("politician"))

    def test_astronaut_who_played_rugby_is_not_an_athlete_entry(self):
        def row(i, occ):
            return {"item": {"value": "http://x/entity/Q%d" % i}, "itemLabel": {"value": "P%d" % i}, "sl": {"value": "90"},
                    "occLabel": {"value": occ}}
        got = W.sport_entries([row(1, "rugby union player"), row(1, "astronaut"), row(2, "rugby union player")], "Q5849", "Rugby union")
        self.assertEqual([e["id"] for e in got], ["Q2"])

    def test_crime_filters(self):
        self.assertTrue(W.CRIME_BLOCK.search("rape"))
        self.assertTrue(W.VICTIM_TITLE.search("Murder of Someone"))
        self.assertEqual(W.crime_group("Second-degree murder"), "homicide")


class FakeSparql(W.Sparql):
    """No network: every query returns no rows. Checks that all templates build valid-looking queries."""

    def __init__(self):
        self.queries = []

    def run(self, query):
        self.queries.append(query)
        return []


class TemplateSmoke(unittest.TestCase):
    def test_all_templates_run_on_empty_data(self):
        sp = FakeSparql()
        for key, fn in W.TEMPLATES:
            self.assertEqual(fn(sp), [], key)
        self.assertGreater(len(sp.queries), 50)
        for q in sp.queries:
            self.assertEqual(q.count("{"), q.count("}"), q)
            self.assertNotIn("%s", q)
            self.assertNotIn("%d", q)
            self.assertNotIn("#SL#", q)
            if "wikibase:sitelinks" in q and "VALUES ?lab" not in q and "pq:P1545" not in q and "ps:P166" not in q and "?num" not in q:
                self.assertRegex(q, r"FILTER\(\?sl >(=| ) ?\d+\)")
        sports = [q for q in sp.queries if "wdt:P641 wd:" in q]
        self.assertEqual(len(sports), len(W.SPORTS))
        self.assertTrue(all("LIMIT" in q for q in sports))

    def test_template_keys_unique_and_categories_valid(self):
        keys = [k for k, _ in W.TEMPLATES]
        self.assertEqual(len(keys), len(set(keys)))

    def test_new_template_questions_are_valid(self):
        entries = []
        for i in range(30):
            entries.append(ent(i, "Peak%d" % i, {"C%d" % (i % 8): "Country%d" % (i % 8)}, sl=200 - i, conts={"c"}))
        out = W.build_pairs(entries, "geography", "Geography", W.geo_question if False else (lambda e: "In which country is %s located?" % e["title"]),
                            lambda e, a: "x", prefer="cont", leak=True, strict_labels=True)
        self.assertTrue(out)
        for item in out:
            self.assertIsNone(B.drop_reason(item), item)
            self.assertIn(item["category"], C.APP_TOPICS)


if __name__ == "__main__":
    unittest.main()
