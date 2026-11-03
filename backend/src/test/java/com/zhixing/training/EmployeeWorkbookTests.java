package com.zhixing.training;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.organization.EmployeeWorkbook;
import com.zhixing.training.organization.EmployeeImportDtos;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class EmployeeWorkbookTests {
    static final String MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    final EmployeeWorkbook codec=new EmployeeWorkbook();
    static byte[] sheet(List<List<String>> rows)throws IOException {
        try(var book=new XSSFWorkbook()){
            var sheet=book.createSheet("员工导入");var header=sheet.createRow(0);
            var names=List.of("工号","姓名","部门编码","岗位","邮箱","电话");for(int c=0;c<6;c++)header.createCell(c).setCellValue(names.get(c));
            for(int n=0;n<rows.size();n++){var row=sheet.createRow(n+1);for(int c=0;c<rows.get(n).size();c++)row.createCell(c).setCellValue(rows.get(n).get(c));}
            var out=new ByteArrayOutputStream();book.write(out);return out.toByteArray();
        }
    }
    static MockMultipartFile upload(byte[] data){return new MockMultipartFile("file","employees.xlsx",MIME,data);}
    @Test void templateKeepsTextColumnsAndReportDoesNotExecuteFormulaLikeStrings()throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(codec.template(List.of())))){assertThat(book.getSheet("员工导入").getColumnStyle(0).getDataFormatString()).isEqualTo("@");assertThat(book.getSheet("填写说明")).isNotNull();}
        var rows=codec.parse(upload(sheet(List.of(List.of("abc_01","示例","TECH","=HYPERLINK(\"https://example.test\")","","00123456")))));
        assertThat(rows.get(0).fields().employeeNo()).isEqualTo("ABC_01");assertThat(rows.get(0).fields().phone()).isEqualTo("00123456");
        try(var report=new XSSFWorkbook(new ByteArrayInputStream(codec.report(rows)))){assertThat(report.getSheetAt(0).getRow(1).getCell(3).getCellType()).isEqualTo(CellType.STRING);}
        assertThat(codec.parse(upload(codec.report(rows))).get(0).fields()).isEqualTo(rows.get(0).fields());
    }
    @Test void formulasNumericIdentifiersAndExtraColumnsAreReported()throws Exception {
        byte[] source=sheet(List.of(List.of("001","示例","TECH","","","")));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(source))){var row=book.getSheetAt(0).getRow(1);row.getCell(0).setCellValue(1);row.getCell(3).setCellFormula("1+1");row.createCell(7).setCellValue("ADMIN");var out=new ByteArrayOutputStream();book.write(out);
            assertThat(codec.parse(upload(out.toByteArray())).get(0).errors()).hasSize(3);
        }
    }
    @Test void malformedOversizedAndExpandedArchivesAreRejected()throws Exception {
        assertThatThrownBy(()->codec.parse(upload("not xlsx".getBytes()))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->codec.parse(upload(new byte[EmployeeWorkbook.MAX_BYTES+1]))).isInstanceOf(ApiException.class);
        var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("[Content_Types].xml"));byte[] block=new byte[1024*1024];for(int i=0;i<21;i++)zip.write(block);zip.closeEntry();}
        assertThatThrownBy(()->codec.parse(upload(out.toByteArray()))).isInstanceOf(ApiException.class);
        try(var book=new XSSFWorkbook()){var sheet=book.createSheet("员工导入");var header=sheet.createRow(0);int c=0;for(String h:List.of("工号","姓名","部门编码","岗位","邮箱","电话"))header.createCell(c++).setCellValue(h);sheet.createRow(5001).createCell(0).setCellValue("TOO_MANY");var bytes=new ByteArrayOutputStream();book.write(bytes);
            assertThatThrownBy(()->codec.parse(upload(bytes.toByteArray()))).isInstanceOf(ApiException.class);
        }
    }
    @Test void writeBrowserFixtures()throws Exception {
        var valid=sheet(List.of(List.of("IMPORT_UI01","导入验收员工","TECH","测试工程师","qa@example.test","00123456"),List.of("IMPORT_UI02","导入验收同事","TECH","培训专员","","")));
        var invalid=sheet(List.of(List.of("DUPLICATE","重复甲","TECH","","bad-email",""),List.of("duplicate","重复乙","MISSING_DEPT","","","")));
        Files.createDirectories(Path.of(".data"));Files.write(Path.of(".data/employee-import-valid.xlsx"),valid);Files.write(Path.of(".data/employee-import-invalid.xlsx"),invalid);
        assertThat(codec.parse(upload(valid))).hasSize(2);
    }
}
