"""Behavior checks for the documentation guardrails; no third-party dependencies."""

import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "check_docs", Path(__file__).with_name("check-docs.py")
)
CHECKS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKS)


class DocumentationChecksTest(unittest.TestCase):
    def test_fenced_examples_are_ignored(self):
        text, unclosed = CHECKS.visible_markdown(
            "# Title\n```markdown\n[example](missing.md)\n```\nVisible"
        )
        self.assertNotIn("missing.md", text)
        self.assertFalse(unclosed)

    def test_unclosed_fence_is_reported(self):
        _, unclosed = CHECKS.visible_markdown("```mermaid\nflowchart LR\n")
        self.assertTrue(unclosed)

    def test_missing_links_and_absolute_paths_are_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            errors = CHECKS.check_links(
                root / "README.md", "[missing](missing.md) [local](/private/file.md)", root
            )
            self.assertEqual(len(errors), 2)

    def test_repository_escape_is_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            errors = CHECKS.check_links(root / "README.md", "[outside](../other.md)", root)
            self.assertIn("escapes", errors[0])

    def test_existing_test_method_is_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            test = root / "ExampleTest.java"
            test.write_text("class ExampleTest { void givenAResult() {} }", encoding="utf-8")
            self.assertEqual(
                CHECKS.check_links(
                    root / "README.md", "[givenAResult()](ExampleTest.java)", root
                ), []
            )
            errors = CHECKS.check_links(
                root / "README.md", "[renamed()](ExampleTest.java)", root
            )
            self.assertIn("missing test method", errors[0])

    def test_verified_requires_acceptance_implementation_and_test_evidence(self):
        errors = CHECKS.check_requirements(
            Path("requirements.md"), "### REQ-SES-001 — Create\n\nStatus: Verified\n"
        )
        self.assertEqual(len(errors), 3)

    def test_duplicate_ids_and_unknown_status_are_reported(self):
        text = (
            "### REQ-SES-001 — Create\n\nStatus: Planned\n"
            "### REQ-SES-001 — Other\n\nStatus: Done\n"
        )
        errors = CHECKS.check_requirements(Path("requirements.md"), text)
        self.assertEqual(len(errors), 2)

    def test_planned_does_not_need_fabricated_evidence(self):
        errors = CHECKS.check_requirements(
            Path("requirements.md"), "### REQ-REC-001 — Recover\n\nStatus: Planned\n"
        )
        self.assertEqual(errors, [])

    def test_malformed_requirement_heading_is_reported(self):
        text = (
            "### REQ-SES-001 — Create\n\nStatus: Planned\n"
            "### REQ-SES-2 — Wrong identifier\n\nStatus: Planned\n"
        )
        errors = CHECKS.check_requirements(Path("requirements.md"), text)
        self.assertIn("malformed requirement heading", errors[0])

    def test_unknown_requirement_reference_is_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "docs").mkdir()
            (root / "docs/requirements.md").write_text(
                "### REQ-SES-001 — Create\n\nStatus: Planned\n", encoding="utf-8"
            )
            (root / "README.md").write_text("See REQ-SES-999.", encoding="utf-8")
            _, errors = CHECKS.check_repository(root)
            self.assertEqual(len(errors), 1)
            self.assertIn("unknown requirement reference", errors[0])


if __name__ == "__main__":
    unittest.main()
