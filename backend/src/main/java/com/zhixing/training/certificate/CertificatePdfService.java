package com.zhixing.training.certificate;

import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.zhixing.training.api.ApiException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.util.Matrix;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Service
public class CertificatePdfService {
    private final byte[] fontBytes;
    private final CertificateLinks links;
    private final ZoneId zone;
    private static final Color TEAL=new Color(27,79,69),GOLD=new Color(171,145,87),GRAY=new Color(113,127,121);
    public CertificatePdfService(CertificateLinks links,Environment environment) throws IOException {
        this.links=links;zone=ZoneId.of(environment.getProperty("zhixing.certificate-time-zone","UTC"));
        try(var stream=getClass().getResourceAsStream("/fonts/CertificateSans-Regular.ttf")) {
            if(stream==null)throw new IllegalStateException("Certificate font resource is missing");
            fontBytes=stream.readAllBytes();
        }
    }
    public byte[] render(CertificateDtos.CertificateView certificate) {
        String url=links.verification(certificate.verificationCode());
        try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            var page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));doc.addPage(page);
            var font=PDType0Font.load(doc,new ByteArrayInputStream(fontBytes),true);
            for(String value:List.of(certificate.recipientName(),certificate.employeeNo(),certificate.planTitle(),certificate.issuerName(),certificate.certificateNo()))font.getStringWidth(clean(value));
            float width=page.getMediaBox().getWidth(),height=page.getMediaBox().getHeight();
            try(var out=new PDPageContentStream(doc,page)) {
                out.setNonStrokingColor(new Color(252,252,248));out.addRect(0,0,width,height);out.fill();
                out.setStrokingColor(TEAL);out.setLineWidth(1.6f);out.addRect(30,30,width-60,height-60);out.stroke();
                out.setStrokingColor(GOLD);out.setLineWidth(.5f);out.addRect(37,37,width-74,height-74);out.stroke();
                text(out,font,"ZHIXING / TRAINING RECORD",9,65,518,TEAL);
                right(out,font,certificate.certificateNo(),10,width-65,518,GRAY,300);
                out.setStrokingColor(GOLD);out.setLineWidth(1);out.moveTo(width/2-30,482);out.lineTo(width/2+30,482);out.stroke();
                centered(out,font,"结 业 证 书",38,width/2,433,TEAL);
                centered(out,font,"CERTIFICATE OF COMPLETION",10,width/2,404,GRAY);
                block(out,font,certificate.recipientName(),30,14,620,2,width/2,352,TEAL,true);
                block(out,font,"工号："+certificate.employeeNo(),11,8,620,2,width/2,305,GRAY,true);
                centered(out,font,"已完成以下培训计划，并达到结业要求",13,width/2,275,GRAY);
                block(out,font,certificate.planTitle(),23,10,610,3,width/2,247,TEAL,true);
                text(out,font,"签发单位",9,68,165,GRAY);
                block(out,font,certificate.issuerName(),13,8,470,2,68,145,TEAL,false);
                text(out,font,"结业日期："+date(certificate.completedAt()),10,68,104,GRAY);
                text(out,font,"签发日期："+date(certificate.issuedAt()),10,68,85,GRAY);
                text(out,font,"记录日期时区："+zone.getId(),7,68,59,GRAY);
                var qr=qr(url,360);
                out.drawImage(LosslessFactory.createFromImage(doc,qr),width-177,88,96,96);
                centered(out,font,"在线验真（点击或扫码）",8,width-129,70,TEAL);
                text(out,font,"当前有效状态请以在线验真结果为准",7,68,45,GRAY);
                if(certificate.legacySnapshot())text(out,font,"历史记录信息于系统升级时固化",7,350,59,GRAY);
                if(certificate.revokedAt()!=null) {
                    out.saveGraphicsState();var alpha=new PDExtendedGraphicsState();alpha.setNonStrokingAlphaConstant(.15f);out.setGraphicsStateParameters(alpha);
                    out.transform(Matrix.getRotateInstance(Math.toRadians(22),190,185));text(out,font,"已 撤 销",76,0,0,new Color(179,66,52));out.restoreGraphicsState();
                    centered(out,font,"已撤销 · "+date(certificate.revokedAt())+" · 本证书当前无效",12,width/2,185,new Color(172,66,54));
                }
            }
            var annotation=new PDAnnotationLink();annotation.setRectangle(new PDRectangle(width-184,63,112,128));
            var border=new PDBorderStyleDictionary();border.setWidth(0);annotation.setBorderStyle(border);
            var action=new PDActionURI();action.setURI(url);annotation.setAction(action);page.getAnnotations().add(annotation);
            doc.getDocumentInformation().setTitle("结业证书 - "+certificate.certificateNo());
            doc.getDocumentInformation().setAuthor(certificate.issuerName());
            doc.getDocumentInformation().setSubject(certificate.revokedAt()==null?"培训结业记录，在线查询当前状态":"已撤销的培训结业记录");
            doc.save(bytes);return bytes.toByteArray();
        } catch(IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"CERTIFICATE_FONT_UNSUPPORTED","证书含有当前字体不支持的字符，无法完整生成 PDF，请联系管理员");
        } catch(IOException|WriterException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CERTIFICATE_PDF_UNAVAILABLE","证书 PDF 暂时无法生成，请稍后重试");
        }
    }
    private String date(Instant instant){return DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone).format(instant);}
    static BufferedImage qr(String value,int size) throws WriterException {
        var matrix=new QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,size,size,Map.of(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M,EncodeHintType.MARGIN,4));
        var image=new BufferedImage(matrix.getWidth(),matrix.getHeight(),BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,matrix.get(x,y)?0xff000000:0xffffffff);
        return image;
    }
    private static String clean(String value){return value.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]"," ");}
    private static float measure(PDFont font,String value,float size) throws IOException {return font.getStringWidth(value)/1000*size;}
    private static void text(PDPageContentStream out,PDFont font,String value,float size,float x,float y,Color color) throws IOException {
        out.setNonStrokingColor(color);out.beginText();out.setFont(font,size);out.newLineAtOffset(x,y);out.showText(clean(value));out.endText();
    }
    private static void centered(PDPageContentStream out,PDFont font,String value,float size,float center,float y,Color color) throws IOException {text(out,font,value,size,center-measure(font,clean(value),size)/2,y,color);}
    private static void right(PDPageContentStream out,PDFont font,String value,float size,float x,float y,Color color,float width) throws IOException {
        value=clean(value);size=Math.min(size,width*1000/font.getStringWidth(value));text(out,font,value,size,x-measure(font,value,size),y,color);
    }
    private static List<String> wrap(PDFont font,String value,float size,float width) throws IOException {
        var result=new ArrayList<String>();var line=new StringBuilder();
        for(int point:clean(value).codePoints().toArray()) {
            String next=new String(Character.toChars(point));
            if(!line.isEmpty()&&measure(font,line+next,size)>width){result.add(line.toString());line.setLength(0);}
            line.append(next);
        }
        if(!line.isEmpty())result.add(line.toString());return result;
    }
    private static void block(PDPageContentStream out,PDFont font,String value,float max,float min,float width,int lines,float x,float top,Color color,boolean center) throws IOException {
        float size=max;List<String> wrapped=wrap(font,value,size,width);
        while(wrapped.size()>lines&&size>min){size=Math.max(min,size-.5f);wrapped=wrap(font,value,size,width);}
        if(wrapped.size()>lines)throw new IllegalArgumentException("Certificate text exceeds layout");
        for(int i=0;i<wrapped.size();i++){String line=wrapped.get(i);text(out,font,line,size,center?x-measure(font,line,size)/2:x,top-i*(size+5),color);}
    }
}
