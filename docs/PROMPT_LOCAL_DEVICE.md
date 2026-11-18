# ローカルの Claude Code への依頼文（別のテレビを調べる・アプリを入れる）

職場などに置いている、別のシャープ製 Android TV（私物）用。自宅の AQUOS に TVhome を入れるときは `PROMPT_LOCAL_INSTALL.md`。
そのテレビと**同じ LAN の PC**で、このリポジトリ（kiwiman0706-beep/Theta.TVhome.Hatch）を clone したフォルダを開き、下の「依頼文」を貼る。

---

## 依頼文

```
職場に置いている私物のシャープ製 Android TV を ADB で調べて、アプリを入れたり、自宅の AQUOS と同じように TVhome を試したりするところまでお願いします（型番はテレビの背面か、つないでから読む）。
このフォルダは kiwiman0706-beep/Theta.TVhome.Hatch です。まず git pull して、CLAUDE.md・docs/ADB.md・docs/device/README.md・docs/device/_TEMPLATE.md・docs/device/4T-C65DQ2.md を読んでください。

前提
- 私物のテレビです。自宅の AQUOS と同じ扱いでよい（置いてある場所が職場なだけ）
- 開発者向けオプションと「USB デバッグ」をオンにする操作は私がテレビでやる。手順を案内して（設定 → デバイス設定 → 端末情報 →「ビルド」を 7 回）
- ワイヤレスデバッグが無ければ、ネットワーク（ポート 5555）でつなぐ（docs/ADB.md §1）。テレビの IP は私が教える
- adb が無ければ入れて（Windows：winget install Google.PlatformTools、Mac：brew install android-platform-tools）。Python 3.12 は入っている

進め方
1. adb connect <IP>:5555 でつなぐ。テレビに許可の画面が出たら私が押すので待つ。つながらなければ、職場のネットワークで端末どうしの通信が止められている可能性も含めて原因を一緒に切り分けて
2. 読むだけの調査：python tools/tv_survey.py --serial <IP>:5555 --label 職場
   （設定は変えない。結果は docs/device/<型番>/ にできる）
3. docs/device/_TEMPLATE.md を docs/device/<型番>.md として写し、survey の結果で表を埋める。「4T-C65DQ2（自宅）とのちがい」も書く。docs/device/README.md の一覧にも 1 行足す
4. リモコンの専用ボタンと、地デジ・BS・録画・入力切替の画面の開き方を調べる（docs/ADB.md §4・§6.5。ボタンは私が押す）
5. アプリを入れる（何を入れるかは私が伝える）。TVhome を試すなら、docs/PROMPT_LOCAL_INSTALL.md の 3〜8 と同じ流れ（照合して入れる → 許可 → レスキューを試す → 私が OK したら既定のホームに）
   - Theta Hatch のアプリは GitHub の Releases の APK（シリーズ共通の鍵で署名）を使い、SHA256SUMS と照合してから adb install
   - 入れた物・許可した物（appops・pm grant）・変えた設定を docs/device/<型番>.md に書く（戻すときに使う）
6. 結果をコミットして push（main）。最後に、何を調べ・何を入れ・何を変えたかを短く報告して

守ってほしいこと
- 文鎮にしないため、純正のホームを無効化・削除しない（pm disable / pm uninstall はしない）。設定を変えるときは、変える前の値を控える
- このリポジトリは公開なので、調べた結果に秘密（Wi-Fi のパスワード、アカウント名、トークンなど）が入っていたら、コミットする前に消すか伏せる。settings list の出力は特によく見る
- 終わったら、USB デバッグをそのままにするか私に聞く
- 困ったら：入れたアプリは adb uninstall で消せば元どおり。既定のホームを変えていたら cmd package set-home-activity <控えた純正のホーム>
```

---

## メモ

- `tools/tv_survey.py` は読むだけのコマンドしか使わない（設定もアプリも変えない）。自宅の AQUOS でも同じものを使える
- このテレビで分かったことは、このクラウドのスレッド（TVhome）でも読めるよう、リポジトリに push してもらう
