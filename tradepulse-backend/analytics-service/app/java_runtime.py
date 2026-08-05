from __future__ import annotations

import os
import shutil
from pathlib import Path
from typing import Iterable


def _candidate_java_homes() -> Iterable[Path]:
    java_home = os.getenv("JAVA_HOME")
    if java_home:
        yield Path(java_home)

    # Common Java install roots for local Windows development.
    if os.name == "nt":
        roots = [
            Path("C:/Program Files/Java"),
            Path("C:/Program Files/Eclipse Adoptium"),
            Path("C:/Program Files/Microsoft"),
        ]
        for root in roots:
            if not root.exists():
                continue
            for child in sorted(root.iterdir(), reverse=True):
                if child.is_dir() and (child / "bin" / "java.exe").exists():
                    yield child
        return

    # Common Linux/macOS locations.
    unix_roots = [Path("/usr/lib/jvm"), Path("/Library/Java/JavaVirtualMachines")]
    for root in unix_roots:
        if not root.exists():
            continue
        for child in sorted(root.iterdir(), reverse=True):
            java_bin = child / "bin" / "java"
            if java_bin.exists():
                yield child
            mac_home = child / "Contents" / "Home"
            if (mac_home / "bin" / "java").exists():
                yield mac_home


def ensure_java_runtime(logger) -> str | None:
    """Ensure the current process can resolve `java` for PySpark startup."""
    current = shutil.which("java")
    if current:
        return current

    for home in _candidate_java_homes():
        java_name = "java.exe" if os.name == "nt" else "java"
        java_path = home / "bin" / java_name
        if not java_path.exists():
            continue

        os.environ["JAVA_HOME"] = str(home)
        path_value = os.environ.get("PATH", "")
        os.environ["PATH"] = f"{home / 'bin'}{os.pathsep}{path_value}" if path_value else str(home / "bin")
        logger.info("Configured JAVA_HOME dynamically: %s", home)
        return str(java_path)

    logger.warning("Java runtime not detected in PATH/JAVA_HOME; PySpark startup may fail.")
    return None

