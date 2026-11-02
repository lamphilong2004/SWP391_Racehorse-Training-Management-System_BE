package com.racehorse.stablecare;

import com.racehorse.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stable-care")
public class StableCareController {
    private final StableCareService service;
    public StableCareController(StableCareService service) { this.service=service; }
    @GetMapping("/horses") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")
    public ApiResponse<List<StableCareApi.Horse>> horses(){return ApiResponse.ok(service.horses());}
    @GetMapping("/grooms") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public ApiResponse<List<StableCareApi.Person>> grooms(){return ApiResponse.ok(service.grooms());}
    @GetMapping("/care-plans") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")
    public ApiResponse<List<StableCareApi.Plan>> carePlans(@RequestParam long horseId){return ApiResponse.ok(service.plans(horseId,false));}
    @PostMapping("/care-plans") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ApiResponse<StableCareApi.Plan> createCare(@Valid @RequestBody StableCareApi.CarePlanRequest r){return ApiResponse.ok(service.care(null,r));}
    @PutMapping("/care-plans/{id}") @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ApiResponse<StableCareApi.Plan> updateCare(@PathVariable long id,@Valid @RequestBody StableCareApi.CarePlanRequest r){return ApiResponse.ok(service.care(id,r));}
    @GetMapping("/nutrition-plans") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN')")
    public ApiResponse<List<StableCareApi.Plan>> nutritionPlans(@RequestParam long horseId){return ApiResponse.ok(service.plans(horseId,true));}
    @PostMapping("/nutrition-plans") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public ApiResponse<StableCareApi.Plan> createNutrition(@Valid @RequestBody StableCareApi.NutritionRequest r){return ApiResponse.ok(service.nutrition(null,r));}
    @PutMapping("/nutrition-plans/{id}") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public ApiResponse<StableCareApi.Plan> updateNutrition(@PathVariable long id,@Valid @RequestBody StableCareApi.NutritionRequest r){return ApiResponse.ok(service.nutrition(id,r));}
    @GetMapping("/tasks") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','GROOM')")
    public ApiResponse<List<StableCareApi.Task>> tasks(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return ApiResponse.ok(service.tasks(date));}
    @GetMapping("/daily-summary") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','GROOM')")
    public ApiResponse<StableCareApi.DailySummary> summary(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return ApiResponse.ok(service.summary(date));}
    @PostMapping("/tasks/{id}/complete") @PreAuthorize("hasRole('GROOM')")
    public ApiResponse<StableCareApi.Task> complete(@PathVariable long id){return ApiResponse.ok(service.complete(id));}
    @PostMapping("/tasks/{id}/reassign") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public ApiResponse<StableCareApi.Task> reassign(@PathVariable long id,@Valid @RequestBody StableCareApi.ReassignTaskRequest r){return ApiResponse.ok(service.reassign(id,r));}
    @PostMapping("/tasks/{id}/cancel") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER')")
    public ApiResponse<StableCareApi.Task> cancel(@PathVariable long id,@Valid @RequestBody StableCareApi.CancelTaskRequest r){return ApiResponse.ok(service.cancel(id,r));}
    @GetMapping("/tasks/{id}/changes") @PreAuthorize("hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','GROOM')")
    public ApiResponse<List<StableCareApi.TaskChange>> changes(@PathVariable long id){return ApiResponse.ok(service.changes(id));}
    @GetMapping("/inventory") @PreAuthorize("hasAnyRole('CLUB_MANAGER','VETERINARIAN','GROOM')")
    public ApiResponse<List<StableCareApi.StockItem>> inventory(){return ApiResponse.ok(service.inventory());}
    @PostMapping("/inventory") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ApiResponse<StableCareApi.StockItem> createItem(@Valid @RequestBody StableCareApi.StockItemRequest r){return ApiResponse.ok(service.saveItem(null,r));}
    @PutMapping("/inventory/{id}") @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ApiResponse<StableCareApi.StockItem> updateItem(@PathVariable long id,@Valid @RequestBody StableCareApi.StockItemRequest r){return ApiResponse.ok(service.saveItem(id,r));}
    @PostMapping("/inventory/{id}/movements") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('CLUB_MANAGER')")
    public ApiResponse<StableCareApi.StockItem> move(@PathVariable long id,@Valid @RequestBody StableCareApi.MovementRequest r){return ApiResponse.ok(service.move(id,r));}
    @GetMapping("/movements") @PreAuthorize("hasAnyRole('CLUB_MANAGER','VETERINARIAN','GROOM')")
    public ApiResponse<List<StableCareApi.Movement>> movements(@RequestParam long inventoryItemId){return ApiResponse.ok(service.movements(inventoryItemId));}
    @GetMapping("/replenishments") @PreAuthorize("hasAnyRole('CLUB_MANAGER','GROOM')")
    public ApiResponse<List<StableCareApi.Replenishment>> requests(){return ApiResponse.ok(service.requests());}
    @PostMapping("/replenishments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('GROOM')")
    public ApiResponse<StableCareApi.Replenishment> request(@Valid @RequestBody StableCareApi.ReplenishmentRequest r){return ApiResponse.ok(service.request(r));}
}
