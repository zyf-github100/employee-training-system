package com.zhixing.training.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
            @NotNull @Size(max = 8) List<@NotBlank @Size(max = 300) String> options,
            @NotNull @Size(max = 8) List<@Min(0) Integer> answer,
            @Size(max = 5000) String analysis,
            @Size(max = 80) String category,
            @Size(max = 20) String difficulty,
            @Size(max = 20) String status,
            @Min(0) long version,
            @Size(max = 5000) String referenceAnswer,
            @Size(max = 5000) String gradingGuide
    ) {
        public QuestionRequest(String type, String stem, List<String> options, List<Integer> answer, String analysis,
                               String category, String difficulty, String status, long version) {
            this(type, stem, options, answer, analysis, category, difficulty, status, version, null, null);
        }
    }

    public record QuestionView(long id, String type, String stem, List<String> options, List<Integer> answer,
                               String analysis, String category, String difficulty, String status, long version,
                               String referenceAnswer, String gradingGuide) { }

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
                                    int points, String analysis, String referenceAnswer, String gradingGuide) {
        public PaperQuestionView(long id, long questionId, String type, String stem, List<String> options,
                                 int points, String analysis) {
            this(id, questionId, type, stem, options, points, analysis, null, null);
        }
    }

    public record ExamDetail(ExamView exam, List<PaperQuestionView> questions) { }

    public record AnswerRequest(long paperQuestionId, List<Integer> answer, @Size(max = 10000) String text) {
        public AnswerRequest(long paperQuestionId, List<Integer> answer) { this(paperQuestionId, answer, null); }
    }

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
                                long remainingSeconds, Map<Long, List<Integer>> answers,
                                Map<Long, String> textAnswers) {
        public AttemptDetail(AttemptView attempt, List<PaperQuestionView> questions,
                             long remainingSeconds, Map<Long, List<Integer>> answers) {
            this(attempt, questions, remainingSeconds, answers, Map.of());
        }
    }

    public record RandomRule(@NotBlank @Size(max = 20) String type, @Size(max = 80) String category,
                             @Size(max = 20) String difficulty, @Min(1) int count, @Min(1) int pointsEach) { }
    public record RandomExamRequest(@NotBlank @Size(max = 160) String title, @Size(max = 80) String category,
                                    @Min(1) @Max(180) int durationMinutes, @Min(1) @Max(100) int passScore,
                                    LocalDate deadline, @NotEmpty @Size(max = 20) List<@Valid RandomRule> rules) { }
    public record GradeItem(@Min(1) long paperQuestionId, @Min(0) int score, @Size(max = 1000) String comment,
                            @Min(0) long version) { }
    public record GradeRequest(@NotEmpty List<@Valid GradeItem> items) { }
    public record GradingQuestion(PaperQuestionView question, String answerText, Integer score, String comment,
                                  long version) { }
    public record GradingView(AttemptView attempt, List<GradingQuestion> questions) { }
    public record ImportRow(int rowNumber, String status, String message, long questionId) { }
    public record ImportResult(String requestId, int imported, int rejected, List<ImportRow> rows) { }
    public record IntegrityEventRequest(@NotBlank @Size(max=32) String eventType, java.time.Instant clientAt,
                                        @Size(max=2000) String metadata) { }
    public record IntegrityEventView(long id, int sequenceNo, String eventType, java.time.Instant clientAt,
                                     java.time.Instant serverAt, String metadata) { }
    public record IntegrityView(long attemptId, String status, List<IntegrityEventView> events) { }
    public record IntegrityDispositionRequest(@NotBlank @Pattern(regexp="CLEAR|REVIEW|DISMISSED") String status,
                                              @Size(max=1000) String reason) { }
}
