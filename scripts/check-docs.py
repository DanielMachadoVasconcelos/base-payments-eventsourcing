#!/usr/bin/env python3
"""Check local documentation structure, not semantic correctness or Mermaid rendering."""

from pathlib import Path
import re
import sys
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {".git", ".gradle", "build", "node_modules", "__pycache__"}
LINK = re.compile(r"\[([^\]]+)\]\(([^)\s]+)\)")
REQUIREMENT = re.compile(r"^### (REQ-[A-Z]+-\d{3}) — (.+)$", re.MULTILINE)
STATUSES = {"Verified", "Adopted", "Planned", "Retired", "Superseded"}


def visible_markdown(text):
    """Ignore examples in fenced blocks and reject an unclosed fence."""
    lines = []
    fence = None
    for line in text.splitlines():
        marker = re.match(r"^\s*(`{3,}|~{3,})", line)
        if marker:
            current = marker.group(1)
            if fence is None:
                fence = current
            elif current[0] == fence[0] and len(current) >= len(fence):
                fence = None
            continue
        if fence is None:
            lines.append(line)
    return "\n".join(lines), fence is not None


def markdown_files(root):
    return sorted(
        path for path in root.rglob("*.md")
        if not any(part in EXCLUDED for part in path.relative_to(root).parts)
    )


def check_links(document, text, root):
    errors = []
    for label, address in LINK.findall(text):
        if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", address):
            continue
        location = unquote(address.split("#", 1)[0])
        if not location:
            continue
        if Path(location).is_absolute():
            errors.append(f"{document}: absolute local link: {address}")
            continue
        target = (document.parent / location).resolve()
        if not target.is_relative_to(root.resolve()):
            errors.append(f"{document}: link escapes the repository: {address}")
        elif not target.exists():
            errors.append(f"{document}: missing local link: {address}")
        elif label.endswith("()") and target.name.endswith("Test.java"):
            method = label[:-2]
            declaration = rf"\bvoid\s+{re.escape(method)}\s*\("
            if not re.search(declaration, target.read_text(encoding="utf-8")):
                errors.append(f"{document}: missing test method: {label} in {address}")
    return errors


def check_requirements(document, text):
    errors = []
    sections = list(REQUIREMENT.finditer(text))
    for heading in re.findall(r"^### REQ-.+$", text, re.MULTILINE):
        if not REQUIREMENT.fullmatch(heading):
            errors.append(f"{document}: malformed requirement heading: {heading}")
    seen = set()
    if not sections:
        return [f"{document}: no stable requirement sections found"]
    for index, heading in enumerate(sections):
        identity = heading.group(1)
        end = sections[index + 1].start() if index + 1 < len(sections) else len(text)
        body = text[heading.end():end]
        if identity in seen:
            errors.append(f"{document}: duplicate requirement ID: {identity}")
        seen.add(identity)
        status = re.search(r"^Status: (.+)$", body, re.MULTILINE)
        if not status or status.group(1) not in STATUSES:
            errors.append(f"{document}: invalid/missing status for {identity}")
            continue
        if status.group(1) == "Verified":
            if not re.search(r"^Acceptance: .+", body, re.MULTILINE):
                errors.append(f"{document}: missing acceptance for {identity}")
            implementation = re.search(r"^Implementation: (.+)$", body, re.MULTILINE)
            if not implementation or not LINK.search(implementation.group(1)):
                errors.append(f"{document}: missing implementation link for {identity}")
            evidence = re.search(r"^Evidence: (.+)$", body, re.MULTILINE)
            links = LINK.findall(evidence.group(1)) if evidence else []
            if not any(
                label.endswith("()") and "src/test/" in address
                and address.endswith("Test.java")
                for label, address in links
            ):
                errors.append(f"{document}: missing executable test link for {identity}")
    return errors


def check_repository(root):
    errors = []
    documents = markdown_files(root)
    catalogue = root / "docs/requirements.md"
    catalogue_text = catalogue.read_text(encoding="utf-8") if catalogue.exists() else ""
    known_ids = {heading.group(1) for heading in REQUIREMENT.finditer(catalogue_text)}
    for document in documents:
        text, unclosed = visible_markdown(document.read_text(encoding="utf-8"))
        if unclosed:
            errors.append(f"{document}: unclosed Markdown fence")
        errors.extend(check_links(document, text, root))
        for identity in set(re.findall(r"\bREQ-[A-Z]+-\d{3}\b", text)):
            if identity not in known_ids:
                errors.append(f"{document}: unknown requirement reference: {identity}")
        if document == root / "docs/requirements.md":
            errors.extend(check_requirements(document, text))
    if not (root / "docs/requirements.md").exists():
        errors.append("Missing docs/requirements.md")
    return documents, errors


def main():
    documents, errors = check_repository(ROOT)
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print(f"Documentation checks passed ({len(documents)} Markdown files).")
    print("Checked local links, test methods, requirement IDs/references/status/evidence and fences.")
    print("Not checked: external URLs, heading anchors, diagram rendering or semantic accuracy.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
