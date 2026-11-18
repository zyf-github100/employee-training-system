package com.zhixing.training.report;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

public final class ReportWorkbook {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private ReportWorkbook() { }

    public static byte[] write(String sheetName, String title, List<String> headers,
                               List<List<Object>> rows, int[] widths) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet(sheetName);
            Styles styles = new Styles(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(30);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(styles.title());
            if (headers.size() > 1) sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.size() - 1));

            Row metaRow = sheet.createRow(1);
            Cell metaCell = metaRow.createCell(0);
            metaCell.setCellValue("导出时间：" + LocalDateTime.now().format(STAMP) + "    共 " + rows.size() + " 条");
            metaCell.setCellStyle(styles.meta());
            if (headers.size() > 1) sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headers.size() - 1));

            Row headerRow = sheet.createRow(2);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.header());
            }

            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 3);
                row.setHeightInPoints(20);
                List<Object> values = rows.get(r);
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellStyle(styles.body());
                    writeValue(cell, c < values.size() ? values.get(c) : null, styles);
                }
            }

            for (int i = 0; i < headers.size(); i++)
                sheet.setColumnWidth(i, Math.min(50, Math.max(8, i < widths.length ? widths[i] : 14)) * 256);
            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, 2 + rows.size(), 0, headers.size() - 1));
            sheet.getPrintSetup().setLandscape(true);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.setDefaultRowHeightInPoints(18);
            workbook.setActiveSheet(0);

            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成报表失败", ex);
        }
    }

    private static void writeValue(Cell cell, Object value, Styles styles) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Instant instant) {
            cell.setCellValue(Date.from(instant));
            cell.setCellStyle(styles.dateTime());
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(styles.date());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean flag) {
            cell.setCellValue(flag ? "是" : "否");
        } else {
            String text = String.valueOf(value);
            cell.setCellValue(text.length() > 32767 ? text.substring(0, 32767) : text);
        }
    }

    private record Styles(CellStyle title, CellStyle meta, CellStyle header, CellStyle body,
                          CellStyle dateTime, CellStyle date) {
        Styles(SXSSFWorkbook workbook) {
            this(createTitle(workbook), createMeta(workbook), createHeader(workbook),
                    createBody(workbook), createDateTime(workbook), createDate(workbook));
        }

        private static CellStyle createTitle(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 14);
            font.setColor(IndexedColors.DARK_TEAL.getIndex());
            style.setFont(font);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        private static CellStyle createMeta(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setFontHeightInPoints((short) 9);
            font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setFont(font);
            return style;
        }

        private static CellStyle createHeader(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 10);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            border(style);
            return style;
        }

        private static CellStyle createBody(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setFontHeightInPoints((short) 10);
            style.setFont(font);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            border(style);
            return style;
        }

        private static CellStyle createDateTime(SXSSFWorkbook workbook) {
            CellStyle style = createBody(workbook);
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat("yyyy-mm-dd hh:mm"));
            return style;
        }

        private static CellStyle createDate(SXSSFWorkbook workbook) {
            CellStyle style = createBody(workbook);
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat("yyyy-mm-dd"));
            return style;
        }

        private static void border(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
    }
}
