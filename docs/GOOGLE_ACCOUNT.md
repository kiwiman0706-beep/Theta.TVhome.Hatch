# 家族の共用 Google アカウントの準備（自宅のテレビ）

自宅の AQUOS（TVhome）は、**家族で共用する Google アカウント**で使う。
シリーズ共通の Google の設定は Theta.Hatch.Series の `spec/THETA_GOOGLE_SETUP.md` が正本。ここはテレビと家族のアカウントに絞った手順。

## 全体像

| どこで | 何に使う | 要るもの | いつ |
| --- | --- | --- | --- |
| ① テレビ本体 | Google Play・YouTube など、テレビのアプリ | テレビに家族のアカウントでログイン | 最初に |
| ② TVhome の予定 | 2 週間の帯・カレンダー | 家族のカレンダーの「iCal 形式の非公開アドレス」（OAuth は要らない） | いま使える |
| ③ TVhome の Gmail・ToDo・連絡先・ドライブ | これから作る機能 | シリーズの Google Cloud のプロジェクトに、テストユーザーとこのアプリ用のクライアントを足す | その機能を入れるとき |

---

## ① テレビに家族のアカウントを入れる

1. テレビの 設定 → アカウントとログイン（機種によっては「アカウント」）→ アカウントを追加
2. テレビに出るコードを、スマホかパソコンで開いて家族のアカウントでログインする（その場でパスワードを打つより楽）
3. いま自分のアカウントが入っていれば：
   - 残す：Play ストアで入れたアプリはそのまま。アカウントを切り替えて使える
   - 消す：同じ画面で自分のアカウントを選んで「削除」。テレビで自分の Gmail などが見えなくなる
4. **子どもが買い物をしないように**：Play ストア → 設定 → 購入時の認証 →「このデバイスで Google Play から購入するときは常に」

## ② 予定を TVhome に出す（いま使える）

TVhome は、Google カレンダーの **iCal 形式の非公開アドレス** を読む（ログインは要らない）。

1. パソコンで、**家族のアカウントで**ログインして Google カレンダー（calendar.google.com）を開く
2. 右上の歯車 → 設定 → 左の「マイカレンダーの設定」で出したいカレンダーを選ぶ
3. 「カレンダーの統合」→「**iCal 形式の非公開アドレス**」をコピー（`https://calendar.google.com/calendar/ical/…/private-…/basic.ics`）
4. 出したいカレンダーが複数あれば、2〜3 をくり返して 1 行に 1 つずつテキストファイル（`calendar.txt`）に並べる
   - 祝日も出すなら、この行も足す（公開のカレンダーなので秘密ではない）：
     `https://calendar.google.com/calendar/ical/ja.japanese%23holiday%40group.v.calendar.google.com/public/basic.ics`
   - 家族の誰かの個人のカレンダーも出したいとき：その人が自分のカレンダーを家族のアカウントに共有するだけでは、家族のアカウント側に非公開アドレスは出ない。**その人自身が**自分のカレンダーの非公開アドレスをコピーして渡す
5. ADB で TVhome に入れる（`docs/ADB.md` §5。ファイルで渡し、読んだら TVhome が消す）

```bash
adb push calendar.txt /sdcard/Android/data/app.thetahatch.tvhome/files/calendar.txt
```

```bash
adb shell am broadcast -a app.thetahatch.adb.SET_SECRET -n app.thetahatch.tvhome/app.thetahatch.adb.CommandReceiver --es key calendar.ics --es file /sdcard/Android/data/app.thetahatch.tvhome/files/calendar.txt
```

6. PC に残った `calendar.txt` は消す

- 非公開アドレスは「知っている人なら誰でも予定を読める」URL。メール・チャット・Git に貼らない
- もれたかもしれないときは、同じ画面の「リセット」で新しいアドレスにして、5 をやり直す
- ローカルの Claude に頼むときも、URL はチャットに貼らず、`calendar.txt` を自分で作ってから「このファイルを SET_SECRET で入れて」と頼む

## ③ Gmail・ToDo・連絡先・ドライブ（これから作る機能の下準備）

TVhome でこれらを入れるときに要る。いまは急がなくてよい。どれも **Google Cloud のシリーズのプロジェクト**（`spec/THETA_GOOGLE_SETUP.md` の「共通」。Meishi で作ったもの）で行う。
画面は https://console.cloud.google.com/ 、上のプロジェクト選択がシリーズのプロジェクトになっていることを確かめる。

- **Google Cloud にログインするのは、プロジェクトを作った自分のアカウント**（家族のアカウントでログインする必要はない）
- 家族のアカウントは、1 で「テストユーザー」に足すだけ。テレビで許可するのが家族のアカウント（4）

1. **テストユーザーに家族のアカウントを足す**（プロジェクトに 1 回。シリーズの全アプリに効く）
   Google Auth Platform →「対象」→「テストユーザー」→ 家族のアカウントのメールアドレスを追加
2. **このアプリ用の Android の OAuth クライアント**（TVhome に 1 回）
   Google Auth Platform →「クライアント」→「クライアントを作成」
   - 種類：Android
   - 名前：`Theta TVhome Hatch Android`
   - パッケージ名：`app.thetahatch.tvhome`
   - SHA-1：`A1:13:EA:34:83:60:56:32:E7:F5:94:4E:4E:8C:20:55:DD:AE:28:BA`（シリーズ共通の鍵。Releases の APK はこの鍵で署名している）
3. **使う API を有効にする**（プロジェクトに 1 回。「API とサービス」→「ライブラリ」）

   | API | TVhome で使う | シリーズで |
   | --- | --- | --- |
   | Gmail API | メールを読む・送る | Home で有効のはず（要確認） |
   | Google Tasks API | やること | まだ（要確認） |
   | Google People API | れんらくさき | 要確認 |
   | Google Drive API | ドライブの一覧・中を見る | 有効 |
   | Google Calendar API | （予定を iCal ではなく API で読むようにしたら） | 要確認 |

4. テレビで初めてその機能を使うとき、許可の画面で家族のアカウントを選んで「許可」
   - 「このアプリは Google で確認されていません」→「続行」でよい（自分たちで作ったテスト中のアプリのため）
   - テスト中のプロジェクトは、許可が 7 日ほどで切れることがある。切れたら、その機能のボタンを押せば許可の画面がもう一度出る

## チェックリスト

- [ ] ① テレビに家族のアカウントでログインした
- [ ] ① Play ストアの購入時の認証を「常に」にした
- [ ] ② 家族のカレンダーの非公開アドレス（と祝日）を `calendar.txt` にまとめ、ADB で入れて、PC から消した
- [ ] ③-1 テストユーザーに家族のアカウントを足した
- [ ] ③-2 `app.thetahatch.tvhome` の Android の OAuth クライアントを作った
- [ ] ③-3 使う API を有効にした

## 関係する決まり

- Netflix は、大人のプロフィールにプロフィールロック（暗証番号）を付けると、子どもは自分のプロフィールしか入れない
- 秘密（非公開アドレス・パスワード）は、テレビの中で暗号化して持つ。このリポジトリ・クラウドには置かない（シリーズの決まり）
