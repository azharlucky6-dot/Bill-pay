const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function validUserProfile(uid) {
  const ts = pastTime(10000);
  return {
    userId: uid,
    displayName: "Aarav Sharma",
    email: "aarav@example.com",
    termsAccepted: false,
    termsVersion: "",
    screenShieldEnabled: true,
    biometricConfirmEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validSavedBiller(uid) {
  const ts = pastTime(10000);
  return {
    userId: uid,
    category: "ELECTRICITY",
    providerName: "BESCOM Bengaluru",
    billerCode: "BBPS-BESCOM-KA",
    nickname: "Home Electricity",
    maskedIdentifier: "KA-90482103",
    lastBillAmountPaise: 248000,
    dueDayOfMonth: 15,
    autopayReminder: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validTransaction(uid, status = "SETTLED") {
  const ts = pastTime(10000);
  return {
    userId: uid,
    billerId: "biller_1",
    category: "ELECTRICITY",
    providerName: "BESCOM Bengaluru",
    maskedIdentifier: "KA-90482103",
    amountPaise: 248000,
    convenienceFeePaise: 0,
    paymentRail: "UPI_INTENT",
    tokenRef: "upi_intent_49281",
    bbpsRefId: "BP261120A9F2",
    status,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validGrievance(uid, txId) {
  const ts = pastTime(5000);
  return {
    userId: uid,
    transactionId: txId,
    bbpsRefId: "BP261120A9F2",
    complaintRef: "CRN261120X41",
    reason: "DEBITED_NOT_UPDATED",
    notes: "UPI debited but biller portal still shows due.",
    slaWindow: "T+1 to T+5 Business Days",
    status: "OPEN",
    createdAt: ts,
    updatedAt: ts,
  };
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("billers").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("grievances").get()
  );
});

test("UserProfile: owner create, T&C accept, security toggle, and ghost-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validUserProfile(ALICE_UID)));

  // Action 1: Accept Terms and Conditions
  await assertSucceeds(
    profileRef.update({
      termsAccepted: true,
      termsVersion: "v1.0-IN",
      updatedAt: pastTime(1000),
    })
  );

  // Action 2: Update Security Preferences
  await assertSucceeds(
    profileRef.update({
      screenShieldEnabled: false,
      biometricConfirmEnabled: true,
      updatedAt: pastTime(500),
    })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    profileRef.update({
      screenShieldEnabled: true,
      isAdmin: true,
      updatedAt: pastTime(200),
    })
  );

  // Cross-user read & write must fail
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      screenShieldEnabled: false,
      biometricConfirmEnabled: false,
      updatedAt: pastTime(100),
    })
  );
});

test("SavedBiller: valid create/update/list/delete and Zero Raw PAN rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const billersCol = aliceDb.collection("users").doc(ALICE_UID).collection("billers");

  // Valid electricity biller
  await assertSucceeds(billersCol.doc("biller_1").set(validSavedBiller(ALICE_UID)));

  // Valid masked credit card biller (last 4 only)
  const cardBiller = {
    ...validSavedBiller(ALICE_UID),
    category: "CREDIT_CARD",
    providerName: "HDFC Bank Credit Card",
    billerCode: "BBPS-HDFC-CC",
    nickname: "HDFC Millennia",
    maskedIdentifier: "•••• 4821",
  };
  await assertSucceeds(billersCol.doc("biller_cc").set(cardBiller));

  // SECURITY: Raw 16-digit credit card PAN must be rejected by rules!
  await assertFails(
    billersCol.doc("biller_pan_fail").set({
      ...cardBiller,
      maskedIdentifier: "4111111111111111",
    })
  );

  // SECURITY: Raw spaced 16-digit PAN must also be rejected!
  await assertFails(
    billersCol.doc("biller_pan_space_fail").set({
      ...cardBiller,
      maskedIdentifier: "4111 1111 1111 1111",
    })
  );

  // Reject @DocumentId 'id' injected in document payload
  await assertFails(
    billersCol.doc("biller_id_fail").set({
      id: "biller_id_fail",
      ...validSavedBiller(ALICE_UID),
    })
  );

  // Reject future timestamp
  await assertFails(
    billersCol.doc("biller_future").set({
      ...validSavedBiller(ALICE_UID),
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Owner list query succeeds
  await assertSucceeds(billersCol.get());

  // Partial update Action 1 (BillerRepository.updateBiller) succeeds
  await assertSucceeds(
    billersCol.doc("biller_1").update({
      nickname: "Updated Home Power",
      lastBillAmountPaise: 295000,
      dueDayOfMonth: 18,
      autopayReminder: false,
      updatedAt: pastTime(1500),
    })
  );

  // Partial update Action 2 (BillerRepository.refreshBillAmount) succeeds
  await assertSucceeds(
    billersCol.doc("biller_1").update({
      lastBillAmountPaise: 312000,
      updatedAt: pastTime(1000),
    })
  );

  // Cross-user list/read/delete fails
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("billers").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("billers").doc("biller_1").delete()
  );

  // Owner delete succeeds
  await assertSucceeds(billersCol.doc("biller_1").delete());
});

test("BillTransaction: lifecycle state machine, immutability, and terminal REFUNDED lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txCol = aliceDb.collection("users").doc(ALICE_UID).collection("transactions");

  // Create pending transaction
  await assertSucceeds(
    txCol.doc("tx_1").set(validTransaction(ALICE_UID, "PENDING_SETTLEMENT"))
  );

  // Cannot create directly as REFUNDED
  await assertFails(
    txCol.doc("tx_bad").set(validTransaction(ALICE_UID, "REFUNDED"))
  );

  // Transition PENDING_SETTLEMENT -> REFUND_INITIATED
  await assertSucceeds(
    txCol.doc("tx_1").update({
      status: "REFUND_INITIATED",
      updatedAt: pastTime(2000),
    })
  );

  // Cannot mutate amountPaise
  await assertFails(
    txCol.doc("tx_1").update({
      amountPaise: 100,
      updatedAt: pastTime(1500),
    })
  );

  // Transition REFUND_INITIATED -> REFUNDED
  await assertSucceeds(
    txCol.doc("tx_1").update({
      status: "REFUNDED",
      updatedAt: pastTime(1000),
    })
  );

  // Terminal State Lock: REFUNDED cannot transition again
  await assertFails(
    txCol.doc("tx_1").update({
      status: "SETTLED",
      updatedAt: pastTime(500),
    })
  );

  // Transactions cannot be deleted
  await assertFails(txCol.doc("tx_1").delete());

  // Owner list query succeeds
  await assertSucceeds(txCol.get());
});

test("GrievanceTicket: relational exists() transaction check, escalation, and terminal RESOLVED lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const txCol = aliceDb.collection("users").doc(ALICE_UID).collection("transactions");
  const grievanceCol = aliceDb.collection("users").doc(ALICE_UID).collection("grievances");

  // Orphaned grievance referencing non-existent transaction must fail
  await assertFails(
    grievanceCol.doc("g_orphan").set(validGrievance(ALICE_UID, "missing_tx"))
  );

  // Create backing transaction first
  await assertSucceeds(txCol.doc("tx_100").set(validTransaction(ALICE_UID, "SETTLED")));

  // Now valid grievance creation succeeds
  await assertSucceeds(
    grievanceCol.doc("g_1").set(validGrievance(ALICE_UID, "tx_100"))
  );

  // Escalate OPEN -> ESCALATED
  await assertSucceeds(
    grievanceCol.doc("g_1").update({
      status: "ESCALATED",
      notes: "Escalated to Nodal Officer after T+5 window.",
      updatedAt: pastTime(2000),
    })
  );

  // Resolve ESCALATED -> RESOLVED
  await assertSucceeds(
    grievanceCol.doc("g_1").update({
      status: "RESOLVED",
      notes: "Auto-refund credited to source UPI account.",
      updatedAt: pastTime(1000),
    })
  );

  // Terminal State Lock: RESOLVED cannot be modified
  await assertFails(
    grievanceCol.doc("g_1").update({
      status: "OPEN",
      notes: "Reopen attempt",
      updatedAt: pastTime(500),
    })
  );

  // Grievances cannot be deleted
  await assertFails(grievanceCol.doc("g_1").delete());

  // Cross-user read denied
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("grievances").get()
  );
});
