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


if __name__ == "__main__":
    unittest.main()
