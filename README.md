# CloudStream MegaRepo — 352 Extensions

The ultimate CloudStream extension repository — **352 unique extensions** from **31 source repos** combined into one. No duplicates.

## 📥 Add to CloudStream

1. Open CloudStream app
2. Go to **Settings → Extensions → Add repository**
3. Enter: `https://github.com/ManusClawAI/CloudStream-MegaRepo`
4. Install the extensions you want

## 📊 Stats

| | Count |
|---|---|
| Total source repos scanned | 34 |
| Successfully cloned | 31 |
| Total extension modules found | 373 |
| Duplicates removed | 21 |
| **Unique extensions in this repo** | **352** |

## 🌍 Source Repos

| Repo | Extensions |
|------|-----------|
| recloudstream/extensions (official) | 5 |
| phisher98/cloudstream-extensions-phisher | ~40 |
| SaurabhKaperwan/CSX | 7 |
| Asm0d3usX/CloudX | 14 |
| aymanbest/Arabico | 9 |
| redblacker8/storm-ext | ~30 |
| doGior/doGiorsHadEnough | ~12 |
| keyiflerolsun/Kekik-cloudstream | ~10 |
| daarkdemon/cs-darkdemon-extensions | ~15 |
| CakesTwix/cloudstream-extensions-uk | ~22 |
| ...and 21 more repos | ... |

## 📁 Structure

```
CloudStream-MegaRepo/
├── repo.json                    ← CloudStream repository manifest
├── settings.gradle.kts          ← Auto-includes all extension dirs
├── build.gradle.kts             ← Root build file (CloudStream plugin)
├── gradle/                      ← Gradle wrapper
├── extensions.json              ← Metadata for all 352 extensions
├── ExtensionName1/
│   ├── build.gradle.kts         ← Extension config
│   └── src/                     ← Kotlin source code
├── ExtensionName2/
│   └── ...
└── ... (352 extension directories)
```

## How This Repo Was Made

Automatically merged from 34 CloudStream extension repositories listed on [cloudstream-apk.com](https://cloudstream-apk.com/cloudstream-repositories-extensions/). Each source repo was cloned, all extension modules extracted, deduplicated by package name, and combined into this single mega repo.

Built with ❤️ by ManusClawAI
