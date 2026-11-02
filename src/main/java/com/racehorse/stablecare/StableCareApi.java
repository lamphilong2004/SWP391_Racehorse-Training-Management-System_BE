package com.racehorse.stablecare;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** MF-10/11/13, reopened by the Leader on 2026-10-28. Stock is recorded manually. */
public final class StableCareApi {
    private StableCareApi() {}
    public record CareItem(@NotBlank @Pattern(regexp="FEEDING|CLEANING|BATHING|LEG_SOAKING|OTHER") String taskType,
                           @NotBlank @Size(max=50) String timeSlot, @NotBlank @Size(max=2000) String instructions) {}
    public record CarePlanRequest(@Positive long horseId, @Positive long groomUserId, @NotNull LocalDate from,
                                  @NotNull LocalDate to, @Size(max=2000) String notes,
                                  @NotEmpty @Size(max=20) List<@NotNull @Valid CareItem> items) {}
    public record NutritionItem(@NotBlank @Size(max=50) String meal,
                                @NotBlank @Pattern(regexp="HAY|GRAIN|VITAMINS_SUPPLEMENTS") String component,
                                @NotNull @DecimalMin("0.001") @DecimalMax("9999999.999") @Digits(integer=7,fraction=3) BigDecimal quantity,
                                @NotBlank @Size(max=20) String unit) {}
    public record NutritionRequest(@Positive long horseId, @Positive long groomUserId, @NotNull LocalDate from,
                                   @NotNull LocalDate to, @Size(max=2000) String notes,
                                   @NotEmpty @Size(max=20) List<@NotNull @Valid NutritionItem> items) {}
    public record Plan(long id, long horseId, String horseName, LocalDate from, LocalDate to, String notes,
                       Long groomUserId, List<CareItem> careItems, List<NutritionItem> nutritionItems) {}
    public record Person(long id, String fullName) {}
    public record Horse(long id, String name, String horseCode) {}
    public record Task(long id, long horseId, String horseName, String horseCode, String source, String taskType,
                       LocalDate taskDate, String slot, String instructions, long groomUserId, String groomName,
                       String boxCode, Instant completedAt, Instant cancelledAt, String cancellationReason) {}
    public record ReassignTaskRequest(@Positive long expectedGroomUserId, @Positive long groomUserId,
                                       @NotBlank @Size(max=500) String reason) {}
    public record CancelTaskRequest(@Positive long expectedGroomUserId, @NotBlank @Size(max=500) String reason) {}
    public record TaskChange(long id, long taskId, String changeType, long previousGroomUserId, String previousGroomName,
                             Long newGroomUserId, String newGroomName, String changedBy, Instant changedAt, String reason) {}
    public record StockItemRequest(@NotBlank @Size(max=150) String name,
                                   @Pattern(regexp="FEED|MEDICINE|MEDICAL_SUPPLY|TOOL") @NotBlank String category,
                                   @NotBlank @Size(max=20) String unit,
                                   @NotNull @DecimalMin("0") @DecimalMax("999999999.999") @Digits(integer=9,fraction=3) BigDecimal threshold,
                                   boolean active) {}
    public record StockItem(long id, String name, String category, String unit, BigDecimal threshold,
                            BigDecimal currentQuantity, boolean active) {}
    public record MovementRequest(@NotNull @Digits(integer=9,fraction=3) BigDecimal changeQuantity,
                                   @NotNull @DecimalMin("0") @DecimalMax("999999999.999") @Digits(integer=9,fraction=3) BigDecimal expectedQuantity,
                                   @NotBlank @Size(max=500) String note) {}
    public record Movement(long id, long inventoryItemId, String itemName, BigDecimal changeQuantity,
                            String unit, String note, String recordedBy, Instant recordedAt) {}
    public record ReplenishmentRequest(@Positive long inventoryItemId,
                                       @NotNull @DecimalMin("0.001") @DecimalMax("999999999.999") @Digits(integer=9,fraction=3) BigDecimal quantity,
                                       @NotBlank @Size(max=1000) String reason) {}
    public record Replenishment(long id, long inventoryItemId, String itemName, BigDecimal quantity,
                                String unit, String reason, String requestedBy, Instant submittedAt) {}
    public record DailySummary(LocalDate date, int total, int completed, int remaining, int cancelled) {}
}
