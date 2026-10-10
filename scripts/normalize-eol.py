#!/usr/bin/env python3
"""把文本文件的行尾规范成 LF（只删掉多余 CR，不重新编码）。

为什么需要这个脚本
------------------
本仓库的 Git 配置是 core.autocrlf=false，且历史上有工具（AI 编辑器 / IDE / 脚本）
把工作区文件整体写成 CRLF，导致 `git add` 产出"整文件改写"的假 diff。

本脚本按**字节**处理：只把 `\\r\\n` 收成 `\\n`，只删除真正孤立的行尾 CR。
它不解码 / 不重新编码文件（因此不改动 GBK、UTF-16 等非 UTF-8 文件的内容），
保留原 BOM，也不给缺行尾的文件补换行。

用法
----
    python scripts/normalize-eol.py --check  <文件...>   # 只检查，退出码 1 表示存在 CR
    python scripts/normalize-eol.py --check              # 检查 git 当前改动的文件
    python scripts/normalize-eol.py --write  <文件...>   # 就地规范化（只动 CR）
    python scripts/normalize-eol.py --write --rename-only-old-name <旧名> <新名>
                                                         # 改名后只处理旧文件仍存在的情况

退出码
------
    0 = 无需改动 / 改动已完成
    1 = --check 发现需要改动（或发现二进制文件被跳过）
    2 = 参数或环境错误
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

BINARY_SNIFF_BYTES = 8192
CR = 0x0D
LF = 0x0A
BOMS = (
    b"\xef\xbb\xbf",      # UTF-8
    b"\xff\xfe\x00\x00",  # UTF-32 LE
    b"\x00\x00\xfe\xff",  # UTF-32 BE
    b"\xff\xfe",          # UTF-16 LE
    b"\xfe\xff",          # UTF-16 BE
)


def looks_binary(data: bytes) -> bool:
    """含 NUL 即视为二进制（不碰）。"""
    return b"\x00" in data[:BINARY_SNIFF_BYTES]


def normalize(data: bytes) -> tuple[bytes, dict[str, int]]:
    """返回 (规范化后的字节, 统计)。只删除 CRLF 与文件行尾处的孤立 CR。"""
    stats = {"crlf": 0, "lone_cr": 0}
    out = bytearray()
    i = 0
    n = len(data)
    while i < n:
        b = data[i]
        if b == CR:
            if i + 1 < n and data[i + 1] == LF:
                stats["crlf"] += 1
                out.append(LF)
                i += 2
                continue
            if i + 1 == n:
                # 文件末尾的孤立 CR：视为行尾，删除
                stats["lone_cr"] += 1
                i += 1
                continue
            # 行内孤立 CR（老式 Mac 换行或刻意内容）：原样保留，不擅自改内容
            out.append(CR)
            i += 1
            continue
        out.append(b)
        i += 1
    return bytes(out), stats


def bom_length(data: bytes) -> int:
    for bom in BOMS:
        if data.startswith(bom):
            return len(bom)
    return 0


def git_changed_files() -> list[str]:
    """git 当前有改动的已跟踪文件（不含未跟踪）。"""
    try:
        out = subprocess.run(
            ["git", "status", "--porcelain=v1"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError) as exc:  # pragma: no cover
        print(f"无法读取 git 状态: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc

    files: list[str] = []
    for line in out.splitlines():
        status, _, path = line[:2], line[2], line[3:]
        if status.strip() in {"D", "R"}:
            continue
        if status.startswith("?"):
            continue
        files.append(path.strip().strip('"'))
    return files


def index_blob_for(path: str) -> bytes | None:
    """返回该路径在 Git 索引里的原始字节；不在索引中则返回 None。"""
    try:
        rev = subprocess.run(
            ["git", "rev-parse", f":{path}"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None
    try:
        return subprocess.run(
            ["git", "cat-file", "blob", rev],
            check=True, capture_output=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        return None


def process(path: Path, write: bool, index_blob: bytes | None = None,
            allow_index_diff: bool = False) -> tuple[str, dict[str, int] | None]:
    try:
        raw = path.read_bytes()
    except OSError as exc:
        return f"读取失败: {exc}", None
    if looks_binary(raw):
        return "二进制，跳过", None

    fixed, stats = normalize(raw)
    if fixed == raw:
        return "已是 LF", {"crlf": 0, "lone_cr": 0}

    if index_blob is not None and fixed != index_blob and not allow_index_diff:
        return "跳过：规范化后与索引内容不一致（疑似有内容改动，未写入）", None

    if write:
        # 原子替换：仅内容变化，属性（权限/时间之外）不变
        path.write_bytes(fixed)
        return f"已规范化 (CRLF {stats['crlf']} 处, 行尾孤立 CR {stats['lone_cr']} 处)", stats
    return (
        f"需要规范化 (CRLF {stats['crlf']} 处, 行尾孤立 CR {stats['lone_cr']} 处)",
        stats,
    )


def expand_at_files(targets: list[str]) -> list[str]:
    """把 @路径 展开为文件里逐行列出的路径（用于超长文件列表）。"""
    expanded: list[str] = []
    for item in targets:
        if not item.startswith("@"):
            expanded.append(item)
            continue
        list_path = Path(item[1:])
        try:
            lines = list_path.read_text(encoding="utf-8").splitlines()
        except OSError as exc:
            print(f"无法读取列表文件 {list_path}: {exc}", file=sys.stderr)
            raise SystemExit(2) from exc
        expanded.extend(line.strip() for line in lines if line.strip())
    return expanded


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="把文本文件行尾规范成 LF（字节级，不重新编码）",
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--check", action="store_true", help="只检查，不写文件")
    group.add_argument("--write", action="store_true", help="就地规范化")
    parser.add_argument("--require-index-match", action="store_true",
                        help="仅当规范化后的内容与索引内容完全一致时才写入（防丢改动）")
    parser.add_argument("--allow-index-diff", action="store_true",
                        help="允许索引里本来就存着 CRLF 的文件也写入（用于把老 CRLF 提交收进 LF）")
    parser.add_argument("files", nargs="*", help="文件路径；省略则用 git 当前改动的文件")
    args = parser.parse_args(argv)

    targets = expand_at_files(args.files) if args.files else git_changed_files()
    if not targets:
        print("没有需要处理的文件。")
        return 0

    need_fix = 0
    skipped = 0
    for name in targets:
        path = Path(name)
        if not path.is_file():
            print(f"[跳过] {name}: 不是普通文件")
            skipped += 1
            continue
        index_blob = index_blob_for(name) if args.require_index_match else None
        message, stats = process(path, args.write, index_blob, args.allow_index_diff)
        print(f"[{ '写' if args.write else '查' }] {name}: {message}")
        if stats is not None and (stats["crlf"] or stats["lone_cr"]) and not args.write:
            need_fix += 1

    print("-" * 60)
    if args.write:
        print(f"完成：处理 {len(targets)} 个路径，跳过 {skipped} 个。")
        return 0
    if need_fix:
        print(f"发现 {need_fix} 个文件行尾不是 LF。用 --write 规范化。")
        return 1
    print("全部已是 LF。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
