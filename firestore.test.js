const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

function safeTimestamp() {
  return new Date(Date.now() - 1000);
}

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validEmailPayload(uid, overrides = {}) {
  return {
    userId: uid,
    senderName: "Clara Vance",
    senderEmail: "clara@studio.io",
    recipientEmail: "alice@example.com",
    subject: "Q4 Editorial Calendar",
    body: "Please review the attached draft milestones before Friday.",
    folder: "inbox",
    read: false,
    starred: false,
    sentAtMillis: Date.now(),
    createdAt: safeTimestamp(),
    updatedAt: safeTimestamp(),
    ...overrides,
  };
}

function validContactPayload(uid, overrides = {}) {
  return {
    userId: uid,
    name: "Clara Vance",
    email: "clara@studio.io",
    createdAt: safeTimestamp(),
    updatedAt: safeTimestamp(),
    ...overrides,
  };
}

function validProfilePayload(uid, overrides = {}) {
  return {
    userId: uid,
    email: "alice@example.com",
    displayName: "Alice",
    mailboxSeeded: true,
    createdAt: safeTimestamp(),
    updatedAt: safeTimestamp(),
    ...overrides,
  };
}

// --- Unauthenticated Rejections ---

test("Unauthenticated user cannot read or write profiles, emails, or contacts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("emails").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("contacts").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("emails")
      .doc("e1")
      .set(validEmailPayload(ALICE_UID))
  );
});

// --- Cross-User Isolation ---

test("Bob cannot read, create, update, or delete Alice's emails or contacts", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("emails")
      .doc("e1")
      .set(validEmailPayload(ALICE_UID));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("contacts")
      .doc("c1")
      .set(validContactPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("emails").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("emails").doc("e1").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("emails")
      .doc("e1")
      .update({ read: true, updatedAt: safeTimestamp() })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("emails")
      .doc("e1")
      .delete()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("contacts").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("contacts")
      .doc("c2")
      .set(validContactPayload(BOB_UID))
  );
});

// --- Owner Happy Paths & Schema Validation ---

test("Alice can create, update, and delete her own profile, emails, and contacts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validProfilePayload(ALICE_UID))
  );

  const emailRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("emails")
    .doc("email_1");
  await assertSucceeds(emailRef.set(validEmailPayload(ALICE_UID)));
  await assertSucceeds(emailRef.update({ read: true, updatedAt: safeTimestamp() }));
  await assertSucceeds(emailRef.update({ starred: true, updatedAt: safeTimestamp() }));
  await assertSucceeds(emailRef.update({ folder: "archive", updatedAt: safeTimestamp() }));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("emails").get()
  );
  await assertSucceeds(emailRef.delete());

  const contactRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("contacts")
    .doc("contact_1");
  await assertSucceeds(contactRef.set(validContactPayload(ALICE_UID)));
  await assertSucceeds(
    contactRef.update({
      name: "Clara Vance-Miller",
      email: "clara.miller@studio.io",
      updatedAt: safeTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").get()
  );
  await assertSucceeds(contactRef.delete());
});

// --- Shadow Update & Dirty Payload Tests ---

test("Rejects ghost fields, invalid folders, invalid contact emails, and uid spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Ghost field on email
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("emails")
      .doc("e_ghost")
      .set(validEmailPayload(ALICE_UID, { isAdmin: true }))
  );

  // Invalid folder enum
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("emails")
      .doc("e_bad_folder")
      .set(validEmailPayload(ALICE_UID, { folder: "spam_hack" }))
  );

  // Spoofed userId
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("contacts")
      .doc("c_spoof")
      .set(validContactPayload(BOB_UID))
  );

  // Invalid email format in contact
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("contacts")
      .doc("c_bad_email")
      .set(validContactPayload(ALICE_UID, { email: "not-an-email" }))
  );
});
