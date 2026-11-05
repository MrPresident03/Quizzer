# Question bank pipeline

- `curated.json`: hand-written questions (source of truth, edit by hand).
- `fetch_opentdb.py`: downloads Open Trivia DB (rate limited, ~15 min) into `sources/opentdb.json`.
- `fetch_wikidata.py`: runs SPARQL templates into `sources/wikidata.json`; a failing template keeps its old items.
- `build_bank.py`: validates + dedupes curated > opentdb > wikidata, writes `app/src/main/assets/questions.json` and `STATS.md`.
- Run: `python3 tools/questions/build_bank.py` (offline); tests: `python3 -m unittest discover tools/questions`.
- Fetchers need network access to opentdb.com / query.wikidata.org; CI does it (`question-bank.yml`, monthly).
- Licences: Open Trivia DB is CC BY-SA 4.0, so the generated `questions.json` is CC BY-SA 4.0 too; Wikidata is CC0.
