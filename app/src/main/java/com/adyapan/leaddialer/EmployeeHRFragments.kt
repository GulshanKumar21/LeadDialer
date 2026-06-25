package com.adyapan.leaddialer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.UUID

// ── 2. EMPLOYEE DOCUMENTS FRAGMENT ──
class EmployeeDocumentsFragment : Fragment() {
    private var activeUploadType = ""
    private var filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadDocumentToStorage(uri, activeUploadType)
        }
    }

    private var onUploadSuccess: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseSync.syncSupabaseDocumentsToFirestore(FirebaseFirestore.getInstance())
            } catch (e: Exception) {
                android.util.Log.e("EmployeeDocumentsFragment", "Supabase doc sync failed: ${e.message}")
            }
        }

        return ComposeView(requireContext()).apply {
            setContent {
                HRPortalTheme {
                    EmployeeDocumentsScreen(
                        onUploadClick = { docKey ->
                            activeUploadType = docKey
                            val mimeType = if (docKey == "passportPhoto") "image/*" else "application/pdf"
                            filePickerLauncher.launch(mimeType)
                        },
                        onSaveProfile = { profileData ->
                            saveUserProfile(profileData)
                        },
                        registerSuccessCallback = { callback ->
                            onUploadSuccess = callback
                        }
                    )
                }
            }
        }
    }

    private fun uploadDocumentToStorage(uri: android.net.Uri, docType: String) {
        val context = context ?: return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()

        Toast.makeText(context, "Uploading file...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (bytes.size > 10 * 1024 * 1024) { // 10MB limit
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File size must be under 10MB", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val ext = if (docType == "passportPhoto") ".jpg" else ".pdf"
                val fileName = "${docType}_${System.currentTimeMillis()}$ext"
                
                val storageRef = storage.reference.child("employee_docs/${user.uid}/$fileName")
                storageRef.putBytes(bytes).await()

                val downloadUrl = storageRef.downloadUrl.await().toString()

                val userRef = db.collection("users").document(user.uid)
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    if (!snapshot.exists()) {
                        transaction.set(userRef, mapOf(
                            "uid" to user.uid,
                            "email" to (user.email ?: ""),
                            "documents" to mapOf(docType to downloadUrl)
                        ))
                    } else {
                        val docs = mutableMapOf<String, String>()
                        val rawDocs = snapshot.get("documents") as? Map<*, *>
                        rawDocs?.forEach { (k, v) ->
                            if (k is String && v is String) {
                                docs[k] = v
                            }
                        }
                        docs[docType] = downloadUrl
                        transaction.update(userRef, "documents", docs)
                    }
                }.await()

                try {
                    SupabaseSync.uploadDocumentToSupabase(user.uid, docType, downloadUrl, fileName)
                } catch (e: Exception) {
                    android.util.Log.e("EmployeeHRFragments", "Supabase doc upload failed: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Uploaded Successfully", Toast.LENGTH_SHORT).show()
                    onUploadSuccess?.invoke()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Upload Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveUserProfile(data: Map<String, Any>) {
        val context = context ?: return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                FirebaseFirestore.getInstance().collection("users")
                    .document(user.uid)
                    .update(data)
                    .await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Profile Saved Successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                try {
                    val fullData = data.toMutableMap()
                    fullData["uid"] = user.uid
                    fullData["email"] = user.email ?: ""
                    FirebaseFirestore.getInstance().collection("users")
                        .document(user.uid)
                        .set(fullData)
                        .await()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Profile Saved Successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (ex: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Save Failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDocumentsScreen(
    onUploadClick: (String) -> Unit,
    onSaveProfile: (Map<String, Any>) -> Unit,
    registerSuccessCallback: (() -> Unit) -> Unit
) {
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val context = androidx.compose.ui.platform.LocalContext.current

    // Form states
    var employeeId by remember { mutableStateOf("") }
    var employeeName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var alternatePhone by remember { mutableStateOf("") }
    var companyEmail by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var motherName by remember { mutableStateOf("") }
    var permanentAddress by remember { mutableStateOf("") }
    var currentAddress by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }

    var department by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var reportingManager by remember { mutableStateOf("") }
    var teamLeader by remember { mutableStateOf("") }
    var teamName by remember { mutableStateOf("") }
    var employeeType by remember { mutableStateOf("") }
    var dateOfJoining by remember { mutableStateOf("") }
    var workLocation by remember { mutableStateOf("") }
    var linkedinProfile by remember { mutableStateOf("") }

    var accountNumber by remember { mutableStateOf("") }
    var confirmAccountNumber by remember { mutableStateOf("") }
    var accountHolderName by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }

    var universityName by remember { mutableStateOf("") }
    var yearOfPassing by remember { mutableStateOf("") }
    var previousExperience by remember { mutableStateOf("") }
    var previousCompanyName by remember { mutableStateOf("") }
    var previousDesignation by remember { mutableStateOf("") }

    val uploadedDocs = remember { mutableStateMapOf<String, String>() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Personal", "Job & Office", "Bank & Edu", "Documents")

    LaunchedEffect(currentUid) {
        if (currentUid.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("users").document(currentUid)
                .addSnapshotListener { doc, err ->
                    if (doc != null && doc.exists()) {
                        employeeId = doc.getString("employeeId") ?: ""
                        employeeName = doc.getString("name") ?: ""
                        phone = doc.getString("phone") ?: ""
                        alternatePhone = doc.getString("alternatePhone") ?: ""
                        companyEmail = doc.getString("companyEmail") ?: ""
                        fatherName = doc.getString("fatherName") ?: ""
                        motherName = doc.getString("motherName") ?: ""
                        permanentAddress = doc.getString("permanentAddress") ?: ""
                        currentAddress = doc.getString("currentAddress") ?: ""
                        gender = doc.getString("gender") ?: ""
                        dob = doc.getString("dob") ?: ""
                        bloodGroup = doc.getString("bloodGroup") ?: ""

                        department = doc.getString("department") ?: ""
                        designation = doc.getString("designation") ?: ""
                        reportingManager = doc.getString("reportingManager") ?: ""
                        teamLeader = doc.getString("teamLeader") ?: ""
                        teamName = doc.getString("teamName") ?: ""
                        employeeType = doc.getString("employeeType") ?: ""
                        dateOfJoining = doc.getString("dateOfJoining") ?: ""
                        workLocation = doc.getString("workLocation") ?: ""
                        linkedinProfile = doc.getString("linkedinProfile") ?: ""

                        accountNumber = doc.getString("accountNumber") ?: ""
                        confirmAccountNumber = doc.getString("confirmAccountNumber") ?: ""
                        accountHolderName = doc.getString("accountHolderName") ?: ""
                        ifscCode = doc.getString("ifscCode") ?: ""
                        branch = doc.getString("branch") ?: ""
                        bankName = doc.getString("bankName") ?: ""

                        universityName = doc.getString("universityName") ?: ""
                        yearOfPassing = doc.getString("yearOfPassing") ?: ""
                        previousExperience = doc.getString("previousExperience") ?: ""
                        previousCompanyName = doc.getString("previousCompanyName") ?: ""
                        previousDesignation = doc.getString("previousDesignation") ?: ""

                        uploadedDocs.clear()
                        val rawDocs = doc.get("documents") as? Map<*, *>
                        rawDocs?.forEach { (k, v) ->
                            if (k is String && v is String) {
                                uploadedDocs[k] = v
                            }
                        }
                    }
                }
        }
    }

    val docTypes = listOf(
        "class10" to "10th Class Certificate (PDF)",
        "class12" to "12th Class Certificate (PDF)",
        "lastSemester" to "Last Semester Mark Sheet (PDF)",
        "passportPhoto" to "Passport Size Photo (Image)",
        "aadhar" to "Aadhar Card (PDF)",
        "pan" to "PAN Card (PDF)",
        "drivingLicense" to "Driving License (PDF)",
        "passport" to "Passport (PDF)",
        "resume" to "Resume (PDF)",
        "offerLetter" to "Offer Letter (PDF)"
    )

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFAF9F6))) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = Color(0xFFFF6A00)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> { // Personal details form
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = employeeId,
                                onValueChange = { employeeId = it },
                                label = { Text("Employee ID *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = employeeName,
                                onValueChange = { employeeName = it },
                                label = { Text("Employee Name *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Mobile Number *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = alternatePhone,
                                onValueChange = { alternatePhone = it },
                                label = { Text("Alternate Mobile (Father) *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = companyEmail,
                                onValueChange = { companyEmail = it },
                                label = { Text("Company Email") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = fatherName,
                                onValueChange = { fatherName = it },
                                label = { Text("Father Name *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = motherName,
                                onValueChange = { motherName = it },
                                label = { Text("Mother Name *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = permanentAddress,
                                onValueChange = { permanentAddress = it },
                                label = { Text("Permanent Address *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = currentAddress,
                                onValueChange = { currentAddress = it },
                                label = { Text("Current Address *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            var genderExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = genderExpanded,
                                onExpandedChange = { genderExpanded = !genderExpanded }
                            ) {
                                OutlinedTextField(
                                    value = gender,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Gender *") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = genderExpanded,
                                    onDismissRequest = { genderExpanded = false }
                                ) {
                                    listOf("Male", "Female", "Other", "Prefer not to say").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                gender = option
                                                genderExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = dob,
                                onValueChange = { dob = it },
                                label = { Text("Date of Birth * (DD/MM/YYYY)") },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val calendar = java.util.Calendar.getInstance()
                                    val year = calendar.get(java.util.Calendar.YEAR)
                                    val month = calendar.get(java.util.Calendar.MONTH)
                                    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                    android.app.DatePickerDialog(context, { _, y, m, d ->
                                        dob = String.format(java.util.Locale.US, "%02d/%02d/%d", d, m + 1, y)
                                    }, year, month, day).show()
                                },
                                readOnly = true
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = bloodGroup,
                                onValueChange = { bloodGroup = it },
                                label = { Text("Blood Group *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Button(
                                onClick = {
                                    onSaveProfile(mapOf(
                                        "employeeId" to employeeId,
                                        "name" to employeeName,
                                        "phone" to phone,
                                        "alternatePhone" to alternatePhone,
                                        "companyEmail" to companyEmail,
                                        "fatherName" to fatherName,
                                        "motherName" to motherName,
                                        "permanentAddress" to permanentAddress,
                                        "currentAddress" to currentAddress,
                                        "gender" to gender,
                                        "dob" to dob,
                                        "bloodGroup" to bloodGroup
                                    ))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00))
                            ) {
                                Text("Save Personal Details", color = Color.White)
                            }
                        }
                    }
                }
                1 -> { // Job Details Form
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = department,
                                onValueChange = { department = it },
                                label = { Text("Department *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = designation,
                                onValueChange = { designation = it },
                                label = { Text("Designation *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = reportingManager,
                                onValueChange = { reportingManager = it },
                                label = { Text("Reporting Manager *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = teamLeader,
                                onValueChange = { teamLeader = it },
                                label = { Text("Team Leader *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = teamName,
                                onValueChange = { teamName = it },
                                label = { Text("Team Name *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = employeeType,
                                onValueChange = { employeeType = it },
                                label = { Text("Employee Type * (e.g. Intern, Permanent)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = dateOfJoining,
                                onValueChange = { dateOfJoining = it },
                                label = { Text("Joining Date * (DD/MM/YYYY)") },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val calendar = java.util.Calendar.getInstance()
                                    val year = calendar.get(java.util.Calendar.YEAR)
                                    val month = calendar.get(java.util.Calendar.MONTH)
                                    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                    android.app.DatePickerDialog(context, { _, y, m, d ->
                                        dateOfJoining = String.format(java.util.Locale.US, "%02d/%02d/%d", d, m + 1, y)
                                    }, year, month, day).show()
                                },
                                readOnly = true
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = workLocation,
                                onValueChange = { workLocation = it },
                                label = { Text("Work Location *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = linkedinProfile,
                                onValueChange = { linkedinProfile = it },
                                label = { Text("LinkedIn Profile (LINK ONLY) *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Button(
                                onClick = {
                                    onSaveProfile(mapOf(
                                        "department" to department,
                                        "designation" to designation,
                                        "reportingManager" to reportingManager,
                                        "teamLeader" to teamLeader,
                                        "teamName" to teamName,
                                        "employeeType" to employeeType,
                                        "dateOfJoining" to dateOfJoining,
                                        "workLocation" to workLocation,
                                        "linkedinProfile" to linkedinProfile
                                    ))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00))
                            ) {
                                Text("Save Job Details", color = Color.White)
                            }
                        }
                    }
                }
                2 -> { // Bank & Education Details Form
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = accountNumber,
                                onValueChange = { accountNumber = it },
                                label = { Text("Account Number *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = confirmAccountNumber,
                                onValueChange = { confirmAccountNumber = it },
                                label = { Text("Confirm Account Number *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = accountHolderName,
                                onValueChange = { accountHolderName = it },
                                label = { Text("Account Holder Name *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = ifscCode,
                                onValueChange = { ifscCode = it },
                                label = { Text("IFSC Code *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = branch,
                                onValueChange = { branch = it },
                                label = { Text("Branch *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = bankName,
                                onValueChange = { bankName = it },
                                label = { Text("Bank Name *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = universityName,
                                onValueChange = { universityName = it },
                                label = { Text("University Name *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = yearOfPassing,
                                onValueChange = { yearOfPassing = it },
                                label = { Text("Year of Passing *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = previousExperience,
                                onValueChange = { previousExperience = it },
                                label = { Text("Previous Company Experience (IF ANY)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = previousCompanyName,
                                onValueChange = { previousCompanyName = it },
                                label = { Text("Previous Company Name (IF ANY)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = previousDesignation,
                                onValueChange = { previousDesignation = it },
                                label = { Text("Previous Designation (IF ANY)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Button(
                                onClick = {
                                    if (accountNumber != confirmAccountNumber) {
                                        Toast.makeText(context, "Account numbers do not match!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    onSaveProfile(mapOf(
                                        "accountNumber" to accountNumber,
                                        "confirmAccountNumber" to confirmAccountNumber,
                                        "accountHolderName" to accountHolderName,
                                        "ifscCode" to ifscCode,
                                        "branch" to branch,
                                        "bankName" to bankName,
                                        "universityName" to universityName,
                                        "yearOfPassing" to yearOfPassing,
                                        "previousExperience" to previousExperience,
                                        "previousCompanyName" to previousCompanyName,
                                        "previousDesignation" to previousDesignation
                                    ))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00))
                            ) {
                                Text("Save Bank & Education Details", color = Color.White)
                            }
                        }
                    }
                }
                3 -> { // Required Onboarding Documents Upload
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Upload Onboarding Documents (PDF/Photo, Max 10MB)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2C3E50)
                            )
                        }
                        items(docTypes) { (docKey, docName) ->
                            val url = uploadedDocs[docKey]
                            val isUploaded = !url.isNullOrBlank()
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isUploaded) Icons.Default.CheckCircle else Icons.Default.Description,
                                        contentDescription = "DocStatus",
                                        tint = if (isUploaded) Color(0xFF22C55E) else Color(0xFFFF9500),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(docName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                                        Text(
                                            text = if (isUploaded) "Uploaded successfully" else "Pending upload",
                                            fontSize = 11.sp,
                                            color = if (isUploaded) Color(0xFF22C55E) else Color.Gray
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (isUploaded) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5856D6)),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("View", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                        Button(
                                            onClick = { onUploadClick(docKey) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if (isUploaded) Color.Gray else Color(0xFFFF6A00)),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(if (isUploaded) "Replace" else "Upload", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 3. EMPLOYEE PERFORMANCE FRAGMENT ──
class EmployeePerformanceFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HRPortalTheme {
                    EmployeePerformanceScreen()
                }
            }
        }
    }
}

@Composable
fun EmployeePerformanceScreen() {
    val performanceList = remember { mutableStateListOf<Map<String, Any>>() }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentUid) {
        if (currentUid.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("performance")
                .whereEqualTo("userId", currentUid)
                .addSnapshotListener { snap, err ->
                    isLoading = false
                    if (snap != null) {
                        performanceList.clear()
                        for (doc in snap.documents) {
                            performanceList.add(mapOf(
                                "id" to doc.id,
                                "employeeName" to (doc.getString("employeeName") ?: ""),
                                "rating" to (doc.getString("rating") ?: ""),
                                "feedback" to (doc.getString("feedback") ?: "")
                            ))
                        }
                    }
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAF9F6))) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFFF6A00))
        } else if (performanceList.isEmpty()) {
            Text(
                "No performance reviews published yet.",
                modifier = Modifier.align(Alignment.Center),
                color = Color.Gray,
                fontSize = 15.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Performance History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                }
                items(performanceList) { item ->
                    val rating = item["rating"]?.toString() ?: ""
                    val feedback = item["feedback"]?.toString() ?: ""
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFFF6A00), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rating: $rating", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(feedback, fontSize = 13.sp, color = Color(0xFF7F8C8D))
                            }
                        }
                    }
                }
            }
        }
    }
}
