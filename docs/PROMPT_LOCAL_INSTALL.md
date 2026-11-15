# ローカルの Claude Code への依頼文（AQUOS に TVhome を入れる）

PC（Windows か Mac）のローカルの Claude Code に、下の「依頼文」をそのまま貼ります。
作業フォルダは、このリポジトリ（kiwiman0706-beep/Theta.TVhome.Hatch）を clone したフォルダにしておきます。

---

## 依頼文

```
テレビ（SHARP AQUOS 4T-C65DQ2、Android TV）に、自作のホームアプリ Theta TVhome Hatch を ADB で入れて、試すところまでお願いします。
このフォルダは kiwiman0706-beep/Theta.TVhome.Hatch です。まず git pull して、docs/ADB.md と docs/device/4T-C65DQ2.md と CLAUDE.md を読んでください。手順はすべて docs/ADB.md に従います。

前提
- テレビは「開発者向けオプション」→「USB デバッグ」をオン済み。ワイヤレスデバッグの項目は無いので、ネットワーク（ポート 5555）でつなぐ（docs/ADB.md §1）
- テレビの IP アドレスは私が教えます（分からなければ、テレビの 設定 → ネットワークとインターネット で見る方法を案内して）
- adb が無ければ入れて（Windows：winget install Google.PlatformTools、Mac：brew install android-platform-tools）

進め方
1. adb connect <IP>:5555 でつなぐ。テレビに許可の画面が出たら私が押すので、待って確かめて
2. 機種の情報を記録（docs/ADB.md §1-4）。いまの既定のホーム（純正のホーム）の component を必ず控える
3. GitHub の Releases の最新の ThetaTVhomeHatch-<版>.apk を取得して、SHA256SUMS と照合。apksigner があれば署名の SHA-256 が f47740d8549bc1c37feb00b18780d80b52758b60252ba0d2217d83ab3f012a23 であることも確かめる
4. adb install -r で入れる。続けて REQUEST_INSTALL_PACKAGES の許可（§2）と、再生中の表示の許可（§5.5）
5. dumpsys の状態と probe（§3・§4）を取り、地デジ・BS・録画のリモコンのボタンを私が押すので、そのとき開いた画面を記録（§4）。見つかった Intent は SET entry.* で入れて LAUNCH で試す
6. リモコンの専用ボタン（アプリ・U-NEXT・Netflix など）の届き方を調べる（§6.5）
7. レスキューを試す（§6 の前）：私がリモコンの「戻る」を すばやく 7 回押す → レスキュー画面 →「いつもの（純正の）ホームを開く」で戻れるか。ADB の OPEN --es screen rescue でも開くか
8. ここまで問題が無ければ、既定のホームにするか私に聞いて。OK なら §6 の手順で切り替え、ホームボタン・再起動・電源の入れ直しのあとの動きを確かめる
9. 結果を docs/device/4T-C65DQ2.md の表に書き（probe の JSON も貼る）、TESTING.md の「未確認」で確かめた物に印を付けて、コミットして push（main）

守ってほしいこと
- 純正のホームやシステムのアプリを、無効化・削除しない（pm disable / pm uninstall はしない）
- テレビの設定を変えるときは、変える前の値を控えて、何を変えるか先に私に言ってから
- 既定のホームの切り替えは、7 のレスキューが動くのを確かめて、私が OK してから
- 困ったら：純正のホームに戻す（cmd package set-home-activity <控えた component>）、それでもだめなら adb uninstall app.thetahatch.tvhome
- 秘密（カレンダーの iCal の URL など）は、コマンドやファイルに残さない。SET_SECRET でファイル経由で入れ、入れたら消える（§5）。今回はやらなくてよい
- 終わったら、ワイヤレスで常に入れる状態のままでよいか私に聞く（使わないなら開発者向けオプションの USB デバッグをオフ）
```

---

## メモ

- テレビに入れる APK は、必ず Releases のもの（シリーズ共通の鍵で署名）。Actions の build.yml のデバッグ版は鍵が違うので、あとで自動更新できなくなる
- 結果は、このクラウドのスレッド（TVhome）でも読めるよう、リポジトリに push してもらう
