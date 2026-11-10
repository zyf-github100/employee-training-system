package com.zhixing.training;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.course.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CourseEngagementTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseRepository courses;
    @Autowired LearningRepository learning;
    @Autowired EngagementRepository engagement;
    long admin;
    long employee;
    long course;

    @BeforeEach void fixture() {
        admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no='ADMIN001'", Long.class);
        employee = user("review-" + UUID.randomUUID().toString().substring(0, 8));
        course = courses.insert(new CourseDtos.CourseRequest("Engagement course", "Summary", null, "QA", "BEGINNER", "PUBLISHED", 5, 0), admin);
        learning.insertChapter(course, new LearningDtos.ChapterRequest("Chapter", 1, "Text", 5));
        learning.enrollment(course, employee);
    }

    @Test void oneReviewPerEmployeeIsUpdatedWithVersionProtection() {
        var first = engagement.upsertReview(course, employee, new CourseDtos.ReviewRequest(5, "Useful", 0));
        assertThat(first.rating()).isEqualTo(5);
        var second = engagement.upsertReview(course, employee, new CourseDtos.ReviewRequest(4, "Updated", first.version()));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.rating()).isEqualTo(4);
        assertThat(engagement.reviews(course, employee).reviewCount()).isEqualTo(1);
        assertThat(engagement.reviews(course, employee).averageRating()).isEqualTo(4.0);
        assertThatThrownBy(() -> engagement.upsertReview(course, employee, new CourseDtos.ReviewRequest(3, "Stale", first.version())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(409));
    }

    @Test void discussionsRequireEnrollmentAndAdminCanHideReplies() {
        var topic = engagement.createDiscussion(course, employee, new CourseDtos.DiscussionRequest("How do I start?", null));
        var reply = engagement.createDiscussion(course, employee, new CourseDtos.DiscussionRequest("Open the first chapter.", topic.id()));
        assertThat(engagement.discussions(course, employee, false)).extracting(CourseDtos.DiscussionView::id).containsExactly(topic.id(), reply.id());
        var hidden = engagement.setStatus(admin, topic.id(), new CourseDtos.DiscussionStatusRequest("HIDDEN", topic.version()));
        assertThat(hidden.status()).isEqualTo("HIDDEN");
        assertThat(engagement.discussions(course, employee, false)).extracting(CourseDtos.DiscussionView::id).containsExactly(reply.id());
        assertThat(engagement.discussions(course, admin, true)).extracting(CourseDtos.DiscussionView::id).contains(topic.id(), reply.id());
    }

    @Test void unenrolledEmployeeCannotRateOrDiscuss() {
        long outsider = user("outsider-" + UUID.randomUUID().toString().substring(0, 8));
        assertThatThrownBy(() -> engagement.upsertReview(course, outsider, new CourseDtos.ReviewRequest(5, "No", 0)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
        assertThatThrownBy(() -> engagement.createDiscussion(course, outsider, new CourseDtos.DiscussionRequest("No", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
    }

    private long user(String employeeNo) {
        jdbc.update("INSERT INTO sys_user(employee_no,password_hash,name) VALUES(?,'unused','QA user')", employeeNo);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE employee_no=?", Long.class, employeeNo);
    }
}
