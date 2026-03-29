# MascotForge — CLAUDE.md

Android アプリ。ホーム画面ウィジェットにキャラクターを表示し、状況に応じたセリフを喋らせるマスコットエンジン。

---

## プロジェクト基本情報

- **言語**: Kotlin
- **UI**: Jetpack Compose (インストール画面) + RemoteViews (ウィジェット)
- **Min SDK**: 26 (Android 8) / Target SDK: 36 (Android 15)
- **パッケージ**: `com.example.mascotforge`
- **主要ライブラリ**: Room 2.7.0, WorkManager 2.10.0, Retrofit 2.9.0, Coroutines 1.9.0, Compose BOM 2024.12.01

---

## ディレクトリ構成

```
app/src/main/
├── java/
│   ├── com/example/mascotforge/        # メインパッケージ
│   │   ├── character/                  # キャラクターエンジン
│   │   ├── characters/                 # 具体的なキャラクター定義
│   │   │   └── default_character/      # デフォルトキャラクター
│   │   ├── installer/                  # ZIPインストーラー
│   │   ├── speech/                     # セリフ処理
│   │   └── ui/theme/components/        # Compose UI
│   └── widget/                         # ウィジェットシステム
│       ├── cache/                      # キャッシュ管理
│       └── database/                   # Roomデータベース(メモ)
├── assets/characters/default_character/
│   ├── images/                         # 感情別画像 (WebP)
│   └── speeches/                       # 時間帯別セリフファイル (.txt)
└── res/layout/                         # widget_normal.xml, widget_layout_compact.xml
```

---

## コアアーキテクチャ

### キャラクターシステム

```
CharacterRegistry
  └─ CharacterProvider (interface)
       ├─ InternalCharacterAdapter → DynamicCharacter (assets読み込み)
       └─ InstalledCharacterAdapter (ZIPインストール済み)

DynamicCharacter
  ├─ SpeechManager → セリフ選択 (SpeechRule優先度評価)
  ├─ CharacterStateManager → SharedPreferences永続化
  └─ CustomVariableManager → カスタム変数のトリガー管理
```

### セリフ生成フロー

```
SpeechContextFactory.create()  → SpeechContext (65+プロパティ)
  ↓
DynamicCharacter.getSpeech(context)
  ↓ SpeechRule評価 (AND/OR条件、優先度順)
テキストファイル選択 → Tagparser.parse() → 最終セリフ
```

### ウィジェット更新フロー

```
TimeWidgetProvider (AppWidgetProvider)
  ↓
WidgetUpdateCoordinator
  ↓
WidgetViewUpdater → RemoteViews更新
  ↑
キャッシュ: BatteryManager / ClockCache / UserWeatherCache / MemoCache
```

---

## 主要ファイル早見表

| ファイル | 役割 |
|---|---|
| `character/DynamicCharacter.kt` | キャラクターコア。セリフルール・感情評価・カスタム変数 |
| `character/SafeExpressionEvaluator.kt` | 条件式の安全な評価 (インジェクション防止) |
| `character/CharacterStateManager.kt` | タッチ数・日付・カスタム変数の永続化 |
| `character/CustomVariableManager.kt` | カスタム変数ランタイム管理・トリガー実行 |
| `character/CustomVariable.kt` | カスタム変数定義 (型・制約・トリガー) |
| `character/CharacterSource.kt` | sealed class: Assets | InstalledFiles |
| `SpeechContext.kt` | セリフ生成に使う全コンテキスト情報 |
| `SpeechManager.kt` | セリフ生成・表示管理 |
| `speech/SpeechContextFactory.kt` | システム状態からSpeechContext生成 |
| `speech/Tagparser.kt` | `[emotion]` `[var:]` などのタグ解析 |
| `characters/CharacterRegistry.kt` | 内部キャラクター一覧・読み込み |
| `characters/default_character/DefaultCharacter.kt` | デフォルトキャラクター定義 |
| `characters/default_character/DefaultCharacterFactory.kt` | デフォルトキャラクターのファクトリー |
| `installer/CharacterInstaller.kt` | ZIPからキャラクターをインストール |
| `installer/ZipSecurityValidator.kt` | パストラバーサル等のセキュリティ検証 |
| `widget/TimeWidgetProvider.kt` | ウィジェットのメインProvider |
| `widget/WidgetUpdateCoordinator.kt` | 複数ウィジェット更新の調整 |
| `widget/WidgetViewUpdater.kt` | RemoteViews更新処理 |
| `widget/database/MemoDatabase.kt` | Roomデータベース (メモ機能) |
| `WeatherUpdateWorker.kt` | WorkManagerによる天気定期取得 |
| `Karioki.kt` | Application エントリーポイント |

---

## キャラクターJSON仕様 (character_json_reference.md に詳細)

- `emotionRules`: 条件→感情名 のマッピング (SafeExpressionEvaluatorで評価)
- `imageMapping`: 感情名→画像ファイル名
- `customVariables`: 型 (NUMBER/STRING/BOOLEAN)・制約・トリガー定義
- `speechRules`: 優先度付き条件→セリフファイル候補リスト

セリフファイルは `assets/characters/{id}/speeches/` に配置 (時間帯: morning/afternoon/evening/night/midnight/default)

---

## 感情画像

`assets/characters/{id}/images/` に WebP 形式で配置:
`angry` / `excited` / `happy` / `sad` / `shy` / `sleepy` / `worried` / `character`(デフォルト) / `thumbnail`

---

## 設計の注意点

- **SharedPreferences のキー**: `CharacterStateManager` が `prefs_{characterId}` でキャラクターごとに分離
- **ウィジェット複数対応**: `widgetId` ごとに別キャラクターを設定可能 (`CharacterPreferences`)
- **セリフの改行**: タグパーサー処理後に最終文字列を組み立てる
- **外部キャラクター**: `filesDir/characters/{id}/` にZIP展開して保存
- **syncCharacters タスク**: ビルド前に `app/src/main/java/.../images/` → `assets/` へ自動コピー

---

## よく触るもの

- セリフ追加・変更 → `assets/characters/default_character/speeches/*.txt`
- 感情ルール変更 → `DefaultCharacter.kt` の `emotionRules`
- カスタム変数追加 → `DefaultCharacter.kt` の `customVariables`
- ウィジェットレイアウト → `res/layout/widget_normal.xml` / `widget_layout_compact.xml`
- 天気取得ロジック → `WeatherUpdateWorker.kt` / `widget/cache/UserWeatherCache.kt`
