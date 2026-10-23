#!/usr/bin/env python3

from pathlib import Path
import mimetypes


# ============================================================
# CONFIGURATION
# ============================================================

ROOT = Path(".").resolve()

# ABSOLUTE LIMIT FOR EACH GENERATED PART
MAX_CHARS = 3000

OUTPUT_DIR = ROOT / "deepseek_repo_context"


# ============================================================
# DIRECTORIES TO IGNORE
# ============================================================

IGNORED_DIRECTORIES = {
    ".git",
    "node_modules",
    ".venv",
    "venv",
    "env",
    "__pycache__",
    ".pytest_cache",
    ".mypy_cache",
    ".ruff_cache",
    ".tox",
    ".nox",
    ".cache",
    "coverage",
    "dist",
    "build",
    ".next",
    ".nuxt",
    ".parcel-cache",
    ".turbo",
    "target",
    "vendor",
}


# ============================================================
# FILES TO IGNORE
# ============================================================

IGNORED_FILES = {
    ".DS_Store",
    "Thumbs.db",
}


# ============================================================
# BINARY / MEDIA EXTENSIONS
# ============================================================

BINARY_EXTENSIONS = {
    # Images
    ".png", ".jpg", ".jpeg", ".gif", ".webp",
    ".bmp", ".ico", ".tif", ".tiff", ".avif",

    # Video
    ".mp4", ".mkv", ".avi", ".mov", ".webm",
    ".flv", ".wmv", ".m4v",

    # Audio
    ".mp3", ".wav", ".ogg", ".flac",
    ".aac", ".m4a",

    # Archives
    ".zip", ".rar", ".7z", ".tar",
    ".gz", ".bz2", ".xz",

    # Binary / compiled
    ".exe", ".dll", ".so", ".dylib",
    ".bin", ".o", ".a", ".class",
    ".pyc", ".pyo",

    # Databases
    ".db", ".sqlite", ".sqlite3",

    # Fonts
    ".ttf", ".otf", ".woff", ".woff2",
}


# ============================================================
# HELPERS
# ============================================================

def should_ignore(path: Path) -> bool:

    try:
        relative = path.relative_to(ROOT)
    except ValueError:
        return True

    if any(
        part in IGNORED_DIRECTORIES
        for part in relative.parts
    ):
        return True

    if path.name in IGNORED_FILES:
        return True

    # Never scan our own generated directory.
    try:
        path.relative_to(OUTPUT_DIR)
        return True
    except ValueError:
        pass

    return False


def get_mime(path: Path) -> str:

    mime, _ = mimetypes.guess_type(path.name)

    return mime or "unknown"


def human_size(size: int) -> str:

    units = ["B", "KB", "MB", "GB", "TB"]

    value = float(size)

    for unit in units:

        if value < 1024:
            return f"{value:.1f} {unit}"

        value /= 1024

    return f"{value:.1f} PB"


def read_text(path: Path):

    try:

        data = path.read_bytes()

        # Detect common binary files.
        if b"\x00" in data:
            return None

        return data.decode("utf-8")

    except (OSError, UnicodeDecodeError):

        return None


# ============================================================
# COLLECT REPOSITORY
# ============================================================

def collect_files():

    files = []

    for path in ROOT.rglob("*"):

        if not path.is_file():
            continue

        if should_ignore(path):
            continue

        try:
            size = path.stat().st_size
        except OSError:
            continue

        relative = str(
            path.relative_to(ROOT)
        )

        # Explicit binary/media.
        if path.suffix.lower() in BINARY_EXTENSIONS:

            files.append({
                "path": relative,
                "type": "BINARY",
                "mime": get_mime(path),
                "size": size,
                "content": None,
            })

            continue

        content = read_text(path)

        if content is None:

            files.append({
                "path": relative,
                "type": "BINARY_OR_UNREADABLE",
                "mime": get_mime(path),
                "size": size,
                "content": None,
            })

        else:

            files.append({
                "path": relative,
                "type": "TEXT",
                "mime": get_mime(path),
                "size": size,
                "content": content,
            })

    files.sort(
        key=lambda item: item["path"].lower()
    )

    return files


# ============================================================
# DIRECTORY TREE
# ============================================================

def build_tree(files):

    root = {}

    for item in files:

        parts = Path(
            item["path"]
        ).parts

        current = root

        for part in parts:

            if part not in current:
                current[part] = {}

            current = current[part]

    lines = ["."]

    def render(node, prefix=""):

        entries = sorted(node.items())

        for index, (name, children) in enumerate(entries):

            last = index == len(entries) - 1

            lines.append(
                prefix +
                ("└── " if last else "├── ") +
                name
            )

            if children:

                render(
                    children,
                    prefix +
                    ("    " if last else "│   ")
                )

    render(root)

    return "\n".join(lines)


# ============================================================
# MANIFEST
# ============================================================

def create_manifest(files, tree):

    lines = [
        "DEEPSEEK REPOSITORY MANIFEST",
        "============================",
        "",
        "DIRECTORY TREE",
        "--------------",
        "",
        tree,
        "",
        "FILE LIST",
        "---------",
        "",
    ]

    for number, item in enumerate(files, 1):

        lines.append(
            f"{number:04d} | "
            f"{item['type']:22} | "
            f"{human_size(item['size']):>10} | "
            f"{item['path']}"
        )

    return "\n".join(lines) + "\n"


# ============================================================
# FILE REPRESENTATION
# ============================================================

def binary_representation(item):

    return (
        "<<<REPOSITORY_FILE_START>>>\n"
        f"PATH: {item['path']}\n"
        f"TYPE: {item['type']}\n"
        f"MIME: {item['mime']}\n"
        f"SIZE_BYTES: {item['size']}\n"
        "CONTENT: EXCLUDED BINARY/MEDIA FILE\n"
        "<<<REPOSITORY_FILE_END>>>\n"
    )


def text_prefix(item):

    return (
        "<<<REPOSITORY_FILE_START>>>\n"
        f"PATH: {item['path']}\n"
        "TYPE: TEXT\n"
        f"MIME: {item['mime']}\n"
        f"SIZE_BYTES: {item['size']}\n"
        "<<<FILE_CONTENT_START>>>\n"
    )


def text_suffix():

    return (
        "\n<<<FILE_CONTENT_END>>>\n"
        "<<<REPOSITORY_FILE_END>>>\n"
    )


# ============================================================
# PART HEADER
# ============================================================

def part_header(part_number):

    return (
        "============================================================\n"
        f"DEEPSEEK REPOSITORY CONTEXT - PART {part_number:03d}\n"
        "============================================================\n"
        "\n"
    )


# ============================================================
# WRITE PART
# ============================================================

def write_part(part_number, content):

    header = part_header(part_number)

    final_text = header + content

    actual_length = len(final_text)

    if actual_length > MAX_CHARS:

        raise RuntimeError(
            f"BUG: part {part_number} is "
            f"{actual_length:,} characters."
        )

    path = (
        OUTPUT_DIR /
        f"{part_number:03d}_REPOSITORY_PART.txt"
    )

    path.write_text(
        final_text,
        encoding="utf-8"
    )

    print(
        f"Created {path.name}: "
        f"{actual_length:,} characters"
    )


# ============================================================
# APPEND TEXT TO CURRENT PART
# ============================================================

def current_header_size(part_number):

    return len(
        part_header(part_number)
    )


# ============================================================
# MAIN EXPORT
# ============================================================

def main():

    print()
    print("Scanning repository...")
    print()

    OUTPUT_DIR.mkdir(
        exist_ok=True
    )

    # --------------------------------------------------------
    # COLLECT FILES
    # --------------------------------------------------------

    files = collect_files()

    print(
        f"Found {len(files):,} relevant files."
    )

    # --------------------------------------------------------
    # MANIFEST
    # --------------------------------------------------------

    tree = build_tree(files)

    manifest = create_manifest(
        files,
        tree
    )

    manifest_path = (
        OUTPUT_DIR /
        "000_MANIFEST.txt"
    )

    manifest_path.write_text(
        manifest,
        encoding="utf-8"
    )

    print(
        f"Created 000_MANIFEST.txt: "
        f"{len(manifest):,} characters"
    )

    # --------------------------------------------------------
    # PART STATE
    # --------------------------------------------------------

    part_number = 1

    header = part_header(
        part_number
    )

    current = header

    # --------------------------------------------------------
    # PROCESS FILES
    # --------------------------------------------------------

    for item in files:

        # ====================================================
        # BINARY FILE
        # ====================================================

        if item["type"] != "TEXT":

            block = binary_representation(
                item
            )

            # If it doesn't fit in the current part,
            # write the current part first.
            if (
                len(current) +
                len(block)
                > MAX_CHARS
            ):

                # Only write if it contains something
                # besides the header.
                if len(current) > len(header):
                    write_part(
                        part_number,
                        current[len(header):]
                    )

                    part_number += 1
                    header = part_header(
                        part_number
                    )
                    current = header

            # A binary metadata block should always fit.
            if (
                len(header) +
                len(block)
                > MAX_CHARS
            ):

                raise RuntimeError(
                    f"Binary metadata for "
                    f"{item['path']} is too large."
                )

            current += block

            continue

        # ====================================================
        # TEXT FILE
        # ====================================================

        prefix = text_prefix(item)
        suffix = text_suffix()

        content = item["content"]

        # ----------------------------------------------------
        # Empty text file
        # ----------------------------------------------------

        if content == "":

            complete_block = (
                prefix +
                suffix
            )

            if (
                len(current) +
                len(complete_block)
                > MAX_CHARS
            ):

                if len(current) > len(header):

                    write_part(
                        part_number,
                        current[len(header):]
                    )

                    part_number += 1
                    header = part_header(
                        part_number
                    )
                    current = header

            if (
                len(header) +
                len(complete_block)
                > MAX_CHARS
            ):

                raise RuntimeError(
                    f"Metadata for empty file "
                    f"{item['path']} is too large."
                )

            current += complete_block

            continue

        # ----------------------------------------------------
        # TEXT FILE CONTENT
        # ----------------------------------------------------

        position = 0

        while position < len(content):

            # Reserve space for prefix and suffix.
            available = (
                MAX_CHARS -
                len(current) -
                len(prefix) -
                len(suffix)
            )

            # ------------------------------------------------
            # No room in current part.
            # ------------------------------------------------

            if available <= 0:

                if len(current) > len(header):

                    write_part(
                        part_number,
                        current[len(header):]
                    )

                    part_number += 1
                    header = part_header(
                        part_number
                    )
                    current = header

                    continue

                # If we're on a completely empty part,
                # there must still be enough room for metadata.
                available = (
                    MAX_CHARS -
                    len(current) -
                    len(prefix) -
                    len(suffix)
                )

                if available <= 0:

                    raise RuntimeError(
                        f"File metadata for "
                        f"{item['path']} is too large "
                        f"for a {MAX_CHARS}-character part."
                    )

            # ------------------------------------------------
            # Take exactly as many original characters as fit.
            # ------------------------------------------------

            take = min(
                available,
                len(content) - position
            )

            piece = content[
                position:
                position + take
            ]

            block = (
                prefix +
                piece +
                (
                    ""
                    if piece.endswith("\n")
                    else "\n"
                ) +
                suffix
            )

            # ------------------------------------------------
            # If the block doesn't fit, reduce the content
            # until it does. This check uses the ACTUAL final
            # string.
            # ------------------------------------------------

            while (
                len(current) +
                len(block)
                > MAX_CHARS
            ):

                # Reduce by one character at a time only when
                # the initial calculation is unexpectedly off.
                take -= 1

                if take <= 0:

                    raise RuntimeError(
                        f"Could not fit file "
                        f"{item['path']} into "
                        f"{MAX_CHARS}-character parts."
                    )

                piece = content[
                    position:
                    position + take
                ]

                block = (
                    prefix +
                    piece +
                    (
                        ""
                        if piece.endswith("\n")
                        else "\n"
                    ) +
                    suffix
                )

            current += block

            position += len(piece)

            # ------------------------------------------------
            # If the file has more content, flush this part.
            # ------------------------------------------------

            if position < len(content):

                write_part(
                    part_number,
                    current[len(header):]
                )

                part_number += 1
                header = part_header(
                    part_number
                )
                current = header

    # --------------------------------------------------------
    # FINAL PART
    # --------------------------------------------------------

    if len(current) > len(header):

        write_part(
            part_number,
            current[len(header):]
        )

    # --------------------------------------------------------
    # VERIFY EVERYTHING
    # --------------------------------------------------------

    print()
    print("Verifying generated parts...")

    generated = sorted(
        OUTPUT_DIR.glob(
            "*_REPOSITORY_PART.txt"
        )
    )

    for path in generated:

        actual = len(
            path.read_text(
                encoding="utf-8"
            )
        )

        if actual > MAX_CHARS:

            raise RuntimeError(
                f"VERIFICATION FAILED: "
                f"{path.name} contains "
                f"{actual:,} characters."
            )

        print(
            f"OK: {path.name} -> "
            f"{actual:,} characters"
        )

    # --------------------------------------------------------
    # SUMMARY
    # --------------------------------------------------------

    text_count = sum(
        item["type"] == "TEXT"
        for item in files
    )

    binary_count = (
        len(files) -
        text_count
    )

    print()
    print("============================================================")
    print("EXPORT COMPLETE")
    print("============================================================")
    print(
        f"Total files:        {len(files):,}"
    )
    print(
        f"Text files:         {text_count:,}"
    )
    print(
        f"Binary/media files: {binary_count:,}"
    )
    print(
        f"Repository parts:   {len(generated):,}"
    )
    print(
        f"Hard limit:         {MAX_CHARS:,} characters"
    )
    print()
    print(
        "Every generated part passed final verification."
    )
    print()
    print(
        "Send 000_MANIFEST.txt first, then "
        "001, 002, 003, ... in order."
    )


if __name__ == "__main__":
    main()
