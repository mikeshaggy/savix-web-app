package com.mikeshaggy.backend.user.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.user.dto.MeResponse;
import com.mikeshaggy.backend.user.dto.MeUpdateRequest;
import com.mikeshaggy.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(MeController.BASE_URL)
@RequiredArgsConstructor
public class MeController {

    public static final String BASE_URL = "/api/me";

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<MeResponse> getCurrentUser() {
        MeResponse response = userService.getUserById(currentUserProvider.getCurrentUserId());

        return ResponseEntity.ok(response);
    }

    @PatchMapping
    public ResponseEntity<MeResponse> updateCurrentUser(@Valid @RequestBody MeUpdateRequest request) {
        MeResponse response = userService.updateUserById(
                currentUserProvider.getCurrentUserId(),
                request
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteCurrentUser() {
        userService.deleteUserById(currentUserProvider.getCurrentUserId());

        return ResponseEntity.noContent().build();
    }
}
