# R4-SH（シャープ 2T-C24EF1）の調べた結果

- 置き場所：職場（私物）
- 使い方：調べる・アプリを入れる・TVhome を試す
- 調べた日：2026-11-20（tools/tv_survey.py、ローカルの Claude）
- 背面のラベルの型番は **2T-C24EF1**（24V 型）。テレビの `ro.product.model` は `R4-SH`（中身は SCBC 製、Realtek の基板）なので、ファイル名は R4-SH
- survey の出力：`docs/device/R4-SH/`（Wi-Fi の名前・IPv6・MAC・シリアル・android_id は伏せた）

| 項目 | 結果 |
| --- | --- |
| メーカー・型番 | SHARP 2T-C24EF1（`ro.product.manufacturer`=SCBC、model=R4-SH、device=R4、name=tchydhj） |
| Android の版・ビルド（sdk） | Android 11（sdk 30）、`SHARP/tchydhj/R4:11/RTT7.240511.001/5046:user/release-keys`、セキュリティパッチ 2025-12-05 |
| 画面（wm size・density） | 1280x720、213 |
| メモリ（MemTotal）・CPU（ro.board.platform） | 2GB、rtd2851m（ARMv7 の 4 コア、**32 ビット** armeabi-v7a）。内部ストレージ 7.6GB（空き 4.6GB） |
| ADB のつなぎ方 | ネットワーク 5555（「USB デバッグ」をオンにするだけでつながった。ワイヤレスデバッグは使っていない、`adb_wifi_enabled=0`） |
| 純正のホーム（component） | `com.google.android.tvlauncher/.MainActivity` |
| ほかのホーム候補 | `com.apps.tvsettings/.ui.FallbackHome`、`com.android.tv.settings/.system.FallbackHome`（どちらも予備） |
| HDMI の切り替え（passthrough の VIEW） | VIEW `content://android.media.tv/passthrough/…` を受けるアプリは**無い**。入力は HDMI ×2（`com.realtek.tv.passthrough/.hdmiinput.HDMITvInputService/HW151519232`・`…/HW151519488`）、AV（`…/.avinput.AVTvInputService/HW50593792`）、アナログ（`com.realtek.tv.atv/.atvinput.AtvInputService/HW33619968`）。入力切替の画面は `com.realtek.tv/.SelectInputActivity` |
| 地デジ・BS（開く画面の Intent） | 地上ボタン → `com.realtek.keyinterceptserver` が `jp.pixela.xit.smarttuner/jp.pixela.player_service.TvTunerService` を経て `jp.pixela.atv_app/.MainActivity`（StationTV）を開く（extras 付き）。BS はそのアプリの中でキー `KEYCODE_TV_SATELLITE_BS` として届く（新しい画面は開かない）。VIEW `content://android.media.tv/channel` を受けるアプリは無い |
| 録画リスト | `KEYCODE_DVR`（173）として StationTV の中で処理。`VIEW_RECORDING_SCHEDULES` を受けるアプリは無い。番組表・録画リスト・予約一覧は純正ホームの「TV Menu」のタイルからも開く（下の「番組表」） |
| リモコンの専用ボタン（getevent・キーコード・起きること） | 下の表。ほぼ全部 `com.realtek.keyinterceptserver` が先に受けて、直接アプリを開く |
| 「提供元不明のアプリ」の設定画面 | ある（`com.android.tv.settings/.device.apps.specialaccess.ExternalSourcesActivity`） |
| 「ほかのアプリの上に表示」の設定画面 | ある（`…specialaccess.SystemAlertActivity`） |
| ユーザー補助の設定画面 | **ない**（`ACCESSIBILITY_SETTINGS` はスタブ `com.google.android.tv.frameworkpackagestubs/.Stubs$SettingsStub`。有効にするなら ADB の settings で） |
| 既定のホームの設定画面 | ある（`com.google.android.permissioncontroller/…role.ui.HomeSettingsActivity`） |
| USB の口・ストレージ | 調べた時点で USB ストレージはつながっていない（`/storage` は emulated だけ）。HDMI-CEC の口は 3 つ（port 0 は ARC） |
| リモコン | IR（`IR_aeha`）と Bluetooth（`SHARP TV BTRC 003`、音声ボタン用） |
| TVhome を入れたか・版 | 入れた：0.6.0（versionCode 7、2026-11-20）。SHA256SUMS と署名 SHA-256 `f47740d8…3f012a23` を照合。720p でも表示は崩れない |
| 既定のホームにしたか・戻し方を確かめたか | **できない**（下の「既定のホーム」）。レスキューは ADB の OPEN rescue で開くのを確認 |

## リモコンのボタン（2026-11-20、IR）

`getevent` のキー名と、`KCR-KeyConverter` の scanCode → メーカーのキーコード、`RtkKeyInterceptService` の名前、開いた画面。

| ボタン | getevent | scan → key | Rtk の名前 | 押すと起きること |
| --- | --- | --- | --- | --- |
| ホーム | KEY_HOME | 102 → 3 | KEYCODE_HOME | `MAIN/HOME` → 既定のホーム |
| 地上 | （取れず） | 675 → 2080 | KEYCODE_TERRESTRIAL_DIGITAL | `jp.pixela.atv_app/.MainActivity`（StationTV） |
| BS | KEY_KPENTER | 96 → 2079 | KEYCODE_SATELLITE_BS | 開いている StationTV に `KEYCODE_TV_SATELLITE_BS` |
| 録画リスト | KEY_KPSLASH | 98 → 173 | （KEYCODE_DVR） | StationTV の中で処理 |
| 入力切替 | 0225 | 549 → 178 | （KEYCODE_TV_INPUT） | `com.realtek.tv/.SelectInputActivity` |
| アプリ | BTN_TRIGGER_HAPPY26 | 729 → 2013 | KEYCODE_APP_LIST | `com.google.android.tvlauncher/.appsview.AppsViewActivity`（`ALL_APPS` の画面と同じ） |
| NETFLIX | 02f6 | 758 → 2048 | KEYCODE_TCL_NETFLIX | `com.netflix.action.NETFLIX_KEY_START` → `com.netflix.ninja/.MainActivity` |
| prime video | 02fb | 763 → 2055 | KEYCODE_AMAZON_VIDEO | `com.amazon.amazonvideo.livingroom.AMAZON_BUTTON` → `…/com.amazon.ignition.IgnitionActivity` |
| U-NEXT | KEY_MACRO15 | 670 → 2074 | KEYCODE_UNEXT | `jp.unext.tv.player` の LAUNCHER |
| hulu | KEY_MACRO16 | 671 → 2075 | KEYCODE_HULU | `jp.happyon.android/.HomeActivity` |
| ABEMA | KEY_MACRO17 | 672 → 2076 | KEYCODE_ABEMATV | `tv.abema/.components.activity.LauncherActivity` |
| Paravi | KEY_MACRO_PRESET2 | 692 → 2097 | KEYCODE_PARAVI | `jp.paravi.androidtv/…SplashActivity` |
| YouTube | BTN_TRIGGER_HAPPY20 | 723 → 2030 | KEYCODE_YOUTUBE | `com.google.android.youtube.tv/…ShellActivity` |
| Disney+ | KEY_MACRO28 | 683 → 2088 | KEYCODE_DISNEY | `com.disney.disneyplus/…MainActivity` |

- 割り当ての見込み（docs/ADB.md §6.5 の 3 つの場合）：
  - アプリ：開く画面が `ALL_APPS` と同じ AppsViewActivity。ただし Rtk が component を直に開いているので、TVhome が `ALL_APPS` を受けても横取りできるかは**未確認**
  - 動画のボタン：Rtk が直接アプリを開く（メーカーの仕組み）。横取りは難しい。前面のアプリには `KEYCODE_UNKNOWN`＋scanCode で届くことがある（StationTV のログで確認）
- 押した順番は利用者の申告と時刻で照合（地上の getevent の行だけ取れなかった）

## TVhome（0.6.0）を試した結果

- 窓口：`dumpsys activity service …StatusService` は「No services match」（サービスが動いていないと答えない）。同じ中身はブロードキャストで取れる：`am broadcast -a app.thetahatch.adb.PROBE -n $W`
- probe：`tvApp`=`com.realtek.tv/.MainActivity`。入力はすべて `viewResolves: true`（HDMI1・HDMI2・AV・ATV・ABEMA・Google Play ムービー）。`candidates` の channel・recorded_program は `com.realtek.tv/.MainActivity`、録画予約は無し
- 「テレビ」（自動）：アナログ入力（ATV）の channel を開き、`com.realtek.tv` が落ちる（`Utils.startPixela` の StringIndexOutOfBoundsException）
- `am start -n jp.pixela.atv_app/.MainActivity` を直に：「url is null」「player_service not found」で閉じる
- **効いたもの**：`jp.pixela.xit.smarttuner/jp.pixela.player_service.TvTunerService` を開く → StationTV が前の局（BS 101）で開く。地上ボタンと同じ道。これを `entry.tv` に入れた
- 地デジと BS を分けて開く Intent は見つからない（地上ボタンは extras 付き。中身はログに出ない）。開いたあとリモコンの 地上・BS で切り替える
- HDMI1：`LAUNCH input:com.realtek.tv.passthrough/.hdmiinput.HDMITvInputService/HW151519232` → `com.realtek.tv/.MainActivity` が開いた（落ちない）
- 放送の映像は screencap に写らない（黒）。字幕・局名の表示だけ写る

### 番組表・録画リスト・予約一覧

- 純正ホームの「TV Menu」の行（`com.realtek.tvchannel` が出すタイル）から開ける。どれも `act=VIEW cmp=jp.pixela.xit.smarttuner/jp.pixela.player_service.TvTunerService`（extras 付き）→ StationTV が次の画面を出す：
  - 番組表 → `file:///android_asset/programList/index.html?uiv=1#/?action=select_ch`
  - ↓ の箱 → `…/recordList/…`（録画リスト）
  - 時計 → `…/reservationList/…`（予約一覧）
- extras の中身はログに出ない。`--es url <上の URL>` を渡しても、ふつうの視聴（`preview`）が開くだけ
- `input keyevent KEYCODE_GUIDE`（ホームから）では何も起きない
- リモコンの「番組表」「予約一覧」ボタンのログは取れなかった（logcat が途中で切れた）。**未確認**
- いまの使い方：TVhome の「テレビ」で StationTV を開いて、リモコンの「番組表」「録画リスト」ボタンを押す
- TVhome から直接開くには、タイルの extras（TV のプロバイダの `preview_program` の `intent_uri`）が要る。shell からは読めなかった

### 既定のホーム

- `cmd package set-home-activity app.thetahatch.tvhome/.MainActivity` は Success、`dumpsys role` の HOME も TVhome になった。**それでもホームボタンでは純正が開く**
- 理由：純正の `com.google.android.tvlauncher/.MainActivity` の HOME の受け口が priority=2（TVhome は 0）。システムアプリの高い優先度が、既定の選択より勝つ
- 変えるには純正を無効化するしかない（`pm disable-user`）が、文鎮を避けるため**しない**。純正に戻した（`set-home-activity com.google.android.tvlauncher/.MainActivity`、role も tvlauncher に戻ったのを確認）
- 使い方：アプリの一覧（リモコンの「アプリ」）から TVhome を開く。専用ボタンへの割り当ては Rtk が先に受けるので難しい

## 4T-C65DQ2（自宅）とのちがい

自宅の 4T-C65DQ2 はまだ調べていないので、わかっている範囲だけ。

- こちらは 24 型の 720p（1280x720）・メモリ 2GB・**32 ビットの CPU**（Realtek rtd2851m）。TVhome の APK は armeabi-v7a を含むので入る見込み。重い表示は控えめに
- 中身が SCBC 製のビルド（`ro.product.manufacturer`=SCBC）。放送は Pixela の StationTV（`jp.pixela.atv_app`）、キーは `com.realtek.keyinterceptserver` が受ける。自宅の AQUOS（シャープ純正のビルド）とは、放送・録画の画面とキーの仕組みが違うはず
- 純正のホームは Google TV ではなく **Android TV の tvlauncher**（`com.google.android.tvlauncher`）
- ユーザー補助の設定画面が無い
- 純正のホームの優先度が高く、TVhome を既定のホームにできない（自宅でできるかは未確認）
- 「ワイヤレスデバッグ」は使わず、USB デバッグだけでネットワークの 5555 でつながった（自宅と同じ想定の流れ）

## 入れた物・許可した物・変えた設定（戻すときに使う）

| いつ | 何をしたか | 戻し方 |
| --- | --- | --- |
| 2026-11-20 | 開発者向けオプション →「USB デバッグ」をオン（利用者がテレビで） | 開発者向けオプションでオフ |
| 2026-11-20 | `adb install -r ThetaTVhomeHatch-0.6.0.apk`（前は入っていない） | `adb uninstall app.thetahatch.tvhome` |
| 2026-11-20 | `appops set app.thetahatch.tvhome REQUEST_INSTALL_PACKAGES allow`（前は未インストールで値なし） | アンインストールで消える（`appops set … default` でも） |
| 2026-11-20 | `cmd notification allow_listener app.thetahatch.tvhome/app.thetahatch.tvhome.media.MediaListener` | `cmd notification disallow_listener …MediaListener`（アンインストールでも消える） |
| 2026-11-20 | TVhome の設定 `entry.tv` = `intent:#Intent;component=jp.pixela.xit.smarttuner/jp.pixela.player_service.TvTunerService;end`（前は空＝自動） | `SET --es key entry.tv --es value ""` |
| 2026-11-20 | `set-home-activity` で TVhome にした → 効かないので**すぐ純正に戻した**（HOME の role＝tvlauncher） | 変更なし（元どおり） |

## メモ

- PC から：`adb connect 192.168.24.28:5555`（職場の LAN の IP。ルーターで固定していなければ変わる）
- 職場の LAN でも、端末どうしの通信（PC → テレビの 5555）は止められていなかった
