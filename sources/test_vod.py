#!/usr/bin/env python3
"""探测用户自己提供的苹果 CMS（MacCMS）JSON 接口是否可访问。

本仓库不附带任何点播接口。把你有权使用的地址作为参数传入：

    python3 sources/test_vod.py https://example.com/api.php/provide/vod/

脚本只在终端打印分类数量和是否返回 JSON，不会把地址写进应用。
"""
import json
import subprocess
import sys


def probe(url: str) -> dict:
    sep = "&" if "?" in url else "?"
    try:
        r = subprocess.run(
            ["curl", "-sL", "-m", "15", url + sep + "ac=list"],
            capture_output=True,
            text=True,
            timeout=18,
        )
        data = json.loads(r.stdout)
        classes = [c.get("type_name") for c in data.get("class", []) if isinstance(c, dict)]
        return {"api": url, "ok": True, "total": data.get("total"), "categories": classes}
    except Exception as e:
        return {"api": url, "ok": False, "error": str(e)}


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print("用法: python3 sources/test_vod.py <苹果CMS接口URL> [更多URL...]", file=sys.stderr)
        print("示例: python3 sources/test_vod.py https://example.com/api.php/provide/vod/", file=sys.stderr)
        return 2
    for url in argv[1:]:
        if not url.startswith(("http://", "https://")):
            print(json.dumps({"api": url, "ok": False, "error": "地址需要以 http:// 或 https:// 开头"}, ensure_ascii=False))
            continue
        print(json.dumps(probe(url), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
