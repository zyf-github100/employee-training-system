package com.zhixing.training;

import com.zhixing.training.course.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test")
class CourseVersionGovernanceTests {
    @Autowired JdbcTemplate jdbc; @Autowired CourseRepository courses; @Autowired LearningRepository learning; @Autowired CourseVersionService versions;
    @Test void releasedSnapshotsKeepExistingLearnersOnTheirVersion() {
        long admin=jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'",Long.class);
        long first=employee("revision-a"); long second=employee("revision-b");
        long course=courses.insert(new CourseDtos.CourseRequest("Revision course","Summary",null,"QA","BEGINNER","DRAFT",10,0),admin);
        long chapter=learning.insertChapter(course,new LearningDtos.ChapterRequest("v1",1,"first",10));
        versions.publish(course,admin,new CourseVersionService.PublishRequest(courses.find(course).version(),"首次发布"));
        learning.enrollment(course,first);
        assertThat(learning.detail(course,first,true).chapters().get(0).title()).isEqualTo("v1");
        learning.updateChapter(course,chapter,new LearningDtos.ChapterRequest("v2",1,"second",10));
        assertThat(learning.detail(course,first,true).chapters().get(0).title()).isEqualTo("v1");
        versions.publish(course,admin,new CourseVersionService.PublishRequest(courses.find(course).version(),"第二版"));
        learning.enrollment(course,second);
        assertThat(learning.detail(course,first,true).chapters().get(0).title()).isEqualTo("v1");
        assertThat(learning.detail(course,second,true).chapters().get(0).title()).isEqualTo("v2");
    }
    private long employee(String prefix){String no=prefix+"-"+UUID.randomUUID().toString().substring(0,8);jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name) VALUES(?,'unused','revision user')",no);return jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?",Long.class,no);}
}
