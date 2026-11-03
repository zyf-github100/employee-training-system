package com.zhixing.training.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;

public final class ExamDtos {
    private ExamDtos() { }

    public record QuestionRequest(
            @NotBlank @Size(max = 20) String type,
            @NotBlank @Size(max = 1000) String stem,
            @NotEmpty @Size(min = 2, max = 8) List<@NotBlank @Size(max = 300) String> options,
            @NotEmpty @Size(min = 1, max = 8) List<@Min(0) Integer> answer,
            @Size(max = 5000) String analysis,
            @Size(max = 80) String category,
            @Size(max = 20) String difficulty,
            @Size(max = 20) String status,
            @Min(0) long version
    ) { }

    public record QuestionView(long id, String type, String stem, List<String> options, List<Integer> answer,
                               String analysis, String category, String difficulty, String status, long version) { }

    public record ExamQuestionRequest(@Min(1) long questionId, @Min(1) int points) { }

    public record ExamRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 80) String category,
            @Min(1) @Max(180) int durationMinutes,
            @Min(1) @Max(100) int passScore,
            LocalDate deadline,
            @NotEmpty @Size(max = 50) List<@Valid ExamQuestionRequest> questions
    ) { }

    public record ExamView(long id, String title, String category, int durationMinutes, int passScore,
                           LocalDate deadline, String status, int questionCount, int totalPoints,
                           int attemptCount, long version) { }

    public record PaperQuestionView(long id, long questionId, String type, String stem, List<String> options,
                                    int points, String analysis) { }

    public record ExamDetail(ExamView exam, List<PaperQuestionView> questions) { }

    public record AnswerRequest(long paperQuestionId, List<Integer> answer) { }

    public record SubmitRequest(@NotEmpty List<@Valid AnswerRequest> answers) { }

    /**
     * The browser sends the complete current answer sheet periodically. Keeping
     * the request separate from SubmitRequest makes it explicit that this
     * endpoint only saves progress and never changes the attempt status.
     */
    public record SaveAnswersRequest(@NotEmpty List<@Valid AnswerRequest> answers) { }

    public record AttemptView(long id, long paperId, String title, String status, Integer score, Boolean passed,
                              Instant startedAt, Instant submittedAt) { }

    public record AttemptDetail(AttemptView attempt, List<PaperQuestionView> questions,
                                long remainingSeconds, Map<Long, List<Integer>> answers) { }
}
