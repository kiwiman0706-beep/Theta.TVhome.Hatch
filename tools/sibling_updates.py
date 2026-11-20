#!/usr/bin/env python3
"""TVhome に内蔵した機能の「元のアプリ」（Movie・Music・Book）の新しい版を洗い出す。

TVhome の版を上げる前に動かし、取り込むべき新しい機能が無いかを見る（CLAUDE.md の決まり）。
    python tools/sibling_updates.py            # 前に見た版より新しい Release の一覧と本文
    python tools/sibling_updates.py --mark     # 一覧を見終えたら、いまの最新を「見た」として記録

記録：docs/siblings.json（アプリごとに、最後に見た版）。取り込むかどうかの判断は docs/SIBLINGS.md の表に書く。
GitHub の API を認証なしで読む（1 時間に 60 回まで）。GITHUB_TOKEN があれば使う。
"""
from __future__ import annotations

import argparse
import json
import os
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
STATE = ROOT / "docs" / "siblings.json"
OWNER = "kiwiman0706-beep"
SIBLINGS = {"music": "Theta.Music.Hatch", "movie": "Theta.Movie.Hatch", "book": "Theta.Book.Hatch"}


def releases(repo: str) -> list[dict]:
    req = urllib.request.Request(f"https://api.github.com/repos/{OWNER}/{repo}/releases?per_page=30",
                                 headers={"Accept": "application/vnd.github+json", "User-Agent": "ThetaTVhomeHatch-tools"})
    if os.environ.get("GITHUB_TOKEN"):
        req.add_header("Authorization", "Bearer " + os.environ["GITHUB_TOKEN"])
    with urllib.request.urlopen(req, timeout=30) as r:
        return [x for x in json.load(r) if not x.get("draft")]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mark", action="store_true", help="いまの最新を「見た」として記録する")
    a = ap.parse_args()
    state = json.loads(STATE.read_text(encoding="utf-8")) if STATE.exists() else {}
    for key, repo in SIBLINGS.items():
        seen = state.get(key, {}).get("tag")
        try:
            rel = releases(repo)
        except Exception as e:  # 読めなくても、ほかのアプリは続ける
            print(f"## {repo}\n読めません：{e}\n")
            continue
        new = []
        for r in rel:
            if r["tag_name"] == seen:
                break
            new.append(r)
        print(f"## {repo}（前に見た版：{seen or 'なし'}）")
        if not new:
            print("新しい版はありません\n")
        for r in reversed(new):
            body = (r.get("body") or "").strip()
            print(f"### {r['tag_name']}  {r.get('published_at', '')[:10]}{'（試験版）' if r.get('prerelease') else ''}\n{body[:3000]}\n")
        if a.mark and rel:
            state[key] = {"repo": repo, "tag": rel[0]["tag_name"], "published": rel[0].get("published_at", "")[:10]}
    if a.mark:
        STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"記録しました：{STATE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
