# character.json リファレンス

コードから逆算して生成。

---

## ファイル構成

```
mychar.zip
└── mychar/              ← 任意。character.json があるフォルダがキャラクタールート
    ├── character.json   ← このファイル（必須）
    ├── Settings.json    ← 任意（画像の切り抜き等の追加設定）
    ├── speeches/
    │   ├── default.txt  ← セリフファイル（1行1セリフ、# でコメント行）
    │   └── ...
    └── images/
        ├── normal.png
        └── ...
```

`character.json` は ZIP 内の任意の階層に置けます。先頭の `C` は大文字・小文字のどちらでも構いませんが、大文字・小文字だけが異なる同名ファイルを複数入れることはできません。画像・セリフ・`Settings.json` は、そのファイルと同じフォルダを基準に配置してください。

- セリフファイルは1行1セリフ。空行と `#` で始まる行は読み飛ばされる
- 1ファイルあたり最大 **10,000行** まで読み込まれる（超過分は無視）
- **意図的に空のセリフファイルにする場合**は、完全な空ファイルではなく `#` のみのファイルを推奨（動作は同じだが意図が明確になる）

---

## トップレベル構造

```json
{
  "id":          "my_character",
  "name":        "マイキャラ",
  "version":     "1.0.0",
  "author":      "あなたの名前",
  "description": "キャラの説明",
  "images":      { ... },
  "emotions":    { ... },
  "speechRules": [ ... ],
  "customVariables": { ... }
}
```

| フィールド | 型 | 必須 | 備考 |
|---|---|---|---|
| `id` | string | ✅ | 英数字・アンダースコアのみ。重複不可 |
| `name` | string | ✅ | 表示名 |
| `version` | string | | デフォルト `"1.0.0"` |
| `author` | string | | デフォルト `"Unknown"` |
| `description` | string | | |
| `images` | object | | 感情名 → 画像ファイル名 |
| `emotions` | object | | 感情判定ルール |
| `speechRules` | array | | セリフファイル切り替えルール（新方式）|
| `customVariables` | object | | キャラ固有の変数定義 |

---

## `images`

感情名をキー、`images/` フォルダ内のファイル名を値にする。

```json
"images": {
  "normal":  "normal.png",
  "happy":   "happy.png",
  "sad":     "sad.png",
  "angry":   "angry.png",
  "sleepy":  "sleepy.png"
}
```

- `"normal"` キーはフォールバックに使われる
- 対応拡張子: `png`, `jpg`, `jpeg`, `webp`

---

## `emotions`

感情ルールの評価順：条件付きルールを上から評価 → 全部外れたら `default`

```json
"emotions": {
  "rules": [
    { "if": "isLowBattery",              "emotion": "worried" },
    { "if": "hour >= 22 || hour < 6",    "emotion": "sleepy"  },
    { "if": "weatherCode == \"雨\"",    "emotion": "sad"     },
    { "if": "favorability > 70",         "emotion": "happy"   },
    { "default": "normal" }
  ]
}
```

### 条件式の書き方

| 演算子 | 例 |
|---|---|
| 比較 | `hour >= 6`, `temperature < 10` |
| 等値 | `weatherCode == "雨"`, `timeSlot == "morning"` |
| 不等 | `batteryLevel != 100` |
| 論理積 | `isWeekend && hour >= 10` |
| 論理和 | `isHoliday \|\| isWeekend` |
| 否定 | `!isCharging` |
| 括弧 | `(hour >= 9 && hour < 18) && !isWeekend` |

> **評価エンジンの制限（SafeExpressionEvaluator）**:
> - 条件式の再帰深度上限は **5**。超過した式は警告ログを出して **false 扱い** になる
> - クォート内のスペースは保護される（`"晴れ 時々 曇り"` 等も正しく比較可能）
> - `!` は外側括弧のアンラップ後に評価されるため、`!(A && B)` が正常動作する
> - `weatherCode` の右辺は英語エイリアス可（`sunny`/`clear`/`partly_cloudy`/`cloudy`/`rain`/`drizzle`/`thunder`/`snow`/`fog`/`storm` → 日本語に変換して照合）

### 利用可能な標準変数（条件式・セリフ共通）

| 変数名 | 型 | 値の例 |
|---|---|---|
| `hour` | int | `0`〜`23` |
| `minute` | int | `0`〜`59` |
| `month` | int | `1`〜`12` |
| `day` | int | `1`〜`31` |
| `dayOfWeek` | string | `"月曜日"`〜`"日曜日"` |
| `timeSlot` | string | `"morning"` / `"afternoon"` / `"evening"` / `"night"` / `"midnight"` |
| `season` | string | `"春"` / `"梅雨"` / `"夏"` / `"秋"` / `"冬"` |
| `isWeekend` | boolean | `true` / `false` |
| `isHoliday` | boolean | `true` / `false` |
| `holidayName` | string | `"元日"` など |
| `isSpecialDay` | boolean | |
| `specialDayName` | string | |
| `weatherCode` | string | 実行時は日本語に正規化される（下記エイリアス参照） |
| `weatherEmoji` | string | `"☀️"` など |
| `temperature` | int | 摂氏 |
| `temperatureFeeling` | string | `"暑い"` / `"少し暑い"` / `"ちょうどいい"` / `"少し寒い"` / `"寒い"` |
| `humidity` | int? | 現状API未取得のため常に `null`。条件式では `"0"`、セリフ展開では空文字 |
| `batteryLevel` | int | `0`〜`100` |
| `batteryStatus` | string | `"充電中"` / `"省電力モード"`（残量≤15%）/ `"通常"` |
| `isCharging` | boolean | |
| `isLowBattery` | boolean | 残量 ≤ 20 で true |
| `launchCount` | int | 今日の起動回数（日付が変わるとリセット） |
| `consecutiveDays` | int | 連続起動日数 |
| `lastLaunchHoursAgo` | int? | 前回起動からの時間。初回起動時は `null`（式・展開とも `"0"` 扱い） |
| `isFirstLaunchToday` | boolean | 今日初めての起動か |
| `wasTouched` | boolean | 1分以内にタッチされていれば true |
| `touchCount` | int | 累計タッチ回数（全期間） |
| `touchCountToday` | int | 今日のタッチ回数 |
| `lastTouchMinutesAgo` | int | 最後にタッチしてからの経過分 |
| `consecutiveTouchCount` | int | 短時間（約10秒以内）の連続タッチ回数 |
| `pettingLevel` | int | 撫で段階 0〜3（連続タッチ強度） |
| `isBeingPetted` | boolean | 短時間の連続タッチ中なら true |
| `userName` | string | ユーザー名 |
| `userGender` | string | 現在未実装（常に空） |
| `isNearBedtime` | boolean | 21:00〜翌1:59 で true |
| `isNearWakeup` | boolean | 6:00〜8:59 で true |
| `moonPhase` | string | 未実装（`null` / 展開は空文字） |

---

## `speechRules`（新方式・推奨）

優先度の高いルールから評価し、最初にマッチしたファイルを使う。
`conditions` が空のルールはデフォルト（フォールバック）として機能する。

```json
"speechRules": [
  {
    "file":       "speeches/rainy.txt",
    "conditions": { "weatherCode": "rain" },
    "priority":   10
  },
  {
    "file":       "speeches/morning.txt",
    "conditions": { "timeSlot": "morning" },
    "priority":   5
  },
  {
    "file":       "speeches/default.txt",
    "conditions": {},
    "priority":   0
  }
]
```

| フィールド | 型 | 必須 | 備考 |
|---|---|---|---|
| `file` | string | △ | `speeches/` 以下のパス（`files` のどちらかは必須） |
| `files` | string[] | △ | 複数候補からランダムに1つ選択。`file` と同時指定した場合は **エラーにならず `files` が優先**され `file` は無視される |
| `conditions` | object | | 全エントリの AND。**省略可**。空 `{}` または省略で無条件マッチ（フォールバック） |
| `anyOf` | object | | いずれか1エントリが一致すれば適合（OR）。`conditions` と併用可 |
| `allOf` | object | | `conditions` の別名。両方書いた場合は `conditions` が優先される |
| `priority` | int | | 高いほど優先。デフォルト `0` |

`anyOf` の各値は、従来どおり文字列を指定できるほか、同じキーに複数の候補を指定する文字列配列にも対応しています。配列の場合は、現在値が配列内のいずれかと一致すれば適合します。

```json
"anyOf": {
  "weatherCode": ["雨", "小雨", "雷雨", "嵐", "霧"]
}
```

旧形式も後方互換のため利用できます。

```json
"anyOf": { "weatherCode": "雨" }
```

> **注意（null 変数の扱い）**: `conditions` / `anyOf` で `humidity`・`lastLaunchHoursAgo` を使う場合、
> 値が `null` のときは **常に不一致（false）** になる。emotions 条件式での `"0"` 扱いとは挙動が異なる。
>
> **注意（hour / day の特別形式）**: `"hour": "7-9"`（範囲・日跨ぎ `"22-6"` 可）や `"day": "1-3"` は使えるが、
> `"hour": ">=6"` 形式は **常に不一致** になる。
>
> **注意（カスタム変数）**: `"customVars[名前]": 値` の明示形式と `"名前": 値` の直接指定の両方が使える。
> boolean 型は `"true"` / `"false"` が整数 `1` / `0` にマッピングされて比較される。
>
> **注意（weatherCode）**: speechRules 側の英語エイリアスは emotions 条件式より少ない
> （小雨・雷雨・霧・嵐・晴れ時々曇りは日本語で書くのが安全）。

> **旧方式（`speechRules` を省略した場合）**
> `speeches/morning.txt` / `afternoon.txt` / `evening.txt` / `night.txt` / `midnight.txt` を自動ロード。

---

## `customVariables`

キャラ固有の変数。最大 **30個** まで。

```json
"customVariables": {
  "favorability": {
    "type":    "number",
    "initial": 50,
    "min":     0,
    "max":     100,
    "onChange": [
      {
        "trigger":   "onSpeech",
        "action":    "increment",
        "value":     1
      },
      {
        "trigger":   "onLaunch",
        "condition": "consecutiveDays >= 7",
        "action":    "increment",
        "value":     5
      }
    ]
  },
  "mood": {
    "type":    "string",
    "initial": "neutral",
    "options": ["happy", "neutral", "sad"]
  },
  "isAngry": {
    "type":    "boolean",
    "initial": false
  }
}
```

### 変数の型

| `type` | `initial` の型 | 追加フィールド |
|---|---|---|
| `"number"` | 数値 | `min`, `max`（省略可。省略時は上下限なし） |
| `"string"` | 文字列 | `options`（**実質必須**。下記注意参照） |
| `"boolean"` | `true` / `false` | なし |

> **STRING型の `options` に関する注意**:
> 内部では選択肢のインデックスとして整数保存するため、`options` 未定義（空）の場合は
> - セリフタグ `[v:xxx=値]` での代入が実質不可
> - onChange の `set` でも実質保存できない
> - 展開時は空文字になり、ログに警告が出る
>
> また `options` に無い値を代入した場合は拒否されるか先頭の option にフォールバックする。
>
> 変数名は英数字とアンダースコアのみ推奨（`[v:]` / `{変数名}` の対象は `[a-zA-Z_][a-zA-Z0-9_]*` 形式のみ）。

### `onChange`（変更ルール）

| フィールド | 値 | 備考 |
|---|---|---|
| `trigger` | `"onLaunch"` / `"onSpeech"` / `"onConsecutiveDays"` / `"onTimeSlotChange"` / `"onTouch"` | |
| `condition` | 条件式（省略可） | 標準変数・カスタム変数が使える |
| `action` | `"set"` / `"increment"` / `"decrement"` / `"toggle"` | |
| `value` | 任意 | `set` / `increment` / `decrement` で使用。`increment` / `decrement` 省略時は ±1 |

**trigger ごとの発火タイミング:**

- `onLaunch`: 今日初めての起動コンテキストでセリフ取得したとき
- `onSpeech`: セリフが実際に表示されたときのみ（候補0件で表示失敗時は発火しない）
- `onConsecutiveDays`: 連続起動日数が前回観測値から変化したとき。SharedPreferences 跨ぎで検知され、キャラごとの初回観測時はベースライン保存のみで発火しない
- `onTimeSlotChange`: 同一プロセス内で前回 getSpeech 時と timeSlot が変わったとき
- `onTouch`: ウィジェットをタップしたとき（ウィジェット・置時計の両方から発火）

※ 数値型の `min` / `max` を指定した場合、更新値はその範囲にクランプされる。

---

## セリフファイル（.txt）の書き方

```
# 朝のセリフ（# から始まる行はコメント、無視される）
おはよう、{userName}！今日も一緒に頑張ろうね！
今日は{weatherEmoji}だね。{temperatureFeeling == "cold" ? 寒いね : いい天気だね}！
今日で{consecutiveDays}日連続だよ！すごい！[var:favorability+2]
```

### 利用可能なタグ

#### 変数展開 `{変数名}`
標準変数・カスタム変数のどちらも使える。

```
こんにちは{userName}！好感度は{favorability}だよ。
```

> **展開の優先順位に注意**: 標準変数が **先に** 置換されるため、
> 標準変数と同名のカスタム変数（例: `hour`）を定義しても `{}` 展開では標準変数の値が使われる。

#### 三項演算子 `{条件 ? 真値 : 偽値}`
条件式を評価し、結果に応じて表示テキストを切り替える。条件部には標準変数・カスタム変数が使用可能。

```
今日は{weatherEmoji}だね。{temperatureFeeling == "cold" ? 寒いね : いい天気だね}！
```

#### 感情タグ `[emotion:感情名]` / `[e:感情名]`
そのセリフを表示するときの画像を指定する。`[e:感情名]` は短縮形。

```
うれしいな！[emotion:happy]
たのしい！[e:happy]
```

#### 変数操作タグ `[var:式]` / `[v:式]`
セリフ表示と同時にカスタム変数を操作する。**NUMBER / STRING / BOOLEAN の全型に対応**。
`[v:式]` は短縮形。空白の有無はどちらでも可（例: `[v:favorability+2]` と `[v: favorability + 2]`）。1行に複数書いてもよい。

**NUMBER型:**

| 記法 | 操作 |
|---|---|
| `[var:favorability+2]` | `favorability += 2` |
| `[var:favorability-1]` | `favorability -= 1` |
| `[var:favorability*2]` | `favorability *= 2` |
| `[var:favorability/2]` | `favorability /= 2`（0での除算は無視） |
| `[var:favorability=50]` | `favorability = 50` |

**STRING型:**

| 記法 | 操作 |
|---|---|
| `[var:mood=happy]` | `mood = "happy"` |
| `[var:mood=うれしい]` | 日本語（マルチバイト）も可 |

- 等号の右辺（RHS）は `[ ]` を含まない最大50文字の任意文字列が可能
- `options` 外の値はスキップ（警告ログ）。`options` 未定義の STRING 型には代入不可

**BOOLEAN型:**

| 記法 | 操作 |
|---|---|
| `[var:isAngry=true]` / `[var:isAngry=false]` | 代入（小文字化される） |
| `[var:isAngry=toggle]` | 現在値を反転 |

**型の不一致はスキップ**（警告ログ、セリフ表示には影響しない）:

- NUMBER型に `[v:xxx=happy]` → スキップ
- STRING型に `[v:xxx=toggle]` → スキップ
- BOOLEAN型に `[v:xxx=toggle]` → 正常動作

#### 改行タグ `{br}` とエスケープ `\n`
セリフ内で改行したい場合は `{br}`（大文字小文字不問）または `\n` を使用する。

```
1行目{br}2行目
1行目\n2行目
```
どちらも以下のように表示される：
> 1行目
> 2行目


## 最小構成のサンプル

```json
{
  "id": "sample_char",
  "name": "サンプルちゃん",
    "version":     "1.0.0",
  "author": "あなた",
  "images": {
    "normal": "normal.png",
    "happy":  "happy.png"
  },
  "emotions": {
    "rules": [
      { "if": "isFirstLaunchToday", "emotion": "happy" },
      { "default": "normal" }
    ]
  },
  "speechRules": [
    { "file": "speeches/default.txt", "conditions": {}, "priority": 0 }
  ]
}
```

---

## `Settings.json`（任意）

`character.json` と同階層に置く。
存在しない場合、画像の切り抜き処理は **行われない**。

### `imageCutout`

感情タグ（`images` のキー）ごとに「指定色を透明化」する設定。
`byTag["*"]` は全タグ共通のデフォルトとして使える。

```json
{
  "imageCutout": {
    "defaultTolerance": 30,
    "byTag": {
      "*": ["#fc0000"],
      "happy": { "tolerance": 20, "colors": ["#00ff00", "#0000ff"] }
    }
  }
}
```

- `byTag` の値は以下を受け付ける
  - `["#RRGGBB", ...]`（色配列、最大10色）
  - `"#RRGGBB"`（単色）
  - `{ "tolerance": 0-255, "colors": [...] }`
