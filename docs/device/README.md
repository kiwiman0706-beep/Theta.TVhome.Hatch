# テレビの一覧（調べた結果の置き場所）

テレビごとに `docs/device/<型番>.md`（人が読む表、`_TEMPLATE.md` から作る）と、`docs/device/<型番>/`（`tools/tv_survey.py` の出力）を置く。
型番はテレビの `ro.product.model`（tv_survey.py が読む）。

| 型番 | 置き場所 | 使い方 | 調べた日 | 表 |
| --- | --- | --- | --- | --- |
| 4T-C65DQ2 | 自宅（リビング） | ホームを置き換える（TVhome） | 未 | [4T-C65DQ2.md](4T-C65DQ2.md) |
| R4-SH（シャープ 2T-C24EF1） | 職場（私物） | 調べる・アプリを入れる・TVhome を試す | 2026-11-20 | [R4-SH.md](R4-SH.md) |

- 新しいテレビ：`docs/PROMPT_LOCAL_DEVICE.md` の依頼文を、そのテレビと同じ LAN の PC のローカルの Claude Code に渡す
- 自宅の AQUOS に TVhome を入れる：`docs/PROMPT_LOCAL_INSTALL.md`
- ADB の手順そのもの：`docs/ADB.md`（どのテレビでも同じ。AQUOS 4T-C65DQ2 の固有の話は印を付けてある）
