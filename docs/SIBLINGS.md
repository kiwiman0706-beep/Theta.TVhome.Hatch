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
| えいが | Movie.Hatch（File Station か WebDAV、mpv で何でも再生、`.nfo`・ポスター） | mpv（libmpv 1.0.0）を内蔵。棚・フォルダ・続きから（見た秒）。`.nfo`・ポスター・話数の読み取りはあと | 0.8.0〜 |
| ほん | Book.Hatch（File Station、EPUB・PDF・CBZ・CBR・M4B） | PDF・CBZ・ZIP をページ送り（落としてから読む）。読んだページ。EPUB・CBR・M4B はあと | 0.8.0〜 |
| しゃしん | Photo.Hatch（写真の整理・XMP）、NAS の `photo` | 年・イベントのフォルダをたどってスライドショー。ホームの背景の写真はあと | 0.8.0〜 |

## 取り込みの表

| アプリ | 版 | 機能 | 取り込む？ | メモ |
| --- | --- | --- | --- | --- |
| Music | （0.7.0 の時点） | 棚（▶ のフォルダ）・`_` のフォルダを読まない | 済み | THETA_LIBRARY_SPEC §4 |
| Music | | タグ（ID3・Vorbis・iTunes）を Range で読む | あとで | いまはファイル名から曲名 |
| Music | | お気に入り（Audio Station の★と同期） | あとで | |
| Music | | 整理の記録（`_整理の記録/*.csv`）でお気に入りを付け替え | あとで | |
| Movie | | 名前の読み取り（`S02E10`・放送日）・ポスター | 済み（0.9.0） | `.nfo` はあと。`第5話`・`#12` などの整理前の名前もあと |
| Movie | | 続きから・最近追加・棚（▶ の目印）・探す | 済み（0.8〜0.9） | 出演者・監督で探すのは `.nfo` を読んでから |
| Book | | 読むフォルダの目印（⑧）・表紙 | ほんで | |
