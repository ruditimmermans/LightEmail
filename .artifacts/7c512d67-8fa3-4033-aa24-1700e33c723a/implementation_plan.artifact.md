# Implementation Plan: 6.1" Screen Compatibility & UI Refinement

This plan focuses on making the app look great on standard 6.1-inch smartphone screens, ensuring navigation text and icons are perfectly sized and don't cut off, while maintaining the "Light" minimalist aesthetic.

## Proposed Changes

### 1. 6.1" Screen Optimization

#### [MODIFY] [MainScreen.kt](file:///C:/Users/Rudi/Development/LightEmail/app/src/main/java/com/light/lightemail/ui/screens/MainScreen.kt)
- **Bottom Navigation**:
    - Set icon size to `26.dp` for standard screens (balanced for taller aspect ratios).
    - Set `NavigationBar` height to `72.dp`.
- **Navigation Text (Compose & Detail Screens)**:
    - Reduce the scaling factor for header text (Cancel, Send, Titles) to ensure they fit within the horizontal bounds, even when the user selects a larger global text size.
    - Implement a more responsive text size calculation for headers: `(textSize * 0.75f).sp` instead of `0.8f`.
    - Adjust padding in the `ComposeEmailScreen` header to prevent items from crowding the edges.
- **Compose Screen**:
    - Increase the maximum height of the quoted email preview (`heightIn`) to `450.dp` on standard screens to utilize the additional vertical space.

### 2. Notification & Flashing Polish

#### [MODIFY] [EmailViewModel.kt](file:///C:/Users/Rudi/Development/LightEmail/app/src/main/java/com/light/lightemail/ui/viewmodel/EmailViewModel.kt)
- Ensure the `_isLoading` indicator only triggers during initial data sync if the cache is empty, preventing flickering on subsequent refreshes.

## Verification Plan

### Manual Verification
1.  **6.1" Screen Layout**: Test on a standard smartphone resolution. Verify that "CANCEL", "NEW EMAIL", and "SEND" fit on one line without overlapping or cutting off.
2.  **Navigation Bar**: Ensure the bottom icons are clear and the bar doesn't take up excessive vertical space on taller screens.
3.  **Large Text Settings**: Go to settings, set the text size to maximum (24sp), then go to the compose screen and verify the navigation items still fit correctly.
4.  **Notifications**: Send several test emails and confirm that only one notification (with updated count) is active at any time.
