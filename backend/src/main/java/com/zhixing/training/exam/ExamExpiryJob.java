package com.zhixing.training.exam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "zhixing.exam.expiry-enabled", matchIfMissing = true)
public class ExamExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ExamExpiryJob.class);
    private final ExamRepository exams;
    public ExamExpiryJob(ExamRepository exams) { this.exams = exams; }

    // Each row uses its own transaction so a failed row cannot roll back the batch.
    @Scheduled(fixedDelayString = "${zhixing.exam.expiry-delay-ms:15000}", initialDelayString = "${zhixing.exam.expiry-delay-ms:15000}")
    public void expire() {
        for (var attempt : exams.expiredAttempts()) {
            try { exams.expireAttempt(attempt.id(), attempt.userId()); }
            catch (RuntimeException error) { log.error("Failed to expire attempt {}", attempt.id(), error); }
        }
    }
}
