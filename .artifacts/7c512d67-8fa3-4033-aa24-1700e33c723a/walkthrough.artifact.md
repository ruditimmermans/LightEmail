# Walkthrough: Notification Timeout

I have updated the app to ensure that new email notifications are automatically dismissed after 30 seconds.

## Changes Made

### SyncWorker Optimization
- **[SyncWorker.kt](file:///C:/Users/Rudi/Development/LightEmail/app/src/main/java/com/light/lightemail/worker/SyncWorker.kt)**: Added `setTimeoutAfter(30000)` to the `NotificationCompat.Builder` in the `showNotification` method. This ensures that if a user doesn't interact with the notification, it will be removed from the status bar after 30 seconds.

## Verification

### Automated Verification
- Code analysis shows the use of `setTimeoutAfter(30000)`, which is the standard Android API for this functionality.

### Manual Verification
1. Receive a new email.
2. Observe the notification appearing.
3. Wait for 30 seconds without interacting with the device.
4. Verify the notification disappears automatically.
