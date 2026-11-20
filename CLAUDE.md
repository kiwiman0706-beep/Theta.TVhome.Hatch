# Theta.TVhome.Hatch

Android TV 用の、広告のない軽いホーム（AQUOS 4T-C65DQ2 で使う）。アプリ ID `app.thetahatch.tvhome`、catalog の id `tvhome`。
文章は日本語、短く。

## Theta Hatch シリーズ共通（必ず参照）

このアプリは Theta Hatch シリーズの 1 つです。シリーズ共通の仕様・デザイン・鍵の決まりは
GitHub の kiwiman0706-beep/Theta.Hatch.Series にあり、このリポジトリの docs より優先します。

- 作業を始める前に、Theta.Hatch.Series を読める状態にする
  （クラウドのスレッドでは add_repo で kiwiman0706-beep/Theta.Hatch.Series を追加して clone する）
- 読むもの：
  - `spec/THETA_HATCH_SPEC.md`（共通仕様）、`spec/THETA_TV_SPEC.md`（TV）、`spec/THETA_ADB_SPEC.md`（ADB の窓口）、`spec/THETA_SHARED_SETTINGS.md`
  - `design/README.md`（スキン。色は `design/skins/skins.json`。`app/.../skin/ThetaSkins.kt` は生成ファイルの写しで、手で直さない）
  - `spec/THETA_HATCH_SPEC.md` §6.1（アプリ内自動更新は必須。TV ではこのアプリが更新のハブ）
  - `secrets/README.md`（署名鍵はシリーズ共通。Secret の名前は ANDROID_KEYSTORE_B64・ANDROID_KEYSTORE_PASSWORD・ANDROID_KEY_ALIAS・ANDROID_KEY_PASSWORD）
- 共通仕様の写しをこのリポジトリに増やさない（変えるときは Theta.Hatch.Series 側を直す）
- 鍵・パスワード・API キーをリポジトリに入れない
- 方針と進み具合は、Theta.Hatch.Series の `progress/tvhome.md` に書く（自分のアプリのファイルだけを触る）
- 作品の版や概要が変わったら、Theta.Hatch.Series の README の一覧も直す

## このリポジトリの決まり

- 軽さ優先：起動を速く、裏で重い処理をしない。依存を増やすときは APK の大きさを確かめる（0.7.0 で約 5MB。書体・Google の許可・再生の部品。えいがで mpv を足すと +25MB ほど）
- リモコンだけで全部できること（十字キー・決定・戻る・メニュー）。子どもが迷わない言葉（ひらがな多め）
- 版を上げるときは `app/build.gradle.kts` の versionCode・versionName と `docs/release-notes/v<版>.md`。main に push すると Release が出る
- 実機（AQUOS）で確かめていないことは `TESTING.md` に「未確認」と書く。実機の調べた結果は `docs/device/4T-C65DQ2.md`
- **Movie・Music・Book の機能は TVhome に内蔵**（docs/SIBLINGS.md）。版を上げる前に `python tools/sibling_updates.py` で元のアプリの新しい版を見て、取り込むかを docs/SIBLINGS.md に書く
- 画面の見本：`./gradlew :app:testDebugUnitTest` で `docs/screenshots/` を描き直す
