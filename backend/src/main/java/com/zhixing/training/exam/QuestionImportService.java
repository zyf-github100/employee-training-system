package com.zhixing.training.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.training.api.ApiException;
import com.zhixing.training.auth.AuthService;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static com.zhixing.training.exam.ExamDtos.*;

@Service
public class QuestionImportService {
    private final JdbcTemplate jdbc;
    private final ExamRepository exams;
    private final ObjectMapper json;
    public QuestionImportService(JdbcTemplate jdbc, ExamRepository exams, ObjectMapper json) { this.jdbc = jdbc; this.exams = exams; this.json = json; }

    @Transactional
    public ImportResult importFile(long actor, MultipartFile file, String suppliedRequestId) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_IMPORT", "导入文件不能为空");
        if (file.getSize() > 5 * 1024 * 1024) throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_TOO_LARGE", "题目导入文件不能超过 5 MB");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_READ_FAILED", "无法读取导入文件"); }
        String hash = hash(bytes);
        List<ImportResult> previous = jdbc.query("SELECT result_json FROM question_import WHERE actor_id=? AND payload_hash=?", (rs, row) -> readResult(rs.getString(1)), actor, hash);
        if (!previous.isEmpty()) return previous.get(0);
        String requestId = suppliedRequestId == null || suppliedRequestId.isBlank() ? UUID.randomUUID().toString() : suppliedRequestId;
        List<QuestionRequest> requests = parse(file, bytes);
        List<ImportRow> rows = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) rows.add(new ImportRow(i + 2, "VALID", "", 0));
        int rejected = 0;
        for (int i = 0; i < requests.size(); i++) {
            try { validate(requests.get(i)); } catch (ApiException e) { rows.set(i, new ImportRow(i + 2, "REJECTED", e.getMessage(), 0)); rejected++; }
        }
        if (rejected == 0) {
            for (int i = 0; i < requests.size(); i++) {
                long id = exams.createQuestion(requests.get(i), actor);
                rows.set(i, new ImportRow(i + 2, "IMPORTED", "", id));
            }
        }
        ImportResult result = new ImportResult(requestId, rejected == 0 ? requests.size() : 0, rejected, rows);
        try { jdbc.update("INSERT INTO question_import(id,actor_id,payload_hash,status,result_json) VALUES(?,?,?,?,?)", requestId, actor, hash, rejected == 0 ? "IMPORTED" : "REJECTED", encode(result)); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new ApiException(HttpStatus.CONFLICT, "IMPORT_REQUEST_CONFLICT", "导入请求编号已被使用"); }
        return result;
    }

    private List<QuestionRequest> parse(MultipartFile file, byte[] bytes) {
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                try (Workbook workbook = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
                    Sheet sheet = workbook.getSheetAt(0); if (sheet.getLastRowNum() < 1) throw bad("导入文件没有数据行");
                    Map<String,Integer> headers = headers(sheet.getRow(0)); List<QuestionRequest> result = new ArrayList<>();
                    for (int i = 1; i <= sheet.getLastRowNum(); i++) result.add(fromRow(i + 1, headers, sheet.getRow(i)));
                    return result;
                }
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
                String header = reader.readLine(); if (header == null) throw bad("导入文件没有表头");
                Map<String,Integer> headers = new HashMap<>(); List<String> cells = csv(header); for (int i=0;i<cells.size();i++) headers.put(cells.get(i).trim(),i);
                List<QuestionRequest> result = new ArrayList<>(); String line; int row = 1; while ((line = reader.readLine()) != null) { row++; if (!line.isBlank()) result.add(fromCells(row, headers, csv(line))); }
                if (result.isEmpty()) throw bad("导入文件没有数据行"); return result;
            }
        } catch (ApiException e) { throw e; } catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_PARSE_FAILED", "题目导入文件解析失败"); }
    }

    private QuestionRequest fromRow(int row, Map<String,Integer> headers, Row source) { List<String> values = new ArrayList<>(); for (int i=0;i<headers.size();i++) values.add(source == null || source.getCell(i) == null ? "" : new DataFormatter().formatCellValue(source.getCell(i))); return fromCells(row, headers, values); }
    private QuestionRequest fromCells(int row, Map<String,Integer> headers, List<String> values) {
        String type = cell(headers, values, "type"); String options = cell(headers, values, "options"); String answer = cell(headers, values, "answer");
        List<String> optionList = type.equals("subjective") ? List.of() : Arrays.stream(options.split("[|\\n]", -1)).map(String::trim).filter(s -> !s.isBlank()).toList();
        List<Integer> answerList = type.equals("subjective") ? List.of() : Arrays.stream(answer.split(",", -1)).map(String::trim).filter(s -> !s.isBlank()).map(Integer::parseInt).toList();
        return new QuestionRequest(type, cell(headers, values, "stem"), optionList, answerList, cell(headers, values, "analysis"), cell(headers, values, "category"), cell(headers, values, "difficulty"), "ENABLED", 0, cell(headers, values, "referenceAnswer"), cell(headers, values, "gradingGuide"));
    }
    private void validate(QuestionRequest request) { exams.validateQuestionForImport(request); }
    private Map<String,Integer> headers(Row row) { Map<String,Integer> result = new HashMap<>(); for (int i=0;i<row.getLastCellNum();i++) result.put(new DataFormatter().formatCellValue(row.getCell(i)).trim(), i); return result; }
    private String cell(Map<String,Integer> headers, List<String> values, String key) { Integer i = headers.get(key); return i == null || i >= values.size() ? "" : values.get(i).trim(); }
    private List<String> csv(String line) { List<String> result = new ArrayList<>(); StringBuilder current = new StringBuilder(); boolean quoted = false; for (int i=0;i<line.length();i++) { char c=line.charAt(i); if(c=='"' && (i+1==line.length() || line.charAt(i+1)!='"')) quoted=!quoted; else if(c==','&&!quoted){result.add(current.toString());current.setLength(0);} else if(c=='"'&&i+1<line.length()&&line.charAt(i+1)=='"'){current.append('"');i++;} else current.append(c);} result.add(current.toString()); return result; }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private ImportResult readResult(String value) { try { return json.readValue(value, ImportResult.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_INVALID", message); }
}
