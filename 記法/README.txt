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
  セリフを表示したとき変数を増やしたい    セリフタグ記法.txt（[v:]）
  文字列・真偽値変数をセリフから変更したい セリフタグ記法.txt（[v:xxx=値]）
  タップしたとき変数を変えたい            customVariables記法.txt（onTouch）
  好感度が高いと画像を変えたい            emotions記法.txt
  毎日起動したらボーナスを与えたい        customVariables記法.txt（onLaunch）
