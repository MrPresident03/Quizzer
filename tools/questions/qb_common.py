"""Shared helpers for the question-bank scripts (stdlib only)."""
import json
import os
import re
import sys
import time

USER_AGENT = "QuizzerQuestionBank/1.0 (+https://github.com/MrPresident03/Quizzer)"
HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES_DIR = os.path.join(HERE, "sources")

APP_TOPICS = [
    "General Knowledge", "Science & Nature", "History", "Geography", "Sports & Recreation",
    "Entertainment", "Literature & Books", "Art & Architecture", "Food & Drink",
    "Technology & Computing", "Mathematics & Logic", "Pop Culture & Memes",
    "Language & Linguistics", "Politics & World Leaders", "Business & Economics",
    "Health & Medicine", "Religion & Mythology", "Animals & Wildlife", "Fashion & Design",
    "True Crime & Law",
]

_WS = re.compile(r"\s+")


def normalized_text(text):
    """Mirror of QuestionPicker.normalizedText: trim, lowercase, collapse whitespace."""
    return _WS.sub(" ", text.strip().lower())


def app_topic_for_open_tdb_label(label):
    """Mirror of Categories.appTopicForOpenTdbLabel.

    Returns None for labels the Kotlin function only handles via its catch-all branch
    (everything except the explicit "General Knowledge"), so those can be skipped.
    """
    if label == "Entertainment: Books":
        return "Literature & Books"
    if label in ("Entertainment: Comics", "Entertainment: Japanese Anime & Manga", "Celebrities"):
        return "Pop Culture & Memes"
    if label.startswith("Entertainment"):
        return "Entertainment"
    if label in ("Science: Computers", "Science: Gadgets"):
        return "Technology & Computing"
    if label == "Science: Mathematics":
        return "Mathematics & Logic"
    simple = {
        "Science & Nature": "Science & Nature", "Mythology": "Religion & Mythology",
        "Sports": "Sports & Recreation", "Geography": "Geography", "History": "History",
        "Politics": "Politics & World Leaders", "Art": "Art & Architecture",
        "Animals": "Animals & Wildlife", "General Knowledge": "General Knowledge",
    }
    return simple.get(label)


def make_question(text, options, correct_index, category, difficulty, explanation="", extra=None):
    q = {
        "text": text, "options": options, "correctIndex": correct_index,
        "category": category, "difficulty": difficulty,
        "explanation": explanation, "imageUrl": None,
    }
    if extra:
        q.update(extra)
    return q


def load_json(path, default=None):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def write_lines(path, items):
    """One compact question per line: stable, reviewable diffs."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write("[\n" + ",\n".join(json.dumps(i, ensure_ascii=False, separators=(",", ":")) for i in items) + "\n]\n")


def warn(msg):
    print("WARNING: " + msg, flush=True)
    if os.environ.get("GITHUB_ACTIONS"):
        print("::warning::" + msg.replace("\n", " "), flush=True)


class Spacer:
    """Ensures at least `gap` seconds between calls to wait()."""

    def __init__(self, gap):
        self.gap = gap
        self.last = 0.0

    def wait(self):
        delta = self.last + self.gap - time.monotonic()
        if delta > 0:
            time.sleep(delta)
        self.last = time.monotonic()
