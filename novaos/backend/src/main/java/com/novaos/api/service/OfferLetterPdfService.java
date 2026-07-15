package com.novaos.api.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;

@Service
public class OfferLetterPdfService {
    public byte[] generate(Map<String, Object> request) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 54, 54, 54, 54);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageNumbers());
            doc.open();

            // Font configurations
            Font fontBrand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new BaseColor(15, 23, 42)); // Slate 900
            Font fontSubBrand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new BaseColor(99, 102, 241)); // Indigo 500
            Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new BaseColor(30, 41, 59)); // Slate 800
            Font fontSectionHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new BaseColor(30, 41, 59)); // Slate 800
            Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new BaseColor(51, 65, 85)); // Slate 700
            Font fontBodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new BaseColor(30, 41, 59)); // Slate 800
            Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new BaseColor(71, 85, 105)); // Slate 600
            Font fontValue = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new BaseColor(15, 23, 42)); // Slate 900
            Font fontHeaderRight = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new BaseColor(100, 116, 139)); // Slate 500
            Font fontSectionBody = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new BaseColor(71, 85, 105)); // Slate 600

            // Header Section Table
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{55f, 45f});

            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

            // Nested table for Logo icon and Brand text
            PdfPTable logoTable = new PdfPTable(2);
            logoTable.setWidths(new float[]{15f, 85f});
            logoTable.setWidthPercentage(100);

            PdfPCell logoIconCell = new PdfPCell();
            logoIconCell.setBorder(Rectangle.NO_BORDER);
            logoIconCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

            PdfPTable iconBlock = new PdfPTable(1);
            iconBlock.setWidthPercentage(100);
            PdfPCell blockCell = new PdfPCell(new Phrase("N", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.WHITE)));
            blockCell.setBackgroundColor(new BaseColor(79, 70, 229)); // Indigo 600
            blockCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            blockCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            blockCell.setPaddingTop(4);
            blockCell.setPaddingBottom(6);
            blockCell.setBorder(Rectangle.NO_BORDER);
            iconBlock.addCell(blockCell);
            logoIconCell.addElement(iconBlock);
            logoTable.addCell(logoIconCell);

            PdfPCell brandTextCell = new PdfPCell();
            brandTextCell.setBorder(Rectangle.NO_BORDER);
            brandTextCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            brandTextCell.setPaddingLeft(6);

            Paragraph brandName = new Paragraph("NOVAOS COPILOT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new BaseColor(15, 23, 42)));
            brandTextCell.addElement(brandName);
            Paragraph subBrand = new Paragraph("NOVAOS ENTERPRISE SUITE", fontSubBrand);
            brandTextCell.addElement(subBrand);
            logoTable.addCell(brandTextCell);

            leftCell.addElement(logoTable);
            headerTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            rightCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

            Paragraph infoText = new Paragraph();
            infoText.setAlignment(Element.ALIGN_RIGHT);
            infoText.setLeading(11);
            infoText.setFont(fontHeaderRight);
            infoText.add(new Chunk("100 Innovation Way, Tech District\n"));
            infoText.add(new Chunk("Bangalore, KA 560001, India\n"));
            infoText.add(new Chunk("hr@novaos.com | www.novaos.com\n"));
            infoText.add(new Chunk("+91 (80) 4912-0000"));
            rightCell.addElement(infoText);
            headerTable.addCell(rightCell);

            doc.add(headerTable);

            // Subtle divider line
            doc.add(new Paragraph("\n"));
            LineSeparator separator = new LineSeparator(0.8f, 100, new BaseColor(226, 232, 240), Element.ALIGN_CENTER, 0); // Slate 200
            doc.add(separator);
            doc.add(new Paragraph("\n"));

            // Large Title
            Paragraph offerTitle = new Paragraph("OFFER OF EMPLOYMENT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new BaseColor(15, 23, 42)));
            offerTitle.setAlignment(Element.ALIGN_CENTER);
            offerTitle.setSpacingBefore(5);
            offerTitle.setSpacingAfter(4);
            doc.add(offerTitle);

            LineSeparator titleSeparator = new LineSeparator(1.2f, 30, new BaseColor(79, 70, 229), Element.ALIGN_CENTER, 0); // Indigo accent line
            doc.add(titleSeparator);
            doc.add(new Paragraph("\n"));

            // 3-Column Info Cards for Date, Reference, Candidate
            PdfPTable infoCardsTable = new PdfPTable(3);
            infoCardsTable.setWidthPercentage(100);
            infoCardsTable.setWidths(new float[]{33f, 33f, 34f});
            infoCardsTable.setSpacingBefore(5);
            infoCardsTable.setSpacingAfter(15);

            String dateString = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            String refId = Objects.toString(request.get("offerReferenceId"), "NOVA-" + String.valueOf(request.get("id")).substring(0,8).toUpperCase());
            String candidateInfo = String.valueOf(request.get("candidateName")) + "\n" + String.valueOf(request.get("candidateEmail"));

            infoCardsTable.addCell(createInfoCard("DATE OF ISSUE", dateString));
            infoCardsTable.addCell(createInfoCard("REFERENCE NUMBER", refId));
            infoCardsTable.addCell(createInfoCard("PREPARED FOR", candidateInfo));
            doc.add(infoCardsTable);

            // Opening Paragraph
            Paragraph openingText = new Paragraph("We are pleased to extend this formal offer of employment with NovaOS Enterprise Suite. Following our review of your professional background, achievements, and technical discussions during the interview process, we are delighted to offer you the position described below. We believe your experience and capabilities will be instrumental in driving our enterprise innovations forward.", fontBody);
            openingText.setLeading(14);
            openingText.setSpacingAfter(15);
            doc.add(openingText);

            // Details Table
            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(100);
            detailsTable.setWidths(new float[]{38f, 62f});
            detailsTable.setSpacingBefore(5);
            detailsTable.setSpacingAfter(18);

            addDetailRow(detailsTable, fontLabel, fontValue, "Position Title", String.valueOf(request.get("jobTitle")));
            addDetailRow(detailsTable, fontLabel, fontValue, "Business Department", String.valueOf(request.get("department")));
            addDetailRow(detailsTable, fontLabel, fontValue, "Employment Type", request.get("employmentType") != null ? String.valueOf(request.get("employmentType")) : "Full-Time");

            String joiningStr = "";
            try {
                joiningStr = LocalDate.parse(String.valueOf(request.get("joiningDate"))).format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
            } catch (Exception e) {
                joiningStr = String.valueOf(request.get("joiningDate"));
            }
            addDetailRow(detailsTable, fontLabel, fontValue, "Joining Date", joiningStr);
            addDetailRow(detailsTable, fontLabel, fontValue, "Reporting Manager", String.valueOf(request.get("reportingManagerName")));
            addDetailRow(detailsTable, fontLabel, fontValue, "Work Location", request.get("location") != null ? String.valueOf(request.get("location")) : "Bangalore Office");

            long salary = 0;
            if (request.get("annualSalaryAmount") != null) {
                salary = ((Number) request.get("annualSalaryAmount")).longValue();
            }
            String salaryFormatted = "₹" + NumberFormat.getIntegerInstance(new Locale("en", "IN")).format(salary) + " per annum (" + request.get("annualPackageLPA") + " LPA)";
            addDetailRow(detailsTable, fontLabel, fontValue, "Annual Cost to Company (CTC)", salaryFormatted);
            addDetailRow(detailsTable, fontLabel, fontValue, "Probation Period", "6 Months");
            addDetailRow(detailsTable, fontLabel, fontValue, "Notice Period", "30 Days (as per company policy)");

            doc.add(detailsTable);

            // Professional Sections
            addSection(doc, fontSectionHeader, fontSectionBody, "Employment Terms",
                    "Your employment is subject to standard service rules, employee guidelines, and policies of NovaOS Enterprise Suite as defined in the employee handbook. You are expected to perform all tasks associated with your role with diligence and professional care.");

            addSection(doc, fontSectionHeader, fontSectionBody, "Compensation",
                    "Your compensation will be paid monthly in accordance with company payroll guidelines, subject to statutory tax deductions and withholding rules. Annual reviews and performance incentives are evaluated based on performance guidelines.");

            addSection(doc, fontSectionHeader, fontSectionBody, "Confidentiality",
                    "By accepting this offer, you agree to safeguard and maintain strict confidentiality regarding all company proprietary tools, client data, codebase architectures, and internal business details. This obligation survives any termination of your employment.");

            addSection(doc, fontSectionHeader, fontSectionBody, "Background Verification",
                    "This offer is contingent upon successful completion of credential validation, reference checks, and background investigations. The company reserves the right to withdraw this offer immediately if any verification details are found unsatisfactory or misrepresented.");

            addSection(doc, fontSectionHeader, fontSectionBody, "Code of Conduct",
                    "NovaOS is committed to promoting an ethical, inclusive, and professional workspace. You are expected to adhere to all company values, compliance guidelines, and code of conduct policies.");

            addSection(doc, fontSectionHeader, fontSectionBody, "Acceptance",
                    "To formalize your acceptance, please sign, date, and return this letter to the Human Resources department within five (5) business days of receipt, after which this offer will be considered expired.");

            // Closing Paragraph
            Paragraph closingText = new Paragraph("We look forward to welcoming you to the NovaOS team and wish you a highly successful and rewarding career with us.", fontBody);
            closingText.setLeading(14);
            closingText.setSpacingBefore(10);
            closingText.setSpacingAfter(25);
            doc.add(closingText);

            // Digital Verification Box
            PdfPTable verifyTable = new PdfPTable(1);
            verifyTable.setWidthPercentage(100);
            verifyTable.setSpacingBefore(10);
            verifyTable.setSpacingAfter(20);
            
            PdfPCell vCell = new PdfPCell();
            vCell.setBackgroundColor(new BaseColor(248, 250, 252)); // Slate 50
            vCell.setBorderColor(new BaseColor(226, 232, 240)); // Slate 200
            vCell.setPadding(10);
            
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, new BaseColor(71, 85, 105)); // Slate 600
            Font fontVerifyBody = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new BaseColor(100, 116, 139)); // Slate 500
            Font fontVerifyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, new BaseColor(30, 41, 59)); // Slate 800
            Font fontVerifySub = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8f, new BaseColor(99, 102, 241)); // Indigo 500
            
            Paragraph vTitle = new Paragraph("DIGITAL VERIFICATION & AUTHENTICITY", fontHeader);
            vTitle.setSpacingAfter(4);
            vCell.addElement(vTitle);
            
            Paragraph vInfo = new Paragraph();
            vInfo.setLeading(12);
            vInfo.add(new Chunk("Document ID: ", fontVerifyBody));
            vInfo.add(new Chunk(refId + "\n", fontVerifyBold));
            
            String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")) + ", " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));
            vInfo.add(new Chunk("Generated on: ", fontVerifyBody));
            vInfo.add(new Chunk(timestamp + "\n", fontVerifyBold));
            vInfo.add(new Chunk("Issued by: ", fontVerifyBody));
            vInfo.add(new Chunk("NovaOS Enterprise Suite\n\n", fontVerifyBold));
            vInfo.add(new Chunk("This is a digitally generated document and is legally valid without physical signature.", fontVerifySub));
            
            vCell.addElement(vInfo);
            verifyTable.addCell(vCell);
            doc.add(verifyTable);

            // Signatures Section Table
            PdfPTable sigTable = new PdfPTable(2);
            sigTable.setWidthPercentage(100);
            sigTable.setWidths(new float[]{50f, 50f});
            sigTable.setSpacingBefore(15);

            PdfPCell cellLeft = new PdfPCell();
            cellLeft.setBorder(Rectangle.NO_BORDER);
            cellLeft.setPadding(0);

            Paragraph hrSig = new Paragraph("Jane Doe", FontFactory.getFont(FontFactory.TIMES_ITALIC, 14, Font.BOLD, new BaseColor(59, 130, 246)));
            hrSig.setSpacingAfter(4);
            cellLeft.addElement(hrSig);

            Paragraph hrSignatureLine = new Paragraph("_________________________\nDigitally Signed\nHR Department\nNovaOS Enterprise Suite", FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new BaseColor(71, 85, 105)));
            hrSignatureLine.setLeading(12);
            cellLeft.addElement(hrSignatureLine);
            sigTable.addCell(cellLeft);

            PdfPCell cellRight = new PdfPCell();
            cellRight.setBorder(Rectangle.NO_BORDER);
            cellRight.setPadding(0);

            Paragraph authSig = new Paragraph("John Smith", FontFactory.getFont(FontFactory.TIMES_ITALIC, 14, Font.BOLD, new BaseColor(79, 70, 229)));
            authSig.setSpacingAfter(4);
            cellRight.addElement(authSig);

            Paragraph authSignatureLine = new Paragraph("_________________________\nAuthorized Signatory\nExecutive Office\nNovaOS Enterprise Suite", FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new BaseColor(71, 85, 105)));
            authSignatureLine.setLeading(12);
            cellRight.addElement(authSignatureLine);
            sigTable.addCell(cellRight);

            doc.add(sigTable);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Offer PDF generation failed: " + e.getMessage(), e);
        }
    }

    private PdfPCell createInfoCard(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new BaseColor(248, 250, 252)); // Slate 50
        cell.setBorderColor(new BaseColor(226, 232, 240)); // Slate 200
        cell.setPadding(10);
        Paragraph l = new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, new BaseColor(100, 116, 139)));
        cell.addElement(l);
        Paragraph v = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new BaseColor(15, 23, 42)));
        v.setLeading(11);
        v.setSpacingBefore(4);
        cell.addElement(v);
        return cell;
    }

    private void addDetailRow(PdfPTable table, Font labelFont, Font valueFont, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setPaddingLeft(10);
        labelCell.setPaddingRight(10);
        labelCell.setPaddingTop(6);
        labelCell.setPaddingBottom(6);
        labelCell.setBackgroundColor(new BaseColor(248, 250, 252)); // Slate 50
        labelCell.setBorderColor(new BaseColor(226, 232, 240)); // Slate 200
        
        PdfPCell valueCell = new PdfPCell(new Phrase(Objects.toString(value, "—"), valueFont));
        valueCell.setPaddingLeft(10);
        valueCell.setPaddingRight(10);
        valueCell.setPaddingTop(6);
        valueCell.setPaddingBottom(6);
        valueCell.setBorderColor(new BaseColor(226, 232, 240)); // Slate 200
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addSection(Document doc, Font headerFont, Font bodyFont, String heading, String body) throws DocumentException {
        Paragraph h = new Paragraph(heading, headerFont);
        h.setSpacingBefore(8);
        h.setSpacingAfter(3);
        h.setLeading(14);
        doc.add(h);
        
        Paragraph b = new Paragraph(body, bodyFont);
        b.setLeading(11.5f);
        b.setSpacingAfter(8);
        doc.add(b);
    }

    private static class PageNumbers extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                // Sleek Line above footer
                BaseColor lineColor = new BaseColor(226, 232, 240);
                PdfContentByte cb = writer.getDirectContent();
                cb.setColorStroke(lineColor);
                cb.setLineWidth(0.8f);
                cb.moveTo(document.left(), document.bottom() - 10);
                cb.lineTo(document.right(), document.bottom() - 10);
                cb.stroke();
                
                // Left-aligned footer note
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                        new Phrase("Confidential | Digitally generated by NovaOS Enterprise Suite. No physical signature required.", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7.5f, BaseColor.GRAY)),
                        document.left(), document.bottom() - 22, 0);
                
                // Right-aligned page numbers
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                        new Phrase("www.novaos.com | hr@novaos.com | Page " + writer.getPageNumber(), FontFactory.getFont(FontFactory.HELVETICA, 7.5f, BaseColor.GRAY)),
                        document.right(), document.bottom() - 22, 0);
            } catch (Exception ignored) {}
        }
    }
}
