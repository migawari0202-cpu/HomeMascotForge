━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  キャラクター制作 記法リファレンス
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

このフォルダの中身:

  character_json構造.txt   ZIPの構成・character.json のトップレベル構造
  speechRules記法.txt      セリフファイルの切り替えルール（conditions/anyOf/files）
  セリフタグ記法.txt        .txt セリフファイル内で使えるタグ一覧
  customVariables記法.txt  キャラ固有変数の定義と自動更新ルール
  emotions記法.txt         感情判定ルールの書き方
  標準変数一覧.txt          {変数名} や条件式で使える全変数リスト

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
■ 読む順番（おすすめ）

  初めて作る場合:
    1. character_json構造.txt  ← まずここ
    2. セリフタグ記法.txt
    3. speechRules記法.txt
    4. customVariables記法.txt
    5. emotions記法.txt
    6. 標準変数一覧.txt         ← 必要に応じて参照

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
■ 機能早見表

  やりたいこと                          参照ファイル
  ─────────────────────────────────────────────────
  セリフを天気・時間帯で切り替えたい      speechRules記法.txt
  「AまたはB」の条件でファイル分岐したい  speechRules記法.txt（anyOf）
  同じ条件で複数セリフファイルをランダム  speechRules記法.txt（files）
  セリフ中に変数の値を表示したい          セリフタグ記法.txt（{変数名}）
  条件で表示文言を切り替えたい            セリフタグ記法.txt（三項演算子）
  セリフを表示したとき変数を増やしたい    セリフタグ記法.txt（[v:]）
  文字列・真偽値変数をセリフから変更したい セリフタグ記法.txt（[v:xxx=値]）
  そのセリフだけ画像を変えたい            セリフタグ記法.txt（[emotion:] / [e:]）
  タップしたとき変数を変えたい            customVariables記法.txt（onTouch）
  好感度が高いと画像を変えたい            emotions記法.txt
  毎日起動したらボーナスを与えたい        customVariables記法.txt（onLaunch）
  条件式で !(A && B) の否定を使いたい     emotions記法.txt / 標準変数一覧.txt
  カスタム変数を変数名だけで直接指定したい  speechRules記法.txt（カスタム変数の参照）
  boolean変数を "true"/"false" で比較したい speechRules記法.txt（boolean型注意）
  使える変数名を全部見たい                標準変数一覧.txt

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
■ ビルド・テストに関する補足

  build.gradle.kts 設定:
  - testOptions { unitTests.isReturnDefaultValues = true } を設定済み
  - testImplementation("org.json:json:20240303") 依存あり
  - ユニットテスト3ファイル（SafeExpressionEvaluatorTest,
    SpeechContextTest, TagParserTest）で全15テストをカバー
  - テスト実行: .\gradlew.bat test（Android Studio同梱JDK(jbr)で動作）
