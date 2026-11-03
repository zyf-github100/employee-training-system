package com.zhixing.training.certificate;

import com.zhixing.training.api.ApiException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class CertificateLinks {
    private final String base;
    public CertificateLinks(Environment environment) {
        String value=environment.getProperty("zhixing.public-web-url","").trim();
        if(value.isEmpty()&&environment.matchesProfiles("local","test"))value="http://127.0.0.1:5173";
        if(!value.isEmpty()) {
            URI uri=URI.create(value);
            if(!java.util.Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)throw new IllegalStateException("TRAINING_PUBLIC_WEB_URL must be an absolute HTTP(S) application URL without credentials, query or fragment");
        }
        base=value.replaceAll("/+$","");
    }
    public String verification(String code) {
        if(base.isEmpty())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CERTIFICATE_URL_UNCONFIGURED","证书验真地址尚未配置，请联系管理员配置 TRAINING_PUBLIC_WEB_URL");
        return base+"/#/verify-certificate?code="+code;
    }
    public String optionalVerification(String code) { return base.isEmpty()?null:verification(code); }
}
