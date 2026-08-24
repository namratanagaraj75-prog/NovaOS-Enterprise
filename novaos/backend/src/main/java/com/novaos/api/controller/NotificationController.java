package com.novaos.api.controller;

import com.novaos.api.service.ReviewNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final ReviewNotificationService notifications;

    public NotificationController(ReviewNotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable String notificationId, Authentication auth) {
        notifications.markRead(notificationId, auth);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/review/{requestId}/read")
    public ResponseEntity<Void> markReviewRead(@PathVariable String requestId, Authentication auth) {
        notifications.markReviewRead(requestId, auth);
        return ResponseEntity.noContent().build();
    }
}
