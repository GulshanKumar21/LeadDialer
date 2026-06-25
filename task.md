# Onboarding Profiles & Attendance-Based Payroll Checklist

- `[x]` Create [PayslipPdfGenerator.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/PayslipPdfGenerator.kt) with PdfDocument drawing and sharing
- `[x]` Update [HRPanelActivity.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/HRPanelActivity.kt) with payroll calculation logic (Tuesday off) and PDF share actions
- `[x]` Update [EmployeeHRFragments.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/EmployeeHRFragments.kt) with payroll breakdown details and payslip PDF downloader
- `[x]` Create `SupabaseSync.kt` utility class for fetching from Supabase and updating Firestore
- `[x]` Hook `SupabaseSync.syncSupabaseUsersToFirestore(db)` inside `AdminViewModel.kt`'s `startMonitoring()` method
- `[x]` Run Kotlin compile check (`.\gradlew.bat compileDebugKotlin`) to verify the build compiles successfully
- `[x]` Commit and push the changes to GitHub
- `[x]` Verify the changes with `.\gradlew.bat assembleDebug` and report completion
- `[x]` Update [AttendanceActivity.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/AttendanceActivity.kt) to enforce check-in grace (11:05-11:10 up to 3 times/month, else Half Day) and early leave check-out rules (min 2.5 hours worked, else Absent)
- `[x]` Update [HRPanelActivity.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/HRPanelActivity.kt) payroll release calculations to check for and count 'Absent' punched records
- `[x]` Update [AttendanceHistoryAdapter.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/AttendanceHistoryAdapter.kt) and [AdminAttendanceAdapter.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/AdminAttendanceAdapter.kt) to display 'Absent' and 'Half Day' status badges correctly
- `[x]` Update [EmployeeAttendanceActivity.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/EmployeeAttendanceActivity.kt) and [AdminEmployeeAttendanceActivity.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/AdminEmployeeAttendanceActivity.kt) to exclude 'Absent' records from worked counts
- `[x]` Re-verify Kotlin compilation and rebuild debug APK (compileDebugKotlin & assembleDebug)
- `[x]` Fix back-press navigation in [HRPanelActivity.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/HRPanelActivity.kt) (hoisting selectedScreen navigation state to avoid blank screens)
- `[x]` Update [AdminViewModel.kt](file:///c:/Users/GULSHAN%20KUMAR/AndroidStudioProjects/LeadDialer/app/src/main/java/com/adyapan/leaddialer/AdminViewModel.kt) to load user designations, supporting an `isHRPortal` flag to exclude Admin users from HR Portal while showing HR users in Admin Portal
- `[x]` Compile and verify final APK (assembleDebug)
