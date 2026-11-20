package com.racehorse.horse.stable;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.common.api.ErrorDetail;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.horse.stable.StableLayoutDtos.Cell;
import com.racehorse.horse.stable.StableLayoutDtos.CreateYardRequest;
import com.racehorse.horse.stable.StableLayoutDtos.Limits;
import com.racehorse.horse.stable.StableLayoutDtos.Occupant;
import com.racehorse.horse.stable.StableLayoutDtos.OutsideBox;
import com.racehorse.horse.stable.StableLayoutDtos.StableMap;
import com.racehorse.horse.stable.StableLayoutDtos.UpdateBoxStatusRequest;
import com.racehorse.horse.stable.StableLayoutDtos.Yard;
import com.racehorse.horse.stable.StableLayoutRepository.BoxRow;
import com.racehorse.horse.stable.StableLayoutRepository.BoxState;
import com.racehorse.horse.stable.StableLayoutRepository.YardRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.racehorse.horse.stable.StableLayoutDtos.MAX_COLUMNS;
import static com.racehorse.horse.stable.StableLayoutDtos.MAX_ROWS;
import static com.racehorse.horse.stable.StableLayoutDtos.MAX_YARDS;

/**
 * DEC-016. The stable capacity is the sum of the yard grids. Boxes are created only as cells of a yard, so the number
 * of boxes can never exceed rows x columns per yard, and the number of yards is capped.
 */
@Service
public class StableLayoutService {

    private static final Pattern YARD_CODE = Pattern.compile("[A-Z]");

    private final StableLayoutRepository repository;
    private final CurrentActorProvider actors;

    public StableLayoutService(StableLayoutRepository repository, CurrentActorProvider actors) {
        this.repository = repository;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public StableMap map() {
        List<YardRow> yardRows = repository.yards();
        Map<Long, List<BoxRow>> byYard = repository.mapBoxes().stream()
                .filter(box -> box.yardId() != null)
                .collect(Collectors.groupingBy(BoxRow::yardId));
        List<OutsideBox> outside = repository.mapBoxes().stream()
                .filter(box -> box.yardId() == null && box.horseId() != null)
                .map(box -> new OutsideBox(box.id(), box.boxCode(), occupant(box)))
                .toList();

        List<Yard> yards = new ArrayList<>();
        int capacity = 0, active = 0, occupied = 0, free = 0, maintenance = 0;
        for (YardRow yard : yardRows) {
            List<Cell> cells = byYard.getOrDefault(yard.id(), List.of()).stream()
                    .map(box -> new Cell(box.id(), box.boxCode(), box.rowNo(), box.columnNo(), box.boxStatus(), occupant(box)))
                    .sorted((a, b) -> a.rowNo() != b.rowNo() ? Integer.compare(a.rowNo(), b.rowNo())
                            : Integer.compare(a.columnNo(), b.columnNo()))
                    .toList();
            int yardOccupied = (int) cells.stream().filter(cell -> cell.horse() != null).count();
            int yardMaintenance = (int) cells.stream().filter(cell -> "MAINTENANCE".equals(cell.boxStatus())).count();
            int yardFree = (int) cells.stream()
                    .filter(cell -> cell.horse() == null && "ACTIVE".equals(cell.boxStatus())).count();
            yards.add(new Yard(yard.id(), yard.yardCode(), yard.name(), yard.rowCount(), yard.columnCount(),
                    yardOccupied, yardFree, yardMaintenance, cells));
            capacity += yard.rowCount() * yard.columnCount();
            active += cells.size() - yardMaintenance;
            occupied += yardOccupied;
            free += yardFree;
            maintenance += yardMaintenance;
        }
        return new StableMap(capacity, active, occupied + outside.size(), free, maintenance,
                new Limits(MAX_YARDS, MAX_ROWS, MAX_COLUMNS), yards, outside);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public StableMap createYard(CreateYardRequest request) {
        ActorContext actor = actor();
        String code = request == null || request.yardCode() == null ? "" : request.yardCode().trim().toUpperCase();
        String name = request == null || request.name() == null ? "" : request.name().trim();
        List<ErrorDetail> errors = new ArrayList<>();
        if (!YARD_CODE.matcher(code).matches()) {
            errors.add(new ErrorDetail("yardCode", "Yard code must be one letter A-Z."));
        }
        if (name.isEmpty() || name.length() > 100) {
            errors.add(new ErrorDetail("name", "Yard name is required (at most 100 characters)."));
        }
        Integer rows = request == null ? null : request.rowCount();
        Integer columns = request == null ? null : request.columnCount();
        if (rows == null || rows < 1 || rows > MAX_ROWS) {
            errors.add(new ErrorDetail("rowCount", "Rows must be between 1 and " + MAX_ROWS + "."));
        }
        if (columns == null || columns < 1 || columns > MAX_COLUMNS) {
            errors.add(new ErrorDetail("columnCount", "Columns must be between 1 and " + MAX_COLUMNS + "."));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errors.getFirst().message(), errors);
        }
        if (repository.yardCount() >= MAX_YARDS) {
            throw conflict("yardCode", "The stable already has the maximum of " + MAX_YARDS + " yards.");
        }
        if (repository.yardCodeExists(code)) {
            throw conflict("yardCode", "Yard " + code + " already exists.");
        }

        long yardId = repository.insertYard(code, name, rows, columns, actor.userId());
        for (int index = 1; index <= rows * columns; index++) {
            int row = (index - 1) / columns + 1;
            int column = (index - 1) % columns + 1;
            String boxCode = code + "-" + (index < 10 ? "0" + index : String.valueOf(index));
            Optional<BoxState> existing = repository.boxByCode(boxCode);
            if (existing.isEmpty()) {
                repository.insertBox(boxCode, name, yardId, row, column);
            } else if (existing.get().yardId() != null
                    || repository.placeLegacyBox(existing.get().id(), name, yardId, row, column) != 1) {
                throw conflict("yardCode", "Box code " + boxCode + " is already used by another yard.");
            }
        }
        repository.audit(actor.userId(), "CLUB_MANAGER", "STABLE_YARD_CREATED", "STABLE_YARD", yardId,
                "Yard " + code + " created with " + rows + " x " + columns + " boxes.");
        return map();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public StableMap updateBoxStatus(long boxId, UpdateBoxStatusRequest request) {
        ActorContext actor = actor();
        String status = request == null || request.status() == null ? "" : request.status().trim().toUpperCase();
        if (!status.equals("ACTIVE") && !status.equals("MAINTENANCE")) {
            throw validation("status", "Status must be ACTIVE or MAINTENANCE.");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (status.equals("MAINTENANCE") && (reason.isEmpty() || reason.length() > 300)) {
            throw validation("reason", "A maintenance reason is required (at most 300 characters).");
        }
        BoxState box = repository.box(boxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Stable box not found."));
        if (box.yardId() == null || "RETIRED".equals(box.boxStatus())) {
            throw conflict("status", "Only a box inside a yard can change status.");
        }
        if (status.equals(box.boxStatus())) {
            throw conflict("status", "The box already has this status.");
        }
        if (status.equals("MAINTENANCE") && box.occupied()) {
            throw conflict("status", "Move the horse out before putting this box under maintenance.");
        }
        if (repository.updateBoxStatus(boxId, box.boxStatus(), status) != 1) {
            throw conflict("status", "The box changed while saving. Reload the stable map.");
        }
        repository.audit(actor.userId(), "CLUB_MANAGER",
                status.equals("MAINTENANCE") ? "STABLE_BOX_MAINTENANCE_STARTED" : "STABLE_BOX_REOPENED",
                "STABLE_BOX", boxId,
                "Box " + box.boxCode() + (status.equals("MAINTENANCE") ? " under maintenance: " + reason : " reopened."));
        return map();
    }

    private static Occupant occupant(BoxRow box) {
        return box.horseId() == null ? null
                : new Occupant(box.horseId(), box.horseCode(), box.horseName(), box.lifecycle());
    }

    private ActorContext actor() {
        return actors.currentActor()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required."));
    }

    private static BusinessException conflict(String field, String message) {
        return new BusinessException(ErrorCode.CONFLICT, message, List.of(new ErrorDetail(field, message)));
    }

    private static BusinessException validation(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, List.of(new ErrorDetail(field, message)));
    }
}
