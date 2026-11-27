# ローカルの Claude Code への依頼文（自宅の AQUOS：放送のチャンネルと USB HDD の録画を TVhome から）

自宅の AQUOS 4T-C65DQ2 の純正のホームには、USB HDD の録画の一覧が出て、そこから直接再生できる。
同じことを TVhome でやるための**下調べ**。PC でこのリポジトリのフォルダを開いたローカルの Claude Code に、「依頼文」を貼る。

## 先に知っておくこと

- 録画は**著作権保護（暗号化）されていて、そのテレビのアプリでしか再生できない**。TVhome が自分で再生するのではなく、**一覧を出して、純正の再生画面を正しい Intent で開く**のがゴール
- Android TV の純正のホームの行は、ふつう各アプリが TV のプロバイダ（`TvContract` の preview program）に出したタイル。ふつうのアプリはほかのアプリのタイルを読めないので、**録画のアプリ自身の入口（Intent・ContentProvider）**を探す
- 純正のアプリ（APK）を PC に取り出して中を読むのは、自分のテレビの中のものを、つなぐための入口を調べる目的で使う範囲にとどめる。**APK と、そこから出したソースはリポジトリに入れない**（このリポジトリは公開）。記録するのは、入口の名前（action・component・extras のキー・provider の URI と列）だけ

---

## 依頼文

```
自宅の AQUOS 4T-C65DQ2 で、次の 2 つを自作のホーム TVhome からできるようにしたいです。そのための下調べをお願いします。
(A) 放送：よく見るチャンネルを名前で登録して 1 つ選ぶとそのチャンネルに切り替わる、チャンネルの一覧（地上・BS・CS）、番組表、裏番組、地上・BS・CS の切り替え
(B) 録画：純正のホームに出る「USB HDD の録画の一覧」を TVhome でも出して、そこから純正の再生画面を開く
このフォルダは kiwiman0706-beep/Theta.TVhome.Hatch です。まず git pull して、CLAUDE.md・docs/ADB.md・docs/PROMPT_LOCAL_RECORDINGS.md・docs/device/R4-SH.md（職場のテレビで調べた例）を読んでください。

前提
- テレビは「USB デバッグ」をオンにしてネットワーク（5555）でつなぐ（docs/ADB.md §1）。IP は私が教える。USB HDD はつないだまま、録画が何本か入っている
- 録画は暗号化されていてテレビのアプリでしか再生できない。ゴールは「一覧を読む方法」と「その 1 本を純正の再生画面で開く Intent」を見つけること

進め方（A：放送）
A1. 私がリモコンで、地上・BS・CS の切り替え、チャンネルの数字ボタン、番組表、裏番組（あれば）、チャンネル一覧を順に押す。そのたびに開いた画面・Intent・キーを記録（adb logcat -b events | grep -E "am_create_activity|am_restart_activity"、ActivityTaskManager の START u0 の行、getevent -lt のキー）
A2. チャンネルの一覧の読み方を探す：adb shell content query --uri content://android.media.tv/channel（TV のプロバイダ。読めるか、どの入力の物か、列 display_number・display_name・service_type）。読めなければ、放送のアプリの provider・Intent を dumpsys package で
A3. 1 つのチャンネルに切り替える Intent を探して試す：ACTION_VIEW content://android.media.tv/channel/<id>、放送のアプリの extras（チャンネル番号・サービス ID・ネットワーク）。am start で試し、地上・BS・CS それぞれ 1 局ずつ
A4. 番組表・裏番組・チャンネル一覧・地上／BS／CS の画面を直接開く Intent（あれば）
A5. 見つからないものは、APK を取り出して入口を調べる（下の 5 と同じやり方）

進め方（B：録画）
1. adb connect <IP>:5555。まだなら、読むだけの調査も：python tools/tv_survey.py --serial <IP>:5555 --label 自宅（docs/device/4T-C65DQ2/ に出る）
2. 純正のホームで、録画の一覧の行とタイルを私が出す。そのとき、どのアプリがタイルを出しているかを調べる：
   - adb shell dumpsys activity activities（前面の画面）、adb shell dumpsys package（provider・receiver・Intent の受け口を持つシャープのアプリ）
   - 録画らしいアプリの候補（pm list packages で sharp・aquos・rec・tv・pvr・dtv などを含むもの）
3. 私が録画のタイルを 1 本選んで再生する。そのとき開いた Intent を記録：adb logcat -b events | grep -E "am_create_activity|am_restart_activity"、と adb logcat（ActivityTaskManager の START u0 の行。extras は {…} の中）
4. 一覧の読み方を探す：
   - adb shell content query --uri content://android.media.tv/recorded_program（TV のプロバイダの録画。読めるか、どのアプリの物か）
   - 候補のアプリの ContentProvider の authority を dumpsys package で見て、content query で読めるか（読めなければ権限の名前を記録）
5. 4 で分からなければ、候補のアプリ（純正のホームと、録画・再生のアプリ）の APK を PC に取り出して、入口を調べる：
   - adb shell pm path <パッケージ> → adb pull <パス> <PC の作業フォルダ>（**リポジトリの外**のフォルダ）
   - jadx（無ければ入れて。Windows：winget install skylot.jadx、Mac：brew install jadx）か apktool で開き、AndroidManifest.xml の exported な activity・provider・receiver、録画の一覧や再生を開く Intent の action・extras のキー、provider の URI と列を探す
   - 見つけた Intent を adb shell am start ... で試す（録画 1 本を開けるか）
6. 一覧が読めたら、**番組ごとにまとめるのに使える情報**があるかを調べる（録画 5〜10 本ぶんの実例を、題名を含めて記録）：番組名・サブタイトル（話数・回）・放送局・放送日時・長さ・ジャンル・シリーズの ID（連続録画の予約の ID など）・見た／まだ・見たところ
7. 結果を docs/device/4T-C65DQ2.md の「放送」「録画」の節にまとめる（A の結果も）：一覧の読み方（provider の URI・列・要る権限）、6 の項目と実例、1 本を開く Intent（action・component・extras）、試した結果、TVhome でできそうか・できない理由
8. コミットして push（main）。最後に短く報告して

守ってほしいこと
- 取り出した APK・jadx の出力・ソースは、リポジトリに入れない（公開のため。PC の別のフォルダに置き、終わったら消してよいか私に聞く）。docs に書くのは入口の名前と試した結果だけ
- 純正のホームやシステムのアプリを無効化・削除しない（pm disable / pm uninstall はしない）。設定を変えるときは前の値を控える
- 録画の中身を PC にコピーしない（暗号化されていて意味がなく、規約にもかかわる）
- 調べた出力に秘密（Wi-Fi のパスワード・アカウント名・トークン）があれば、コミット前に伏せる
```

---

## 見つかったら（このクラウドのスレッドで）

- チャンネルに切り替える Intent がある → TVhome の「テレビ」を作り直す：よく見るチャンネル（名前で登録、並べ替え）・チャンネル一覧（地上・BS・CS）・番組表・裏番組・地上・BS・CS
- チャンネルの一覧が読めない → 登録は、番号・名前・Intent を ADB かスマホで入れる形に

- 一覧が読める（provider か Intent）→ TVhome のクロスバーに「ろくが」の一覧を出し、決定で純正の再生画面を開く
  - **番組ごとの仮想フォルダ**：シリーズの ID があればそれで、無ければ題名から話数・回・記号（`#12`・`第5話`・`（字）`・`[新]`・`[再]`・サブタイトルの「」など）を外した名前でまとめる。1 本だけの番組はフォルダにしない
  - 並べ替え：題名順・新しい順・古い順・放送局順（メニューボタンで切り替え）。フォルダの中は放送日順
  - まだ見ていない物に印（分かれば）
- 一覧は読めないが、録画リストの画面を開く Intent はある → 今の「ろくが」の入口をそれにする（`entry.recordings`）
