# Publishing Quizzer on Google Play

Everything code-side is ready. This is the checklist for the parts done in Play Console.

| | |
|---|---|
| **App name** | Quizzer: Daily Trivia |
| **Package name (permanent)** | `nz.durbs.quizzer` |
| **Dev builds** | `nz.durbs.quizzer.debug` ("Quizzer Dev"), install alongside the store app |
| **Privacy policy URL** | https://github.com/MrPresident03/Quizzer/blob/main/PRIVACY.md |
| **Listing text** | `fastlane/metadata/android/en-US/` |
| **Icon / feature graphic** | `fastlane/metadata/android/en-US/images/` |

## 1. One-time setup

1. **Keep the upload key safe.** `quizzer-upload-key.jks` and its password are not in this repo. Store both in a password manager (and a second copy somewhere safe). If they're ever lost, Google can reset the upload key, but it takes a support request.
2. **Add two GitHub secrets** (repo → Settings → Secrets and variables → Actions → New repository secret):
   - `QUIZZER_UPLOAD_KEYSTORE_BASE64`: the full contents of `quizzer-upload-key.base64.txt`
   - `QUIZZER_UPLOAD_KEYSTORE_PASSWORD`: the keystore password
3. **Create a Play developer account** at https://play.google.com/console (one-time US$25 fee, ID verification).

## 2. Build a release

Actions tab → **Release** → **Run workflow** → version `1.0.0`. (Or push a tag: `git tag v1.0.0 && git push origin v1.0.0`.)

It produces:
- `Quizzer-1.0.0.aab`: upload this to Play Console
- `Quizzer-1.0.0.apk`: installable APK for testing or sideloading
- `mapping-1.0.0.txt`: upload in Play Console (App bundle explorer → Downloads) so crash reports are readable

Both files are also attached to a GitHub Release. Each run gets a higher version code automatically.

## 3. Create the app in Play Console

- Create app → name **Quizzer: Daily Trivia**, default language English, **App**, **Free**.
- **Play App Signing**: accept the default (Google manages the app signing key; you upload with the upload key).

### App content answers

| Section | Answer |
|---|---|
| Privacy policy | URL above |
| Ads | No, the app contains no ads |
| App access | All functionality is available without special access |
| Content rating | Category **Reference, News, or Educational** (or Game → Trivia). No violence, sexual content, profanity, drugs, gambling or user interaction. Some quiz questions mention historical events and criminal law at a factual level. |
| Target audience | **13 and over** (choosing under-13 brings in the Families policy requirements) |
| Data safety | **No data collected, no data shared.** Questions are downloaded from opentdb.com over HTTPS, but no user data is sent. There are no accounts, so account deletion doesn't apply. |
| Government / financial / health features | None |
| News app | No |

### Store listing

- Title, short and full description: copy from `fastlane/metadata/android/en-US/`
- App icon: `images/icon.png` (512×512)
- Feature graphic: `images/featureGraphic.png` (1024×500)
- **Phone screenshots: 2–8 needed.** Take them on your phone from the release build: Home, a question with the explanation showing, the Daily Challenge result, Progress/Topic mastery, Practice.
- Category: **Trivia** (Games) or **Education**. Contact email required (it's public).

## 4. Testing, then production

New personal developer accounts must run a **closed test with at least 12 testers opted in for 14 days in a row** before applying for production.

1. Testing → Closed testing → create a track, upload the `.aab`, add testers (an email list or a Google Group).
2. Send testers the opt-in link; they must stay opted in for 14 consecutive days.
3. Upload new versions to the same track as you fix things (run the Release workflow with a new version each time).
4. After 14 days: Dashboard → **Apply for production**, then promote the release.

## Later (optional)

- Automate uploads to Play from the Release workflow with a Play Console service account.
- iOS would need a separate app (this one is Android-only).
