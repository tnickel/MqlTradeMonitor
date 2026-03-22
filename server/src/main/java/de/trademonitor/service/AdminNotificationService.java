package de.trademonitor.service;

import de.trademonitor.dto.AdminNotification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-memory service for storing admin dashboard notifications.
 * Keeps a rolling buffer of the last 50 notifications.
 */
@Service
public class AdminNotificationService {

    private static final int MAX_NOTIFICATIONS = 50;
    private final LinkedList<AdminNotification> notifications = new LinkedList<>();

    /**
     * Add a new notification. Oldest entries are removed when the buffer is full.
     */
    public synchronized void addNotification(AdminNotification notification) {
        notifications.addFirst(notification);
        while (notifications.size() > MAX_NOTIFICATIONS) {
            notifications.removeLast();
        }
        System.out.println("[AdminNotification] " + notification.getSeverity()
                + " | " + notification.getCategory()
                + " | " + notification.getTitle());
    }

    /**
     * Get all unacknowledged notifications (newest first).
     */
    public synchronized List<AdminNotification> getUnacknowledgedNotifications() {
        return notifications.stream()
                .filter(n -> !n.isAcknowledged())
                .collect(Collectors.toList());
    }

    /**
     * Get the most recent notifications (newest first).
     */
    public synchronized List<AdminNotification> getRecentNotifications(int count) {
        List<AdminNotification> result = new ArrayList<>();
        int limit = Math.min(count, notifications.size());
        for (int i = 0; i < limit; i++) {
            result.add(notifications.get(i));
        }
        return result;
    }

    /**
     * Mark all notifications as acknowledged.
     */
    public synchronized void acknowledgeAll() {
        for (AdminNotification n : notifications) {
            n.setAcknowledged(true);
        }
        System.out.println("[AdminNotification] All notifications acknowledged.");
    }

    /**
     * Get count of unacknowledged notifications.
     */
    public synchronized int getUnacknowledgedCount() {
        return (int) notifications.stream().filter(n -> !n.isAcknowledged()).count();
    }
}
