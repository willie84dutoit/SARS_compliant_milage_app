r"""Tkinter review tool: page through the downloaded odometer photos, see the app's OCR
reading beside each one, and mark Correct or Fail.

It reads:
  - manifest.csv   (from download_odometers.py: source_name, image_number, file_path, source_url)
  - readings.csv   (from the Android ML Kit runner)
                   columns: source_name, image_number, ocr_reading, confidence, result_type
                   result_type is one of: Confident / LowConfidence / NoTextFound
If readings.csv is absent the photos still display; the OCR line shows "(no reading yet)".

For each photo you click  Correct  or  Fail  (does the OCR reading match the odometer?).
Verdicts save continuously to confirmations.csv. Once every photo has a verdict the report
is written automatically (you can also force it with the Generate report button) to report.md,
with one table per source:  | image number | odometer pass |

Run from the project root:
    .venv/Scripts/python.exe tools/ocr-test/confirm_readings.py
"""

import csv
import os
import tkinter as tkinter_module
from tkinter import ttk

from PIL import Image, ImageTk

SCRIPT_DIRECTORY = os.path.dirname(os.path.abspath(__file__))
MANIFEST_PATH = os.path.join(SCRIPT_DIRECTORY, "manifest.csv")
READINGS_PATH = os.path.join(SCRIPT_DIRECTORY, "readings.csv")
CONFIRMATIONS_PATH = os.path.join(SCRIPT_DIRECTORY, "confirmations.csv")
REPORT_PATH = os.path.join(SCRIPT_DIRECTORY, "report.md")

MAXIMUM_IMAGE_DISPLAY_WIDTH = 760
MAXIMUM_IMAGE_DISPLAY_HEIGHT = 540

VERDICT_CORRECT = "correct"
VERDICT_FAIL = "fail"


def load_manifest_entries():
    """Return the list of photo entries from manifest.csv (source_name, image_number, file_path)."""
    manifest_entries = []
    if not os.path.exists(MANIFEST_PATH):
        return manifest_entries
    with open(MANIFEST_PATH, newline="", encoding="utf-8") as manifest_file:
        for manifest_row in csv.DictReader(manifest_file):
            manifest_entries.append(
                {
                    "source_name": manifest_row["source_name"],
                    "image_number": int(manifest_row["image_number"]),
                    "file_path": manifest_row["file_path"],
                }
            )
    return manifest_entries


def load_ocr_readings():
    """Return a dict keyed by (source_name, image_number) -> reading info, or empty if no readings.csv."""
    ocr_readings_by_photo = {}
    if not os.path.exists(READINGS_PATH):
        return ocr_readings_by_photo
    with open(READINGS_PATH, newline="", encoding="utf-8") as readings_file:
        for reading_row in csv.DictReader(readings_file):
            photo_key = (reading_row["source_name"], int(reading_row["image_number"]))
            ocr_readings_by_photo[photo_key] = {
                "ocr_reading": reading_row.get("ocr_reading", ""),
                "confidence": reading_row.get("confidence", ""),
                "result_type": reading_row.get("result_type", ""),
            }
    return ocr_readings_by_photo


def load_existing_confirmations():
    """Return a dict keyed by (source_name, image_number) -> saved verdict, for resume."""
    saved_confirmations = {}
    if not os.path.exists(CONFIRMATIONS_PATH):
        return saved_confirmations
    with open(CONFIRMATIONS_PATH, newline="", encoding="utf-8") as confirmations_file:
        for confirmation_row in csv.DictReader(confirmations_file):
            photo_key = (confirmation_row["source_name"], int(confirmation_row["image_number"]))
            saved_confirmations[photo_key] = confirmation_row["verdict"]
    return saved_confirmations


class OdometerReviewApplication:
    def __init__(self, tkinter_root):
        self.tkinter_root = tkinter_root
        self.tkinter_root.title("Odometer OCR review — Correct / Fail")

        self.manifest_entries = load_manifest_entries()
        self.ocr_readings_by_photo = load_ocr_readings()
        self.verdict_by_photo = load_existing_confirmations()
        self.current_index = 0
        self.current_photo_image = None  # keep a reference so Tk does not garbage-collect it

        self._build_widgets()
        if not self.manifest_entries:
            self.status_variable.set("No manifest.csv found — run download_odometers.py first.")
        else:
            self._show_current_photo()

    def _build_widgets(self):
        self.image_label = ttk.Label(self.tkinter_root, anchor="center")
        self.image_label.grid(row=0, column=0, columnspan=5, padx=10, pady=10)

        self.caption_variable = tkinter_module.StringVar()
        ttk.Label(self.tkinter_root, textvariable=self.caption_variable, font=("Segoe UI", 11, "bold")).grid(
            row=1, column=0, columnspan=5
        )

        self.ocr_reading_variable = tkinter_module.StringVar()
        ttk.Label(self.tkinter_root, textvariable=self.ocr_reading_variable, font=("Segoe UI", 14)).grid(
            row=2, column=0, columnspan=5, pady=(8, 4)
        )

        correct_button = ttk.Button(self.tkinter_root, text="Correct (Y)", command=self._mark_correct)
        fail_button = ttk.Button(self.tkinter_root, text="Fail (N)", command=self._mark_fail)
        previous_button = ttk.Button(self.tkinter_root, text="< Prev", command=self._go_previous)
        next_button = ttk.Button(self.tkinter_root, text="Next >", command=self._go_next)
        report_button = ttk.Button(self.tkinter_root, text="Generate report", command=self._generate_report)

        correct_button.grid(row=3, column=0, padx=4, pady=12)
        fail_button.grid(row=3, column=1, padx=4, pady=12)
        previous_button.grid(row=3, column=2, padx=4, pady=12)
        next_button.grid(row=3, column=3, padx=4, pady=12)
        report_button.grid(row=3, column=4, padx=4, pady=12)

        self.status_variable = tkinter_module.StringVar()
        ttk.Label(self.tkinter_root, textvariable=self.status_variable, foreground="#555").grid(
            row=4, column=0, columnspan=5, pady=(0, 10)
        )

        # Keyboard shortcuts: Y = correct, N = fail, arrows navigate.
        self.tkinter_root.bind("y", lambda key_event: self._mark_correct())
        self.tkinter_root.bind("n", lambda key_event: self._mark_fail())
        self.tkinter_root.bind("<Left>", lambda key_event: self._go_previous())
        self.tkinter_root.bind("<Right>", lambda key_event: self._go_next())

    def _current_entry(self):
        return self.manifest_entries[self.current_index]

    def _show_current_photo(self):
        current_entry = self._current_entry()
        absolute_image_path = os.path.join(SCRIPT_DIRECTORY, current_entry["file_path"])

        try:
            loaded_image = Image.open(absolute_image_path)
            loaded_image.thumbnail((MAXIMUM_IMAGE_DISPLAY_WIDTH, MAXIMUM_IMAGE_DISPLAY_HEIGHT))
            self.current_photo_image = ImageTk.PhotoImage(loaded_image)
            self.image_label.configure(image=self.current_photo_image, text="")
        except (FileNotFoundError, OSError) as image_error:
            self.image_label.configure(image="", text=f"[cannot open image]\n{image_error}")

        photo_key = (current_entry["source_name"], current_entry["image_number"])
        reviewed_count = len(self.verdict_by_photo)
        self.caption_variable.set(
            f"{current_entry['source_name']}  #{current_entry['image_number']:02d}"
            f"   ({self.current_index + 1} of {len(self.manifest_entries)})"
            f"   — reviewed {reviewed_count}/{len(self.manifest_entries)}"
        )

        ocr_reading_info = self.ocr_readings_by_photo.get(photo_key)
        if ocr_reading_info is None:
            self.ocr_reading_variable.set("OCR reading:  (no reading yet — run the Android OCR step)")
        else:
            self.ocr_reading_variable.set(
                f"OCR reading:  {ocr_reading_info['ocr_reading'] or '(none)'}"
                f"    [{ocr_reading_info['result_type']} {ocr_reading_info['confidence']}]"
            )

        existing_verdict = self.verdict_by_photo.get(photo_key, "not reviewed yet")
        self.status_variable.set(f"Verdict: {existing_verdict}")

    def _record_verdict(self, verdict_value):
        current_entry = self._current_entry()
        photo_key = (current_entry["source_name"], current_entry["image_number"])
        self.verdict_by_photo[photo_key] = verdict_value
        self._save_all_confirmations()
        if len(self.verdict_by_photo) >= len(self.manifest_entries):
            self._generate_report()
            self.status_variable.set("All photos reviewed — report.md written.")
        else:
            self._go_next()

    def _mark_correct(self):
        self._record_verdict(VERDICT_CORRECT)

    def _mark_fail(self):
        self._record_verdict(VERDICT_FAIL)

    def _go_next(self):
        if self.current_index < len(self.manifest_entries) - 1:
            self.current_index += 1
            self._show_current_photo()

    def _go_previous(self):
        if self.current_index > 0:
            self.current_index -= 1
            self._show_current_photo()

    def _save_all_confirmations(self):
        with open(CONFIRMATIONS_PATH, "w", newline="", encoding="utf-8") as confirmations_file:
            confirmations_writer = csv.writer(confirmations_file)
            confirmations_writer.writerow(["source_name", "image_number", "verdict"])
            for manifest_entry in self.manifest_entries:
                photo_key = (manifest_entry["source_name"], manifest_entry["image_number"])
                saved_verdict = self.verdict_by_photo.get(photo_key)
                if saved_verdict is not None:
                    confirmations_writer.writerow(
                        [manifest_entry["source_name"], manifest_entry["image_number"], saved_verdict]
                    )

    def _generate_report(self):
        source_names_in_order = []
        for manifest_entry in self.manifest_entries:
            if manifest_entry["source_name"] not in source_names_in_order:
                source_names_in_order.append(manifest_entry["source_name"])

        report_lines = ["# Odometer OCR test report", ""]
        for source_name in source_names_in_order:
            report_lines.append(f"## {source_name}")
            report_lines.append("")
            report_lines.append("| image number | odometer pass |")
            report_lines.append("|---|---|")
            passed_count = 0
            reviewed_count = 0
            for manifest_entry in self.manifest_entries:
                if manifest_entry["source_name"] != source_name:
                    continue
                photo_key = (source_name, manifest_entry["image_number"])
                saved_verdict = self.verdict_by_photo.get(photo_key)
                if saved_verdict == VERDICT_CORRECT:
                    pass_cell = "PASS"
                    passed_count += 1
                    reviewed_count += 1
                elif saved_verdict == VERDICT_FAIL:
                    pass_cell = "FAIL"
                    reviewed_count += 1
                else:
                    pass_cell = "—"
                report_lines.append(f"| {manifest_entry['image_number']:02d} | {pass_cell} |")
            report_lines.append("")
            report_lines.append(f"**{source_name}: {passed_count}/{reviewed_count} passed.**")
            report_lines.append("")

        with open(REPORT_PATH, "w", encoding="utf-8") as report_file:
            report_file.write("\n".join(report_lines))
        self.status_variable.set(f"Report written to {REPORT_PATH}")


def main():
    tkinter_root = tkinter_module.Tk()
    OdometerReviewApplication(tkinter_root)
    tkinter_root.mainloop()


if __name__ == "__main__":
    main()
