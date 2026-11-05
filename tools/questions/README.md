# Question bank pipeline

- `curated.json`: hand-written questions (source of truth, edit by hand).
- `fetch_opentdb.py`: downloads Open Trivia DB (rate limited, ~15 min) into `sources/opentdb.json`.
- `fetch_wikidata.py`: runs SPARQL templates into `sources/wikidata.json`; a failing template keeps its old items.
- `build_bank.py`: validates + dedupes curated > opentdb > wikidata, writes `app/src/main/assets/questions.json` and `STATS.md`.
- Run: `python3 tools/questions/build_bank.py` (offline); tests: `python3 -m unittest discover tools/questions`.
- Fetchers need network access to opentdb.com / query.wikidata.org; CI does it (`question-bank.yml`, monthly).
- Licences: Open Trivia DB is CC BY-SA 4.0, so the generated `questions.json` is CC BY-SA 4.0 too; Wikidata is CC0.

## Wikidata templates

`fetch_wikidata.py` runs one `t_*` function per template (`TEMPLATES`). Newer templates run first so the time budget never starves them; a template with fewer than 20 items (or a failure) keeps its previous items. Every query has a sitelinks floor and a LIMIT, and each prints `PROBE` lines (id to English label, plus a few discovery lookups) and 3 sample questions in the job log.

| Topic | Templates (key) |
|---|---|
| Geography | `geography` (river / mountain / lake / island / volcano / waterfall / desert / national park to country), `geo_heritage` (World Heritage Sites), `geo_cities` (non-capital cities), `geo_continents`. No capital-city questions: the app has a capitals mode. |
| Sports & Recreation | `sports_people` (one query per sport, athlete occupations only), `events` (Olympic host cities; FIFA World Cup, Women's World Cup and UEFA Euro host countries; cancelled and future editions skipped) |
| Politics & World Leaders | `leaders` (ordinal offices, only when the numbering is a clean 1..N), `leaders_current` (current head of state / government), `leaders_country` (which country a leader led) |
| Food & Drink | `food_origin` (country of origin of dishes, cheeses, cocktails, drinks) |
| Business & Economics | `company_founders`, `company_hq`, `currency`, `company_parent` |
| Health & Medicine | `disease_cause`, `drug_discoverer`, `nobel_medicine` (laureates per year), `organ_systems` |
| Language & Linguistics | `official_language`, `writing_system`, `language_family` (top-level families only) |
| Fashion & Design | `fashion_founders`, `fashion_country`, `fashion_designers` |
| True Crime & Law | `crimes` (deceased people only; no victims, no sexual offences), `legal_bodies` (legislature / highest court to country), `trials` (legal case to country) |

Quality rules enforced by `build_pairs`: one right answer only (items with more values than `multi` are dropped; for one-to-many relations every valid answer is excluded from the distractors), distractors come from the same kind of entity, labels that are QIDs, empty, or contain commas are dropped, answers that share a word with the subject are dropped (`leak`), and a per-answer cap keeps one answer (e.g. "United States", "euro") from dominating. Difficulty comes from sitelinks tertiles.
