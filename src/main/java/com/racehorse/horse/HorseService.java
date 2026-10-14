package com.racehorse.horse;

import com.racehorse.audit.AuditService;
import com.racehorse.identity.RoleCode;
import com.racehorse.integration.ActivationHook;
import com.racehorse.integration.Db;
import com.racehorse.restriction.RestrictionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DEMO IMPLEMENTATION of Flow 1 (flow1-horse.openapi.yaml, CORE-004 contract; implementation tasks CORE-011/012
 * owned by Dũng). DRAFT → ACTIVE is automatic when IDENTITY, PEDIGREE_AND_OWNER, STABLE_BOX and VET_BASELINE are
 * complete (DEC-009 §1). The Head Trainer readiness review is non-blocking and only audited (no content persisted).
 */
@Service
public class HorseService implements ActivationHook {
    public record UserRef(long id, String userCode, String fullName) {}
    public record ParentRef(Long horseId, String horseCode, String name) {}
    public record Pedigree(ParentRef sire, ParentRef dam) {}
    public record Ownership(long id, UserRef owner, BigDecimal sharePercentage, LocalDate effectiveFrom,
                            LocalDate effectiveTo, boolean isManagingOwner) {}
    public record StableAssignment(long id, long stableBoxId, String boxCode, String yard, LocalDate effectiveFrom,
                                   LocalDate effectiveTo, String reason, UserRef assignedBy) {}
    public record WeightPoint(BigDecimal weightKg, Instant measuredAt) {}
    public record HorseSummary(long id, String horseCode, String name, String breed, String sex, LocalDate foaledDate,
                               String lifecycle, String healthStatus, String trainingAvailability,
                               String currentBoxCode, String primaryPhotoUrl) {}
    public record HorseDetail(long id, String horseCode, String name, String breed, String sex, LocalDate foaledDate,
                              String lifecycle, String healthStatus, String trainingAvailability, String currentBoxCode,
                              String primaryPhotoUrl, String countryOfOrigin, String registrationNo, Instant activatedAt,
                              Pedigree pedigree, List<Ownership> currentOwnerships, StableAssignment currentAssignment,
                              WeightPoint latestWeight, Instant createdAt, Instant updatedAt) {}
    public record IdentityRequest(String horseCode, String name, String breed, String sex, LocalDate foaledDate,
                                  String countryOfOrigin, String registrationNo) {}
    public record PedigreeRequest(ParentRef sire, ParentRef dam) {}
    public record OwnershipRequest(Long ownerUserId, BigDecimal sharePercentage, LocalDate effectiveFrom,
                                   Boolean isManagingOwner) {}
    public record AssignmentRequest(Long stableBoxId, LocalDate effectiveFrom, String reason) {}
    public record StableBox(long id, String boxCode, String yard, String status, Map<String, Object> currentHorse) {}
    public record ChecklistItem(String item, boolean mandatory, boolean complete, String detail) {}
    public record ReadinessReview(String recommendation, boolean blocking, boolean reviewed, Instant lastReviewedAt) {}
    public record ActivationStatus(long horseId, String horseCode, String name, String lifecycle, Instant activatedAt,
                                   List<ChecklistItem> checklist, ReadinessReview readinessReview) {}
    public record Page<T>(List<T> items, int page, int size, long totalElements) {}

    private static final RoleCode[] READERS = {RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER, RoleCode.TRAINER,
            RoleCode.VETERINARIAN, RoleCode.GROOM};

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final RestrictionService restrictions;

    public HorseService(JdbcTemplate jdbc, AuditService audit, RestrictionService restrictions) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.restrictions = restrictions;
    }

    // ---------------- reads ----------------

    @Transactional(readOnly = true)
    public Page<HorseSummary> list(String q, String lifecycle, String availability, int page, int size) {
        Db.require(READERS);
        if (page < 0 || size < 1 || size > 100) throw Db.invalid("Invalid page or size.");
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (q != null && !q.isBlank()) {
            where.append(" AND (h.name LIKE ? OR h.horse_code LIKE ?)");
            args.add("%" + q.trim() + "%");
            args.add("%" + q.trim() + "%");
        }
        if (lifecycle != null && !lifecycle.isBlank()) {
            where.append(" AND h.lifecycle=?");
            args.add(lifecycle);
        }
        List<HorseSummary> all = jdbc.query("SELECT h.* FROM dbo.horse h" + where + " ORDER BY h.name, h.id",
                (r, n) -> summary(r), args.toArray());
        if (availability != null && !availability.isBlank())
            all = all.stream().filter(h -> availability.equals(h.trainingAvailability())).toList();
        int from = Math.min(page * size, all.size());
        return new Page<>(all.subList(from, Math.min(from + size, all.size())), page, size, all.size());
    }

    @Transactional(readOnly = true)
    public HorseDetail detail(long id) {
        Db.require(READERS);
        return jdbc.query("SELECT * FROM dbo.horse WHERE id=?", (r, n) -> detailRow(r), id).stream().findFirst()
                .orElseThrow(() -> Db.notFound("Horse"));
    }

    @Transactional(readOnly = true)
    public List<StableBox> boxes() {
        Db.require(RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER);
        return jdbc.query("SELECT b.id, b.box_code, b.yard, h.id AS horse_id, h.horse_code, h.name FROM dbo.stable_box b "
                + "LEFT JOIN dbo.horse_stable_assignment a ON a.stable_box_id=b.id AND a.effective_to IS NULL "
                + "LEFT JOIN dbo.horse h ON h.id=a.horse_id ORDER BY b.yard, b.box_code", (r, n) -> {
                    Long horseId = Db.lng(r, "horse_id");
                    return new StableBox(r.getLong("id"), r.getString("box_code"), r.getString("yard"),
                            horseId == null ? "FREE" : "OCCUPIED", horseId == null ? null
                            : Map.of("id", horseId, "horseCode", r.getString("horse_code"), "name", r.getString("name")));
                });
    }

    @Transactional(readOnly = true)
    public List<Ownership> ownerships(long horseId) {
        Db.require(RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER);
        requireHorse(horseId);
        return ownershipRows(horseId, false);
    }

    @Transactional(readOnly = true)
    public List<StableAssignment> assignments(long horseId) {
        Db.require(RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER);
        requireHorse(horseId);
        return assignmentRows(horseId, false);
    }

    @Transactional(readOnly = true)
    public List<UserRef> owners() {
        Db.require(RoleCode.CLUB_MANAGER);
        return jdbc.query("SELECT u.id, u.user_code, u.full_name FROM dbo.app_user u JOIN dbo.user_role ur ON ur.user_id=u.id "
                + "JOIN dbo.role r ON r.id=ur.role_id WHERE r.role_code='HORSE_OWNER' AND u.account_status='ACTIVE' "
                + "ORDER BY u.full_name", (r, n) -> new UserRef(r.getLong(1), r.getString(2), r.getString(3)));
    }

    @Transactional(readOnly = true)
    public List<ActivationStatus> pending() {
        Db.require(RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER, RoleCode.VETERINARIAN);
        return jdbc.queryForList("SELECT id FROM dbo.horse WHERE lifecycle='DRAFT' ORDER BY created_at, id", Long.class)
                .stream().map(this::statusOf).toList();
    }

    @Transactional(readOnly = true)
    public ActivationStatus activationStatus(long horseId) {
        Db.require(RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER, RoleCode.VETERINARIAN);
        requireHorse(horseId);
        return statusOf(horseId);
    }

    // ---------------- writes ----------------

    @Transactional
    public HorseDetail create(IdentityRequest req) {
        Db.require(RoleCode.CLUB_MANAGER);
        validateIdentity(req);
        long id;
        try {
            id = Db.insert(jdbc, "INSERT INTO dbo.horse (horse_code, name, breed, sex, foaled_date, country_of_origin, "
                            + "registration_no, lifecycle, created_at) VALUES (?,?,?,?,?,?,?,'DRAFT',?)",
                    req.horseCode().trim(), req.name().trim(), req.breed(), req.sex(), req.foaledDate(),
                    req.countryOfOrigin(), req.registrationNo(), Db.nowUtc());
        } catch (DuplicateKeyException e) {
            throw Db.conflict("Horse code " + req.horseCode() + " already exists.");
        }
        audit.record("HORSE_CREATED", "horse", id, "code=" + req.horseCode());
        return detail(id);
    }

    @Transactional
    public HorseDetail update(long id, IdentityRequest req) {
        Db.require(RoleCode.CLUB_MANAGER);
        requireHorse(id);
        validateIdentity(req);
        try {
            jdbc.update("UPDATE dbo.horse SET horse_code=?, name=?, breed=?, sex=?, foaled_date=?, country_of_origin=?, "
                            + "registration_no=?, updated_at=? WHERE id=?", req.horseCode().trim(), req.name().trim(),
                    req.breed(), req.sex(), req.foaledDate(), req.countryOfOrigin(), req.registrationNo(), Db.nowUtc(), id);
        } catch (DuplicateKeyException e) {
            throw Db.conflict("Horse code " + req.horseCode() + " already exists.");
        }
        evaluate(id);
        return detail(id);
    }

    @Transactional
    public HorseDetail pedigree(long id, PedigreeRequest req) {
        Db.require(RoleCode.CLUB_MANAGER);
        requireHorse(id);
        if (req == null || req.sire() == null || req.dam() == null) throw Db.invalid("sire and dam are required.");
        Object[] sire = parent(id, req.sire(), "sire");
        Object[] dam = parent(id, req.dam(), "dam");
        if (sire[0] != null && sire[0].equals(dam[0])) throw Db.invalid("Sire and dam must differ.");
        jdbc.update("UPDATE dbo.horse SET sire_horse_id=?, sire_name=?, dam_horse_id=?, dam_name=?, updated_at=? WHERE id=?",
                sire[0], sire[1], dam[0], dam[1], Db.nowUtc(), id);
        evaluate(id);
        return detail(id);
    }

    @Transactional
    public Ownership addOwnership(long id, OwnershipRequest req) {
        Db.require(RoleCode.CLUB_MANAGER);
        requireHorse(id);
        if (req == null || req.ownerUserId() == null || req.sharePercentage() == null || req.effectiveFrom() == null)
            throw Db.invalid("ownerUserId, sharePercentage and effectiveFrom are required.");
        if (req.sharePercentage().signum() <= 0 || req.sharePercentage().compareTo(BigDecimal.valueOf(100)) > 0)
            throw Db.invalid("sharePercentage must be > 0 and ≤ 100.");
        Integer isOwner = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.user_role ur JOIN dbo.role r ON r.id=ur.role_id "
                + "WHERE ur.user_id=? AND r.role_code='HORSE_OWNER'", Integer.class, req.ownerUserId());
        if (isOwner == null || isOwner == 0) throw Db.invalid("The selected user is not a Horse Owner.");
        BigDecimal current = jdbc.queryForObject("SELECT COALESCE(SUM(share_percentage),0) FROM dbo.horse_ownership "
                + "WHERE horse_id=? AND effective_to IS NULL", BigDecimal.class, id);
        if (current.add(req.sharePercentage()).compareTo(BigDecimal.valueOf(100)) > 0)
            throw Db.conflict("Total active ownership would exceed 100%.");
        long oid = Db.insert(jdbc, "INSERT INTO dbo.horse_ownership (horse_id, owner_user_id, share_percentage, "
                        + "effective_from, is_managing_owner, created_at) VALUES (?,?,?,?,?,?)", id, req.ownerUserId(),
                req.sharePercentage(), req.effectiveFrom(), Boolean.TRUE.equals(req.isManagingOwner()), Db.nowUtc());
        evaluate(id);
        return ownershipRows(id, true).stream().filter(o -> o.id() == oid).findFirst().orElseThrow();
    }

    @Transactional
    public StableAssignment assign(long id, AssignmentRequest req) {
        var user = Db.require(RoleCode.CLUB_MANAGER);
        requireHorse(id);
        if (req == null || req.stableBoxId() == null || req.effectiveFrom() == null)
            throw Db.invalid("stableBoxId and effectiveFrom are required.");
        Integer box = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.stable_box WHERE id=?", Integer.class, req.stableBoxId());
        if (box == null || box == 0) throw Db.notFound("Stable box");
        List<Long> occupant = jdbc.queryForList("SELECT horse_id FROM dbo.horse_stable_assignment WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE stable_box_id=? AND effective_to IS NULL", Long.class, req.stableBoxId());
        if (!occupant.isEmpty() && occupant.getFirst() != id) throw Db.conflict("The stable box is already occupied.");
        if (!occupant.isEmpty()) return assignmentRows(id, true).getFirst();
        jdbc.update("UPDATE dbo.horse_stable_assignment SET effective_to=?, updated_at=? WHERE horse_id=? AND effective_to IS NULL",
                req.effectiveFrom(), Db.nowUtc(), id);
        long aid = Db.insert(jdbc, "INSERT INTO dbo.horse_stable_assignment (horse_id, stable_box_id, assigned_by_user_id, "
                        + "effective_from, reason, created_at) VALUES (?,?,?,?,?,?)", id, req.stableBoxId(), user.id(),
                req.effectiveFrom(), req.reason(), Db.nowUtc());
        evaluate(id);
        return assignmentRows(id, true).stream().filter(a -> a.id() == aid).findFirst().orElseThrow();
    }

    @Transactional
    public void readinessReview(long id) {
        Db.require(RoleCode.HEAD_TRAINER);
        requireHorse(id);
        audit.record("HEAD_TRAINER_READINESS_REVIEWED", "horse", id, "Non-blocking readiness review (DEC-009 A1)");
    }

    /** Called after every Flow 1 change and by the Veterinarian baseline examination (CORE-014). */
    @Override
    @Transactional
    public void evaluate(long horseId) {
        ActivationStatus s = statusOf(horseId);
        if (!"DRAFT".equals(s.lifecycle())) return;
        if (s.checklist().stream().allMatch(ChecklistItem::complete)) {
            int n = jdbc.update("UPDATE dbo.horse SET lifecycle='ACTIVE', activated_at=?, updated_at=? WHERE id=? AND lifecycle='DRAFT'",
                    Db.nowUtc(), Db.nowUtc(), horseId);
            if (n == 1) {
                audit.record("HORSE_ACTIVATED", "horse", horseId, "Automatic activation: all mandatory items complete");
                for (Long cm : jdbc.queryForList("SELECT ur.user_id FROM dbo.user_role ur JOIN dbo.role r ON r.id=ur.role_id "
                        + "WHERE r.role_code IN ('CLUB_MANAGER','HEAD_TRAINER')", Long.class))
                    jdbc.update("INSERT INTO dbo.notification (recipient_user_id, category, notification_type, title, message, "
                                    + "target_type, target_id, is_simulated, created_at) VALUES (?,?,?,?,?,?,?,0,?)",
                            cm, "SCHEDULE", "HORSE_ACTIVATED", s.name() + " is ACTIVE",
                            s.horseCode() + " completed onboarding and was activated automatically.", "horse", horseId, Db.nowUtc());
            }
        }
    }

    // ---------------- helpers ----------------

    private ActivationStatus statusOf(long horseId) {
        Map<String, Object> h = jdbc.queryForMap("SELECT * FROM dbo.horse WHERE id=?", horseId);
        boolean identity = notBlank(h.get("horse_code")) && notBlank(h.get("name")) && notBlank(h.get("breed"))
                && notBlank(h.get("sex")) && h.get("foaled_date") != null;
        boolean pedigree = (h.get("sire_horse_id") != null || notBlank(h.get("sire_name")))
                && (h.get("dam_horse_id") != null || notBlank(h.get("dam_name")));
        Integer owners = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse_ownership WHERE horse_id=? AND effective_to IS NULL",
                Integer.class, horseId);
        Integer box = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse_stable_assignment WHERE horse_id=? AND effective_to IS NULL",
                Integer.class, horseId);
        Integer baseline = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.veterinary_examination WHERE horse_id=? AND exam_type='BASELINE'",
                Integer.class, horseId);
        boolean owner = owners != null && owners > 0;
        List<ChecklistItem> items = List.of(
                new ChecklistItem("IDENTITY", true, identity, identity ? "Identity complete" : "Code, name, breed, sex and foaled date required"),
                new ChecklistItem("PEDIGREE_AND_OWNER", true, pedigree && owner,
                        (pedigree ? "Pedigree recorded" : "Sire and dam required") + " · " + (owner ? "Owner recorded" : "Owner required")),
                new ChecklistItem("STABLE_BOX", true, box != null && box > 0, box != null && box > 0 ? "Box assigned" : "Free stable box required"),
                new ChecklistItem("VET_BASELINE", true, baseline != null && baseline > 0,
                        baseline != null && baseline > 0 ? "Baseline examination recorded" : "Veterinarian baseline examination required"));
        List<Instant> reviewed = jdbc.query("SELECT TOP 1 occurred_at FROM dbo.audit_log WHERE action='HEAD_TRAINER_READINESS_REVIEWED' "
                + "AND target_type='horse' AND target_id=? ORDER BY occurred_at DESC", (r, n) -> Db.instant(r, "occurred_at"), horseId);
        return new ActivationStatus(horseId, (String) h.get("horse_code"), (String) h.get("name"), (String) h.get("lifecycle"),
                Db.instant(h.get("activated_at")), items,
                new ReadinessReview("RECOMMENDED", false, !reviewed.isEmpty(), reviewed.isEmpty() ? null : reviewed.getFirst()));
    }

    private HorseSummary summary(ResultSet r) throws SQLException {
        long id = r.getLong("id");
        java.sql.Date foaled = r.getDate("foaled_date");
        return new HorseSummary(id, r.getString("horse_code"), r.getString("name"), r.getString("breed"), r.getString("sex"),
                foaled == null ? null : foaled.toLocalDate(), r.getString("lifecycle"), r.getString("health_status"),
                restrictions.availability(id).availability(), boxCode(id), null);
    }

    private HorseDetail detailRow(ResultSet r) throws SQLException {
        HorseSummary s = summary(r);
        long id = s.id();
        Pedigree pedigree = new Pedigree(parentView(Db.lng(r, "sire_horse_id"), r.getString("sire_name")),
                parentView(Db.lng(r, "dam_horse_id"), r.getString("dam_name")));
        List<StableAssignment> current = assignmentRows(id, true);
        List<WeightPoint> weight = jdbc.query("SELECT TOP 1 weight, measured_at FROM dbo.weight_record WHERE horse_id=? "
                + "ORDER BY measured_at DESC, id DESC", (w, n) -> new WeightPoint(w.getBigDecimal(1), Db.instant(w, "measured_at")), id);
        return new HorseDetail(id, s.horseCode(), s.name(), s.breed(), s.sex(), s.foaledDate(), s.lifecycle(),
                s.healthStatus(), s.trainingAvailability(), s.currentBoxCode(), null, r.getString("country_of_origin"),
                r.getString("registration_no"), Db.instant(r, "activated_at"), pedigree, ownershipRows(id, true),
                current.isEmpty() ? null : current.getFirst(), weight.isEmpty() ? null : weight.getFirst(),
                Db.instant(r, "created_at"), Db.instant(r, "updated_at"));
    }

    private ParentRef parentView(Long horseId, String name) {
        if (horseId == null) return new ParentRef(null, null, name);
        var row = jdbc.queryForMap("SELECT horse_code, name FROM dbo.horse WHERE id=?", horseId);
        return new ParentRef(horseId, (String) row.get("horse_code"), (String) row.get("name"));
    }

    private Object[] parent(long self, ParentRef p, String label) {
        if (p.horseId() != null) {
            if (p.horseId() == self) throw Db.invalid(label + " cannot be the horse itself.");
            Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse WHERE id=?", Integer.class, p.horseId());
            if (c == null || c == 0) throw Db.notFound(label);
            return new Object[]{p.horseId(), null};
        }
        if (p.name() == null || p.name().isBlank()) throw Db.invalid(label + " name or horseId is required.");
        if (p.name().length() > 150) throw Db.invalid(label + " name is too long.");
        return new Object[]{null, p.name().trim()};
    }

    private String boxCode(long horseId) {
        List<String> codes = jdbc.queryForList("SELECT b.box_code FROM dbo.horse_stable_assignment a JOIN dbo.stable_box b "
                + "ON b.id=a.stable_box_id WHERE a.horse_id=? AND a.effective_to IS NULL", String.class, horseId);
        return codes.isEmpty() ? null : codes.getFirst();
    }

    private List<Ownership> ownershipRows(long horseId, boolean activeOnly) {
        return jdbc.query("SELECT o.*, u.user_code, u.full_name FROM dbo.horse_ownership o JOIN dbo.app_user u ON u.id=o.owner_user_id "
                        + "WHERE o.horse_id=?" + (activeOnly ? " AND o.effective_to IS NULL" : "") + " ORDER BY o.effective_from DESC, o.id DESC",
                (r, n) -> new Ownership(r.getLong("id"), new UserRef(r.getLong("owner_user_id"), r.getString("user_code"),
                        r.getString("full_name")), r.getBigDecimal("share_percentage"), r.getDate("effective_from").toLocalDate(),
                        r.getDate("effective_to") == null ? null : r.getDate("effective_to").toLocalDate(),
                        r.getBoolean("is_managing_owner")), horseId);
    }

    private List<StableAssignment> assignmentRows(long horseId, boolean activeOnly) {
        return jdbc.query("SELECT a.*, b.box_code, b.yard, u.user_code, u.full_name FROM dbo.horse_stable_assignment a "
                        + "JOIN dbo.stable_box b ON b.id=a.stable_box_id JOIN dbo.app_user u ON u.id=a.assigned_by_user_id "
                        + "WHERE a.horse_id=?" + (activeOnly ? " AND a.effective_to IS NULL" : "") + " ORDER BY a.effective_from DESC, a.id DESC",
                (r, n) -> new StableAssignment(r.getLong("id"), r.getLong("stable_box_id"), r.getString("box_code"),
                        r.getString("yard"), r.getDate("effective_from").toLocalDate(),
                        r.getDate("effective_to") == null ? null : r.getDate("effective_to").toLocalDate(), r.getString("reason"),
                        new UserRef(r.getLong("assigned_by_user_id"), r.getString("user_code"), r.getString("full_name"))), horseId);
    }

    private void validateIdentity(IdentityRequest req) {
        if (req == null || req.horseCode() == null || req.horseCode().isBlank() || req.horseCode().length() > 20)
            throw Db.invalid("horseCode is required (max 20).");
        if (req.name() == null || req.name().isBlank() || req.name().length() > 150)
            throw Db.invalid("name is required (max 150).");
        if (req.foaledDate() != null && req.foaledDate().isAfter(LocalDate.now(ZoneOffset.UTC)))
            throw Db.invalid("foaledDate cannot be in the future.");
    }

    private void requireHorse(long id) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.horse WHERE id=?", Integer.class, id);
        if (c == null || c == 0) throw Db.notFound("Horse");
    }

    private static boolean notBlank(Object v) { return v != null && !v.toString().isBlank(); }
}
