package com.example.TextileManagement.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.TextileManagement.repository.CompanyProfileRepository;
import com.example.TextileManagement.config.CurrentCompanyContext;
import com.example.TextileManagement.config.InputLimitExceededException;
import com.example.TextileManagement.entities.CompanyProfile;
import com.example.TextileManagement.entities.Customer;
import com.example.TextileManagement.entities.Sale;
import com.example.TextileManagement.entities.TakaEntry;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class PdfDocumentService {
    private static final Color GOLD = new Color(255, 192, 0);
    private static final Color LIGHT_GOLD = new Color(255, 242, 204);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final int CHALLAN_COLUMNS = 4;
    private static final int CHALLAN_ROWS = 12;
    private static final float CHALLAN_ROW_HEIGHT = 21f;

    private final CompanyProfileRepository companyProfileRepository;
    private final CurrentCompanyContext currentCompanyContext;
    private final PrivateObjectStorageService objectStorage;
    private final int maxTakaEntries;
    private final int maxPages;

    public PdfDocumentService(CompanyProfileRepository companyProfileRepository, CurrentCompanyContext currentCompanyContext,
            PrivateObjectStorageService objectStorage,
            @Value("${app.sales.max-taka-entries:200}") int maxTakaEntries,
            @Value("${app.pdf.max-pages:20}") int maxPages) {
        this.companyProfileRepository = companyProfileRepository;
        this.currentCompanyContext = currentCompanyContext;
        this.objectStorage = objectStorage;
        this.maxTakaEntries = Math.max(1, maxTakaEntries);
        this.maxPages = Math.max(1, maxPages);
    }

    @Transactional(readOnly = true)
    public byte[] generateChallan(Sale sale) {
        List<ChallanLayoutPlanner.ChallanPage> challanGroups = validatePdfLimits(sale);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 24, 24, 20, 14);
            PdfWriter.getInstance(document, output);
            document.open();

            CompanyProfile company = company();
            for (int index = 0; index < challanGroups.size(); index++) {
                if (index > 0) {
                    document.newPage();
                }
                addTopLine(document, company);
                addBrandHeader(document, company, "DELIVERY CHALLAN");
                addCentered(document, value(company.getAddress()), bold(11), 10);
                addChallanInfo(document, sale, index, challanGroups.size());
                addTakaTable(document, challanGroups.get(index));
                addChallanTotals(document, challanGroups.get(index).entries());
                addChallanFooter(document);
            }

            document.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate challan PDF", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateBill(Sale sale) {
        validatePdfLimits(sale);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 24, 24, 20, 20);
            PdfWriter.getInstance(document, output);
            document.open();

            CompanyProfile company = company();
            addTopLine(document, company);
            addBrandHeader(document, company, "Bill");
            addCentered(document, value(company.getAddress()), bold(11), 10);
            addBillInfo(document, sale);
            addBillItems(document, sale);
            addBillTaxTable(document, sale);
            addBillFooter(document, sale);

            document.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate bill PDF", ex);
        }
    }

    private List<ChallanLayoutPlanner.ChallanPage> validatePdfLimits(Sale sale) {
        if (sale == null || sale.getTakaEntries() == null) {
            throw new InputLimitExceededException("Sale taka entries are required");
        }
        if (sale.getTakaEntries().size() > maxTakaEntries) {
            throw new InputLimitExceededException("Too many taka entries for a PDF");
        }
        List<ChallanLayoutPlanner.ChallanPage> pages = ChallanLayoutPlanner.plan(
                sale.getTakaEntries(), sale.isBalanceChallanColumnsByMeters());
        if (pages.size() > maxPages) {
            throw new InputLimitExceededException("PDF exceeds the maximum page count");
        }
        return pages;
    }

    private void addTopLine(Document document, CompanyProfile company) throws Exception {
        PdfPTable table = new PdfPTable(new float[] { 170, 260, 170 });
        table.setWidthPercentage(100);
        table.addCell(borderless("GSTIN: " + value(company.getGstNo()), bold(10), Element.ALIGN_LEFT));
        PdfPCell mantra = borderless("");
        mantra.setHorizontalAlignment(Element.ALIGN_CENTER);
        mantra.addElement(asset("Mantra.png", 180, 14));
        table.addCell(mantra);
        table.addCell(borderless("Mob: " + value(company.getPhone()), bold(10), Element.ALIGN_RIGHT));
        document.add(table);
        document.add(spacer(6));
    }

    private void addBrandHeader(Document document, CompanyProfile company, String title) throws Exception {
        PdfPTable table = new PdfPTable(new float[] { 110, 360, 110 });
        table.setWidthPercentage(100);

        PdfPCell logo = borderless("");
        logo.setRowspan(3);
        logo.setHorizontalAlignment(Element.ALIGN_CENTER);
        logo.addElement(companyLogo(company, 72, 72));
        table.addCell(logo);
        table.addCell(centeredCell(title, regular(13), 0));
        PdfPCell bappa = borderless("");
        bappa.setRowspan(3);
        bappa.setHorizontalAlignment(Element.ALIGN_CENTER);
        bappa.addElement(asset("Bappa.png", 86, 86));
        table.addCell(bappa);

        PdfPCell trade = centeredCell(value(company.getTradeName()), bold(34, GOLD), 0);
        table.addCell(trade);
        table.addCell(centeredCell("Mfg & Dealers In: ARTSILK CLOTH", regular(11), 0));
        document.add(table);
        document.add(spacer(6));
    }

    private void addChallanInfo(Document document, Sale sale, int challanIndex, int totalChallans) throws Exception {
        Customer customer = sale.getCustomer();
        PdfPTable table = new PdfPTable(new float[] { 420, 160 });
        table.setWidthPercentage(100);
        addInfoRow(table, "M/s: " + value(customer.getName()), "Challan No:");
        addInfoRow(table, "Address: " + value(customer.getAddress()), String.valueOf(challanNumberForSegment(sale, challanIndex)));
        addInfoRow(table, "Broker: " + value(sale.getBrokerName()), "");
        addInfoRow(table, "Quality: " + value(sale.getQuality()), "Date: " + DATE_FORMAT.format(sale.getSaleDate()));
        if (totalChallans > 1) {
            addInfoRow(table, "Bill No: " + value(String.valueOf(sale.getBillNo())), "Page " + (challanIndex + 1) + " of " + totalChallans);
        }
        document.add(table);
        document.add(spacer(4));
    }

    private void addTakaTable(Document document, ChallanLayoutPlanner.ChallanPage challanPage) throws Exception {
        BigDecimal[] groupTotals = new BigDecimal[CHALLAN_COLUMNS];
        for (int group = 0; group < CHALLAN_COLUMNS; group++) {
            groupTotals[group] = challanPage.columns().get(group).stream()
                    .map(taka -> safe(taka.getMeters()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        PdfPTable table = new PdfPTable(CHALLAN_COLUMNS * 3);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 30, 55, 60, 30, 55, 60, 30, 55, 60, 30, 55, 60 });
        for (int g = 0; g < CHALLAN_COLUMNS; g++) {
            table.addCell(headerCell("No"));
            table.addCell(headerCell("Taka No"));
            table.addCell(headerCell("Meters"));
        }
        for (int row = 0; row < CHALLAN_ROWS; row++) {
            for (int group = 0; group < CHALLAN_COLUMNS; group++) {
                List<TakaEntry> column = challanPage.columns().get(group);
                TakaEntry taka = row < column.size() ? column.get(row) : null;
                table.addCell(fixedGridCell(String.valueOf(group * CHALLAN_ROWS + row + 1), regular(11), Element.ALIGN_CENTER, Color.WHITE));
                table.addCell(fixedGridCell(taka == null ? "" : String.valueOf(taka.getTakaNo()), regular(11), Element.ALIGN_CENTER, Color.WHITE));
                table.addCell(fixedGridCell(taka == null ? "" : money(taka.getMeters()), regular(11), Element.ALIGN_CENTER, Color.WHITE));
            }
        }
        for (int g = 0; g < CHALLAN_COLUMNS; g++) {
            PdfPCell total = gridCell("TOTAL", bold(11), Element.ALIGN_CENTER, LIGHT_GOLD);
            total.setColspan(2);
            table.addCell(total);
            table.addCell(gridCell(money(groupTotals[g]), bold(11), Element.ALIGN_CENTER, LIGHT_GOLD));
        }
        document.add(table);
        document.add(spacer(4));
    }

    private void addChallanTotals(Document document, List<TakaEntry> entries) throws Exception {
        BigDecimal totalMeters = entries.stream().map(taka -> safe(taka.getMeters()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PdfPTable table = new PdfPTable(new float[] { 120, 140, 120, 140 });
        table.setWidthPercentage(92);
        table.addCell(borderless("Total Pieces", bold(11), Element.ALIGN_LEFT));
        table.addCell(borderless(String.valueOf(entries.size()), bold(11), Element.ALIGN_LEFT));
        table.addCell(borderless("Total Meters", bold(11), Element.ALIGN_LEFT));
        table.addCell(borderless(money(totalMeters), bold(11), Element.ALIGN_LEFT));
        document.add(table);
        document.add(spacer(6));
    }

    private void addChallanFooter(Document document) throws Exception {
        PdfPTable table = new PdfPTable(new float[] { 120, 140, 120, 140 });
        table.setWidthPercentage(92);
        table.addCell(borderless("Prepared By", bold(11), Element.ALIGN_LEFT));
        table.addCell(borderless("", bold(11), Element.ALIGN_LEFT));
        table.addCell(borderless("Received By", bold(11), Element.ALIGN_LEFT));
        table.addCell(borderless("", bold(11), Element.ALIGN_LEFT));
        document.add(table);
        addLeft(document, "Subject to Surat Jurisdiction", regular(8, Color.GRAY), 0);
    }

    private void addBillInfo(Document document, Sale sale) throws Exception {
        Customer customer = sale.getCustomer();
        PdfPTable table = new PdfPTable(new float[] { 410, 80, 80 });
        table.setWidthPercentage(100);
        table.addCell(gridCell("M/s: " + value(customer.getName()), bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("Bill No:", bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell(String.valueOf(sale.getBillNo()), bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("Address: " + value(customer.getAddress()), bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("Challan No:", bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell(challanLabel(sale), bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("GST No: " + value(customer.getGstNo()), bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("Date:", bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell(DATE_FORMAT.format(sale.getSaleDate()), bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("Broker: " + value(sale.getBrokerName()), bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("", bold(12), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("", bold(12), Element.ALIGN_LEFT, Color.WHITE));
        document.add(table);
        document.add(spacer(14));
    }

    private void addBillItems(Document document, Sale sale) throws Exception {
        PdfPTable table = new PdfPTable(new float[] { 200, 60, 90, 90, 130 });
        table.setWidthPercentage(100);
        for (String header : List.of("Particulars", "Pieces", "Meters", "Rate per Meter", "Amount")) {
            table.addCell(headerCell(header));
        }
        table.addCell(gridCell(value(sale.getQuality()), bold(11), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell(String.valueOf(sale.getTakaEntries().size()), bold(11), Element.ALIGN_CENTER, Color.WHITE));
        table.addCell(gridCell(money(sale.getTotalMeters()), bold(11), Element.ALIGN_CENTER, Color.WHITE));
        table.addCell(gridCell(money(sale.getRate()) + "rs", bold(11), Element.ALIGN_CENTER, Color.WHITE));
        table.addCell(gridCell(money(sale.getAmount()) + "rs", bold(11), Element.ALIGN_CENTER, Color.WHITE));
        document.add(table);
    }

    private void addBillTaxTable(Document document, Sale sale) throws Exception {
        BigDecimal amount = safeMoney(sale.getAmount());
        BigDecimal sgst = amount.multiply(new BigDecimal("0.025")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cgst = amount.multiply(new BigDecimal("0.025")).setScale(2, RoundingMode.HALF_UP);
        long grandTotal = amount.add(sgst).add(cgst).setScale(0, RoundingMode.HALF_UP).longValue();
        String delivery = value(sale.getCustomer().getDeliveryAddress());
        if (delivery.isBlank()) {
            delivery = value(sale.getCustomer().getAddress());
        }

        PdfPTable table = new PdfPTable(new float[] { 260, 160, 150 });
        table.setWidthPercentage(100);
        PdfPCell deliveryCell = gridCell("Delivery: " + delivery, bold(10), Element.ALIGN_LEFT, Color.WHITE);
        deliveryCell.setRowspan(2);
        table.addCell(deliveryCell);
        table.addCell(gridCell("Sub Total", bold(11), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell(money(amount) + "rs", bold(11), Element.ALIGN_RIGHT, Color.WHITE));
        table.addCell(gridCell("SGST @ 2.5%", bold(11), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell(money(sgst) + "rs", bold(11), Element.ALIGN_RIGHT, Color.WHITE));
        table.addCell(gridCell("Payment within 45 Days", bold(11), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("CGST @ 2.5%", bold(11), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell(money(cgst) + "rs", bold(11), Element.ALIGN_RIGHT, Color.WHITE));
        table.addCell(gridCell("Due Date: " + DATE_FORMAT.format(sale.getDueDate()), bold(11), Element.ALIGN_LEFT, Color.WHITE));
        table.addCell(gridCell("TOTAL", bold(11), Element.ALIGN_LEFT, LIGHT_GOLD));
        table.addCell(gridCell(grandTotal + "rs", bold(11), Element.ALIGN_RIGHT, LIGHT_GOLD));
        document.add(table);
        document.add(spacer(18));
    }

    private void addBillFooter(Document document, Sale sale) throws Exception {
        addLeft(document,
                "Terms of Sale:\n1. Payments to be made by payee's A/C. Cheque or Draft.\n"
                        + "2. Any complaint for goods should be made within 7 days after which no complaint will be entertained.\n"
                        + "3. Interest @24% per month will be charged after due date of bill.\n"
                        + "4. We are not responsible for any loss or damage during transit.",
                regular(9), 6);
        PdfPTable signature = new PdfPTable(new float[] { 200, 140, 200 });
        signature.setWidthPercentage(92);
        signature.addCell(borderless("Receiver's Signature", bold(11), Element.ALIGN_LEFT));
        signature.addCell(borderless("", bold(11), Element.ALIGN_LEFT));
        signature.addCell(borderless("For, " + value(company().getTradeName()), bold(11), Element.ALIGN_RIGHT));
        document.add(signature);
        addCentered(document, "Subject to Surat Jurisdiction", regular(9, Color.GRAY), 0);
    }

    private void addInfoRow(PdfPTable table, String left, String right) {
        PdfPCell leftCell = gridCell(left, bold(13), Element.ALIGN_LEFT, Color.WHITE);
        leftCell.setPadding(3);
        table.addCell(leftCell);
        PdfPCell rightCell = gridCell(right, bold(right.matches("\\d+") ? 18 : 13), Element.ALIGN_CENTER, Color.WHITE);
        rightCell.setPadding(3);
        table.addCell(rightCell);
    }

    private PdfPCell headerCell(String text) {
        return gridCell(text, bold(11, Color.WHITE), Element.ALIGN_CENTER, GOLD);
    }

    private PdfPCell gridCell(String text, Font font, int align, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(value(text), font));
        cell.setBorderColor(GOLD);
        cell.setBorderWidth(1f);
        cell.setBackgroundColor(background);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }

    private PdfPCell fixedGridCell(String text, Font font, int align, Color background) {
        PdfPCell cell = gridCell(text, font, align, background);
        cell.setFixedHeight(CHALLAN_ROW_HEIGHT);
        return cell;
    }

    private PdfPCell centeredCell(String text, Font font, int border) {
        PdfPCell cell = new PdfPCell(new Phrase(value(text), font));
        cell.setBorder(border);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private PdfPCell borderless(String text) {
        return borderless(text, regular(10), Element.ALIGN_LEFT);
    }

    private PdfPCell borderless(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(value(text), font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private Image asset(String name, float width, float height) throws Exception {
        Image image = Image.getInstance(new ClassPathResource("pdf-assets/" + name).getContentAsByteArray());
        image.scaleToFit(width, height);
        image.setAlignment(Image.ALIGN_CENTER);
        return image;
    }

    private Image companyLogo(CompanyProfile company, float width, float height) throws Exception {
        byte[] logoData = company.getLogoData();
        if (logoData == null && company.getLogoStorageKey() != null && objectStorage.isEnabled()) {
            logoData = objectStorage.read(company.getLogoStorageKey());
        }
        Image image = logoData == null
                ? Image.getInstance(new ClassPathResource("pdf-assets/" + companyLogoAsset(company)).getContentAsByteArray())
                : Image.getInstance(logoData);
        image.scaleToFit(width, height);
        image.setAlignment(Image.ALIGN_CENTER);
        return image;
    }

    private String companyLogoAsset(CompanyProfile company) {
        String tradeName = company == null ? "" : value(company.getTradeName()).trim().toLowerCase();
        if (tradeName.contains("ritika")) {
            return "Ritika Creation.jpeg";
        }
        return "DevashishTextileLogo.png";
    }

    private Paragraph spacer(int height) {
        Paragraph paragraph = new Paragraph(" ");
        paragraph.setSpacingAfter(height);
        return paragraph;
    }

    private void addCentered(Document document, String text, Font font, int spacingAfter) throws Exception {
        Paragraph paragraph = new Paragraph(value(text), font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(spacingAfter);
        document.add(paragraph);
    }

    private void addLeft(Document document, String text, Font font, int spacingAfter) throws Exception {
        Paragraph paragraph = new Paragraph(value(text), font);
        paragraph.setAlignment(Element.ALIGN_LEFT);
        paragraph.setSpacingAfter(spacingAfter);
        document.add(paragraph);
    }

    private Font regular(int size) {
        return regular(size, Color.BLACK);
    }

    private Font regular(int size, Color color) {
        return new Font(Font.HELVETICA, size, Font.NORMAL, color);
    }

    private Font bold(int size) {
        return bold(size, Color.BLACK);
    }

    private Font bold(int size, Color color) {
        return new Font(Font.HELVETICA, size, Font.BOLD, color);
    }

    private CompanyProfile company() {
        Long companyId = currentCompanyContext.getCompanyId();
        if (companyId != null) {
            return companyProfileRepository.findById(companyId).orElseGet(this::defaultCompany);
        }
        return companyProfileRepository.findFirstByOrderByIdAsc().orElseGet(this::defaultCompany);
    }

    private CompanyProfile defaultCompany() {
        CompanyProfile profile = new CompanyProfile();
        profile.setTradeName("Devashish Textile");
        profile.setPhone("+91 95121 51000");
        profile.setDefaultQuality("ARTSILK CLOTH");
        return profile;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    public String buildDownloadName(String type, Sale sale) {
        String date = sale.getSaleDate() == null ? "unknown-date" : FILE_DATE_FORMAT.format(sale.getSaleDate());
        String number = "challan".equalsIgnoreCase(type)
                ? challanDownloadLabel(sale)
                : String.valueOf(sale.getBillNo() == null ? sale.getChallanNo() : sale.getBillNo());
        return type + "-" + date + "-" + number + ".pdf";
    }

    private int challanNumberForSegment(Sale sale, int segmentIndex) {
        return safeInt(sale.getChallanNo()) + segmentIndex;
    }

    private String challanLabel(Sale sale) {
        int start = safeInt(sale.getChallanNo());
        int count = Math.max(1, safeInt(sale.getChallanCount()));
        if (count == 1) {
            return String.valueOf(start);
        }
        return start + " to " + (start + count - 1);
    }

    private String challanDownloadLabel(Sale sale) {
        return challanLabel(sale).replace(" ", "");
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
