from reportlab.platypus import Image, Paragraph, Table, TableStyle, Spacer
from flask import Flask, request, send_file
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_RIGHT, TA_LEFT
from reportlab.lib import colors
from reportlab.lib.units import inch
from io import BytesIO

import datetime
import math
app = Flask(__name__)

# Styles
header_style = ParagraphStyle("header", fontSize=14,alignment=TA_CENTER, leading=14, spaceAfter=2, textColor=colors.black,fontName='Helvetica-Bold')
company_style = ParagraphStyle("company", fontSize=38, alignment=TA_CENTER, leading=16, textColor=colors.HexColor("#FFC000"),fontName='Helvetica-Bold')
small_style = ParagraphStyle("small", fontSize=12, alignment=TA_CENTER, leading=12,fontName='Helvetica')

def generate_challan_pdf(data):
    buffer = BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        leftMargin=24,
        rightMargin=24,
        topMargin=20,
        bottomMargin=20
    )

    elements = []

    # ---------- TOP HEADER ----------
    
    JaiMataji=Image("Mantra.png",2.5*inch,0.2*inch)

    gst_table = Table(
        [[f"GSTIN: {data.get("gst_no","")}", JaiMataji,"Mob: +919512151000"]],
        colWidths=[170, 260,170]
    )
    gst_table.setStyle(TableStyle([
        ("ALIGN", (0, 0), (0, 0), "LEFT"),
        ("ALIGN",(1,0),(1,0),"CENTER"),
        ("ALIGN", (2, 0), (2, 0), "RIGHT"),
        ("FONTSIZE", (0, 0), (-1, -1), 10),
        ("TEXTCOLOR", (0, 0), (-1, -1), colors.black),
        ("FONT", (0,0), (-1, -1), "Helvetica-Bold")
    ]))
    elements.append(gst_table)
    elements.append(Spacer(1, 8))

    # ---------- TITLE ----------
    
    challan=Paragraph(
            "<u><b>DELIVERY CHALLAN</b></u>",
            ParagraphStyle(
                "title",
                fontSize=13,
                alignment=TA_CENTER,
                textColor=colors.black
            )
        )
    if data.get("trade_name","")=="Devashish Textile":
        logo = Image("logo.png", 1 * inch, 1 * inch)
    else:
        logo = Image("logo1.png", 1 * inch, 1 * inch)

    bappa = Image("Bappa.png", 1.2 * inch, 1.2 * inch)

    TradeName = [
        Paragraph(f"<b><b>{data.get("trade_name","")}</b></b>", company_style),
        Paragraph(
            "Mfg & Dealers In: ARTSILK CLOTH",
            ParagraphStyle(
                "sub", 
                fontSize=11, 
                alignment=TA_CENTER,
                fontName='Helvetica',
                spaceAfter=4))
    ]
    table_data = [
        [logo, challan, bappa],
        ["", TradeName[0], ""],
        ["",TradeName[1],""]
    ]

    LogoTable = Table(
        table_data,
        colWidths=[110, 360, 110],
        rowHeights=[5, 45,25]
    )

    LogoTable.setStyle(TableStyle([
        # Row spans for images
        ("SPAN", (0,0), (0,2)),  # Logo spans 2 rows
        ("SPAN", (2,0), (2,2)),  # Bappa spans 2 rows

        # Alignment
        ("ALIGN", (0,0), (-1,-1), "CENTER"),
        ("VALIGN", (0,0), (-1,-1), "MIDDLE"),
        ("VALIGN", (1,0), (1,0), "BOTTOM"),
        ("VALIGN", (1,1), (1,1), "TOP"),
        ("VALIGN", (1,2), (1,2), "BOTTOM"),
        ("ROWHEIGHT",(0,0),(-1,-1),150)

        # Padding
        # ("TOPPADDING", (0,0), (-1,-1), 6),
        # ("BOTTOMPADDING", (0,0), (-1,-1), 6),
    ]))

    elements.append(LogoTable)
    elements.append(Spacer(1, 6))
    elements.append(
        Paragraph(
            f"<b>{data.get("self_add","")}</b>",
            ParagraphStyle("addr", fontSize=11, alignment=TA_CENTER)
        )
    )
    elements.append(Spacer(1, 12))

    # ---------- PARTY / CHALLAN INFO ----------
    challan_label_style = ParagraphStyle(
        "challan_label",
        fontSize=13,
        leading=13,
        alignment=TA_CENTER,
        fontName="Helvetica-Bold"
    )

    challan_value_style = ParagraphStyle(
        "challan_value",
        fontSize=18,
        leading=20,
        fontName="Helvetica-Bold",
        textColor=colors.black,
        spaceAfter=8,
        alignment=TA_CENTER
    )

    left_col = [
        f"M/s: {data.get('customer', '')}",
        f"Address: {data.get('address', '')}",
        f"Broker: {data.get('broker', '')}",
        f"Quality: {data.get('quality', '')}",
    ]

    challan_no_block = [
        Paragraph("Challan No:", challan_label_style),
        Paragraph(str(data.get("challan_no", "")), challan_value_style)
    ]

    right_col = [
        challan_no_block[0],
        challan_no_block[1],
        "",                
        Paragraph(f"Date: {data.get('date', str(datetime.date.today()))}", header_style)
    ]

    info_rows = [
        [left_col[0], right_col[0]],
        [left_col[1], right_col[1]],
        [left_col[2], right_col[2]],
        [left_col[3], right_col[3]],
    ]
    
    info_table = Table(info_rows, colWidths=[420, 160], rowHeights=[30, 30, 30, 30])

    info_table.setStyle(TableStyle([
        ("GRID", (0,0), (-1,-1), 2, colors.HexColor("#FFC000")),
        # Row span for Challan No value
        ("SPAN", (1,1), (1,2)),
        ("VALIGN", (0,0), (1,3), "MIDDLE"),
        ("LEFTPADDING", (0,0), (-1,-1), 8),
        ("RIGHTPADDING", (0,0), (-1,-1), 8),
        ("FONTSIZE", (0,0), (-1,-1), 14),
        ("BACKGROUND", (0,0), (-1,0), colors.white),
        ("ROUNDEDCORNERS",[15,15,15,15])
    ]))

    elements.append(info_table)
    elements.append(Spacer(1, 14))

    # ---------- TAKA TABLE ----------
    takas = data.get("takas", [])
    num_groups = 4
    rows_needed = math.ceil(len(takas) / num_groups)

    # Columns: [No, Taka No, Meters] × 4
    cols = [[] for _ in range(num_groups * 3)]
    meter_totals = [0.0] * num_groups  # store totals per group

    for idx, t in enumerate(takas):
        group = idx // rows_needed
        cols[group * 3].append(str(idx + 1))
        cols[group * 3 + 1].append(str(t["taka_no"]))
        cols[group * 3 + 2].append(f'{t["meters"]:.2f}')
        meter_totals[group] += float(t["meters"])

    # Normalize column lengths
    max_len = max(len(c) for c in cols)
    for c in cols:
        while len(c) < max_len:
            c.append("")

    # Header
    header = ["No", "Taka No", "Meters"] * num_groups
    rows = [header]

    # Data rows
    for r in range(max_len):
        rows.append([cols[c][r] for c in range(num_groups * 3)])

    # ---- TOTAL ROW ----
    total_row = []
    for g in range(num_groups):
        total_row.extend(["TOTAL", "", f"{meter_totals[g]:.2f}"])

    rows.append(total_row)

    # Table
    taka_table = Table(
        rows,
        colWidths=[30, 55, 60] * num_groups,
        rowHeights=22
    )

    taka_table.setStyle(TableStyle([
        ("GRID", (0, 0), (-1, -1), 1, colors.HexColor("#FFC000")),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#FFC000")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),

        # TOTAL ROW STYLE
        ("BACKGROUND", (0, -1), (-1, -1), colors.HexColor("#FFF2CC")),
        ("FONT", (0, -1), (-1, -1), "Helvetica-Bold"),

        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("FONTSIZE", (0, 0), (-1, -1), 12),

        ("ROWBACKGROUNDS", (0, 1), (-1, -2),
        [colors.whitesmoke, colors.transparent]),

        ("FONT", (0, 0), (-1, -1), "Helvetica-Bold"),
        ("SPAN", (0,-1), (1,-1)),
        ("SPAN", (3,-1), (4,-1)),
        ("SPAN", (6,-1), (7,-1)),
        ("SPAN", (9,-1), (10,-1))
    ]))

    elements.append(taka_table)
    elements.append(Spacer(1, 12))

    # ---------- TOTALS ---------- 
    total_metres=sum(meter_totals)
    total_pieces=len(takas)
    totals_table = Table( [["Total Pieces", total_pieces, "Total Meters", total_metres]], colWidths=[120, 140, 120, 140] ) 
    totals_table.setStyle(TableStyle([  
        ("BACKGROUND", (0, 0), (-1, -1), colors.white), 
        ("FONTSIZE", (0, 0), (-1, -1), 11), 
        ("FONT", (0, 0), (-1, -1), "Helvetica-Bold") ])) 
    elements.append(totals_table) 
    elements.append(Spacer(1, 18))

    # ---------- FOOTER ----------
    footer_table = Table(
        [["Prepared By", "", "Received By", ""]],
        colWidths=[120, 140, 120, 140]
    )
    footer_table.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("FONTSIZE", (0, 0), (-1, -1), 12),
        ("FONT", (0, 0), (-1, -1), "Helvetica-Bold")
    ]))
    elements.append(footer_table)

    elements.append(Spacer(1, 6))
    elements.append(
        Paragraph("Subject to Surat Jurisdiction",
                  ParagraphStyle("jur", fontSize=9, alignment=TA_LEFT, textColor=colors.grey))
    )

    doc.build(elements)
    buffer.seek(0)
    return buffer

def generate_bill_pdf(data):
    buffer = BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        leftMargin=24,
        rightMargin=24,
        topMargin=20,
        bottomMargin=20
    )

    elements = []

    # ---------- TOP HEADER ----------
    JaiMataji = Image("Mantra.png", 2.5 * inch, 0.2 * inch)
    wrap_style = ParagraphStyle(
        "wrap",
        fontSize=11,
        leading=16,
        fontName="Helvetica",
        spaceAfter=2
    )

    gst_table = Table(
        [[f"GSTIN: {data.get("gst_no","")}", JaiMataji, "Mob: +91 95121 51000"]],
        colWidths=[170, 260, 170]
    )

    gst_table.setStyle(TableStyle([
        ("ALIGN", (0, 0), (0, 0), "LEFT"),
        ("ALIGN",(1,0),(1,0),"CENTER"),
        ("ALIGN", (2, 0), (2, 0), "RIGHT"),
        ("FONTSIZE", (0, 0), (-1, -1), 10),
        ("TEXTCOLOR", (0, 0), (-1, -1), colors.black),
        ("FONT", (0,0), (-1, -1), "Helvetica-Bold")
    ]))
    elements.append(gst_table)
    elements.append(Spacer(1, 6))

    # ---------- TITLE + LOGO ----------
    logo = Image("logo.png", 1 * inch, 1 * inch)
    bappa = Image("Bappa.png", 1.2 * inch, 1.2 * inch)

    bill_title=Paragraph(
            "<u><b>Bill</b></u>",
            ParagraphStyle(
                "title",
                fontSize=13,
                alignment=TA_CENTER,
                textColor=colors.black
            )
        )

    trade_name = Paragraph(
        f"<b>{data.get('trade_name','')}</b>",company_style)

    subtitle =Paragraph(
        "Mfg & Dealers In: ARTSILK CLOTH",
            ParagraphStyle(
                "sub", 
                fontSize=11, 
                alignment=TA_CENTER,
                fontName='Helvetica',
                spaceAfter=4))
    

    header_table = Table(
        [
            [logo, bill_title, bappa],
            ["", trade_name, ""],
            ["", subtitle, ""]
        ],
        colWidths=[110, 360, 110],
        rowHeights=[5, 45,25]
    )

    header_table.setStyle(TableStyle([
        ("SPAN", (0,0), (0,2)),  # Logo spans 2 rows
        ("SPAN", (2,0), (2,2)),  # Bappa spans 2 rows

        # Alignment
        ("ALIGN", (0,0), (-1,-1), "CENTER"),
        ("VALIGN", (0,0), (-1,-1), "MIDDLE"),
        ("VALIGN", (1,0), (1,0), "BOTTOM"),
        ("VALIGN", (1,1), (1,1), "TOP"),
        ("VALIGN", (1,2), (1,2), "BOTTOM"),
        ("ROWHEIGHT",(0,0),(-1,-1),150)
    ]))

    elements.append(header_table)
    elements.append(Spacer(1, 6))

    elements.append(
        Paragraph(
            f"<b>{data.get("self_add","")}</b>",
            ParagraphStyle("addr", fontSize=11, alignment=TA_CENTER)
        )
    )
    elements.append(Spacer(1, 12))

    # ---------- PARTY / BILL INFO ----------
    info_table = Table(
        [
            [Paragraph(f"M/s: {data.get('customer','')}"), "Bill No:", data.get("bill_no","")],
            [Paragraph(f"Address: {data.get('address','')}"), "Challan No:", data.get("challan_no","")],
            [Paragraph(f"GST No: {data.get('cust_gst_no','')}"),"Date:", data.get("date","")],
            [Paragraph(f"Broker:{data.get("broker","")}"),"",""],
        ],
        colWidths=[410,80,80],
        rowHeights=28
    )

    info_table.setStyle(TableStyle([
        ("GRID", (0,0), (-1,-1), 1.5, colors.HexColor("#FFC000")),
        ("FONT", (0,0), (-1,-1), "Helvetica-Bold"),
        ("FONTSIZE", (0,0), (-1,-1), 12),
        ("VALIGN", (0,0), (-1,-1), "MIDDLE"),
    ]))

    elements.append(info_table)
    elements.append(Spacer(1, 14))

    # ---------- BILL ITEMS ----------
    pieces= len(data["takas"])
    meters = sum(float(t["meters"]) for t in data.get("takas", []))
    rate = data["rate"]
    amount = meters * rate

    bill_items = Table(
        [
            ["Particulars","Pieces", "Meters", "Rate per Meter", "Amount"],
            [Paragraph(data["quality"],wrap_style), f"{pieces}",f"{meters:.2f}", f"{rate:.2f}rs", f"{amount:.2f}rs"],
        ],
        colWidths=[200,60, 90, 90, 130]
    )

    bill_items.setStyle(TableStyle([
        ("GRID", (0,0), (-1,-1), 1, colors.HexColor("#FFC000")),
        ("BACKGROUND", (0,0), (-1,0), colors.HexColor("#FFC000")),
        ("TEXTCOLOR", (0,0), (-1,0), colors.white),
        ("ALIGN", (1,1), (-1,-1), "CENTER"),
        ("FONT", (0,0), (-1,-1), "Helvetica-Bold"),
        ("VALIGN",(0,0),(-1,-1),"MIDDLE")
    ]))

    elements.append(bill_items)

    # ---------- TAXES ----------
    sgst = amount * 0.025
    cgst = amount * 0.025
    grand_total = round(amount + sgst + cgst)

    tax_table = Table(
        [
            [Paragraph(f"<b>Delivery:</b>{data.get("delivery_add")}",ParagraphStyle("delivery",leading=16,fontName="Helvetica",spaceAfter=2)), "Sub Total", f"{amount:.2f}rs"],
            ["", "SGST @ 2.5%", f"{sgst:.2f}rs"],
            ["Payment within 45 Days", "CGST @ 2.5%", f"{cgst:.2f}rs"],
            ["Due Date:", "TOTAL", f"{grand_total:.2f}rs"],
        ],
        colWidths=[260, 160, 150],
        # rowHeights=26
    )

    tax_table.setStyle(TableStyle([
        ("GRID", (0,0), (-1,-1), 1, colors.HexColor("#FFC000")),
        ("FONT", (0,0), (-1,-1), "Helvetica-Bold"),
        ("ALIGN", (2,0), (2,-1), "RIGHT"),
        ("BACKGROUND", (1,-1), (-1,-1), colors.HexColor("#FFF2CC")),
        ("SPAN",(0,0),(0,1))
    ]))

    elements.append(tax_table)
    elements.append(Spacer(1, 18))

    # ---------- FOOTER ----------
    terms_style = ParagraphStyle(
        "terms",
        fontSize=9,
        leading=12,
        alignment=TA_LEFT,
        fontName="Helvetica"
    )

    eo_style = ParagraphStyle(
        "eo",
        fontSize=9,
        alignment=TA_RIGHT,
        fontName="Helvetica-Bold"
    )

    terms_text = """
    <b>Terms of Sale:</b><br/>
    1. Payments to be made by payee's A/C. Cheque or Draft.<br/>
    2. Any complaint for goods should be made within 7 days after which no complaint will be entertained.<br/>
    3. Interest @24% per month will be charged after due date of bill.<br/>
    4. We are not responsible for any loss or damage during transit.
    """

    terms_para = Paragraph(terms_text, terms_style)
    eo_para = Paragraph("E. &amp; O. E", eo_style)

    # --- TERMS TABLE (2 columns)
    terms_table = Table(
        [[terms_para, eo_para]],
        colWidths=[430, 150]
    )

    terms_table.setStyle(TableStyle([
        ("VALIGN", (0,0), (-1,-1), "TOP"),
        ("LEFTPADDING", (0,0), (-1,-1), 4),
        ("RIGHTPADDING", (0,0), (-1,-1), 4),
    ]))

    elements.append(terms_table)
    elements.append(Spacer(1, 10))

    # --- SIGNATURE TABLE
    signature_table = Table(
        [["Receiver's Signature", "", f"For, {data.get('trade_name', '')}"]],
        colWidths=[200, 140, 200],
        rowHeights=40
    )

    signature_table.setStyle(TableStyle([
        ("VALIGN", (0,0), (-1,-1), "BOTTOM"),
        ("FONT", (0,0), (-1,-1), "Helvetica-Bold"),
    ]))

    elements.append(signature_table)
    elements.append(Spacer(1, 10))
    elements.append(
        Paragraph("Subject to Surat Jurisdiction",
                  ParagraphStyle("jur", fontSize=9, alignment=TA_CENTER, textColor=colors.grey))
    )
    doc.build(elements)
    buffer.seek(0)
    return buffer

@app.route("/challan", methods=["POST"])
def challan_endpoint():
    data = request.json
    print(data)
    pdf_buffer = generate_challan_pdf(data)
    return send_file(pdf_buffer, as_attachment=True, download_name="challan.pdf", mimetype="application/pdf")

@app.route("/bill", methods=["POST"])
def bill_endpoint():
    data = request.json
    print(data)
    pdf_buffer = generate_bill_pdf(data)
    return send_file(pdf_buffer, as_attachment=True, download_name="bill.pdf", mimetype="application/pdf")

if __name__ == "__main__":
    app.run(debug=True)
