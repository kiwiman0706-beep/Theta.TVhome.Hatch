# Movie・Music・Book の機能の取り込み

テレビでは、Theta Movie・Music・Book Hatch の機能を **TVhome に内蔵**する（2026-11-20 決定。TV 用の別アプリは作らない）。
コードは写さず、**NAS の並べ方の決まり**（Theta.Hatch.Series の `spec/THETA_LIBRARY_SPEC.md`）と**つなぎ方**（DSM の File Station）をそろえて、画面はテレビ用に作る。

- **版を上げる前に** `python tools/sibling_updates.py` を動かし、元のアプリの新しい Release を見る。取り込むかを下の表に書いたら `--mark`
- 再生は別のプロセス（`:player`）。再生で落ちてもホームは落ちない
- NAS は File Station（2 段階認証は初回だけ。デバイストークン）。家の外は Tailscale（入っていなければ案内）

## 方針

| | 元のアプリ | TVhome での作り | 版 |
| --- | --- | --- | --- |
| おんがく | Music.Hatch（WebDAV か Audio Station、Flutter・just_audio） | File Station でフォルダをたどる、ExoPlayer（media3）で裏で再生 | 0.7.0〜 |
| えいが | Movie.Hatch（File Station か WebDAV、mpv で何でも再生、`.nfo`・ポスター） | mpv を内蔵（DTS・ARIB 字幕・二か国語の .ts まで）。棚・続きから | これから |
| ほん | Book.Hatch（File Station、EPUB・PDF・CBZ・CBR・M4B） | PDF・漫画（画像の束）をページ送り。EPUB はあと | これから |

## 取り込みの表

| アプリ | 版 | 機能 | 取り込む？ | メモ |
| --- | --- | --- | --- | --- |
| Music | （0.7.0 の時点） | 棚（▶ のフォルダ）・`_` のフォルダを読まない | 済み | THETA_LIBRARY_SPEC §4 |
| Music | | タグ（ID3・Vorbis・iTunes）を Range で読む | あとで | いまはファイル名から曲名 |
| Music | | お気に入り（Audio Station の★と同期） | あとで | |
| Music | | 整理の記録（`_整理の記録/*.csv`）でお気に入りを付け替え | あとで | |
| Movie | | 名前の読み取り（`第5話`・`S02E10` など）・`.nfo`・ポスター | えいがで | `docs/naming-convention.md` |
| Movie | | 続きから・最近追加・棚（⑧ の目印） | えいがで | |
| Book | | 読むフォルダの目印（⑧）・表紙 | ほんで | |
