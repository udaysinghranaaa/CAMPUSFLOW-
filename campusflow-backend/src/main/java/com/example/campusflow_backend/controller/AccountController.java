package com.example.campusflow_backend.controller;
import com.example.campusflow_backend.auth.*;
import com.example.campusflow_backend.dto.PageResponse;
import com.example.campusflow_backend.security.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts=accounts; }
    @PostMapping public ResponseEntity<AccountResponse> create(@Valid @RequestBody ProvisionAccountRequest request) {
        var result=accounts.provision(request);return ResponseEntity.created(URI.create("/api/v1/admin/accounts/"+result.id())).body(result);
    }
    @GetMapping public PageResponse<AccountResponse> list(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return accounts.list(PageRequest.of(page,size,Sort.by("id"))); }
    @GetMapping("/{id}") public AccountResponse get(@PathVariable @Positive Long id) { return accounts.get(id); }
    @PatchMapping("/{id}/status") public AccountResponse status(@PathVariable @Positive Long id,@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AccountStatusRequest request) { return accounts.status(id,principal.id(),request); }
}
