# ADB で入れる・調べる・設定する（AQUOS 4T-C65DQ2）

ADB の作業をするスレッド（PC のローカルの Claude Code）向けの手順。調べた結果は `docs/device/4T-C65DQ2.md` に書いて push する。
窓口の決まりは Theta.Hatch.Series の `spec/THETA_ADB_SPEC.md`。以下の `W` は窓口の長い名前の略：

```
W=app.thetahatch.tvhome/app.thetahatch.adb.CommandReceiver
S=app.thetahatch.tvhome/app.thetahatch.adb.StatusService
```

## 1. つなぐ

1. テレビ：設定 → デバイス設定 → 端末情報 →「ビルド」を 7 回押す → 開発者向けオプション →「USB デバッグ」をオン
   - AQUOS 4T-C65DQ2 には「ワイヤレスデバッグ」の項目が無い（2026-11-15 に確認）。**Android TV では「USB デバッグ」をオンにすると、ネットワーク（ポート 5555）でもつながる**ことが多いので、まずそれを試す
   - テレビの USB の口は USB メモリ・HDD 用（ホスト）なので、PC と USB ケーブルではつながらない見込み
2. テレビの IP アドレスを調べる：設定 → ネットワークとインターネット → つないでいる Wi-Fi（または有線）→ IP アドレス。ルーターで IP を固定しておくと毎回同じになる
3. PC（テレビと同じ LAN）から：

```bash
adb connect <テレビの IP>:5555
```

   - テレビに「USB デバッグを許可しますか」が出たら「このコンピューターを常に許可」にチェックして OK
   - `adb devices` で `<IP>:5555 device` と出ればつながった（`unauthorized` ならテレビの確認を見落としている、`failed to connect` ならポートが開いていない）
   - つながらないときは、テレビの電源を入れ直す（USB デバッグの切り替えのあと、再起動が要る機種がある）

4. 機種の情報を記録：

```bash
adb shell getprop ro.build.version.release
```

```bash
adb shell getprop ro.build.fingerprint
```

```bash
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

## 2. 入れる

Releases の最新 `ThetaTVhomeHatch-<版>.apk`（シリーズ共通の鍵で署名したもの）を入れる。

```bash
adb install -r ThetaTVhomeHatch-0.1.0.apk
```

```bash
adb shell appops set app.thetahatch.tvhome REQUEST_INSTALL_PACKAGES allow
```

- 2 行目は自動更新に必要（テレビの画面では「提供元不明のアプリ」の許可が出せないことがある）
- ほかの Hatch アプリを入れるときは `adb install -r -i app.thetahatch.tvhome <APK>`（このホームが確認なしで更新できるように）
- Actions のデバッグ版（build.yml の成果物）は鍵が違うので、Release 版に上書きできない（入れ替えはアンインストールから）

## 3. 状態を見る

```bash
adb shell dumpsys activity service $S
```

1 行の JSON：版・既定のホーム・ハブ（受け持ち・インストールの許可）・設定（秘密は有無だけ）・入口・Hatch アプリ・足りない権限・エラー。

## 4. テレビの入口を調べる（いちばん大事）

```bash
adb shell dumpsys activity service $S probe
```

- `inputs`：TvInputManager の入力（HDMI・チューナー）。`viewResolves` が true なら HDMI の切り替えはそのまま動くはず
- `tvApp`：放送の画面を出すアプリ、`tvishPackages`：放送・録画の画面を持っていそうなアプリ、`candidates`：決まった Intent を受けるアプリ

地デジ・BS・録画は、**リモコンのボタンを押したときに開く画面**を見て Intent を決める：

```bash
adb logcat -c
```

```bash
adb logcat -b events | grep -E "am_create_activity|am_restart_activity|wm_create_activity"
```

↑を動かしたまま、リモコンの「地デジ」「BS」「録画リスト」などを押す。出た component・action・data を記録する。

```bash
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity|intent="
```

見つけたら設定して試す（Intent は `intent:` の URI 形式。例は仮）：

```bash
adb shell am broadcast -a app.thetahatch.adb.SET -n $W --es key entry.terrestrial --es value "'intent:#Intent;action=android.intent.action.VIEW;component=com.example.tv/.LiveActivity;S.mode=terrestrial;end'"
```

```bash
adb shell am broadcast -a app.thetahatch.adb.LAUNCH -n $W --es key terrestrial
```

- 入口のキー：`tv`・`terrestrial`・`bs`・`recordings`・`input:<入力の id>`
- 名前を変える：`--es key label.input:<id> --es value ゲーム`
- 消す（自動に戻す）：`--es value ""`
- `SET` の前に、まず `am start` で直接試してもよい：`adb shell am start -n <component>`

## 5. ホームの設定

```bash
adb shell am broadcast -a app.thetahatch.adb.SET -n $W --es key weather.place --es value "35.44,139.64,横浜"
```

カレンダーは、ふつうは TVhome のせってい →「Google カレンダー」で家族のアカウントを許可するだけ（docs/GOOGLE_ACCOUNT.md）。下の iCal は予備：**URL は秘密**なので、ファイルで渡して読んだら消す：

```bash
adb push calendar.txt /sdcard/Android/data/app.thetahatch.tvhome/files/calendar.txt
```

```bash
adb shell am broadcast -a app.thetahatch.adb.SET_SECRET -n $W --es key calendar.ics --es file /sdcard/Android/data/app.thetahatch.tvhome/files/calendar.txt
```

ほか：`ui.skin`（dark・light・pop など）、`kids.mode`（true・false）、`kids.pin`（数字）、`apps.favorites`（パッケージ名をカンマ区切り）、`apps.hidden`、`update.auto`。
画面を開く：`-a app.thetahatch.adb.OPEN --es screen settings`（home・settings・updates・apps）。
更新：`-a app.thetahatch.adb.CHECK_UPDATE`、`-a app.thetahatch.adb.UPDATE`。

## 5.5 再生中の表示（任意）

左上に「いま再生中」を出すには、メディアの情報を読む許可が要る（テレビの画面では出せないことが多い）：

```bash
adb shell cmd notification allow_listener app.thetahatch.tvhome/app.thetahatch.tvhome.media.MediaListener
```

## 6. 既定のホームにする（様子を見てから）

**その前に**：レスキューを試しておく（リモコンの「戻る」を すばやく 7 回 → レスキュー画面 →「いつもの（純正の）ホームを開く」）。ADB からは：

```bash
adb shell am broadcast -a app.thetahatch.adb.OPEN -n $W --es screen rescue
```


```bash
adb shell cmd package set-home-activity app.thetahatch.tvhome/.MainActivity
```

効かなければ：

```bash
adb shell cmd role add-role-holder android.app.role.HOME app.thetahatch.tvhome
```

- 純正のホーム（`com.google.android.tvlauncher` など。1. の resolve-activity で記録したもの）は**無効にしない**。戻すときに困るため
- リモコンのホームボタンでこのホームが出るか、再起動のあと・テレビの電源を入れ直したあとも出るかを確かめる
- 元に戻す（1. で記録した純正のホームに）：

```bash
adb shell cmd package set-home-activity <純正のホームの component>
```

- Android TV の更新で純正に戻ることがある（そのときは同じ手順でもう一度）
- **どうにもならないとき**：このアプリを消せば、純正のホームに戻る

```bash
adb uninstall app.thetahatch.tvhome
```


## 6.5 リモコンの専用ボタンを調べる（アプリ・U-NEXT など）

重ねるバー・タイマーを「アプリ」ボタンや使っていないボタン（U-NEXT）に割り当てるための下調べ。ボタンごとに、**どの層で何が起きるか**を記録する。

```bash
adb shell getevent -lt
```

↑を動かしたままボタンを押す → 出たキー名・スキャンコードを記録（Ctrl+C で止める）。

```bash
adb shell dumpsys input | grep -iE "KeyLayoutFile|KeyCharacterMapFile"
```

```bash
adb logcat -c
```

```bash
adb logcat | grep -iE "KeyEvent|keycode|GLOBAL_BUTTON|START u0|am_create_activity"
```

↑を動かしたままボタンを押す → 開いた画面（Intent の action・component）や、送られたブロードキャストを記録。

```bash
adb shell cmd package query-activities -a android.intent.action.ALL_APPS
```

記録のしかた（`docs/device/4T-C65DQ2.md`）：

| ボタン | getevent のキー | Android のキーコード | 押すと起きること（開く画面の Intent・ブロードキャスト） |
| --- | --- | --- | --- |
| アプリ | | | |
| U-NEXT | | | |
| Netflix など | | | |

- 開く画面が決まった Intent（例 `android.intent.action.ALL_APPS`）なら、TVhome がその Intent を受けるだけで割り当てられる（いちばん安定。ユーザー補助は要らない）
- キーコードとして届くだけなら、ユーザー補助サービスで受ける
- メーカーの仕組み（`GLOBAL_BUTTON` のブロードキャストなど）で直接アプリが開くなら、横取りは難しい

## 7. 記録すること（docs/device/4T-C65DQ2.md）

- Android の版・ビルド、純正のホームの component
- probe の JSON（そのまま）
- 地デジ・BS・録画・HDMI の Intent と、試した結果（開いた・開かない・別の画面）
- 既定のホームにできたか、ホームボタン・再起動・電源の入れ直しでの動き
- USB の口の数と、録画用 HDD 以外にさせるか（データ用 HDD の予定）
- メモリ：`adb shell cat /proc/meminfo | head -3`、CPU：`adb shell getprop ro.board.platform`
