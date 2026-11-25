const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_shop_owner";
const BOB_UID = "bob_other_tailor";
const STAFF_UID = "staff_karigar_01";
const STAFF2_UID = "staff_karigar_02";
const PHONE_ID = "device_phone_01";
const TABLET_ID = "device_tablet_02";
const REVOKED_ID = "device_revoked_03";
const STAFF_PHONE_ID = "staff_phone_01";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function shopPayload(uid = ALICE_UID) {
  const ts = pastDate(10000);
  return {
    ownerUid: uid,
    shopName: "Al-Noor Tailors",
    contactNumber: "+923001234567",
    email: "owner@alnoor.pk",
    nextOrderNumber: 1001,
    version: 1,
    createdAt: ts,
    updatedAt: ts,
  };
}

function devicePayload(
  uid = ALICE_UID,
  role = "PHONE",
  status = "AUTHORIZED",
  userUid = ALICE_UID,
  userEmail = "owner@alnoor.pk",
  userRole = "OWNER"
) {
  const ts = pastDate(10000);
  return {
    ownerUid: uid,
    userUid,
    userEmail,
    userRole,
    deviceName: role === "PHONE" ? "Shop Phone" : "Shop Tablet",
    deviceRole: role,
    status,
    appVersion: "1.0",
    registeredAt: ts,
    lastSeenAt: ts,
    updatedAt: ts,
  };
}

function staffUserPayload(ownerUid = ALICE_UID, staffUid = STAFF_UID, email = "staff1@alnoor.pk", status = "ACTIVE") {
  const ts = pastDate(10000);
  return {
    ownerUid,
    staffUid,
    displayName: "Master Usman",
    email,
    role: "STAFF",
    status,
    createdAt: ts,
    updatedAt: ts,
  };
}

function staffLinkPayload(ownerUid = ALICE_UID, staffUid = STAFF_UID, email = "staff1@alnoor.pk", status = "ACTIVE") {
  const ts = pastDate(10000);
  return {
    staffUid,
    ownerUid,
    displayName: "Master Usman",
    email,
    status,
    createdAt: ts,
    updatedAt: ts,
  };
}

function customerPayload(uid = ALICE_UID, deviceId = PHONE_ID) {
  const ts = pastDate(8000);
  return {
    ownerUid: uid,
    name: "Tariq Mahmood",
    phone: "+923001112233",
    normalizedPhone: "+923001112233",
    notes: "Regular VIP client",
    version: 1,
    lastModifiedByDevice: deviceId,
    deleted: false,
    createdAtEpochMillis: Date.now() - 8000,
    updatedAtEpochMillis: Date.now() - 8000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function measurementPayload(uid = ALICE_UID, customerId = "cust_1", deviceId = PHONE_ID) {
  const ts = pastDate(7000);
  return {
    ownerUid: uid,
    customerId,
    label: "Eid Suit 2026",
    currentFlag: true,
    kameezLambai: 41.5,
    tera: 18.0,
    bazu: 24.5,
    chati: 42.0,
    kamar: 38.0,
    gheraDaman: 44.0,
    galaCollar: 16.0,
    cuff: 9.5,
    shalwarLambai: 40.0,
    paincha: 7.5,
    gheraAsan: 16.0,
    shalwarGhair: 46.0,
    neckStyle: "BAN",
    damanStyle: "GOL",
    pocketStyle: "TWO_SIDE",
    sleeveStyle: "KAF",
    silaiStyle: "KAMDO_DHAGA",
    shalwarType: "BALOCHI",
    shoulderType: "DOWN_SHOULDER",
    rawInputsJson: "{\"bazu\":\"23/12\"}",
    notes: "Soft canvas collar",
    version: 1,
    lastModifiedByDevice: deviceId,
    deleted: false,
    createdAtEpochMillis: Date.now() - 7000,
    updatedAtEpochMillis: Date.now() - 7000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function orderPayload(uid = ALICE_UID, customerId = "cust_1", deviceId = PHONE_ID, orderNumber = 1001) {
  const ts = pastDate(6000);
  const payload = {
    ownerUid: uid,
    customerId,
    suitCount: 2,
    deliveryDateMillis: Date.now() + 86400000 * 3,
    totalAmountPkr: 5000,
    paidAmountPkr: 1500,
    remainingAmountPkr: 3500,
    orderStatus: "PENDING",
    paymentStatus: "PARTIAL",
    measurementSnapshot: {
      sourceMeasurementId: "meas_1",
      kameezLambai: 41.5,
      tera: 18.0,
      bazu: 24.5,
      chati: 42.0,
      kamar: 38.0,
      gheraDaman: 44.0,
      galaCollar: 16.0,
      cuff: 0,
      shalwarLambai: 40.0,
      paincha: 7.5,
      gheraAsan: 16.0,
      shalwarGhair: 46.0,
      rawInputsJson: "{\"bazu\":\"23/12\"}",
      notes: "Soft canvas collar",
    },
    styleSnapshot: {
      neckStyle: "BAN",
      damanStyle: "GOL",
      pocketStyle: "TWO_SIDE",
      sleeveStyle: "KURTA",
      silaiStyle: "KAMDO_DHAGA",
      shalwarType: "PANJAMA",
      shoulderType: "HANGER_SHOULDER",
    },
    notes: "Urgent stitch",
    version: 1,
    lastModifiedByDevice: deviceId,
    deleted: false,
    createdAtEpochMillis: Date.now() - 6000,
    updatedAtEpochMillis: Date.now() - 6000,
    createdAt: ts,
    updatedAt: ts,
  };
  if (orderNumber !== null) {
    payload.orderNumber = orderNumber;
  }
  return payload;
}

function paymentPayload(uid = ALICE_UID, orderId = "order_1", deviceId = PHONE_ID) {
  const ts = pastDate(5000);
  return {
    ownerUid: uid,
    orderId,
    amountPkr: 1500,
    paymentMethod: "CASH",
    note: "Advance",
    deviceId,
    createdAtEpochMillis: Date.now() - 5000,
    createdAt: ts,
  };
}

async function seedShopAndDevices() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("shops").doc(ALICE_UID).set(shopPayload(ALICE_UID));
    await db.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID).set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED"));
    await db.collection("shops").doc(ALICE_UID).collection("devices").doc(REVOKED_ID).set(devicePayload(ALICE_UID, "TABLET", "REVOKED"));
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

test("Unauthenticated users cannot read, list, or write any shop data", async () => {
  await seedShopAndDevices();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection("shops").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("shops").get());
  await assertFails(unauthDb.collection("shops").doc(ALICE_UID).collection("devices").get());
  await assertFails(unauthDb.collection("shops").doc(ALICE_UID).collection("customers").get());
  await assertFails(unauthDb.collection("shops").doc(ALICE_UID).set(shopPayload(ALICE_UID)));
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's shop, devices, and records", async () => {
  await seedShopAndDevices();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertFails(bobDb.collection("shops").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("shops").get());
  await assertFails(bobDb.collection("shops").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, PHONE_ID)));
  await assertFails(bobDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(BOB_UID, PHONE_ID)));
});

test("Shop owner can create/update shop profile, register devices, and revoke a device", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Blanket list on /shops forbidden even for authenticated users
  await assertFails(aliceDb.collection("shops").get());

  // Create shop
  await assertSucceeds(aliceDb.collection("shops").doc(ALICE_UID).set(shopPayload(ALICE_UID)));

  // Reject future timestamp or ghost field
  await assertFails(
    aliceDb.collection("shops").doc(BOB_UID).set({
      ...shopPayload(ALICE_UID),
    })
  );
  await assertFails(
    aliceDb.collection("shops").doc(ALICE_UID).update({
      shopName: "Updated",
      isAdmin: true,
      updatedAt: pastDate(1000),
    })
  );

  // Valid shop profile update
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).update({
      shopName: "Al-Noor Bespoke",
      version: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Register Phone & Tablet
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID).set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED"))
  );
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).collection("devices").doc(TABLET_ID).set(devicePayload(ALICE_UID, "TABLET", "AUTHORIZED"))
  );

  // List devices succeeds for shop owner
  await assertSucceeds(aliceDb.collection("shops").doc(ALICE_UID).collection("devices").get());

  // Revoke tablet
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).collection("devices").doc(TABLET_ID).update({
      status: "REVOKED",
      lastSeenAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("Device authorization gate blocks writes from revoked or unregistered devices", async () => {
  await seedShopAndDevices();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Revoked device write fails
  await assertFails(
    aliceDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, REVOKED_ID))
  );

  // Unknown device write fails
  await assertFails(
    aliceDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, "unknown_device"))
  );

  // Authorized device write succeeds
  await assertSucceeds(
    aliceDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, PHONE_ID))
  );
});

test("Full domain relational chain: customer -> measurement -> order -> append-only payment & terminal state locking", async () => {
  await seedShopAndDevices();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const shopRef = aliceDb.collection("shops").doc(ALICE_UID);

  // Orphan measurement without customer fails
  await assertFails(
    shopRef.collection("measurements").doc("meas_1").set(measurementPayload(ALICE_UID, "missing_cust", PHONE_ID))
  );

  // Create customer (reject @DocumentId ghost 'id' field)
  await assertFails(
    shopRef.collection("customers").doc("cust_bad").set({
      id: "cust_bad",
      ...customerPayload(ALICE_UID, PHONE_ID),
    })
  );
  await assertSucceeds(
    shopRef.collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, PHONE_ID))
  );

  // Create measurement: 0, large values (788), floats, and 8/10 dual-slash are allowed; negative values fail
  await assertFails(
    shopRef.collection("measurements").doc("meas_bad_negative").set({
      ...measurementPayload(ALICE_UID, "cust_1", PHONE_ID),
      kameezLambai: -1,
    })
  );
  await assertSucceeds(
    shopRef.collection("measurements").doc("meas_compulsory_zero_and_large").set({
      ...measurementPayload(ALICE_UID, "cust_1", PHONE_ID),
      kameezLambai: 0,
      tera: 788,
      bazu: 8,
      chati: 0.5,
      rawInputsJson: "{\"KAMEEZ_LAMBAI\":\"0\",\"BAZU\":\"8/10\"}",
    })
  );
  await assertSucceeds(
    shopRef.collection("measurements").doc("meas_optional_zero").set({
      ...measurementPayload(ALICE_UID, "cust_1", PHONE_ID),
      kamar: 0,
      cuff: 0,
      gheraAsan: 0,
      shalwarGhair: 0,
      rawInputsJson: "{}",
    })
  );
  await assertSucceeds(
    shopRef.collection("measurements").doc("meas_1").set(measurementPayload(ALICE_UID, "cust_1", PHONE_ID))
  );
  await assertSucceeds(
    shopRef.collection("measurements").doc("meas_1").update({
      shalwarGhair: 52.5,
      shalwarType: "SMART",
      shoulderType: "FULL_DOWN_SHOULDER",
      silaiStyle: "CHAMAK_DHAGA",
      sleeveStyle: "KURTA",
      rawInputsJson: "{\"bazu\":\"23/12\",\"paincha\":\"7.5/8\"}",
      version: 2,
      lastModifiedByDevice: PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    shopRef.collection("measurements").doc("meas_unselected_styles").set({
      ...measurementPayload(ALICE_UID, "cust_1", PHONE_ID),
      neckStyle: "",
      damanStyle: "",
      pocketStyle: "",
      sleeveStyle: "",
      silaiStyle: "",
      shalwarType: "",
      shoulderType: "",
    })
  );
  await assertFails(
    shopRef.collection("measurements").doc("meas_invalid_style").set({
      ...measurementPayload(ALICE_UID, "cust_1", PHONE_ID),
      neckStyle: "INVALID_STYLE",
    })
  );
  await assertFails(
    shopRef.collection("measurements").doc("meas_invalid_shalwar_type").set({
      ...measurementPayload(ALICE_UID, "cust_1", PHONE_ID),
      shalwarType: "INVALID_SHALWAR",
    })
  );
  await assertFails(
    shopRef.collection("measurements").doc("meas_invalid_shoulder_type").set({
      ...measurementPayload(ALICE_UID, "cust_1", PHONE_ID),
      shoulderType: "INVALID_SHOULDER",
    })
  );
  await assertSucceeds(
    shopRef.collection("measurements").doc("meas_unbounded_shalwar_ghair").set({
      ...measurementPayload(ALICE_UID, "cust_1", PHONE_ID),
      shalwarGhair: 250,
    })
  );
  await assertFails(
    shopRef.collection("measurements").doc("meas_negative_shalwar_ghair").set({
      ...measurementPayload(ALICE_UID, "cust_1", PHONE_ID),
      shalwarGhair: -0.5,
    })
  );

  // Create order & list orders (including empty unselected styles)
  await assertSucceeds(
    shopRef.collection("orders").doc("order_1").set(orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1001))
  );
  await assertSucceeds(
    shopRef.collection("orders").doc("order_unselected_styles").set({
      ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1002),
      styleSnapshot: {
        neckStyle: "",
        damanStyle: "",
        pocketStyle: "",
        sleeveStyle: "",
        silaiStyle: "",
        shalwarType: "",
        shoulderType: "",
      },
    })
  );
  await assertFails(
    shopRef.collection("orders").doc("order_invalid_shoulder_type").set({
      ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1005),
      styleSnapshot: {
        ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1005).styleSnapshot,
        shoulderType: "INVALID_SHOULDER",
      },
    })
  );
  await assertSucceeds(
    shopRef.collection("orders").doc("order_custom_measurements").set({
      ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1003),
      measurementSnapshot: {
        ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1003).measurementSnapshot,
        kameezLambai: 0,
        tera: 788.5,
        bazu: 8,
        rawInputsJson: "{\"KAMEEZ_LAMBAI\":\"0\",\"BAZU\":\"8/10\"}",
      },
    })
  );
  await assertFails(
    shopRef.collection("orders").doc("order_negative_measurement").set({
      ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1004),
      measurementSnapshot: {
        ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1004).measurementSnapshot,
        kameezLambai: -5,
      },
    })
  );
  await assertSucceeds(shopRef.collection("orders").get());

  // Cannot tamper with measurementSnapshot or mismatch remainingAmountPkr
  await assertFails(
    shopRef.collection("orders").doc("order_1").update({
      paidAmountPkr: 5000,
      remainingAmountPkr: 500, // invalid math!
      paymentStatus: "PAID",
      version: 2,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(500),
    })
  );

  // Valid payment + order balance settlement
  await assertSucceeds(
    shopRef.collection("payments").doc("pay_1").set(paymentPayload(ALICE_UID, "order_1", PHONE_ID))
  );

  // Payments are strictly append-only (no update or delete)
  await assertFails(
    shopRef.collection("payments").doc("pay_1").update({ amountPkr: 9999 })
  );
  await assertFails(
    shopRef.collection("payments").doc("pay_1").delete()
  );

  // Order transition PENDING -> READY -> DELIVERED
  await assertSucceeds(
    shopRef.collection("orders").doc("order_1").update({
      orderStatus: "READY",
      version: 2,
      lastModifiedByDevice: PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    shopRef.collection("orders").doc("order_1").update({
      orderStatus: "DELIVERED",
      version: 3,
      lastModifiedByDevice: PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(300),
    })
  );

  // Terminal state lock: DELIVERED cannot transition back to PENDING
  await assertFails(
    shopRef.collection("orders").doc("order_1").update({
      orderStatus: "PENDING",
      version: 4,
      lastModifiedByDevice: PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(200),
    })
  );

  // Order number reservation creation succeeds for authorized device
  await assertSucceeds(
    shopRef.collection("order_reservations").doc("res_1").set({
      ownerUid: ALICE_UID,
      deviceId: PHONE_ID,
      startNumber: 1001,
      endNumber: 1050,
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
});

test("Owner can provision staff user atomically; staff can sync on own device but cannot escalate privileges or use owner's device", async () => {
  await seedShopAndDevices();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const staffDb = testEnv.authenticatedContext(STAFF_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Single-document staff_users creation without matching staff_links fails atomic rule
  await assertFails(
    aliceDb.collection("shops").doc(ALICE_UID).collection("staff_users").doc(STAFF_UID)
      .set(staffUserPayload(ALICE_UID, STAFF_UID))
  );

  // Outsider Bob cannot create a staff link pointing into Alice's shop
  await assertFails(
    outsiderDb.collection("staff_links").doc(STAFF_UID)
      .set(staffLinkPayload(ALICE_UID, STAFF_UID))
  );

  // Owner atomically creates staff_users + staff_links in a single batch
  const batch = aliceDb.batch();
  batch.set(
    aliceDb.collection("shops").doc(ALICE_UID).collection("staff_users").doc(STAFF_UID),
    staffUserPayload(ALICE_UID, STAFF_UID)
  );
  batch.set(
    aliceDb.collection("staff_links").doc(STAFF_UID),
    staffLinkPayload(ALICE_UID, STAFF_UID)
  );
  await assertSucceeds(batch.commit());

  // Staff reads own staff_link and shop profile
  await assertSucceeds(staffDb.collection("staff_links").doc(STAFF_UID).get());
  await assertSucceeds(staffDb.collection("shops").doc(ALICE_UID).get());

  // Staff cannot edit shop profile
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).update({
      shopName: "Hijacked Tailors",
      version: 2,
      updatedAt: pastDate(500),
    })
  );

  // Staff cannot list or create other staff users
  await assertFails(staffDb.collection("shops").doc(ALICE_UID).collection("staff_users").get());
  const rogueBatch = staffDb.batch();
  rogueBatch.set(
    staffDb.collection("shops").doc(ALICE_UID).collection("staff_users").doc(STAFF2_UID),
    staffUserPayload(ALICE_UID, STAFF2_UID, "staff2@alnoor.pk")
  );
  rogueBatch.set(
    staffDb.collection("staff_links").doc(STAFF2_UID),
    staffLinkPayload(ALICE_UID, STAFF2_UID, "staff2@alnoor.pk")
  );
  await assertFails(rogueBatch.commit());

  // Staff registers their own device (STAFF role + own UID)
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc(STAFF_PHONE_ID)
      .set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED", STAFF_UID, "staff1@alnoor.pk", "OWNER"))
  );
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc(STAFF_PHONE_ID)
      .set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED", STAFF_UID, "staff1@alnoor.pk", "STAFF"))
  );

  // Staff can query their own devices, but cannot query all devices or revoke Owner's device
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices")
      .where("userUid", "==", STAFF_UID).get()
  );
  await assertFails(staffDb.collection("shops").doc(ALICE_UID).collection("devices").get());
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID).update({
      status: "REVOKED",
      lastSeenAt: pastDate(300),
      updatedAt: pastDate(300),
    })
  );

  // Staff can create customers using their own authorized device, NOT owner's deviceId
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_spoof")
      .set(customerPayload(ALICE_UID, PHONE_ID))
  );
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_staff")
      .set(customerPayload(ALICE_UID, STAFF_PHONE_ID))
  );

  // Owner disables Staff user -> Staff is immediately blocked from reading/writing shop data
  const revokeBatch = aliceDb.batch();
  revokeBatch.update(
    aliceDb.collection("shops").doc(ALICE_UID).collection("staff_users").doc(STAFF_UID),
    { status: "REVOKED", updatedAt: pastDate(200) }
  );
  revokeBatch.update(
    aliceDb.collection("staff_links").doc(STAFF_UID),
    { status: "REVOKED", updatedAt: pastDate(200) }
  );
  await assertSucceeds(revokeBatch.commit());

  await assertFails(staffDb.collection("shops").doc(ALICE_UID).get());
  await assertFails(staffDb.collection("shops").doc(ALICE_UID).collection("customers").get());
  await assertFails(
    staffDb.collection("shops").doc(ALICE_UID).collection("customers").doc("cust_after_revoke")
      .set(customerPayload(ALICE_UID, STAFF_PHONE_ID))
  );
});

test("Single-device session transfer: owner and active staff can delete previous device records without cross-user delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const staffDb = testEnv.authenticatedContext(STAFF_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("shops").doc(ALICE_UID).set(shopPayload(ALICE_UID));
    await db.collection("shops").doc(ALICE_UID).collection("staff_users").doc(STAFF_UID)
      .set(staffUserPayload(ALICE_UID, STAFF_UID));
    await db.collection("staff_links").doc(STAFF_UID)
      .set(staffLinkPayload(ALICE_UID, STAFF_UID));
    await db.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID)
      .set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED", ALICE_UID, "owner@alnoor.pk", "OWNER"));
    await db.collection("shops").doc(ALICE_UID).collection("devices").doc(STAFF_PHONE_ID)
      .set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED", STAFF_UID, "staff1@alnoor.pk", "STAFF"));
  });

  // Outsider Bob cannot delete Alice's or staff's devices
  await assertFails(outsiderDb.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID).delete());
  await assertFails(outsiderDb.collection("shops").doc(ALICE_UID).collection("devices").doc(STAFF_PHONE_ID).delete());

  // Staff cannot delete Owner's device
  await assertFails(staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID).delete());

  // Staff can delete their own old device and register their new device atomically
  const staffTransferBatch = staffDb.batch();
  staffTransferBatch.delete(staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc(STAFF_PHONE_ID));
  staffTransferBatch.set(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc("staff_phone_02"),
    devicePayload(ALICE_UID, "PHONE", "AUTHORIZED", STAFF_UID, "staff1@alnoor.pk", "STAFF")
  );
  await assertSucceeds(staffTransferBatch.commit());

  // Owner can delete previous Owner device when switching phones and keep Settings clean
  const ownerTransferBatch = aliceDb.batch();
  ownerTransferBatch.delete(aliceDb.collection("shops").doc(ALICE_UID).collection("devices").doc(PHONE_ID));
  ownerTransferBatch.set(
    aliceDb.collection("shops").doc(ALICE_UID).collection("devices").doc(TABLET_ID),
    devicePayload(ALICE_UID, "TABLET", "AUTHORIZED", ALICE_UID, "owner@alnoor.pk", "OWNER")
  );
  await assertSucceeds(ownerTransferBatch.commit());
});

test("Order updates: mark paid, partial payments, orderNumber assignment, and soft-delete across all statuses", async () => {
  await seedShopAndDevices();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const staffDb = testEnv.authenticatedContext(STAFF_UID).firestore();
  const shopRef = aliceDb.collection("shops").doc(ALICE_UID);

  // Provision staff + staff device to verify staff order updates don't hit CEL expression limits
  const staffBatch = aliceDb.batch();
  staffBatch.set(shopRef.collection("staff_users").doc(STAFF_UID), staffUserPayload(ALICE_UID, STAFF_UID));
  staffBatch.set(aliceDb.collection("staff_links").doc(STAFF_UID), staffLinkPayload(ALICE_UID, STAFF_UID));
  await assertSucceeds(staffBatch.commit());
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("devices").doc(STAFF_PHONE_ID)
      .set(devicePayload(ALICE_UID, "PHONE", "AUTHORIZED", STAFF_UID, "staff1@alnoor.pk", "STAFF"))
  );

  // Seed customer + orders in PENDING, READY, DELIVERED, CANCELLED states + pending orderNumber
  await assertSucceeds(shopRef.collection("customers").doc("cust_1").set(customerPayload(ALICE_UID, PHONE_ID)));
  await assertSucceeds(shopRef.collection("orders").doc("order_pay").set({
    ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1101),
    totalAmountPkr: 2000,
    paidAmountPkr: 0,
    remainingAmountPkr: 2000,
    paymentStatus: "UNPAID",
  }));
  await assertSucceeds(shopRef.collection("orders").doc("order_no_num").set(orderPayload(ALICE_UID, "cust_1", PHONE_ID, null)));
  await assertSucceeds(shopRef.collection("orders").doc("order_ready").set({
    ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1102),
    orderStatus: "READY",
  }));
  await assertSucceeds(shopRef.collection("orders").doc("order_delivered").set({
    ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1103),
    orderStatus: "DELIVERED",
  }));
  await assertSucceeds(shopRef.collection("orders").doc("order_cancelled").set({
    ...orderPayload(ALICE_UID, "cust_1", PHONE_ID, 1104),
    orderStatus: "CANCELLED",
  }));

  // 1. Mark order #1101 PAID using SyncMappers.orderPaymentUpdateMap shape
  await assertSucceeds(
    shopRef.collection("orders").doc("order_pay").update({
      paidAmountPkr: 2000,
      remainingAmountPkr: 0,
      paymentStatus: "PAID",
      version: 2,
      lastModifiedByDevice: PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(400),
    })
  );

  // 2. Assign pending orderNumber via orderDetailsUpdateMap
  await assertSucceeds(
    shopRef.collection("orders").doc("order_no_num").update({
      orderNumber: 1105,
      suitCount: 2,
      deliveryDateMillis: Date.now() + 86400000 * 3,
      orderStatus: "PENDING",
      notes: "Urgent stitch",
      version: 2,
      lastModifiedByDevice: PHONE_ID,
      deleted: false,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(350),
    })
  );

  // 3. Soft-delete 3 orders across PENDING, READY, DELIVERED, CANCELLED via orderDetailsUpdateMap
  for (const [docId, status] of [
    ["order_pay", "PENDING"],
    ["order_ready", "READY"],
    ["order_delivered", "DELIVERED"],
    ["order_cancelled", "CANCELLED"],
  ]) {
    const existingSnap = await shopRef.collection("orders").doc(docId).get();
    await assertSucceeds(
      shopRef.collection("orders").doc(docId).update({
        suitCount: existingSnap.get("suitCount"),
        deliveryDateMillis: existingSnap.get("deliveryDateMillis"),
        orderStatus: status,
        notes: existingSnap.get("notes"),
        version: 3,
        lastModifiedByDevice: PHONE_ID,
        deleted: true,
        updatedAtEpochMillis: Date.now(),
        updatedAt: pastDate(300),
      })
    );
  }

  // 4. Cannot resurrect/un-delete or edit notes on a CANCELLED order or record payment on CANCELLED order
  await assertFails(
    shopRef.collection("orders").doc("order_cancelled").update({
      suitCount: 2,
      deliveryDateMillis: Date.now() + 86400000 * 3,
      orderStatus: "PENDING",
      notes: "Reopened",
      version: 4,
      lastModifiedByDevice: PHONE_ID,
      deleted: true,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(250),
    })
  );
  await assertFails(
    shopRef.collection("orders").doc("order_cancelled").update({
      paidAmountPkr: 5000,
      remainingAmountPkr: 0,
      paymentStatus: "PAID",
      version: 4,
      lastModifiedByDevice: PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(250),
    })
  );

  // 5. Active staff member can also update orderStatus to READY via orderDetailsUpdateMap
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("orders").doc("order_no_num").update({
      suitCount: 2,
      deliveryDateMillis: Date.now() + 86400000 * 3,
      orderStatus: "READY",
      notes: "Urgent stitch",
      version: 3,
      lastModifiedByDevice: STAFF_PHONE_ID,
      deleted: false,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(200),
    })
  );

  // 6. Active staff member can create an order, record payment, mark PAID, and soft-delete
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("orders").doc("order_staff_1")
      .set(orderPayload(ALICE_UID, "cust_1", STAFF_PHONE_ID, 1106))
  );
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("payments").doc("pay_staff_1")
      .set(paymentPayload(ALICE_UID, "order_staff_1", STAFF_PHONE_ID))
  );
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("orders").doc("order_staff_1").update({
      paidAmountPkr: 5000,
      remainingAmountPkr: 0,
      paymentStatus: "PAID",
      version: 2,
      lastModifiedByDevice: STAFF_PHONE_ID,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(100),
    })
  );
  await assertSucceeds(
    staffDb.collection("shops").doc(ALICE_UID).collection("orders").doc("order_staff_1").update({
      suitCount: 2,
      deliveryDateMillis: Date.now() + 86400000 * 3,
      orderStatus: "PENDING",
      notes: "Urgent stitch",
      version: 3,
      lastModifiedByDevice: STAFF_PHONE_ID,
      deleted: true,
      updatedAtEpochMillis: Date.now(),
      updatedAt: pastDate(50),
    })
  );
});

