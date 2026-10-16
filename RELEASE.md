# リリース / 署名の設定手順

このリポジトリは GitHub Actions で以下を自動化しています。

- **CI**（`.github/workflows/ci.yml`）: `main` への push / PR で、ユニットテスト＋`assembleDebug`＋APKをartifact保存
- **Release**（`.github/workflows/release.yml`）: `v*` タグを push すると、**署名済み release APK** を作って GitHub Release に添付

署名キーはリポジトリに入れず、**Secret か `key.properties`（ローカル）** から読みます。
どちらも無い場合は「未署名」として扱われ、ビルド自体は通ります（＝今の状態でも CI は緑）。

---

## 1. ローカルで署名付き release を作る場合

リポジトリ直下に `key.properties` を作ります（`.gitignore` 済み・コミットされません）。

```properties
storeFile=app/release.jks        # リポジトリ直下からの相対パス、または絶対パス
storePassword=（ストアのパスワード）
keyAlias=（キーのエイリアス）
keyPassword=（キーのパスワード）
```

その後:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleRelease
```

`app/build/outputs/apk/release/app-release.apk` が生成されます。

> 環境変数 `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` が設定されていれば、そちらが `key.properties` より優先されます（CIはこの方式）。

---

## 2. GitHub Actions にキーを登録する（CD用）

GitHub の **Settings → Secrets and variables → Actions → New repository secret** で、
次の4つを登録します。**ここに自分のキーを入れるだけ**で CD が有効になります。

| Secret 名 | 入れる値 |
|---|---|
| `KEYSTORE_BASE64` | keystore ファイル(`.jks`)を Base64 にした文字列（下記コマンドで作る） |
| `KEYSTORE_PASSWORD` | ストア(=keystore)のパスワード |
| `KEY_ALIAS` | キーのエイリアス |
| `KEY_PASSWORD` | キーのパスワード |

`KEYSTORE_BASE64` の作り方（PowerShell・作成済みの keystore を指定）:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\your-release.jks")) | Set-Clipboard
```

→ クリップボードにコピーされるので、そのまま `KEYSTORE_BASE64` に貼り付け。

---

## 3. リリースを出す

タグを push するだけです。

```powershell
git tag v2.3
git push origin v2.3
```

- Actions の **Release** ワークフローが走り、署名済み APK が作られます。
- GitHub の Releases に **ドラフト**として作られるので、内容を確認して「Publish release」を押せば公開完了です。
- 公開を自動にしたい場合は `.github/workflows/release.yml` の `draft: true` を `false` に変更してください。

---

## メモ

- `key.properties` / `*.jks` / `*.keystore` は `.gitignore` 済み。**絶対にコミットしない**こと。
- 一度公開したアプリの署名キーは変更できないため、keystore は安全な場所に保管すること（失うとアプリの更新ができなくなる）。
- `versionCode` / `versionName` は現状固定（`1` / `1.0`）。タグと合わせて上げたい場合は `app/build.gradle.kts` を毎回編集するか、将来的に自動採番に置き換える。
