package com.familyconnect.modules.dashboard.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PdfReportGenerator {

    private static final float MARGIN = 42;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm XXX", Locale.forLanguageTag("vi-VN"));

    private final String configuredFontPath;

    public PdfReportGenerator(@Value("${report.pdf.font-path:}") String configuredFontPath) {
        this.configuredFontPath = configuredFontPath;
    }

    public byte[] generate(String title, String scope, String period, OffsetDateTime generatedAt,
                           String creator, List<Section> sections) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Path regularPath = findFont(configuredFontPath);
            Path boldPath = findBoldFont(regularPath);
            PDType0Font regular;
            PDType0Font bold;
            try (InputStream input = Files.newInputStream(regularPath)) {
                regular = PDType0Font.load(document, input);
            }
            try (InputStream input = Files.newInputStream(boldPath)) {
                bold = PDType0Font.load(document, input);
            }

            Layout layout = new Layout(document, regular, bold);
            layout.text(title, bold, 20, MARGIN, false);
            layout.text("Phạm vi: " + scope, regular, 10, MARGIN, false);
            layout.text("Khoảng thời gian: " + period, regular, 10, MARGIN, false);
            layout.text("Ngày tạo: " + DATE_TIME.format(generatedAt), regular, 10, MARGIN, false);
            layout.text("Người tạo: " + creator, regular, 10, MARGIN, false);
            layout.gap(12);

            for (Section section : sections) {
                layout.section(section);
            }
            layout.finish();
            layout.addPageNumbers();
            document.save(output);
            return output.toByteArray();
        }
    }

    private Path findFont(String configured) throws IOException {
        List<Path> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(Path.of(configured));
        }
        candidates.addAll(List.of(
                Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
                Path.of("/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"),
                Path.of("C:\\Windows\\Fonts\\arial.ttf"),
                Path.of("C:\\Windows\\Fonts\\calibri.ttf")));
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElseThrow(() ->
                new IOException("Không tìm thấy font hỗ trợ tiếng Việt; cài fonts-dejavu-core hoặc đặt report.pdf.font-path."));
    }

    private Path findBoldFont(Path regular) {
        String fileName = regular.getFileName().toString();
        String boldName = fileName.replace("Regular", "Bold");
        if (boldName.equals(fileName) && fileName.equals("DejaVuSans.ttf")) {
            boldName = "DejaVuSans-Bold.ttf";
        }
        Path bold = regular.resolveSibling(boldName);
        if (Files.isRegularFile(bold)) {
            return bold;
        }
        Path windowsBold = regular.resolveSibling(fileName.equalsIgnoreCase("arial.ttf") ? "arialbd.ttf" : "calibrib.ttf");
        return Files.isRegularFile(windowsBold) ? windowsBold : regular;
    }

    public record Section(String title, List<String> headers, List<List<String>> rows, boolean chart) {
        public Section {
            headers = List.copyOf(headers);
            rows = rows.stream().map(List::copyOf).toList();
        }
    }

    private static final class Layout {
        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private PDPageContentStream stream;
        private float y;

        private Layout(PDDocument document, PDType0Font regular, PDType0Font bold) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            newPage();
        }

        private void text(String value, PDType0Font font, float size, float x, boolean keepTogether)
                throws IOException {
            List<String> lines = wrap(value, font, size, PAGE_WIDTH - MARGIN * 2);
            ensure(lines.size() * (size + 4));
            for (String line : lines) {
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(x, y);
                stream.showText(line);
                stream.endText();
                y -= size + 4;
            }
            if (!keepTogether) {
                y -= 2;
            }
        }

        private void section(Section section) throws IOException {
            ensure(34);
            text(section.title(), bold, 14, MARGIN, true);
            if (section.rows().isEmpty()) {
                text("Không có dữ liệu trong phạm vi đã chọn.", regular, 10, MARGIN, false);
                gap(8);
                return;
            }
            if (section.chart() && section.headers().size() >= 2) {
                chart(section.rows());
            }
            table(section.headers(), section.rows());
            gap(12);
        }

        private void chart(List<List<String>> rows) throws IOException {
            List<List<String>> plotted = rows.stream().limit(8).toList();
            ensure(plotted.size() * 22 + 16);
            float labelWidth = 120;
            float chartWidth = PAGE_WIDTH - MARGIN * 2 - labelWidth - 38;
            long max = plotted.stream().map(row -> number(row.get(1))).mapToLong(Long::longValue).max().orElse(0);
            for (List<String> row : plotted) {
                String label = truncate(row.get(0), regular, 9, labelWidth - 8);
                long value = number(row.get(1));
                float barWidth = max == 0 ? 0 : (chartWidth - 35) * value / max;
                stream.setNonStrokingColor(41, 106, 152);
                stream.addRect(MARGIN + labelWidth, y - 3, barWidth, 10);
                stream.fill();
                drawText(label, regular, 9, MARGIN, y);
                drawText(Long.toString(value), regular, 9, MARGIN + labelWidth + barWidth + 5, y);
                y -= 20;
            }
            if (rows.size() > plotted.size()) {
                text("Biểu đồ hiển thị tối đa 8 nhóm; bảng bên dưới gồm toàn bộ dữ liệu.",
                        regular, 8, MARGIN, false);
            }
            gap(5);
        }

        private void table(List<String> headers, List<List<String>> rows) throws IOException {
            headersForRepeat = headers;
            float columnWidth = (PAGE_WIDTH - MARGIN * 2) / headers.size();
            drawRow(headers, columnWidth, true);
            for (List<String> row : rows) {
                drawRow(row, columnWidth, false);
            }
            headersForRepeat = List.of();
        }

        private void drawRow(List<String> cells, float columnWidth, boolean header) throws IOException {
            PDType0Font font = header ? bold : regular;
            float size = header ? 8.5f : 8;
            List<List<String>> wrapped = new ArrayList<>(cells.size());
            int maxLines = 1;
            for (String cell : cells) {
                List<String> lines = wrap(cell, font, size, columnWidth - 10);
                wrapped.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            float rowHeight = maxLines * (size + 3) + 8;
            if (y - rowHeight < MARGIN) {
                newPage();
                if (!header && !headersForRepeat.isEmpty()) {
                    drawRow(headersForRepeat, columnWidth, true);
                }
            }
            if (header) {
                stream.setNonStrokingColor(231, 238, 244);
                stream.addRect(MARGIN, y - rowHeight + 3, PAGE_WIDTH - MARGIN * 2, rowHeight);
                stream.fill();
            }
            float x = MARGIN;
            for (List<String> lines : wrapped) {
                float baseline = y - 8 - size;
                for (String line : lines) {
                    drawText(line, font, size, x + 5, baseline);
                    baseline -= size + 3;
                }
                x += columnWidth;
            }
            y -= rowHeight;
            stream.setStrokingColor(205, 211, 217);
            stream.moveTo(MARGIN, y);
            stream.lineTo(PAGE_WIDTH - MARGIN, y);
            stream.stroke();
        }

        private List<String> headersForRepeat = List.of();

        private void drawText(String value, PDType0Font font, float size, float x, float baseline)
                throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(value);
            stream.endText();
        }

        private void ensure(float height) throws IOException {
            if (y - height < MARGIN) {
                newPage();
            }
        }

        private void gap(float amount) {
            y -= amount;
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        private void finish() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void addPageNumbers() throws IOException {
            int count = document.getNumberOfPages();
            for (int i = 0; i < count; i++) {
                try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(i),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.newLineAtOffset(PAGE_WIDTH - MARGIN - 55, 22);
                    footer.showText("Trang " + (i + 1) + " / " + count);
                    footer.endText();
                }
            }
        }

        private static long number(String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private static String truncate(String value, PDType0Font font, float size, float maxWidth)
                throws IOException {
            String result = value;
            while (!result.isEmpty() && font.getStringWidth(result) / 1000 * size > maxWidth) {
                result = result.substring(0, result.length() - 1);
            }
            return result.equals(value) ? result : result + "…";
        }

        private static List<String> wrap(String value, PDType0Font font, float size, float maxWidth)
                throws IOException {
            String text = value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ").trim();
            if (text.isEmpty()) {
                return List.of("-");
            }
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split("\\s+")) {
                if (font.getStringWidth(word) / 1000 * size > maxWidth) {
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    StringBuilder fragment = new StringBuilder();
                    for (int offset = 0; offset < word.length(); offset++) {
                        fragment.append(word.charAt(offset));
                        if (font.getStringWidth(fragment.toString()) / 1000 * size > maxWidth
                                && fragment.length() > 1) {
                            char last = fragment.charAt(fragment.length() - 1);
                            fragment.setLength(fragment.length() - 1);
                            lines.add(fragment.toString());
                            fragment.setLength(0);
                            fragment.append(last);
                        }
                    }
                    line.append(fragment);
                    continue;
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.getStringWidth(candidate) / 1000 * size > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    if (!line.isEmpty()) {
                        line.append(' ');
                    }
                    line.append(word);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
            return lines;
        }
    }
}
