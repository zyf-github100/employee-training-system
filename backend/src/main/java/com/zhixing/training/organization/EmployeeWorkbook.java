package com.zhixing.training.organization;

import com.zhixing.training.api.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import static com.zhixing.training.organization.EmployeeImportDtos.*;

@Component
public class EmployeeWorkbook {
    public static final int MAX_ROWS=5000;
    public static final int MAX_BYTES=5*1024*1024;
    private static final long MAX_EXPANDED=20L*1024*1024;
    private static final List<String> HEADERS=List.of("工号","姓名","部门编码","岗位","邮箱","电话");

    public List<Row> parse(MultipartFile file) {
        if(file.isEmpty() || file.getSize()>MAX_BYTES) throw bad("请上传不超过 5 MB 的 .xlsx 文件");
        String name=Objects.toString(file.getOriginalFilename(),"");
        if(!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw bad("只支持 .xlsx 文件，请使用导入模板");
        try {
            byte[] bytes;
            try(var input=file.getInputStream()){bytes=input.readNBytes(MAX_BYTES+1);}
            if(bytes.length>MAX_BYTES)throw bad("文件超过 5 MB");
            checkArchive(bytes);
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                if(book.getWorkbookType()!=XSSFWorkbookType.XLSX || !book.getExternalLinksTable().isEmpty()) throw bad("文件不能包含宏或外部工作簿链接");
                Sheet sheet=book.getSheet("员工导入");
                if(sheet==null)throw bad("缺少“员工导入”工作表，请下载模板填写");
                var header=sheet.getRow(0);
                if(header==null)throw bad("缺少表头");
                for(int i=0;i<HEADERS.size();i++) if(!HEADERS.get(i).equals(textHeader(header.getCell(i))))throw bad("表头应依次为："+String.join("、",HEADERS));
                boolean reportColumn="校验提示".equals(textHeader(header.getCell(6)));
                for(var cell:header)if(cell.getColumnIndex()>=6 && !(cell.getColumnIndex()==6&&reportColumn) && cell.getCellType()!=CellType.BLANK)throw bad("表头包含不支持的列");
                if(sheet.getLastRowNum()>MAX_ROWS)throw bad("一次最多导入 5,000 行，请清除表格尾部多余行");
                List<Row> result=new ArrayList<>();
                for(int rowNo=1;rowNo<=sheet.getLastRowNum();rowNo++) {
                    var source=sheet.getRow(rowNo);if(source==null)continue;
                    String[] values=new String[6];List<String> errors=new ArrayList<>();boolean present=false;
                    for(int c=0;c<6;c++) {
                        Cell cell=source.getCell(c);values[c]="";
                        if(cell==null || cell.getCellType()==CellType.BLANK)continue;
                        if(cell.getCellType()!=CellType.STRING) {errors.add(HEADERS.get(c)+"请以文本填写，不支持公式、数字或错误单元格");present=true;continue;}
                        values[c]=cell.getStringCellValue().trim();present|=!values[c].isEmpty();
                        if(values[c].length()>500)throw bad("第 "+(rowNo+1)+" 行单元格过长");
                    }
                    for(Cell cell:source)if(cell.getColumnIndex()>=6 && !(cell.getColumnIndex()==6&&reportColumn) && cell.getCellType()!=CellType.BLANK) {errors.add("包含模板之外的数据列");present=true;}
                    if(!present)continue;
                    result.add(new Row(rowNo+1,new Fields(values[0].toUpperCase(Locale.ROOT),values[1],values[2].toUpperCase(Locale.ROOT),values[3],values[4],values[5]),List.copyOf(errors)));
                }
                if(result.isEmpty())throw bad("表格没有员工数据");
                return result;
            }
        } catch(ApiException e){throw e;} catch(Exception e){throw bad("无法读取工作簿，请确认文件未加密且使用有效的 .xlsx 模板");}
    }
    // Bound total decompression before POI allocates the object model. Nothing is extracted to disk.
    private void checkArchive(byte[] bytes)throws IOException {
        Set<String> names=new HashSet<>();long total=0;byte[] block=new byte[8192];
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(!names.add(entry.getName())||names.size()>128)throw bad("工作簿包含重复条目或过多附件");
                if(entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject"))throw bad("不支持含宏的工作簿");
                int n;while((n=zip.read(block))!=-1){total+=n;if(total>MAX_EXPANDED)throw bad("工作簿解压后超过 20 MB，请删除图片或多余内容");}
            }
        }
        if(!names.contains("[Content_Types].xml"))throw bad("文件不是有效的 .xlsx 工作簿");
    }
    private String textHeader(Cell c){return c!=null&&c.getCellType()==CellType.STRING?c.getStringCellValue().replace("\uFEFF","").trim():"";}
    public byte[] template(List<OrganizationDtos.Department> departments) {
        try(var book=new XSSFWorkbook()) {
            Sheet sheet=createSheet(book,"员工导入",HEADERS);
            var textStyle=book.createCellStyle();textStyle.setDataFormat(book.createDataFormat().getFormat("@"));
            for(int c=0;c<6;c++)sheet.setDefaultColumnStyle(c,textStyle);
            Sheet refs=createSheet(book,"部门参考",List.of("部门编码","部门名称"));int row=1;
            for(var d:departments)if("ACTIVE".equals(d.status()))write(refs.createRow(row++),List.of(d.code(),d.name()));
            Sheet info=createSheet(book,"填写说明",List.of("填写说明"));info.setColumnWidth(0,120*256);
            write(info.createRow(1),List.of("只填写员工导入工作表，最多 5000 行；工号、姓名、部门编码必填，其他列可空。所有单元格使用文本格式。"));
            write(info.createRow(2),List.of("部门编码请从部门参考中复制；不接受重复工号、管理员角色、密码或公式。全部校验通过后才会导入。"));
            write(info.createRow(3),List.of("新账号保持未激活。导入后下载激活名单并分别交付员工，员工设置自己的密码后才能登录。"));
            return bytes(book);
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public byte[] report(List<Row> rows) {
        try(var book=new XSSFWorkbook()) {
            List<String> headers=new ArrayList<>(HEADERS);headers.add("校验提示");Sheet sheet=createSheet(book,"员工导入",headers);
            for(var r:rows){var f=r.fields();write(sheet.createRow(r.rowNumber()-1),List.of(f.employeeNo(),f.name(),f.departmentCode(),f.jobTitle(),f.email(),f.phone(),r.errors().isEmpty()?"通过":String.join("；",r.errors())));}
            return bytes(book);
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public byte[] activationList(List<List<String>> rows) {
        try(var book=new XSSFWorkbook()) {
            Sheet sheet=createSheet(book,"激活名单",List.of("工号","姓名","状态","设置密码地址","一次性设置码","有效期"));int n=1;
            for(var row:rows)write(sheet.createRow(n++),row);
            return bytes(book);
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private Sheet createSheet(XSSFWorkbook book,String name,List<String> headers){
        Sheet sheet=book.createSheet(name);sheet.createFreezePane(0,1);
        var style=book.createCellStyle();style.setFillForegroundColor(IndexedColors.TEAL.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var font=book.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());style.setFont(font);
        var row=sheet.createRow(0);write(row,headers);for(var cell:row)cell.setCellStyle(style);
        for(int c=0;c<headers.size();c++)sheet.setColumnWidth(c,(c==1?20:32)*256);
        return sheet;
    }
    private void write(org.apache.poi.ss.usermodel.Row row,List<String> values){for(int c=0;c<values.size();c++)row.createCell(c,CellType.STRING).setCellValue(values.get(c));}
    private byte[] bytes(XSSFWorkbook book)throws IOException{var out=new ByteArrayOutputStream();book.write(out);return out.toByteArray();}
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_WORKBOOK",message);}
}
