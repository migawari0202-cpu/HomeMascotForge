# HomeMascot

HomeMascot は、Android のホーム画面に「キャラクター付きデスクトップマスコット風ウィジェット」を置くためのアプリです。  
時計・天気・バッテリー・メモをまとめて表示しつつ、キャラクターが状況に応じてセリフや表情を変えます。

## できること

- ホーム画面ウィジェットに時刻・天気・バッテリー残量・メモを表示
- キャラクターごとに画像・セリフ・条件分岐ルールを切り替え
- タップ時の反応や、時間帯 / 天気 / 連続起動日数などに応じた会話変化
- ZIP 配布された追加キャラクターのインストール
- ウィジェットごとのキャラクター設定とデフォルトキャラクター管理
- 起動後や再起動後の更新スケジュール維持

## 主な特徴

### 1. キャラクターがしゃべるウィジェット

ウィジェット上のキャラクターは、以下のような情報をもとに表示内容を変えます。

- 時間帯
- 曜日・季節・祝日
- 天気と気温
- バッテリー状態
- 連続起動日数
- タップ操作による状態変化

セリフは単純な固定文ではなく、条件付きルールやカスタム変数を使って変化させられます。

### 2. メモ表示

- Room でメモを保存
- ウィジェット上に短いメモを表示
- アプリ内画面から追加・確認・削除

### 3. 天気連携

- 現在地ベースで天気を取得
- OpenWeatherMap API を利用
- WorkManager とアラームで定期更新

### 4. 追加キャラクターの導入

- `application/zip` を受け取ってキャラクターを導入
- `character.json` を中心に、画像・セリフ・設定をまとめて配布可能
- 外部配布キャラクター向けに ZIP 安全検証を実装

## セットアップ

### 前提

- Android Studio
- Android SDK
- JDK 11
- Android 8.0 以上を対象にしたビルド環境

このプロジェクトの Android 設定は以下です。

- `minSdk = 26`
- `targetSdk = 36`
- `compileSdk = 36`

### API キー設定

天気取得には OpenWeatherMap の API キーが必要です。  
リポジトリ直下の `local.properties` に次を追加してください。

```properties
WEATHER_API_KEY=your_api_key_here
```

この値は `BuildConfig.WEATHER_API_KEY` として参照されます。

### ビルド

Windows:

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux:

```bash
./gradlew assembleDebug
```

`preBuild` 時に `syncCharacters` タスクが走り、組み込みキャラクター素材が `app/src/main/assets/characters` に同期されます。

## 使い方

1. アプリをインストール
2. ホーム画面にウィジェットを追加
3. 位置情報権限を許可すると天気表示が有効化
4. 必要に応じてメモを追加
5. キャラクター管理画面から追加キャラクターを選択 / 導入

キャラクター ZIP を端末で開いたときに、このアプリがインストール画面を受け取れる構成になっています。

## キャラクター作成・配布

追加キャラクターは `character.json` と `speeches/`, `images/` を含む ZIP として配布できます。

- 仕様メモ: `character_json_reference.md`
- インストール処理: `app/src/main/java/com/example/mascotforge/installer/`
- 安全検証: `app/src/main/java/com/example/mascotforge/installer/ZipSecurityValidator.kt`

README では概要だけ触れ、詳細仕様は `character_json_reference.md` に分離しています。

## プロジェクト構成

- `app/src/main/java/widget/`  
  ウィジェット更新、表示制御、スケジューリング
- `app/src/main/java/com/example/mascotforge/character/`  
  キャラクター読込、状態管理、画像キャッシュ
- `app/src/main/java/com/example/mascotforge/speech/`  
  セリフ文脈生成、タグ解析、条件評価
- `app/src/main/java/com/example/mascotforge/installer/`  
  キャラクター / シェル ZIP の検証と導入
- `app/src/main/java/widget/database/`  
  メモ保存用 Room
- `app/src/main/assets/characters/`  
  組み込みキャラクター資産

## セキュリティ方針

外部キャラクター ZIP の導入では、少なくとも以下を検証しています。

- パストラバーサル対策
- 危険な拡張子の拒否
- エントリ数 / 展開サイズ上限
- シンボリックリンク拒否
- `character.json` の参照整合性確認
- 画像マジックバイト検証

## ライセンス

MIT License。詳細は `LICENSE` を参照してください。
