"""Génère le dossier PDF depuis sa source éditable et les vrais rapports de tests."""
from pathlib import Path
import csv
import json
import re
import xml.etree.ElementTree as ET
from datetime import datetime
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Preformatted
import pypdfium2 as pdfium
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output"
OUTPUT.mkdir(exist_ok=True)


def measurements():
    result = {"tests": 0, "failures": 0, "errors": 0}
    for path in (ROOT / "backend/target/surefire-reports").glob("TEST-*.xml"):
        suite = ET.parse(path).getroot()
        for key in result:
            result[key] += int(suite.attrib.get(key, 0))
    if result["tests"] == 0 or result["failures"] or result["errors"]:
        raise RuntimeError("La génération exige des résultats JUnit complets et sans échec.")
    with (ROOT / "backend/target/site/jacoco/jacoco.csv").open(newline="", encoding="utf-8-sig") as stream:
        rows = list(csv.DictReader(stream))
    for kind in ("LINE", "BRANCH", "INSTRUCTION"):
        covered = sum(int(row[f"{kind}_COVERED"]) for row in rows)
        total = covered + sum(int(row[f"{kind}_MISSED"]) for row in rows)
        key = kind.lower()
        result[f"{key}_covered"] = covered
        result[f"{key}_total"] = total
        result[f"{key}_coverage"] = f"{covered / total * 100:.2f}".replace(".", ",")
    cypress = OUTPUT / "cypress-results.xml"
    if cypress.exists():
        suites = ET.parse(cypress).getroot()
        result["cypress_tests"] = sum(int(s.attrib.get("tests", 0)) for s in suites.iter("testsuite"))
        result["cypress_failures"] = sum(int(s.attrib.get("failures", 0)) for s in suites.iter("testsuite"))
    result["generated_at"] = datetime.now().astimezone().isoformat()
    return result


def main():
    metrics = measurements()
    source = (ROOT / "docs/DOSSIER_BLOC3.md").read_text(encoding="utf-8")
    for key, value in metrics.items():
        source = source.replace("{{" + key + "}}", str(value))
    if "{{" in source:
        raise RuntimeError("Mesure manquante dans le dossier.")
    (OUTPUT / "DOSSIER_BLOC3_editable.md").write_text(source, encoding="utf-8")
    (OUTPUT / "verification.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    font_root = Path("C:/Windows/Fonts")
    for name, filename in (("Arial", "arial.ttf"), ("Arial-Bold", "arialbd.ttf"), ("Arial-Italic", "ariali.ttf"), ("Consolas", "consola.ttf")):
        pdfmetrics.registerFont(TTFont(name, str(font_root / filename)))
    pdfmetrics.registerFontFamily("Arial", normal="Arial", bold="Arial-Bold", italic="Arial-Italic", boldItalic="Arial-Bold")
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle("BodyLocal", fontName="Arial", fontSize=10.2, leading=15.1, spaceAfter=9, textColor=colors.HexColor("#243039")))
    styles.add(ParagraphStyle("TitleLocal", fontName="Arial-Bold", fontSize=23, leading=28, spaceAfter=20, textColor=colors.black, keepWithNext=True))
    styles.add(ParagraphStyle("SubtitleLocal", fontName="Arial-Bold", fontSize=12.4, leading=17, spaceBefore=10, spaceAfter=8, textColor=colors.black, keepWithNext=True))
    styles.add(ParagraphStyle("CellLocal", fontName="Arial", fontSize=8.7, leading=12.2, textColor=colors.HexColor("#243039")))
    styles.add(ParagraphStyle("CellHeaderLocal", parent=styles["CellLocal"], fontName="Arial-Bold", textColor=colors.white))
    styles.add(ParagraphStyle("BulletLocal", parent=styles["BodyLocal"], leftIndent=12, firstLineIndent=-9, spaceAfter=6))
    code_style = ParagraphStyle("CodeLocal", fontName="Consolas", fontSize=8.8, leading=13.2, spaceBefore=6, spaceAfter=14)
    width, height = A4
    available = width - 100
    story = []

    def paragraph(text, style="BodyLocal"):
        return Paragraph(escape(text), styles[style])

    def add_table(lines):
        parsed = [[c.strip() for c in line.strip().strip("|").split("|")] for line in lines]
        parsed = [row for row in parsed if not all(re.fullmatch(r":?-+:?", c) for c in row)]
        n = len(parsed[0])
        if n == 2:
            widths = [available * .37, available * .63]
        elif n == 3:
            widths = [available * .24, available * .43, available * .33]
        else:
            widths = [available / n] * n
        cells = [[paragraph(c, "CellHeaderLocal" if r == 0 else "CellLocal") for c in row] for r, row in enumerate(parsed)]
        table = Table(cells, colWidths=widths, repeatRows=1, hAlign="LEFT")
        table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#324b60")),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f2f5f7")]),
            ("GRID", (0, 0), (-1, -1), .45, colors.HexColor("#d9d9d9")),
            ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ("LEFTPADDING", (0, 0), (-1, -1), 9), ("RIGHTPADDING", (0, 0), (-1, -1), 9),
            ("TOPPADDING", (0, 0), (-1, -1), 8), ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
        ]))
        story.extend([Spacer(1, 5), table, Spacer(1, 13)])

    lines = source.splitlines()
    index = 0
    while index < len(lines):
        line = lines[index].strip()
        if not line:
            index += 1
            continue
        if line == "<!-- page -->":
            story.append(PageBreak())
        elif line.startswith("```"):
            code = []
            index += 1
            while index < len(lines) and not lines[index].startswith("```"):
                code.append(lines[index]); index += 1
            story.append(Preformatted("\n".join(code), code_style))
        elif line.startswith("|"):
            table_lines = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                table_lines.append(lines[index]); index += 1
            add_table(table_lines)
            continue
        elif line.startswith("## "):
            story.append(paragraph(line[3:], "SubtitleLocal"))
        elif line.startswith("# "):
            story.append(paragraph(line[2:], "TitleLocal"))
        elif line.startswith("- "):
            story.append(paragraph("• " + line[2:], "BulletLocal"))
        else:
            text = [line]
            while index + 1 < len(lines) and lines[index + 1].strip() and not lines[index + 1].startswith(("#", "|", "- ", "```", "<!--")):
                index += 1; text.append(lines[index].strip())
            story.append(paragraph(" ".join(text)))
        index += 1

    pdf_path = OUTPUT / "BachelorHETIC_Examen_B3Java_copiearendre_GHAJDAOUI_Adam.pdf"
    def page(canvas, doc):
        canvas.setTitle("Bloc 3 Développement de la solution digitale M-Motors")
        canvas.setAuthor("Adam GHAJDAOUI")
        canvas.setFont("Arial", 8)
        canvas.setFillColor(colors.HexColor("#657482"))
        canvas.drawString(50, height - 32, "M-MOTORS  /  BLOC 3")
        canvas.drawString(50, 28, "Validation locale - publication à compléter avant dépôt final")
        canvas.drawRightString(width - 50, 28, str(doc.page))
    SimpleDocTemplate(str(pdf_path), pagesize=A4, rightMargin=50, leftMargin=50, topMargin=60, bottomMargin=53).build(story, onFirstPage=page, onLaterPages=page)
    render_root = OUTPUT / "qa-pdf"
    render_root.mkdir(exist_ok=True)
    thumbs = []
    pdf = pdfium.PdfDocument(str(pdf_path))
    for n in range(len(pdf)):
        image = pdf[n].render(scale=1.4).to_pil().convert("RGB")
        image.save(render_root / f"page-{n+1:02}.png")
        image.thumbnail((300, 425))
        tile = Image.new("RGB", (320, 455), "#e9edf0")
        tile.paste(image, ((320 - image.width)//2, 10))
        ImageDraw.Draw(tile).text((12, 438), f"Page {n+1}", fill="black")
        thumbs.append(tile)
    sheet = Image.new("RGB", (320*4, 455*((len(thumbs)+3)//4)), "white")
    for n, thumb in enumerate(thumbs):
        sheet.paste(thumb, ((n%4)*320, (n//4)*455))
    sheet.save(render_root / "planche.png")
    print(json.dumps({"pdf": str(pdf_path), "pages": len(pdf), "metrics": metrics}, ensure_ascii=False))


if __name__ == "__main__":
    main()
