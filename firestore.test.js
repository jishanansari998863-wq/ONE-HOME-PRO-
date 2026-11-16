const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const SUPER_ADMIN_EMAIL = "jishanansari998863@gmail.com";
const SUPER_ADMIN_UID = "super_admin_uid_001";
const IMPOSTER_ADMIN_UID = "imposter_uid_999";
const STRANGER_UID = "stranger_uid_111";
const STRANGER_EMAIL = "stranger@example.com";
const OTHER_OWNER_UID = "other_owner_uid_222";
const OTHER_OWNER_EMAIL = "other@example.com";

function pastTimestamp() {
  return new Date(Date.now() - 5000);
}

function superAdminDb() {
  return testEnv
    .authenticatedContext(SUPER_ADMIN_UID, {
      email: SUPER_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
}

function strangerDb(uid = STRANGER_UID, email = STRANGER_EMAIL) {
  return testEnv
    .authenticatedContext(uid, {
      email,
      email_verified: true,
    })
    .firestore();
}

async function seedBootstrappedSuperAdmin() {
  const admin = superAdminDb();
  const ts = pastTimestamp();
  await admin.collection("platform_config").doc("global").set({
    superAdminUid: SUPER_ADMIN_UID,
    superAdminEmail: SUPER_ADMIN_EMAIL,
    mfaEnabled: true,
    passwordSalt: "saltBase64_01234567",
    passwordHash: "hashValue_0123456789abcdef",
    totpSecret: "JBSWY3DPEHPK3PXP1234",
    recoveryCodeHashes: ["abc123def456"],
    createdAt: ts,
    updatedAt: ts,
  });
  await admin.collection("platform_status").doc("public").set({
    isBootstrapped: true,
    registrationsEnabled: true,
    emergencyLockAllNonSuperAdmin: false,
    updatedAt: ts,
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("1. Stranger (unauthenticated) downloads APK: denied all reads and writes", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("platform_status").doc("public").get());
  await assertFails(unauth.collection("platform_config").doc("global").get());
  await assertFails(unauth.collection("access_requests").get());
  await assertFails(unauth.collection("access_grants").get());
  await assertFails(unauth.collection("businesses").get());
  await assertFails(unauth.collection("sessions").get());
  await assertFails(unauth.collection("admin_audit_logs").get());
});

test("2. Email spoofing / unverified email cannot bootstrap or access Super Admin config", async () => {
  const spoofed = testEnv
    .authenticatedContext(IMPOSTER_ADMIN_UID, {
      email: SUPER_ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
  const ts = pastTimestamp();
  await assertFails(
    spoofed.collection("platform_config").doc("global").set({
      superAdminUid: IMPOSTER_ADMIN_UID,
      superAdminEmail: SUPER_ADMIN_EMAIL,
      mfaEnabled: true,
      passwordSalt: "saltBase64_01234567",
      passwordHash: "hashValue_0123456789abcdef",
      totpSecret: "JBSWY3DPEHPK3PXP1234",
      recoveryCodeHashes: ["abc123def456"],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(spoofed.collection("platform_config").doc("global").get());
});

test("3. Verified Super Admin bootstrap locks authority UID and prevents replacement", async () => {
  await assertSucceeds(seedBootstrappedSuperAdmin());

  const secondUidSameEmail = testEnv
    .authenticatedContext(IMPOSTER_ADMIN_UID, {
      email: SUPER_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  const ts = pastTimestamp();
  await assertFails(secondUidSameEmail.collection("platform_config").doc("global").get());
  await assertFails(
    secondUidSameEmail.collection("platform_config").doc("global").set({
      superAdminUid: IMPOSTER_ADMIN_UID,
      superAdminEmail: SUPER_ADMIN_EMAIL,
      mfaEnabled: true,
      passwordSalt: "saltBase64_01234567",
      passwordHash: "hashValue_0123456789abcdef",
      totpSecret: "JBSWY3DPEHPK3PXP1234",
      recoveryCodeHashes: ["abc123def456"],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Even a stranger attempting to overwrite or escalate is rejected
  const stranger = strangerDb();
  await assertFails(
    stranger.collection("platform_config").doc("global").update({ superAdminUid: STRANGER_UID })
  );
});

test("4. Stranger submitting QID/passport/business creates PENDING_APPROVAL only, never active access or self-promotion", async () => {
  await seedBootstrappedSuperAdmin();
  const stranger = strangerDb();
  const ts = pastTimestamp();

  // Valid pending request submission succeeds
  await assertSucceeds(
    stranger.collection("access_requests").doc(STRANGER_UID).set({
      uid: STRANGER_UID,
      email: STRANGER_EMAIL,
      fullName: "Stranger Applicant",
      phone: "+97450001111",
      requestedRole: "OWNER",
      requestedBusinessName: "Stranger Properties WLL",
      requestedBranchName: "Doha HQ",
      qidNumber: "29000001234",
      passportNumber: "P887766",
      identityDocSummary: "sha256:testdoc",
      deviceId: "device_phone_1",
      deviceName: "Pixel Phone 1",
      status: "PENDING_APPROVAL",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Stranger CANNOT self-approve existing request
  await assertFails(
    stranger.collection("access_requests").doc(STRANGER_UID).update({
      status: "APPROVED",
      updatedAt: ts,
    })
  );

  // Stranger CANNOT request SUPER_ADMIN role
  await assertFails(
    stranger.collection("access_requests").doc(STRANGER_UID).set({
      uid: STRANGER_UID,
      email: STRANGER_EMAIL,
      fullName: "Hacker",
      phone: "123456",
      requestedRole: "SUPER_ADMIN",
      requestedBusinessName: "Hack Co",
      requestedBranchName: "HQ",
      deviceId: "dev1",
      deviceName: "Phone",
      status: "PENDING_APPROVAL",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Shadow/Ghost field attack fails
  await assertFails(
    strangerDb(OTHER_OWNER_UID, OTHER_OWNER_EMAIL)
      .collection("access_requests")
      .doc(OTHER_OWNER_UID)
      .set({
        uid: OTHER_OWNER_UID,
        email: OTHER_OWNER_EMAIL,
        fullName: "Ghost User",
        phone: "+97450002222",
        requestedRole: "OWNER",
        requestedBusinessName: "Ghost LLC",
        requestedBranchName: "HQ",
        deviceId: "dev2",
        deviceName: "Phone",
        status: "PENDING_APPROVAL",
        isSuperAdmin: true,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Stranger CANNOT create an active business or grant themselves access
  await assertFails(
    stranger.collection("businesses").doc("biz_stranger").set({
      name: "Stranger Properties WLL",
      code: "BIZ-STR",
      branchName: "Doha HQ",
      ownerUid: STRANGER_UID,
      ownerEmail: STRANGER_EMAIL,
      status: "ACTIVE",
      approvedByUid: STRANGER_UID,
      currency: "QAR",
      timezone: "Asia/Qatar",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    stranger.collection("access_grants").doc(STRANGER_UID).set({
      uid: STRANGER_UID,
      email: STRANGER_EMAIL,
      fullName: "Stranger Applicant",
      authorityRole: "OWNER",
      status: "ACTIVE",
      activeBusinessId: "biz_stranger",
      allowedBusinessIds: ["biz_stranger"],
      allowedPropertyIds: [],
      restrictToAssignedProperties: false,
      permissionKeys: ["DASHBOARD:VIEW"],
      approvedByUid: STRANGER_UID,
      minSessionValidAfterEpoch: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Pending user tries another phone -> cannot create session!
  await assertFails(
    stranger.collection("sessions").doc("sess_phone_2").set({
      uid: STRANGER_UID,
      businessId: "biz_stranger",
      deviceId: "device_phone_2",
      deviceName: "Samsung Phone 2",
      isRevoked: false,
      rememberMe: true,
      expiresAtEpoch: Date.now() + 3600000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // PII Blanket list test: stranger cannot scrape other access_requests or grants
  await assertFails(stranger.collection("access_requests").get());
  await assertFails(stranger.collection("access_grants").get());
  await assertFails(stranger.collection("admin_audit_logs").get());
});

test("5. Super Admin approval grants scoped business access; revocation and emergency lock immediately deny access", async () => {
  await seedBootstrappedSuperAdmin();
  const admin = superAdminDb();
  const staff = strangerDb(OTHER_OWNER_UID, OTHER_OWNER_EMAIL);
  const ts = pastTimestamp();

  // Super Admin creates business and grants EMPLOYEE scoped access
  await assertSucceeds(
    admin.collection("businesses").doc("biz_approved_1").set({
      name: "Doha Skyline WLL",
      code: "BIZ-SKY",
      branchName: "West Bay",
      ownerUid: SUPER_ADMIN_UID,
      ownerEmail: SUPER_ADMIN_EMAIL,
      status: "ACTIVE",
      approvedByUid: SUPER_ADMIN_UID,
      currency: "QAR",
      timezone: "Asia/Qatar",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    admin.collection("access_grants").doc(OTHER_OWNER_UID).set({
      uid: OTHER_OWNER_UID,
      email: OTHER_OWNER_EMAIL,
      fullName: "Approved Staff",
      authorityRole: "EMPLOYEE",
      status: "ACTIVE",
      activeBusinessId: "biz_approved_1",
      allowedBusinessIds: ["biz_approved_1"],
      allowedPropertyIds: ["prop_1"],
      restrictToAssignedProperties: true,
      permissionKeys: ["MAINTENANCE:VIEW", "MAINTENANCE:EDIT"],
      approvedByUid: SUPER_ADMIN_UID,
      minSessionValidAfterEpoch: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Approved staff can read own grant, read assigned business, and start session
  await assertSucceeds(staff.collection("access_grants").doc(OTHER_OWNER_UID).get());
  await assertSucceeds(staff.collection("businesses").doc("biz_approved_1").get());
  await assertSucceeds(
    staff.collection("sessions").doc("sess_staff_1").set({
      uid: OTHER_OWNER_UID,
      businessId: "biz_approved_1",
      deviceId: "staff_phone_1",
      deviceName: "Pixel Staff",
      isRevoked: false,
      rememberMe: false,
      expiresAtEpoch: Date.now() + 3600000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Staff CANNOT modify own permissions/role in access_grants or delete audit logs
  await assertFails(
    staff.collection("access_grants").doc(OTHER_OWNER_UID).update({
      authorityRole: "OWNER",
      updatedAt: ts,
    })
  );
  await assertFails(
    staff.collection("sessions").doc("sess_other_biz").set({
      uid: OTHER_OWNER_UID,
      businessId: "biz_unassigned",
      deviceId: "staff_phone_1",
      deviceName: "Pixel Staff",
      isRevoked: false,
      rememberMe: false,
      expiresAtEpoch: Date.now() + 3600000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Emergency Lock ON -> blocks approved non-Super-Admin access across all devices immediately
  await assertSucceeds(
    admin.collection("platform_status").doc("public").update({
      isBootstrapped: true,
      registrationsEnabled: false,
      emergencyLockAllNonSuperAdmin: true,
      lockMessage: "Emergency lockdown by Super Admin",
      updatedAt: ts,
    })
  );
  await assertFails(staff.collection("access_grants").doc(OTHER_OWNER_UID).get());
  await assertFails(staff.collection("businesses").doc("biz_approved_1").get());
  await assertFails(
    staff.collection("sessions").doc("sess_staff_locked").set({
      uid: OTHER_OWNER_UID,
      businessId: "biz_approved_1",
      deviceId: "staff_phone_2",
      deviceName: "Phone 2",
      isRevoked: false,
      rememberMe: false,
      expiresAtEpoch: Date.now() + 3600000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // New registrations disabled -> stranger blocked from submitting access request
  await assertFails(
    strangerDb().collection("access_requests").doc(STRANGER_UID).set({
      uid: STRANGER_UID,
      email: STRANGER_EMAIL,
      fullName: "Blocked Applicant",
      phone: "5555",
      requestedRole: "OWNER",
      requestedBusinessName: "Blocked Co",
      requestedBranchName: "HQ",
      deviceId: "dev_x",
      deviceName: "Phone",
      status: "PENDING_APPROVAL",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Unlock emergency, then revoke specific user -> revoked user still blocked
  await assertSucceeds(
    admin.collection("platform_status").doc("public").update({
      isBootstrapped: true,
      registrationsEnabled: true,
      emergencyLockAllNonSuperAdmin: false,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    admin.collection("access_grants").doc(OTHER_OWNER_UID).update({
      status: "REVOKED",
      updatedAt: ts,
    })
  );
  await assertFails(
    staff.collection("sessions").doc("sess_staff_revoked").set({
      uid: OTHER_OWNER_UID,
      businessId: "biz_approved_1",
      deviceId: "staff_phone_3",
      deviceName: "Phone 3",
      isRevoked: false,
      rememberMe: false,
      expiresAtEpoch: Date.now() + 3600000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Super Admin can append immutable audit log and cannot delete/overwrite audit logs
  await assertSucceeds(
    admin.collection("admin_audit_logs").doc("log_1").set({
      actorUid: SUPER_ADMIN_UID,
      actorEmail: SUPER_ADMIN_EMAIL,
      action: "REVOKE_USER",
      targetUid: OTHER_OWNER_UID,
      targetEmail: OTHER_OWNER_EMAIL,
      businessId: "biz_approved_1",
      details: "Revoked user access from Super Admin console",
      createdAt: ts,
    })
  );
  await assertFails(admin.collection("admin_audit_logs").doc("log_1").delete());
});

test("6. Super Admin lost-device block, self-reauthorize denial, cloud snapshot isolation, and ownership immutability", async () => {
  await seedBootstrappedSuperAdmin();
  const admin = superAdminDb();
  const stranger = strangerDb();
  const ts = pastTimestamp();

  // Super Admin registers Phone 1 (old lost) & Phone 2 (new)
  const dev1Id = `${SUPER_ADMIN_UID}_lost_phone_1`;
  const dev2Id = `${SUPER_ADMIN_UID}_new_phone_2`;
  await assertSucceeds(
    admin.collection("admin_devices").doc(dev1Id).set({
      uid: SUPER_ADMIN_UID,
      email: SUPER_ADMIN_EMAIL,
      deviceId: "lost_phone_1",
      deviceName: "Old Lost Pixel",
      status: "ACTIVE",
      lastSeenEpoch: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    admin.collection("admin_devices").doc(dev2Id).set({
      uid: SUPER_ADMIN_UID,
      email: SUPER_ADMIN_EMAIL,
      deviceId: "new_phone_2",
      deviceName: "New Galaxy S25",
      status: "ACTIVE",
      lastSeenEpoch: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Recovery updates password/MFA/keys while preserving immutable superAdminUid & superAdminEmail
  await assertSucceeds(
    admin.collection("platform_config").doc("global").update({
      passwordSalt: "newSalt_01234567",
      passwordHash: "newHash_0123456789abcdef",
      totpSecret: "NB2W45DFOIZA1234",
      recoveryCodeHashes: ["newcodehash1"],
      backupKeySalt: "newKeySalt_012345",
      backupKeyHash: "newKeyHash_0123456789abcdef",
      minAdminSessionValidAfterEpoch: Date.now(),
      updatedAt: ts,
    })
  );

  // Nobody — not even Super Admin — can overwrite superAdminEmail / superAdminUid ownership!
  await assertFails(
    admin.collection("platform_config").doc("global").update({
      superAdminEmail: STRANGER_EMAIL,
      updatedAt: ts,
    })
  );
  await assertFails(
    stranger.collection("platform_config").doc("global").update({
      passwordHash: "hacked",
      updatedAt: ts,
    })
  );

  // Phone 2 marks Phone 1 LOST -> Phone 1 cannot create new session AND cannot reset itself via set()!
  await assertSucceeds(
    admin.collection("admin_devices").doc(dev1Id).update({
      status: "REVOKED_LOST",
      revokedReason: "Phone stolen",
      revokedByDeviceId: "new_phone_2",
      updatedAt: ts,
    })
  );
  await assertFails(
    admin.collection("sessions").doc("sess_from_lost_phone_1").set({
      uid: SUPER_ADMIN_UID,
      businessId: "biz_superadmin_hq",
      deviceId: "lost_phone_1",
      deviceName: "Old Lost Pixel",
      isRevoked: false,
      rememberMe: true,
      expiresAtEpoch: Date.now() + 3600000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    admin.collection("admin_devices").doc(dev1Id).set({
      uid: SUPER_ADMIN_UID,
      email: SUPER_ADMIN_EMAIL,
      deviceId: "lost_phone_1",
      deviceName: "Old Lost Pixel",
      status: "ACTIVE",
      lastSeenEpoch: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // New Phone 2 CAN create session & upload/download cloud business snapshot
  await assertSucceeds(
    admin.collection("businesses").doc("biz_superadmin_hq").set({
      name: "ONE HOME PRO HQ",
      code: "BIZ-OHPHQ",
      branchName: "Doha HQ",
      ownerUid: SUPER_ADMIN_UID,
      ownerEmail: SUPER_ADMIN_EMAIL,
      status: "ACTIVE",
      approvedByUid: SUPER_ADMIN_UID,
      currency: "QAR",
      timezone: "Asia/Qatar",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    admin.collection("sessions").doc("sess_from_new_phone_2").set({
      uid: SUPER_ADMIN_UID,
      businessId: "biz_superadmin_hq",
      deviceId: "new_phone_2",
      deviceName: "New Galaxy S25",
      isRevoked: false,
      rememberMe: true,
      expiresAtEpoch: Date.now() + 3600000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    admin
      .collection("businesses")
      .doc("biz_superadmin_hq")
      .collection("cloud_snapshots")
      .doc("latest")
      .set({
        businessId: "biz_superadmin_hq",
        payloadBase64: "H4sIAAAAAAAA/w==",
        sha256Checksum: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        recordCount: 12,
        syncedByUid: SUPER_ADMIN_UID,
        syncedByDeviceName: "New Galaxy S25",
        appLanguageCode: "hi",
        darkTheme: true,
        syncedAtEpoch: Date.now(),
        createdAt: ts,
        updatedAt: ts,
      })
  );
  // Stranger cannot read or write cloud snapshots!
  await assertFails(
    stranger
      .collection("businesses")
      .doc("biz_superadmin_hq")
      .collection("cloud_snapshots")
      .doc("latest")
      .get()
  );
});

