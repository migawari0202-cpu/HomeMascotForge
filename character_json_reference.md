# character.json リファレンス

コードから逆算して生成。

---

## ファイル構成

```
mychar.zip
├── character.json       ← このファイル（必須）
├── Settings.json        ← 任意（画像の切り抜き等の追加設定）
├── speeches/
│   ├── default.txt      ← セリフファイル（1行1セリフ、# でコメント行）
│   └── ...
└── images/
    ├── normal.png
    └── ...
```

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
    { "if": "weatherCode == \"rain\"",   "emotion": "sad"     },
    { "if": "favorability > 70",         "emotion": "happy"   },
    { "default": "normal" }
  ]
}
```

### 条件式の書き方

| 演算子 | 例 |
|---|---|
| 比較 | `hour >= 6`, `temperature < 10` |
| 等値 | `weatherCode == "rain"`, `timeSlot == "morning"` |
| 不等 | `batteryLevel != 100` |
| 論理積 | `isWeekend && hour >= 10` |
| 論理和 | `isHoliday \|\| isWeekend` |
| 否定 | `!isCharging` |
| 括弧 | `(hour >= 9 && hour < 18) && !isWeekend` |

### 利用可能な標準変数（条件式・セリフ共通）

| 変数名 | 型 | 値の例 |
|---|---|---|
| `hour` | int | `0`〜`23` |
| `minute` | int | `0`〜`59` |
| `month` | int | `1`〜`12` |
| `day` | int | `1`〜`31` |
| `dayOfWeek` | string | `"Monday"` など |
| `timeSlot` | string | `"morning"` / `"afternoon"` / `"evening"` / `"night"` / `"midnight"` |
| `season` | string | `"spring"` / `"summer"` / `"autumn"` / `"winter"` |
| `isWeekend` | boolean | `true` / `false` |
| `isHoliday` | boolean | `true` / `false` |
| `holidayName` | string | `"元日"` など |
| `isSpecialDay` | boolean | |
| `specialDayName` | string | |
| `weatherCode` | string | `"clear"` / `"rain"` / `"snow"` / `"cloudy"` など |
| `weatherEmoji` | string | `"☀️"` など |
| `temperature` | int | 摂氏 |
| `temperatureFeeling` | string | `"cold"` / `"mild"` / `"hot"` |
| `humidity` | int | `0`〜`100` |
| `batteryLevel` | int | `0`〜`100` |
| `batteryStatus` | string | |
| `isCharging` | boolean | |
| `isLowBattery` | boolean | |
| `launchCount` | int | 累計起動回数 |
| `consecutiveDays` | int | 連続起動日数 |
| `lastLaunchHoursAgo` | int | 前回起動からの時間 |
| `isFirstLaunchToday` | boolean | 今日初めての起動か |
| `userName` | string | ユーザー名 |
| `userGender` | string | |
| `isNearBedtime` | boolean | |
| `isNearWakeup` | boolean | |
| `moonPhase` | string | |

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
| `file` | string | ✅ | `speeches/` 以下のパス |
| `conditions` | object | ✅ | 空オブジェクト `{}` でデフォルトルール |
| `priority` | int | | 高いほど優先。デフォルト `0` |

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
| `"number"` | 数値 | `min`, `max`（省略可） |
| `"string"` | 文字列 | `options`（省略可、選択肢リスト） |
| `"boolean"` | `true` / `false` | なし |

### `onChange`（変更ルール）

| フィールド | 値 | 備考 |
|---|---|---|
| `trigger` | `"onLaunch"` / `"onSpeech"` / `"onConsecutiveDays"` / `"onTimeSlotChange"` | |
| `condition` | 条件式（省略可） | 標準変数・カスタム変数が使える |
| `action` | `"set"` / `"increment"` / `"decrement"` / `"toggle"` | |
| `value` | 任意 | `set` / `increment` / `decrement` で使用 |

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

#### 感情タグ `[emotion:感情名]`
そのセリフを表示するときの画像を指定する。

```
うれしいな！[emotion:happy]
```

#### 変数操作タグ `[var:変数名+値]`
セリフ表示と同時にカスタム変数を操作する（`NUMBER` 型のみ）。

| 記法 | 操作 |
|---|---|
| `[var:favorability+2]` | `favorability += 2` |
| `[var:favorability-1]` | `favorability -= 1` |
| `[var:favorability*2]` | `favorability *= 2` |
| `[var:favorability/2]` | `favorability /= 2` |
| `[var:favorability=50]` | `favorability = 50` |

---

## 最小構成のサンプル

```json
{
  "id": "sample_char",
  "name": "サンプルちゃん",
  "version": "1.0.0",
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
