#!/usr/bin/env python3
"""
Convert DOCX documents to Markdown with images.

Extracts text (headings, paragraphs, lists, tables) and embedded images
from .docx files, producing a clean Markdown file with an images subfolder.

Requirements: python-docx
    pip install python-docx

Usage:
    python docx_to_markdown.py <input.docx> [output.md]
    python docx_to_markdown.py <input.docx> --image-format none
"""

import argparse
import re
import sys
from pathlib import Path

try:
    from docx import Document
    from docx.oxml.ns import qn
    from docx.table import Table
    from docx.text.paragraph import Paragraph
    from docx.text.run import Run
except ImportError:
    print("Error: python-docx is required. Install with: pip install python-docx",
          file=sys.stderr)
    sys.exit(1)


def normalize_filename(name):
    """Replace spaces and problematic chars with underscores."""
    return re.sub(r"[\s]+", "_", name)


# ── Image saving ─────────────────────────────────────────────────────

def _save_image_from_rid(rid, rels, image_dir, image_counter):
    """Save an image by relationship ID. Returns markdown ref or None."""
    if image_dir is None or rid not in rels:
        return None
    rel = rels[rid]
    try:
        blob = rel.target_part.blob
        content_type = rel.target_part.content_type
    except Exception:
        return None

    ext_map = {
        "image/png": ".png", "image/jpeg": ".jpg", "image/gif": ".gif",
        "image/bmp": ".bmp", "image/tiff": ".tiff", "image/x-emf": ".emf",
        "image/x-wmf": ".wmf", "image/svg+xml": ".svg",
    }
    ext = ext_map.get(content_type, ".png")
    fname = f"image_{image_counter[0]:03d}{ext}"
    (image_dir / fname).write_bytes(blob)
    image_counter[0] += 1
    return fname


# ── Run-level processing (text + inline images) ─────────────────────

def _get_run_format(run_element):
    """Extract bold/italic/strike from a run's rPr."""
    rPr = run_element.find(qn("w:rPr"))
    bold = False
    italic = False
    strike = False
    if rPr is not None:
        b = rPr.find(qn("w:b"))
        bold = b is not None and b.get(qn("w:val"), "true") != "false"
        bCs = rPr.find(qn("w:bCs"))
        if not bold and bCs is not None and bCs.get(qn("w:val"), "true") != "false":
            bold = True
        i = rPr.find(qn("w:i"))
        italic = i is not None and i.get(qn("w:val"), "true") != "false"
        s = rPr.find(qn("w:strike"))
        strike = s is not None and s.get(qn("w:val"), "true") != "false"
    return bold, italic, strike


def _wrap_format(text, bold, italic, strike):
    """Wrap text with markdown formatting markers.

    Whitespace at the edges is moved outside the markers — emphasis delimiters
    adjacent to spaces do not render in strict Markdown.
    """
    if not text:
        return ""
    core = text.strip()
    if not core or not (bold or italic or strike):
        return text
    lead = text[:len(text) - len(text.lstrip())]
    trail = text[len(text.rstrip()):]
    if strike:
        core = f"~~{core}~~"
    if bold and italic:
        core = f"***{core}***"
    elif bold:
        core = f"**{core}**"
    elif italic:
        core = f"*{core}*"
    return f"{lead}{core}{trail}"


# ── OMML math → LaTeX (best effort) ─────────────────────────────────

M_NS = "http://schemas.openxmlformats.org/officeDocument/2006/math"


def _m(tag):
    return f"{{{M_NS}}}{tag}"


def _omml_children(el):
    if el is None:
        return ""
    return "".join(_omml_to_latex(c) for c in el)


def _omml_to_latex(el):
    """Convert an OMML element to LaTeX-ish text, falling back to concatenated m:t text."""
    tag = el.tag
    if tag == _m("t"):
        return el.text or ""
    if tag == _m("f"):
        return f"\\frac{{{_omml_children(el.find(_m('num')))}}}{{{_omml_children(el.find(_m('den')))}}}"
    if tag == _m("sSub"):
        return f"{_omml_children(el.find(_m('e')))}_{{{_omml_children(el.find(_m('sub')))}}}"
    if tag == _m("sSup"):
        return f"{_omml_children(el.find(_m('e')))}^{{{_omml_children(el.find(_m('sup')))}}}"
    if tag == _m("sSubSup"):
        return (f"{_omml_children(el.find(_m('e')))}"
                f"_{{{_omml_children(el.find(_m('sub')))}}}"
                f"^{{{_omml_children(el.find(_m('sup')))}}}")
    if tag == _m("rad"):
        deg = _omml_children(el.find(_m("deg")))
        body = _omml_children(el.find(_m("e")))
        return f"\\sqrt[{deg}]{{{body}}}" if deg else f"\\sqrt{{{body}}}"
    if tag == _m("acc"):
        chr_el = el.find(f"{_m('accPr')}/{_m('chr')}")
        ch = chr_el.get(_m("val")) if chr_el is not None else "̂"
        body = _omml_children(el.find(_m("e")))
        accents = {"⃗": "vec", "→": "vec", "̅": "bar", "¯": "bar",
                   "̂": "hat", "̃": "tilde", "̇": "dot"}
        name = accents.get(ch)
        return f"\\{name}{{{body}}}" if name else body
    if tag == _m("d"):
        dPr = el.find(_m("dPr"))
        beg, end = "(", ")"
        if dPr is not None:
            beg_el = dPr.find(_m("begChr"))
            end_el = dPr.find(_m("endChr"))
            if beg_el is not None:
                beg = beg_el.get(_m("val"), "")
            if end_el is not None:
                end = end_el.get(_m("val"), "")
        inner = ",".join(_omml_children(e) for e in el.findall(_m("e")))
        return f"{beg}{inner}{end}"
    if tag == _m("nary"):
        chr_el = el.find(f"{_m('naryPr')}/{_m('chr')}")
        ch = chr_el.get(_m("val")) if chr_el is not None else "∫"
        op = {"∑": "\\sum", "∏": "\\prod", "∫": "\\int"}.get(ch, ch)
        out = op
        sub = _omml_children(el.find(_m("sub")))
        sup = _omml_children(el.find(_m("sup")))
        if sub:
            out += f"_{{{sub}}}"
        if sup:
            out += f"^{{{sup}}}"
        return f"{out} {_omml_children(el.find(_m('e')))}"
    return _omml_children(el)


def _process_run(run_el, rels, image_dir, image_counter, segments):
    """Extract text and images from a single <w:r> element into segments list."""
    bold, italic, strike = _get_run_format(run_el)

    # Check for images in this run
    for blip in run_el.findall(f".//{qn('a:blip')}"):
        rid = blip.get(qn("r:embed"))
        if rid and rels:
            fname = _save_image_from_rid(rid, rels, image_dir, image_counter)
            if fname:
                segments.append(("__IMG__", fname))

    # VML images
    vml_ns = "urn:schemas-microsoft-com:vml"
    r_ns = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    for imgdata in run_el.findall(f".//{{{vml_ns}}}imagedata"):
        rid = imgdata.get(f"{{{r_ns}}}id")
        if rid and rels:
            fname = _save_image_from_rid(rid, rels, image_dir, image_counter)
            if fname:
                segments.append(("__IMG__", fname))

    # Collect text from w:t elements
    text_parts = []
    for t in run_el.findall(qn("w:t")):
        text_parts.append(t.text or "")
    text = "".join(text_parts)
    if text:
        segments.append(("__TEXT__", text, bold, italic, strike))


def _merge_segments(segments):
    """Merge adjacent text segments with same formatting to avoid **a****b** artifacts."""
    merged = []
    for seg in segments:
        if seg[0] == "__TEXT__":
            _, text, bold, italic, strike = seg
            if (merged and merged[-1][0] == "__TEXT__"
                    and merged[-1][2] == bold and merged[-1][3] == italic
                    and merged[-1][4] == strike):
                merged[-1] = ("__TEXT__", merged[-1][1] + text, bold, italic, strike)
            else:
                merged.append(seg)
        else:
            merged.append(seg)
    return merged


def _segments_to_string(segments, image_rel_path):
    parts = []
    for seg in segments:
        if seg[0] == "__IMG__":
            fname = seg[1]
            parts.append(f"\n\n![{fname}]({image_rel_path}/{fname})\n\n")
        else:
            _, text, bold, italic, strike = seg
            parts.append(_wrap_format(text, bold, italic, strike))
    return "".join(parts)


def _process_children(parent_el, rels, image_dir, image_counter, image_rel_path, segments):
    """Walk paragraph-level children in order, collecting text/image segments.

    Handles runs, hyperlinks (resolving external URLs), tracked-change and
    content-control containers, and OMML math (<m:oMath>).
    """
    for child in parent_el:
        tag = child.tag.split("}")[-1] if "}" in child.tag else child.tag

        if tag == "r":
            _process_run(child, rels, image_dir, image_counter, segments)

        elif tag == "hyperlink":
            sub = []
            _process_children(child, rels, image_dir, image_counter, image_rel_path, sub)
            url = None
            rid = child.get(qn("r:id"))
            if rid and rid in rels and getattr(rels[rid], "is_external", False):
                url = rels[rid].target_ref
            text = _segments_to_string(_merge_segments(sub), image_rel_path).strip()
            if url and text:
                segments.append(("__TEXT__", f"[{text}]({url})", False, False, False))
            elif url:
                segments.append(("__TEXT__", f"<{url}>", False, False, False))
            else:
                segments.extend(sub)

        elif tag in ("ins", "moveTo", "smartTag", "sdt"):
            target = child.find(qn("w:sdtContent")) if tag == "sdt" else child
            if target is not None:
                _process_children(target, rels, image_dir, image_counter,
                                  image_rel_path, segments)

        elif tag in ("oMath", "oMathPara"):
            latex = _omml_to_latex(child).strip()
            if latex:
                delim = "$$" if tag == "oMathPara" else "$"
                segments.append(("__TEXT__", f"{delim}{latex}{delim}", False, False, False))


def _process_paragraph_content(para_element, rels, image_dir, image_counter, image_rel_path):
    """Process a paragraph element's children in order.

    Returns a string with text, links, math, and inline image references
    placed correctly.
    """
    segments = []
    _process_children(para_element, rels, image_dir, image_counter, image_rel_path, segments)
    return _segments_to_string(_merge_segments(segments), image_rel_path).strip()


# ── List detection ───────────────────────────────────────────────────

def _get_list_info(para):
    """Return (numId, ilvl) if the paragraph is a list item, else (None, None)."""
    pPr = para._element.find(qn("w:pPr"))
    if pPr is None:
        return None, None
    numPr = pPr.find(qn("w:numPr"))
    if numPr is None:
        return None, None
    numId_el = numPr.find(qn("w:numId"))
    ilvl_el = numPr.find(qn("w:ilvl"))
    numId = int(numId_el.get(qn("w:val"), "0")) if numId_el is not None else 0
    ilvl = int(ilvl_el.get(qn("w:val"), "0")) if ilvl_el is not None else 0
    if numId == 0:
        return None, None
    return numId, ilvl


def _is_ordered_list(doc, numId):
    """Check if a numbering ID corresponds to an ordered (numbered) list."""
    try:
        numbering = doc.part.numbering_part.numbering_definitions._numbering
    except Exception:
        return False
    for num in numbering.findall(qn("w:num")):
        if num.get(qn("w:numId")) == str(numId):
            abstract_id_el = num.find(qn("w:abstractNumId"))
            if abstract_id_el is None:
                return False
            abstract_id = abstract_id_el.get(qn("w:val"))
            for abstract in numbering.findall(qn("w:abstractNum")):
                if abstract.get(qn("w:abstractNumId")) == abstract_id:
                    lvl = abstract.find(qn("w:lvl"))
                    if lvl is not None:
                        fmt = lvl.find(qn("w:numFmt"))
                        if fmt is not None:
                            val = fmt.get(qn("w:val"), "")
                            return val in ("decimal", "upperLetter", "lowerLetter",
                                           "upperRoman", "lowerRoman")
            break
    return False


# ── Table conversion ─────────────────────────────────────────────────

def _para_to_md(para, doc, rels, image_dir, image_counter, image_rel_path,
                ordered_lists_cache):
    """Convert a single paragraph to markdown, handling lists, headings, and images.

    Returns (md_string, is_list_item).
    """
    content = _process_paragraph_content(
        para._element, rels, image_dir, image_counter, image_rel_path)

    numId, ilvl = _get_list_info(para)
    if numId is not None and content:
        indent = "  " * ilvl
        if numId not in ordered_lists_cache:
            ordered_lists_cache[numId] = _is_ordered_list(doc, numId)
        if ordered_lists_cache[numId]:
            return f"{indent}1. {content}", True
        else:
            return f"{indent}- {content}", True

    return content, False


def _iter_cell_blocks(cell):
    """Yield ('p', Paragraph) and ('tbl', Table) children of a cell in document order."""
    for child in cell._tc:
        if child.tag == qn("w:p"):
            yield "p", Paragraph(child, cell)
        elif child.tag == qn("w:tbl"):
            yield "tbl", Table(child, cell)


def _cell_to_md_inline(cell, doc, rels, image_dir, image_counter, image_rel_path,
                       ordered_lists_cache):
    """Convert a table cell to inline markdown (for pipe tables).

    Nested tables are flattened: cells joined with a dash, rows with <br>.
    """
    parts = []
    for kind, block in _iter_cell_blocks(cell):
        if kind == "p":
            md, _ = _para_to_md(block, doc, rels, image_dir, image_counter,
                                image_rel_path, ordered_lists_cache)
            if md:
                parts.append(md)
            continue
        rows = []
        for row in block.rows:
            seen = set()
            row_cells = []
            for c in row.cells:
                if id(c._tc) in seen:
                    continue
                seen.add(id(c._tc))
                text = _cell_to_md_inline(c, doc, rels, image_dir, image_counter,
                                          image_rel_path, ordered_lists_cache)
                if text:
                    row_cells.append(text)
            if row_cells:
                rows.append(" – ".join(row_cells))
        if rows:
            parts.append("<br>".join(rows))
    return "<br>".join(parts)


def _cell_to_md_block(cell, doc, rels, image_dir, image_counter, image_rel_path,
                      ordered_lists_cache):
    """Convert a table cell to block markdown (for sectioned rendering).

    Handles list items with proper prefixes, regular paragraphs separated by
    blank lines, and images inline where they appear.
    """
    lines = []
    prev_was_list = False

    for kind, block in _iter_cell_blocks(cell):
        if kind == "tbl":
            tbl_md = table_to_markdown(block, doc, rels, image_dir, image_counter,
                                       image_rel_path, ordered_lists_cache)
            if tbl_md:
                if lines:
                    lines.append("")
                lines.append(tbl_md)
                prev_was_list = False
            continue

        md, is_list = _para_to_md(block, doc, rels, image_dir, image_counter,
                                  image_rel_path, ordered_lists_cache)
        if not md:
            if prev_was_list:
                prev_was_list = False
            continue

        if is_list:
            if not prev_was_list and lines:
                lines.append("")
            lines.append(md)
            prev_was_list = True
        else:
            if prev_was_list:
                lines.append("")
            elif lines:
                lines.append("")
            lines.append(md)
            prev_was_list = False

    return "\n".join(lines)


def _table_has_block_content(table, rels, image_dir, image_counter, image_rel_path):
    """Check if any cell contains images or very long content that needs block rendering."""
    for row in table.rows:
        for cell in row.cells:
            # Check for images in this cell
            blips = cell._tc.findall(f".//{qn('a:blip')}")
            if blips:
                return True
            # Check for VML images
            vml_ns = "urn:schemas-microsoft-com:vml"
            vml_imgs = cell._tc.findall(f".//{{{vml_ns}}}imagedata")
            if vml_imgs:
                return True
            # Nested tables need block rendering to stay readable
            if cell._tc.findall(f".//{qn('w:tbl')}"):
                return True
    return False


def table_to_markdown(table, doc, rels, image_dir, image_counter, image_rel_path,
                      ordered_lists_cache):
    """Convert a docx Table to markdown.

    Simple tables → pipe table.
    Tables with images → each row rendered as a labeled section so images
    appear inline where they belong.
    """
    if not table.rows:
        return ""

    has_block = _table_has_block_content(
        table, rels, image_dir, image_counter, image_rel_path)

    if has_block:
        return _table_to_sections(table, doc, rels, image_dir, image_counter,
                                  image_rel_path, ordered_lists_cache)

    # Simple pipe table
    rows = []
    for row in table.rows:
        cells = []
        seen = set()
        for cell in row.cells:
            key = id(cell._tc)
            if key in seen:
                continue
            seen.add(key)
            text = _cell_to_md_inline(cell, doc, rels, image_dir, image_counter,
                                      image_rel_path, ordered_lists_cache)
            cells.append(text.replace("|", "\\|"))
        rows.append(cells)

    if not rows:
        return ""

    max_cols = max(len(r) for r in rows)
    for r in rows:
        while len(r) < max_cols:
            r.append("")

    lines = []
    lines.append("| " + " | ".join(rows[0]) + " |")
    lines.append("| " + " | ".join("---" for _ in rows[0]) + " |")
    for row in rows[1:]:
        lines.append("| " + " | ".join(row) + " |")
    return "\n".join(lines)


def _table_to_sections(table, doc, rels, image_dir, image_counter, image_rel_path,
                       ordered_lists_cache):
    """Render a complex table as labeled sections so block content (images) is preserved."""
    output = []

    for row in table.rows:
        cells = []
        seen = set()
        for cell in row.cells:
            key = id(cell._tc)
            if key in seen:
                continue
            seen.add(key)
            cells.append(cell)

        if not cells:
            continue

        # Get content for each cell
        cell_contents = []
        for cell in cells:
            content = _cell_to_md_block(cell, doc, rels, image_dir, image_counter,
                                        image_rel_path, ordered_lists_cache)
            cell_contents.append(content.strip())

        # Skip entirely empty rows
        if all(not c for c in cell_contents):
            continue

        if len(cell_contents) == 1:
            output.append(cell_contents[0])
            output.append("")
        elif len(cell_contents) == 2:
            label = cell_contents[0]
            body = cell_contents[1]
            if label and body:
                output.append(f"**{label.strip('*')}**")
                output.append("")
                output.append(body)
                output.append("")
            elif label:
                output.append(label)
                output.append("")
            elif body:
                output.append(body)
                output.append("")
        else:
            # 3+ columns: first cell as label, rest joined
            label = cell_contents[0]
            rest = [c for c in cell_contents[1:] if c]
            body = "\n\n".join(rest)
            if label and body:
                output.append(f"**{label.strip('*')}**")
                output.append("")
                output.append(body)
                output.append("")
            elif label:
                output.append(label)
                output.append("")
            elif body:
                output.append(body)
                output.append("")

    return "\n".join(output)


# ── Main conversion ──────────────────────────────────────────────────

def _iter_block_items(doc):
    """Yield Paragraph and Table objects in document-order from the body."""
    body = doc.element.body
    for child in body:
        if child.tag == qn("w:p"):
            yield Paragraph(child, doc)
        elif child.tag == qn("w:tbl"):
            yield Table(child, doc)


def convert_docx_to_markdown(docx_path, output_path=None, image_mode="file"):
    """
    Convert a DOCX file to Markdown.

    Args:
        docx_path: Path to input .docx
        output_path: Path for output .md (default: same dir, underscored name)
        image_mode: "file" (extract to folder) or "none" (skip images)
    """
    docx_path = Path(docx_path)
    stem = normalize_filename(docx_path.stem)

    if output_path is None:
        output_path = docx_path.parent / f"{stem}.md"
    else:
        output_path = Path(output_path)

    image_dir = output_path.parent / f"{stem}_images"
    image_rel_path = f"{stem}_images"

    doc = Document(str(docx_path))

    # Relationship map for image extraction and hyperlink URL resolution
    rels = {rel.rId: rel for rel in doc.part.rels.values()}
    if image_mode == "file":
        image_dir.mkdir(parents=True, exist_ok=True)
    else:
        image_dir = None

    image_counter = [1]
    ordered_lists_cache = {}
    md_lines = []
    prev_was_list = False

    for block in _iter_block_items(doc):
        if isinstance(block, Table):
            if prev_was_list:
                md_lines.append("")
                prev_was_list = False
            md_lines.append("")
            md_lines.append(table_to_markdown(
                block, doc, rels, image_dir, image_counter, image_rel_path,
                ordered_lists_cache))
            md_lines.append("")

        elif isinstance(block, Paragraph):
            content = _process_paragraph_content(
                block._element, rels, image_dir, image_counter, image_rel_path)
            style_name = (block.style.name or "").lower() if block.style else ""

            # Headings
            if style_name.startswith("heading"):
                if prev_was_list:
                    md_lines.append("")
                    prev_was_list = False
                try:
                    level = int(style_name.replace("heading", "").strip())
                except ValueError:
                    level = 1
                level = min(level, 6)
                md_lines.append("")
                md_lines.append(f"{'#' * level} {content}")
                md_lines.append("")
                continue

            if style_name == "title":
                md_lines.append(f"# {content}")
                md_lines.append("")
                prev_was_list = False
                continue
            if style_name == "subtitle":
                md_lines.append(f"## {content}")
                md_lines.append("")
                prev_was_list = False
                continue

            # List items
            numId, ilvl = _get_list_info(block)
            if numId is not None:
                indent = "  " * ilvl
                if numId not in ordered_lists_cache:
                    ordered_lists_cache[numId] = _is_ordered_list(doc, numId)
                if ordered_lists_cache[numId]:
                    md_lines.append(f"{indent}1. {content}")
                else:
                    md_lines.append(f"{indent}- {content}")
                prev_was_list = True
                continue

            # Regular paragraph
            if prev_was_list:
                md_lines.append("")
                prev_was_list = False

            if content:
                md_lines.append(content)
                md_lines.append("")

    # Join and clean up
    md_text = "\n".join(md_lines)
    md_text = re.sub(r"\n{4,}", "\n\n\n", md_text)
    md_text = re.sub(r"[ \t]+\n", "\n", md_text)
    md_text = md_text.strip() + "\n"

    output_path.write_text(md_text, encoding="utf-8")

    # Clean up empty image directory
    if image_mode == "file" and image_dir.exists() and not any(image_dir.iterdir()):
        image_dir.rmdir()

    stats = {
        "output": str(output_path),
        "size": len(md_text),
    }
    if image_mode == "file" and image_dir.exists():
        stats["images_dir"] = str(image_dir)
        stats["images"] = len(list(image_dir.iterdir()))

    return stats


def main():
    parser = argparse.ArgumentParser(
        description="Convert DOCX documents to Markdown with images"
    )
    parser.add_argument("input", help="Input DOCX file")
    parser.add_argument("output", nargs="?",
                        help="Output markdown file (default: same name, underscored)")
    parser.add_argument(
        "--image-format",
        choices=["file", "none"],
        default="file",
        help="How to handle images: 'file' extracts to folder (default), 'none' skips",
    )

    args = parser.parse_args()

    if not Path(args.input).exists():
        print(f"Error: {args.input} not found", file=sys.stderr)
        sys.exit(1)

    stats = convert_docx_to_markdown(args.input, args.output, args.image_format)
    print(f"Converted: {stats['output']}")
    print(f"Size: {stats['size']:,} chars")
    if "images" in stats:
        print(f"Images: {stats['images']} saved to {stats['images_dir']}")


if __name__ == "__main__":
    main()
