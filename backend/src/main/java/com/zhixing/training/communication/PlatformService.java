package com.zhixing.training.communication;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.organization.DataScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.zhixing.training.communication.CommunicationDtos.*;

@Service
public class PlatformService {
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;
    public PlatformService(JdbcTemplate jdbc,DataScopeService scopes){this.jdbc=jdbc;this.scopes=scopes;}
    public PlatformView read() {
        return jdbc.queryForObject("SELECT * FROM platform_setting WHERE id=1",(rs,row)->new PlatformView(
                rs.getString("company_name"),rs.getString("contact_name"),rs.getString("contact_email"),
                rs.getBoolean("unread_reminders"),rs.getLong("version"),rs.getTimestamp("updated_at").toInstant()));
    }
    @Transactional
    public PlatformView save(long actor,PlatformRequest body) {
        scopes.lockMutations();scopes.requireSuper(actor);
        int changed=jdbc.update("UPDATE platform_setting SET company_name=?,contact_name=?,contact_email=?,unread_reminders=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=1 AND version=?",
                body.companyName().trim(),body.contactName().trim(),body.contactEmail().trim(),body.unreadReminders(),actor,body.version());
        if(changed!=1)throw new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","平台设置已更新，请重新读取后再保存");
        return read();
    }
}
