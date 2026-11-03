package com.zhixing.training.exam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.training.api.ApiException;
import com.zhixing.training.plan.PlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class ExamRepository {
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };
    private static final TypeReference<List<Integer>> INTS = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PlanRepository plans;

    private final com.zhixing.training.organization.DataScopeService scopes;
    public ExamRepository(JdbcTemplate jdbc, ObjectMapper mapper, PlanRepository plans, com.zhixing.training.organization.DataScopeService scopes) { this.jdbc = jdbc; this.mapper = mapper; this.plans = plans; this.scopes=scopes; }

    public List<ExamDtos.QuestionView> questions(String keyword, String status) {
        StringBuilder sql = new StringBuilder("SELECT id,question_type,stem,options_json,answer_json,analysis,category,difficulty,status,version FROM question_item WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND (stem LIKE ? OR category LIKE ?)"); String k = "%" + keyword.trim() + "%"; args.add(k); args.add(k); }
        if (status != null && !status.isBlank()) { sql.append(" AND status=?"); args.add(status); }
        sql.append(" ORDER BY id DESC");
        return jdbc.query(sql.toString(), (rs, row) -> question(rs), args.toArray());
    }

    public Optional<ExamDtos.QuestionView> question(long id) {
        return jdbc.query("SELECT id,question_type,stem,options_json,answer_json,analysis,category,difficulty,status,version FROM question_item WHERE id=?", (rs, row) -> question(rs), id).stream().findFirst();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long createQuestion(ExamDtos.QuestionRequest body, long userId) {
        validateQuestion(body.type(), body.options(), body.answer());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO question_item(question_type,stem,options_json,answer_json,analysis,category,difficulty,status,created_by) VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, body.type()); ps.setString(2, body.stem().trim()); ps.setString(3, json(body.options())); ps.setString(4, json(normalize(body.answer())));
            ps.setString(5, body.analysis()); ps.setString(6, blank(body.category(), "通用能力")); ps.setString(7, blank(body.difficulty(), "基础")); ps.setString(8, blank(body.status(), "ENABLED")); ps.setLong(9, userId); return ps;
        }, key);
        return key.getKey().longValue();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateQuestion(long id, ExamDtos.QuestionRequest body) {
        validateQuestion(body.type(), body.options(), body.answer());
        int updated = jdbc.update("UPDATE question_item SET question_type=?,stem=?,options_json=?,answer_json=?,analysis=?,category=?,difficulty=?,status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?", body.type(), body.stem().trim(), json(body.options()), json(normalize(body.answer())), body.analysis(), blank(body.category(), "通用能力"), blank(body.difficulty(), "基础"), blank(body.status(), "ENABLED"), id, body.version());
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "题目已被其他人修改，请刷新后重试");
    }

    public List<ExamDtos.ExamView> exams(boolean admin, long userId) {
        String where = admin ? (scopes.resolve(userId).all()?"":" AND (ep.status='PUBLISHED' OR ep.created_by="+userId+")") : " AND ep.status='PUBLISHED' AND EXISTS (SELECT 1 FROM exam_assignment ea WHERE ea.paper_id=ep.id AND ea.user_id=?)";
        String attempts = admin ? "(SELECT COUNT(*) FROM exam_attempt ea JOIN sys_user au ON au.id=ea.user_id WHERE ea.paper_id=ep.id AND "+scopes.resolve(userId).userCondition("au")+")" : "(SELECT COUNT(*) FROM exam_attempt ea WHERE ea.paper_id=ep.id AND ea.user_id=?)";
        List<Object> args = admin ? List.of() : List.of(userId, userId);
        return jdbc.query("SELECT ep.id,ep.title,ep.category,ep.duration_minutes,ep.pass_score,ep.deadline,ep.status,ep.version,(SELECT COUNT(*) FROM exam_paper_question pq WHERE pq.paper_id=ep.id) question_count,(SELECT COALESCE(SUM(points),0) FROM exam_paper_question pq WHERE pq.paper_id=ep.id) total_points," + attempts + " attempt_count FROM exam_paper ep WHERE 1=1" + where + " ORDER BY ep.id DESC", (rs, row) -> exam(rs), args.toArray());
    }

    public Optional<ExamDtos.ExamDetail> detail(long id, boolean includeAnswer, long userId) {
        String visibility = includeAnswer ? (scopes.resolve(userId).all()?"":" AND (ep.status='PUBLISHED' OR ep.created_by="+userId+")") : " AND ep.status='PUBLISHED' AND EXISTS (SELECT 1 FROM exam_assignment ax WHERE ax.paper_id=ep.id AND ax.user_id=?)";
        List<Object> args = includeAnswer ? List.of(userId, id) : List.of(userId, id, userId);
        List<ExamDtos.ExamView> exams = jdbc.query("SELECT ep.id,ep.title,ep.category,ep.duration_minutes,ep.pass_score,ep.deadline,ep.status,ep.version,(SELECT COUNT(*) FROM exam_paper_question pq WHERE pq.paper_id=ep.id) question_count,(SELECT COALESCE(SUM(points),0) FROM exam_paper_question pq WHERE pq.paper_id=ep.id) total_points,(SELECT COUNT(*) FROM exam_attempt ea WHERE ea.paper_id=ep.id AND ea.user_id=?) attempt_count FROM exam_paper ep WHERE ep.id=?" + visibility, (rs, row) -> exam(rs), args.toArray());
        if (exams.isEmpty()) return Optional.empty();
        List<ExamDtos.PaperQuestionView> questions = paperQuestions(id, includeAnswer);
        return Optional.of(new ExamDtos.ExamDetail(exams.get(0), questions));
    }

    private List<ExamDtos.PaperQuestionView> paperQuestions(long paperId, boolean review) {
        return jdbc.query("SELECT id,question_id,question_type,stem,options_json,points," +
                (review ? "analysis" : "NULL AS analysis") +
                " FROM exam_paper_question WHERE paper_id=? ORDER BY sort_no,id", (rs, row) -> paperQuestion(rs), paperId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long createExam(ExamDtos.ExamRequest body, long userId) {
        validateExam(body);
        KeyHolder key = new GeneratedKeyHolder();
        LocalDate deadline = body.deadline() == null ? LocalDate.now().plusDays(30) : body.deadline();
        jdbc.update(connection -> { PreparedStatement ps = connection.prepareStatement("INSERT INTO exam_paper(title,category,duration_minutes,pass_score,deadline,created_by) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1, body.title().trim()); ps.setString(2, blank(body.category(), "综合测评")); ps.setInt(3, body.durationMinutes()); ps.setInt(4, body.passScore()); ps.setObject(5, deadline); ps.setLong(6, userId); return ps; }, key);
        long id = key.getKey().longValue();
        int sort = 1;
        for (ExamDtos.ExamQuestionRequest q : body.questions()) { jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,sort_no,points,question_type,stem,options_json,answer_json,analysis) SELECT ?,id,?,?,question_type,stem,options_json,answer_json,analysis FROM question_item WHERE id=? AND status='ENABLED'", id, sort++, q.points(), q.questionId()); }
        int count = count("SELECT COUNT(*) FROM exam_paper_question WHERE paper_id=?", id);
        if (count != body.questions().size()) throw new ApiException(HttpStatus.BAD_REQUEST, "QUESTION_NOT_AVAILABLE", "试卷包含不存在或已停用的题目");
        return id;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExamDtos.ExamDetail publish(long id, long userId) {
        scopes.lockMutations();scopes.requireContentOwner(userId,"exam_paper",id);
        int count = count("SELECT COUNT(*) FROM exam_paper_question WHERE paper_id=?", id);
        int points = integer("SELECT COALESCE(SUM(points),0) FROM exam_paper_question WHERE paper_id=?", id);
        if (count == 0 || count > 50 || points != 100) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAPER", "试卷题目数量必须为 1-50，且总分必须为 100");
        int updated = jdbc.update("UPDATE exam_paper SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND status='DRAFT'", id);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "EXAM_ALREADY_PUBLISHED", "试卷不存在或已经发布");
        jdbc.update("INSERT INTO exam_assignment(paper_id,user_id) SELECT ?,u.id FROM sys_user u WHERE u.role_code='EMPLOYEE' AND u.activated=TRUE AND u.status='ACTIVE' AND "+scopes.resolve(userId).userCondition("u")+" ON DUPLICATE KEY UPDATE paper_id=VALUES(paper_id)", id);
        return detail(id, true, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EXAM_NOT_FOUND", "试卷不存在"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExamDtos.AttemptDetail begin(long paperId, long userId) {
        ExamDtos.ExamDetail detail = detail(paperId, false, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EXAM_NOT_FOUND", "考试不存在或未分配"));
        if (!"PUBLISHED".equals(detail.exam().status()) || detail.exam().deadline().isBefore(LocalDate.now())) throw new ApiException(HttpStatus.BAD_REQUEST, "EXAM_CLOSED", "考试未发布或已截止");
        // Serialize starts for the same assignment. This prevents two browser
        // tabs (or a retry after a network timeout) from creating two attempts.
        jdbc.queryForList("SELECT id FROM exam_assignment WHERE paper_id=? AND user_id=? FOR UPDATE", Long.class, paperId, userId);
        Optional<ExamDtos.AttemptView> inProgress = jdbc.query("SELECT ea.id,ea.paper_id,ep.title,ea.status,ea.score,ea.passed,ea.started_at,ea.submitted_at FROM exam_attempt ea JOIN exam_paper ep ON ep.id=ea.paper_id WHERE ea.paper_id=? AND ea.user_id=? AND ea.status='IN_PROGRESS' ORDER BY ea.id DESC LIMIT 1", (rs, row) -> new ExamDtos.AttemptView(rs.getLong("id"),rs.getLong("paper_id"),rs.getString("title"),rs.getString("status"), (Integer) rs.getObject("score"), (Boolean) rs.getObject("passed"),rs.getTimestamp("started_at").toInstant(),rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant()), paperId, userId).stream().findFirst();
        if (inProgress.isPresent()) {
            ExamDtos.AttemptDetail current = attemptDetail(inProgress.get().id(), userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "答卷不存在"));
            // Returning the terminal result also commits any expiry performed
            // above. Throwing ATTEMPT_LIMIT here would roll that expiry back.
            return current;
        }
        int max = integer("SELECT COALESCE(MAX(max_attempts),1) FROM exam_assignment WHERE paper_id=? AND user_id=?", paperId, userId);
        int attempts = integer("SELECT COUNT(*) FROM exam_attempt WHERE paper_id=? AND user_id=?", paperId, userId);
        if (attempts >= max) throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_LIMIT", "已达到考试次数上限");
        KeyHolder key = new GeneratedKeyHolder();
        int attemptNo = attempts + 1;
        jdbc.update(connection -> { PreparedStatement ps = connection.prepareStatement("INSERT INTO exam_attempt(paper_id,user_id,attempt_no) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setLong(1, paperId); ps.setLong(2, userId); ps.setInt(3, attemptNo); return ps; }, key);
        ExamDtos.AttemptView attempt = attempt(key.getKey().longValue());
        return new ExamDtos.AttemptDetail(attempt, detail.questions(), Math.max(0, detail.exam().durationMinutes() * 60L), Map.of());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<ExamDtos.AttemptView> activeAttempts(long userId) {
        List<ExamDtos.AttemptView> attempts = jdbc.query("SELECT ea.id,ea.paper_id,ep.title,ea.status,ea.score,ea.passed,ea.started_at,ea.submitted_at FROM exam_attempt ea JOIN exam_paper ep ON ep.id=ea.paper_id WHERE ea.user_id=? AND ea.status='IN_PROGRESS' ORDER BY ea.started_at DESC", (rs, row) -> new ExamDtos.AttemptView(rs.getLong("id"),rs.getLong("paper_id"),rs.getString("title"),rs.getString("status"), (Integer) rs.getObject("score"), (Boolean) rs.getObject("passed"),rs.getTimestamp("started_at").toInstant(),rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant()), userId);
        // Touch each attempt so a page refresh after the deadline also gets a
        // terminal result and does not leave stale in-progress rows around.
        for (ExamDtos.AttemptView attempt : attempts) expireIfNeeded(attempt.id(), userId);
        return attempts.stream().map(attempt -> attemptForUser(attempt.id(), userId).orElse(attempt)).filter(attempt -> "IN_PROGRESS".equals(attempt.status())).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExamDtos.AttemptDetail saveAnswers(long attemptId, long userId, ExamDtos.SaveAnswersRequest body) {
        lockAttempt(attemptId, userId);
        ExamDtos.AttemptView existing = attemptForUser(attemptId, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "答卷不存在"));
        if ("SUBMITTED".equals(existing.status())) return attemptDetail(attemptId, userId).orElseThrow();
        if (expireIfNeeded(attemptId, userId)) return attemptDetail(attemptId, userId).orElseThrow();

        List<ExamQuestionRow> questions = jdbc.query("SELECT pq.id,pq.points,pq.answer_json,pq.options_json FROM exam_paper_question pq JOIN exam_attempt ea ON ea.paper_id=pq.paper_id WHERE ea.id=? ORDER BY pq.sort_no", (rs, row) -> new ExamQuestionRow(rs.getLong("id"), rs.getInt("points"), parseInts(rs.getString("answer_json")), parseStrings(rs.getString("options_json")).size()), attemptId);
        Set<Long> valid = questions.stream().map(ExamQuestionRow::id).collect(java.util.stream.Collectors.toSet());
        for (ExamDtos.AnswerRequest answer : body.answers()) {
            if (!valid.contains(answer.paperQuestionId())) continue;
            ExamQuestionRow question = questions.stream().filter(item -> item.id() == answer.paperQuestionId()).findFirst().orElseThrow();
            List<Integer> normalized = normalize(answer.answer());
            if (normalized.stream().anyMatch(index -> index < 0 || index >= question.optionCount())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "答案选项索引不合法");
            jdbc.update("INSERT INTO exam_answer(attempt_id,paper_question_id,answer_json,is_correct,score) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE answer_json=VALUES(answer_json),is_correct=NULL,score=NULL,answered_at=CURRENT_TIMESTAMP", attemptId, question.id(), json(normalized), null, null);
        }
        return attemptDetail(attemptId, userId).orElseThrow();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExamDtos.AttemptView submit(long attemptId, long userId, ExamDtos.SubmitRequest body) {
        lockAttempt(attemptId, userId);
        ExamDtos.AttemptView existing = attemptForUser(attemptId, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "答卷不存在"));
        if ("SUBMITTED".equals(existing.status())) return existing;
        // Once the server deadline has passed, grade the latest persisted
        // snapshot and ignore any late client payload.
        if (expireIfNeeded(attemptId, userId)) return attempt(attemptId);
        List<ExamQuestionRow> questions = jdbc.query("SELECT pq.id,pq.points,pq.answer_json,pq.options_json FROM exam_paper_question pq JOIN exam_attempt ea ON ea.paper_id=pq.paper_id WHERE ea.id=? ORDER BY pq.sort_no", (rs, row) -> new ExamQuestionRow(rs.getLong("id"), rs.getInt("points"), parseInts(rs.getString("answer_json")), parseStrings(rs.getString("options_json")).size()), attemptId);
        Set<Long> valid = questions.stream().map(ExamQuestionRow::id).collect(java.util.stream.Collectors.toSet());
        for (ExamDtos.AnswerRequest answer : body.answers()) {
            if (!valid.contains(answer.paperQuestionId())) continue;
            ExamQuestionRow q = questions.stream().filter(item -> item.id() == answer.paperQuestionId()).findFirst().orElseThrow();
            List<Integer> normalized = normalize(answer.answer());
            if (normalized.stream().anyMatch(index -> index < 0 || index >= q.optionCount())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "答案选项索引不合法");
            boolean correct = same(normalized, q.answer()); int score = correct ? q.points() : 0;
            jdbc.update("INSERT INTO exam_answer(attempt_id,paper_question_id,answer_json,is_correct,score) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE answer_json=VALUES(answer_json),is_correct=VALUES(is_correct),score=VALUES(score),answered_at=CURRENT_TIMESTAMP", attemptId, q.id(), json(normalized), correct, score);
        }
        return finalizeAttempt(attemptId, userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<ExamDtos.AttemptDetail> attemptDetail(long attemptId, long userId) {
        if (!lockAttempt(attemptId, userId)) return Optional.empty();
        Optional<ExamDtos.AttemptView> item = attemptForUser(attemptId, userId); if (item.isEmpty()) return Optional.empty();
        if ("IN_PROGRESS".equals(item.get().status()) && expireIfNeeded(attemptId, userId)) item = attemptForUser(attemptId, userId);
        long paperId = item.get().paperId(); ExamDtos.ExamDetail exam = detail(paperId, false, userId).orElseThrow();
        long remaining = 0;
        Timestamp start = jdbc.queryForObject("SELECT started_at FROM exam_attempt WHERE id=?", Timestamp.class, attemptId);
        if (start != null && "IN_PROGRESS".equals(item.get().status())) remaining = Math.max(0, exam.exam().durationMinutes() * 60L - (Instant.now().getEpochSecond() - start.toInstant().getEpochSecond()));
        Map<Long, List<Integer>> answers = new LinkedHashMap<>();
        jdbc.queryForList("SELECT paper_question_id,answer_json FROM exam_answer WHERE attempt_id=? ORDER BY paper_question_id", attemptId).forEach(row -> answers.put(((Number) row.get("paper_question_id")).longValue(), parseInts(String.valueOf(row.get("answer_json")))));
        return Optional.of(new ExamDtos.AttemptDetail(item.get(), paperQuestions(paperId, "SUBMITTED".equals(item.get().status())), remaining, answers));
    }

    public ExamDtos.AttemptView attempt(long id) { return jdbc.queryForObject("SELECT ea.id,ea.paper_id,ep.title,ea.status,ea.score,ea.passed,ea.started_at,ea.submitted_at FROM exam_attempt ea JOIN exam_paper ep ON ep.id=ea.paper_id WHERE ea.id=?", (rs, row) -> new ExamDtos.AttemptView(rs.getLong("id"),rs.getLong("paper_id"),rs.getString("title"),rs.getString("status"), (Integer) rs.getObject("score"), (Boolean) rs.getObject("passed"),rs.getTimestamp("started_at").toInstant(),rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant()), id); }
    private Optional<ExamDtos.AttemptView> attemptForUser(long id,long userId){return jdbc.query("SELECT ea.id,ea.paper_id,ep.title,ea.status,ea.score,ea.passed,ea.started_at,ea.submitted_at FROM exam_attempt ea JOIN exam_paper ep ON ep.id=ea.paper_id WHERE ea.id=? AND ea.user_id=?",(rs,row)->new ExamDtos.AttemptView(rs.getLong("id"),rs.getLong("paper_id"),rs.getString("title"),rs.getString("status"),(Integer)rs.getObject("score"),(Boolean)rs.getObject("passed"),rs.getTimestamp("started_at").toInstant(),rs.getTimestamp("submitted_at")==null?null:rs.getTimestamp("submitted_at").toInstant()),id,userId).stream().findFirst();}
    private ExamDtos.AttemptView finalizeAttempt(long attemptId, long userId) {
        // Lock the attempt row so duplicate submit requests converge on one
        // terminal result even when two tabs submit at the same time.
        String status = jdbc.queryForObject("SELECT status FROM exam_attempt WHERE id=? AND user_id=? FOR UPDATE", String.class, attemptId, userId);
        if ("SUBMITTED".equals(status)) return attempt(attemptId);
        List<ExamQuestionRow> questions = jdbc.query("SELECT pq.id,pq.points,pq.answer_json FROM exam_paper_question pq JOIN exam_attempt ea ON ea.paper_id=pq.paper_id WHERE ea.id=? ORDER BY pq.sort_no", (rs, row) -> new ExamQuestionRow(rs.getLong("id"), rs.getInt("points"), parseInts(rs.getString("answer_json")), 0), attemptId);
        int earned = 0;
        for (ExamQuestionRow question : questions) {
            List<Integer> answer = jdbc.query("SELECT answer_json FROM exam_answer WHERE attempt_id=? AND paper_question_id=?", (rs, row) -> parseInts(rs.getString("answer_json")), attemptId, question.id()).stream().findFirst().orElse(List.of());
            boolean correct = same(answer, question.answer());
            int score = correct ? question.points() : 0;
            earned += score;
            jdbc.update("UPDATE exam_answer SET is_correct=?,score=? WHERE attempt_id=? AND paper_question_id=?", correct, score, attemptId, question.id());
        }
        int total = integer("SELECT COALESCE(SUM(points),0) FROM exam_paper_question pq JOIN exam_attempt ea ON ea.paper_id=pq.paper_id WHERE ea.id=?", attemptId);
        int score = total == 0 ? 0 : Math.round(earned * 100f / total);
        int pass = integer("SELECT pass_score FROM exam_paper ep JOIN exam_attempt ea ON ea.paper_id=ep.id WHERE ea.id=?", attemptId);
        jdbc.update("UPDATE exam_attempt SET status='SUBMITTED',score=?,passed=?,submitted_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status='IN_PROGRESS'", score, score >= pass, attemptId, userId);
        plans.refreshForExam(attempt(attemptId).paperId(), userId);
        return attempt(attemptId);
    }
    private boolean expireIfNeeded(long attemptId, long userId) {
        if (!lockAttempt(attemptId, userId)) return false;
        List<AttemptTiming> timings = jdbc.query("SELECT ea.status,ea.started_at,ep.duration_minutes FROM exam_attempt ea JOIN exam_paper ep ON ep.id=ea.paper_id WHERE ea.id=? AND ea.user_id=?", (rs, row) -> new AttemptTiming(rs.getString("status"), rs.getTimestamp("started_at").toInstant(), rs.getInt("duration_minutes")), attemptId, userId);
        if (timings.isEmpty() || !"IN_PROGRESS".equals(timings.get(0).status())) return false;
        AttemptTiming timing = timings.get(0);
        if (!Instant.now().isBefore(timing.startedAt().plusSeconds(timing.durationMinutes() * 60L))) {
            finalizeAttempt(attemptId, userId);
            return true;
        }
        return false;
    }
    private boolean lockAttempt(long attemptId, long userId) {
        return !jdbc.queryForList("SELECT id FROM exam_attempt WHERE id=? AND user_id=? FOR UPDATE", Long.class, attemptId, userId).isEmpty();
    }

    public List<ExpiredAttempt> expiredAttempts() {
        return jdbc.query("SELECT ea.id,ea.user_id FROM exam_attempt ea JOIN exam_paper ep ON ep.id=ea.paper_id WHERE ea.status='IN_PROGRESS' AND TIMESTAMPADD(MINUTE,ep.duration_minutes,ea.started_at)<=CURRENT_TIMESTAMP ORDER BY ea.started_at,ea.id LIMIT 100",
                (rs, row) -> new ExpiredAttempt(rs.getLong("id"), rs.getLong("user_id")));
    }

    public List<ExamDtos.AttemptView> myAttempts(long userId) {
        return jdbc.queryForList("SELECT id FROM exam_attempt WHERE user_id=? ORDER BY id DESC LIMIT 100", Long.class, userId).stream().map(this::attempt).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void expireAttempt(long attemptId, long userId) { expireIfNeeded(attemptId, userId); }

    public record ExpiredAttempt(long id, long userId) { }
    private ExamDtos.QuestionView question(java.sql.ResultSet rs) throws java.sql.SQLException { return new ExamDtos.QuestionView(rs.getLong("id"),rs.getString("question_type"),rs.getString("stem"),parseStrings(rs.getString("options_json")),parseInts(rs.getString("answer_json")),rs.getString("analysis"),rs.getString("category"),rs.getString("difficulty"),rs.getString("status"),rs.getLong("version")); }
    private ExamDtos.ExamView exam(java.sql.ResultSet rs) throws java.sql.SQLException { return new ExamDtos.ExamView(rs.getLong("id"),rs.getString("title"),rs.getString("category"),rs.getInt("duration_minutes"),rs.getInt("pass_score"),rs.getObject("deadline",LocalDate.class),rs.getString("status"),rs.getInt("question_count"),rs.getInt("total_points"),rs.getInt("attempt_count"),rs.getLong("version")); }
    private ExamDtos.PaperQuestionView paperQuestion(java.sql.ResultSet rs) throws java.sql.SQLException { return new ExamDtos.PaperQuestionView(rs.getLong("id"),rs.getLong("question_id"),rs.getString("question_type"),rs.getString("stem"),parseStrings(rs.getString("options_json")),rs.getInt("points"),rs.getString("analysis")); }
    private record ExamQuestionRow(long id,int points,List<Integer> answer,int optionCount) { }
    private record AttemptTiming(String status, Instant startedAt, int durationMinutes) { }
    private void validateQuestion(String type,List<String> options,List<Integer> answer){if(!Set.of("single","multiple","boolean").contains(type))throw bad("题目类型仅支持 single、multiple、boolean");if(options==null||options.size()<2||options.stream().map(String::trim).filter(s->!s.isBlank()).distinct().count()!=options.size())throw bad("选项至少 2 个且不能重复");List<Integer> normalized=normalize(answer);if(normalized.stream().anyMatch(i->i<0||i>=options.size()))throw bad("答案选项索引不合法");if(("single".equals(type)||"boolean".equals(type))&&normalized.size()!=1)throw bad("单选和判断题只能有一个答案");if("multiple".equals(type)&&normalized.size()<2)throw bad("多选题至少需要两个答案");if("boolean".equals(type)&&options.size()!=2)throw bad("判断题必须有两个选项");}
    private void validateExam(ExamDtos.ExamRequest b){if(b.deadline()!=null&&b.deadline().isBefore(LocalDate.now()))throw bad("截止日期不能早于今天");Set<Long> ids=new HashSet<>();int points=0;for(var q:b.questions()){if(!ids.add(q.questionId()))throw bad("试卷不能重复引用题目");points+=q.points();}if(points!=100)throw bad("试卷总分必须为 100");}
    private static ApiException bad(String m){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",m);}
    private int count(String sql,Object...args){return integer(sql,args);}
    private int integer(String sql,Object...args){Integer n=jdbc.queryForObject(sql,Integer.class,args);return n==null?0:n;}
    private String blank(String s,String fallback){return s==null||s.isBlank()?fallback:s.trim();}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private List<String> parseStrings(String s){try{return mapper.readValue(s,STRINGS);}catch(Exception e){return List.of();}}
    private List<Integer> parseInts(String s){try{return mapper.readValue(s,INTS);}catch(Exception e){return List.of();}}
    private List<Integer> normalize(List<Integer> values){return values==null?List.of():values.stream().filter(Objects::nonNull).distinct().sorted().toList();}
    private boolean same(List<Integer> a,List<Integer> b){return normalize(a).equals(normalize(b));}
}
