package com.racehorse.account;

import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.racehorse.account.AccountModels.*;

@RestController
@Validated
public class AccountController {
    private final AccountService accounts;
    private final PasswordResetService resets;
    public AccountController(AccountService accounts,PasswordResetService resets) {this.accounts=accounts;this.resets=resets;}
    @GetMapping("/api/account")
    @Operation(operationId="getMyAccount")
    public ApiResponse<Profile> own() {return ApiResponse.ok(accounts.own());}
    @PutMapping("/api/account")
    @Operation(operationId="updateMyAccount")
    public ApiResponse<Profile> updateOwn(@Valid @RequestBody UpdateProfile input) {return ApiResponse.ok(accounts.updateOwn(input));}
    @PutMapping("/api/account/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId="changeMyPassword")
    public void password(@Valid @RequestBody PasswordChange input) {accounts.changePassword(input);}
    @PostMapping("/api/auth/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId="forgotPassword")
    public ApiResponse<NeutralMessage> forgot(@Valid @RequestBody Forgot input,HttpServletRequest request) {
        resets.forgot(input,request.getRemoteAddr());
        return ApiResponse.ok(new NeutralMessage("If an account exists for this email, a reset link has been sent."));
    }
    @PostMapping("/api/auth/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId="resetPassword")
    public void reset(@Valid @RequestBody Reset input) {resets.reset(input);}
    @PostMapping("/api/admin/users") @PreAuthorize("hasRole('CLUB_MANAGER')") @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId="createUser")
    public ApiResponse<UserDetail> create(@Valid @RequestBody UserInput input) {return ApiResponse.ok(accounts.create(input));}
    @GetMapping("/api/admin/users/{id}") @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="getUser")
    public ApiResponse<UserDetail> detail(@PathVariable @Positive long id) {return ApiResponse.ok(accounts.detail(id));}
    @PutMapping("/api/admin/users/{id}") @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="updateUser")
    public ApiResponse<UserDetail> update(@PathVariable @Positive long id,@Valid @RequestBody UserInput input) {return ApiResponse.ok(accounts.update(id,input));}
    @PatchMapping("/api/admin/users/{id}/status") @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="setUserStatus")
    public ApiResponse<UserDetail> status(@PathVariable @Positive long id,@Valid @RequestBody StatusInput input) {return ApiResponse.ok(accounts.status(id,input));}
    @GetMapping("/api/admin/skills") @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="listSkills")
    public ApiResponse<List<Skill>> skills(@RequestParam(required=false) SkillKind kind) {return ApiResponse.ok(accounts.skills(kind));}
    @GetMapping("/api/admin/roles") @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId="listRoles")
    public ApiResponse<List<RoleInfo>> roles() {return ApiResponse.ok(accounts.roles());}
}
