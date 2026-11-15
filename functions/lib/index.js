"use strict";

/**
 * sande — Phase 5 Cloud Functions (trusted server-side ledger writes).
 *
 * Callable functions:
 *   recordSale, recordVoid, recordAdjustment, syncStock
 * Shared helpers: auditLog, negative-stock policy, membership/permission checks.
 *
 * Firestore layout (matches ROADMAP Phase 3):
 *   businesses/{b}                 {name, ownerUid, negativeStockPolicy | settings.negativeStockPolicy}
 *   businesses/{b}/members/{uid}   {role, status}
 *   businesses/{b}/products/{p}    {priceCents, isActive, stockBalance, ...}
 *   businesses/{b}/movements/{m}   {productId, type, quantity, unitPriceCents, referenceId, deviceId, ...}
 *   businesses/{b}/sales/{s}       {receiptNumber, totalCents, status, lineItems[], ...}
 *   businesses/{b}/audit/{a}       {action, actorUid, entityId, details, createdAt}
 *
 * Ledger sign convention (same as the Android MovementDao):
 *   RECEIVE +q, SALE -q, ADJUST +q (q is signed), VOID +q (restores stock).
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();
const db = getFirestore();

const REGION = "us-central1";
const OPTIONS = { region: REGION, timeoutSeconds: 30 };
const MAX_LINES_PER_SALE = 100; // each line = 2 writes; Firestore tx limit is 500

const NegativeStockPolicy = Object.freeze({ WARN: "WARN", BLOCK: "BLOCK", ALLOW: "ALLOW" });

/* ------------------------------------------------------------------ */
/* Permissions (mirrors domain/PermissionPolicy.kt)                    */
/* ------------------------------------------------------------------ */

const ROLES_ALL = ["OWNER", "MANAGER", "SALESPERSON"];
const ROLES_MANAGER_UP = ["OWNER", "MANAGER"];

const Permission = Object.freeze({
  RECORD_SALE: ROLES_ALL,
  VOID_SALE: ROLES_MANAGER_UP,
  ADJUST_STOCK: ROLES_MANAGER_UP,
  OVERRIDE_PRICE: ROLES_MANAGER_UP,
  SYNC_STOCK: ROLES_MANAGER_UP,
});

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

const businessRef = (businessId) => db.doc(`businesses/${businessId}`);
const productRef = (businessId, productId) =>
  db.doc(`businesses/${businessId}/products/${productId}`);
const movementRef = (businessId, movementId) =>
  db.doc(`businesses/${businessId}/movements/${movementId}`);
const saleRef = (businessId, saleId) => db.doc(`businesses/${businessId}/sales/${saleId}`);

function requireAuth(request) {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  return request.auth.uid;
}

function requireString(data, key, maxLen = 200) {
  const v = data && data[key];
  if (typeof v !== "string" || v.length === 0 || v.length > maxLen || v.includes("/")) {
    throw new HttpsError("invalid-argument", `Invalid or missing field: ${key}`);
  }
  return v;
}

function requireInt(data, key, { min = Number.MIN_SAFE_INTEGER, allowZero = false } = {}) {
  const v = data && data[key];
  if (!Number.isSafeInteger(v) || v < min || (!allowZero && v === 0)) {
    throw new HttpsError("invalid-argument", `Invalid or missing integer field: ${key}`);
  }
  return v;
}

/** Load the caller's membership inside or outside a transaction and enforce role. */
async function requireMember(tx, businessId, uid, allowedRoles) {
  const ref = db.doc(`businesses/${businessId}/members/${uid}`);
  const snap = tx ? await tx.get(ref) : await ref.get();
  const member = snap.exists ? snap.data() : null;
  if (!member || member.status !== "ACTIVE" || !allowedRoles.includes(member.role)) {
    throw new HttpsError("permission-denied", "Not allowed for this business.");
  }
  return member;
}

function readPolicy(businessData) {
  const raw =
    (businessData && businessData.settings && businessData.settings.negativeStockPolicy) ||
    (businessData && businessData.negativeStockPolicy);
  return NegativeStockPolicy[raw] || NegativeStockPolicy.WARN; // default WARN (decision D8)
}

/** Ledger balance for a list of movement records. */
function computeStockBalance(movements) {
  return movements.reduce((balance, m) => {
    switch (m.type) {
      case "RECEIVE":
      case "ADJUST":
      case "VOID":
        return balance + m.quantity;
      case "SALE":
        return balance - m.quantity;
      default:
        return balance;
    }
  }, 0);
}

/** Queue an audit entry inside a transaction. */
function auditInTx(tx, businessId, action, actorUid, entityId, details) {
  tx.set(db.collection(`businesses/${businessId}/audit`).doc(), {
    action,
    actorUid,
    entityId,
    details: details || {},
    createdAt: FieldValue.serverTimestamp(),
  });
}

/** Standalone audit write (used outside transactions). */
async function auditLog(businessId, action, actorUid, entityId, details) {
  await db.collection(`businesses/${businessId}/audit`).add({
    action,
    actorUid,
    entityId,
    details: details || {},
    createdAt: FieldValue.serverTimestamp(),
  });
}

function movementDoc({ id, businessId, productId, type, quantity, unitPriceCents, referenceId, deviceId, actorUid }) {
  return {
    id,
    businessId,
    productId,
    type, // SALE | RECEIVE | ADJUST | VOID
    quantity,
    unitPriceCents: unitPriceCents === undefined ? null : unitPriceCents,
    referenceId: referenceId || null,
    deviceId,
    actorUid,
    createdAt: Date.now(),
    serverTime: FieldValue.serverTimestamp(),
    synced: true,
  };
}

/* ------------------------------------------------------------------ */
/* recordSale                                                          */
/* ------------------------------------------------------------------ */

/**
 * data: { businessId, saleId (client UUID, idempotency key), deviceId,
 *         receiptNumber?, deviceTime?, items: [{productId, quantity, unitPriceCents}] }
 */
exports.recordSale = onCall(OPTIONS, async (request) => {
  const uid = requireAuth(request);
  const data = request.data || {};
  const businessId = requireString(data, "businessId");
  const saleId = requireString(data, "saleId");
  const deviceId = requireString(data, "deviceId");
  const receiptNumber =
    typeof data.receiptNumber === "string" && data.receiptNumber.length > 0 && data.receiptNumber.length <= 100
      ? data.receiptNumber
      : `${deviceId.slice(0, 6)}-${Date.now()}`;

  if (!Array.isArray(data.items) || data.items.length === 0 || data.items.length > MAX_LINES_PER_SALE) {
    throw new HttpsError("invalid-argument", `items must contain 1-${MAX_LINES_PER_SALE} lines.`);
  }
  const items = data.items.map((it, i) => {
    const line = it || {};
    return {
      productId: requireString(line, "productId"),
      quantity: requireInt(line, "quantity", { min: 1 }),
      unitPriceCents: requireInt(line, "unitPriceCents", { min: 0, allowZero: true }),
      index: i,
    };
  });

  return db.runTransaction(async (tx) => {
    const member = await requireMember(tx, businessId, uid, Permission.RECORD_SALE);

    // Idempotency: a retry of the same saleId returns the stored result.
    const existing = await tx.get(saleRef(businessId, saleId));
    if (existing.exists) {
      const s = existing.data();
      return {
        status: "already_recorded",
        saleId,
        receiptNumber: s.receiptNumber,
        totalCents: s.totalCents,
        warnings: s.warnings || [],
      };
    }

    const bizSnap = await tx.get(businessRef(businessId));
    const policy = readPolicy(bizSnap.exists ? bizSnap.data() : null);

    // Reads first: one read per distinct product.
    const productIds = [...new Set(items.map((i) => i.productId))];
    const productSnaps = await Promise.all(productIds.map((p) => tx.get(productRef(businessId, p))));
    const products = new Map();
    productSnaps.forEach((snap, idx) => {
      if (!snap.exists || snap.data().isActive === false) {
        throw new HttpsError("failed-precondition", `Product unavailable: ${productIds[idx]}`);
      }
      products.set(productIds[idx], snap.data());
    });

    const canOverride = Permission.OVERRIDE_PRICE.includes(member.role);
    const balances = new Map(productIds.map((p) => [p, Number(products.get(p).stockBalance) || 0]));
    const warnings = [];

    for (const line of items) {
      const catalogPrice = products.get(line.productId).priceCents;
      if (!canOverride && Number.isSafeInteger(catalogPrice) && line.unitPriceCents !== catalogPrice) {
        throw new HttpsError("permission-denied", `Price override not allowed: ${line.productId}`);
      }
      balances.set(line.productId, balances.get(line.productId) - line.quantity);
    }

    for (const [productId, balance] of balances) {
      if (balance < 0) {
        if (policy === NegativeStockPolicy.BLOCK) {
          throw new HttpsError("failed-precondition", `Insufficient stock: ${productId}`);
        }
        if (policy === NegativeStockPolicy.WARN) {
          warnings.push({ code: "NEGATIVE_STOCK", productId, resultingBalance: balance });
        }
      }
    }

    // Writes.
    const totalCents = items.reduce((sum, l) => sum + l.quantity * l.unitPriceCents, 0);
    if (!Number.isSafeInteger(totalCents)) {
      throw new HttpsError("invalid-argument", "Sale total out of range.");
    }

    items.forEach((line) => {
      const movementId = `${saleId}_${line.index}`;
      tx.create(
        movementRef(businessId, movementId),
        movementDoc({
          id: movementId,
          businessId,
          productId: line.productId,
          type: "SALE",
          quantity: line.quantity,
          unitPriceCents: line.unitPriceCents,
          referenceId: saleId,
          deviceId,
          actorUid: uid,
        })
      );
    });
    balances.forEach((balance, productId) => {
      tx.update(productRef(businessId, productId), { stockBalance: balance, updatedAt: Date.now() });
    });

    tx.create(saleRef(businessId, saleId), {
      id: saleId,
      businessId,
      receiptNumber,
      totalCents,
      status: "COMPLETED",
      deviceId,
      deviceTime: Number.isSafeInteger(data.deviceTime) ? data.deviceTime : null,
      serverTime: FieldValue.serverTimestamp(),
      createdAt: Date.now(),
      actorUid: uid,
      synced: true,
      warnings,
      lineItems: items.map((l) => ({
        productId: l.productId,
        quantity: l.quantity,
        unitPriceCents: l.unitPriceCents,
        lineTotalCents: l.quantity * l.unitPriceCents,
      })),
    });

    auditInTx(tx, businessId, "SALE_RECORDED", uid, saleId, { receiptNumber, totalCents, lines: items.length });
    warnings.forEach((w) => auditInTx(tx, businessId, "NEGATIVE_STOCK_WARN", uid, w.productId, w));

    return { status: "success", saleId, receiptNumber, totalCents, warnings, stockPolicy: policy };
  });
});

/* ------------------------------------------------------------------ */
/* recordVoid                                                          */
/* ------------------------------------------------------------------ */

/** data: { businessId, saleId, deviceId, reason? } — restores stock with VOID movements. */
exports.recordVoid = onCall(OPTIONS, async (request) => {
  const uid = requireAuth(request);
  const data = request.data || {};
  const businessId = requireString(data, "businessId");
  const saleId = requireString(data, "saleId");
  const deviceId = requireString(data, "deviceId");
  const reason = typeof data.reason === "string" ? data.reason.slice(0, 300) : "";

  return db.runTransaction(async (tx) => {
    await requireMember(tx, businessId, uid, Permission.VOID_SALE);

    const sSnap = await tx.get(saleRef(businessId, saleId));
    if (!sSnap.exists) throw new HttpsError("not-found", "Sale not found.");
    const sale = sSnap.data();
    if (sale.status === "VOIDED") return { status: "already_voided", saleId };

    const lines = Array.isArray(sale.lineItems) ? sale.lineItems : [];
    const productIds = [...new Set(lines.map((l) => l.productId))];
    const pSnaps = await Promise.all(productIds.map((p) => tx.get(productRef(businessId, p))));
    const balances = new Map();
    pSnaps.forEach((snap, idx) => {
      if (snap.exists) balances.set(productIds[idx], Number(snap.data().stockBalance) || 0);
    });

    lines.forEach((line, i) => {
      const movementId = `${saleId}_void_${i}`;
      tx.create(
        movementRef(businessId, movementId),
        movementDoc({
          id: movementId,
          businessId,
          productId: line.productId,
          type: "VOID",
          quantity: line.quantity,
          unitPriceCents: null,
          referenceId: saleId,
          deviceId,
          actorUid: uid,
        })
      );
      if (balances.has(line.productId)) {
        balances.set(line.productId, balances.get(line.productId) + line.quantity);
      }
    });
    balances.forEach((balance, productId) => {
      tx.update(productRef(businessId, productId), { stockBalance: balance, updatedAt: Date.now() });
    });

    tx.update(saleRef(businessId, saleId), {
      status: "VOIDED",
      voidedAt: Date.now(),
      voidedBy: uid,
      voidReason: reason,
    });
    auditInTx(tx, businessId, "SALE_VOIDED", uid, saleId, { reason, lines: lines.length });

    return { status: "success", saleId, restoredLines: lines.length };
  });
});

/* ------------------------------------------------------------------ */
/* recordAdjustment                                                    */
/* ------------------------------------------------------------------ */

/**
 * data: { businessId, productId, adjustmentId (client UUID), deviceId, quantity (signed int ≠ 0), reason? }
 * Negative stock policy applies: BLOCK rejects a result below zero.
 */
exports.recordAdjustment = onCall(OPTIONS, async (request) => {
  const uid = requireAuth(request);
  const data = request.data || {};
  const businessId = requireString(data, "businessId");
  const productId = requireString(data, "productId");
  const adjustmentId = requireString(data, "adjustmentId");
  const deviceId = requireString(data, "deviceId");
  const quantity = requireInt(data, "quantity");
  const reason = typeof data.reason === "string" ? data.reason.slice(0, 300) : "";

  return db.runTransaction(async (tx) => {
    await requireMember(tx, businessId, uid, Permission.ADJUST_STOCK);

    const mRef = movementRef(businessId, adjustmentId);
    const existing = await tx.get(mRef);
    if (existing.exists) return { status: "already_recorded", movementId: adjustmentId };

    const bizSnap = await tx.get(businessRef(businessId));
    const policy = readPolicy(bizSnap.exists ? bizSnap.data() : null);
    const pRef = productRef(businessId, productId);
    const pSnap = await tx.get(pRef);
    if (!pSnap.exists) throw new HttpsError("not-found", "Product not found.");

    const resulting = (Number(pSnap.data().stockBalance) || 0) + quantity;
    const warnings = [];
    if (resulting < 0) {
      if (policy === NegativeStockPolicy.BLOCK) {
        throw new HttpsError("failed-precondition", "Adjustment would make stock negative.");
      }
      if (policy === NegativeStockPolicy.WARN) {
        warnings.push({ code: "NEGATIVE_STOCK", productId, resultingBalance: resulting });
      }
    }

    tx.create(
      mRef,
      movementDoc({
        id: adjustmentId,
        businessId,
        productId,
        type: "ADJUST",
        quantity,
        unitPriceCents: null,
        referenceId: null,
        deviceId,
        actorUid: uid,
      })
    );
    tx.update(pRef, { stockBalance: resulting, updatedAt: Date.now() });
    auditInTx(tx, businessId, "STOCK_ADJUSTED", uid, productId, { quantity, reason, resultingBalance: resulting });
    warnings.forEach((w) => auditInTx(tx, businessId, "NEGATIVE_STOCK_WARN", uid, productId, w));

    return { status: "success", movementId: adjustmentId, stockBalance: resulting, warnings };
  });
});

/* ------------------------------------------------------------------ */
/* syncStock                                                           */
/* ------------------------------------------------------------------ */

/**
 * data: { businessId, productId? } — recompute stockBalance from the movement ledger.
 * Without productId, reconciles every product of the business.
 */
exports.syncStock = onCall({ ...OPTIONS, timeoutSeconds: 120 }, async (request) => {
  const uid = requireAuth(request);
  const data = request.data || {};
  const businessId = requireString(data, "businessId");
  await requireMember(null, businessId, uid, Permission.SYNC_STOCK);

  let productDocs;
  if (data.productId !== undefined) {
    const snap = await productRef(businessId, requireString(data, "productId")).get();
    if (!snap.exists) throw new HttpsError("not-found", "Product not found.");
    productDocs = [snap];
  } else {
    productDocs = (await db.collection(`businesses/${businessId}/products`).get()).docs;
  }

  const corrections = [];
  for (const doc of productDocs) {
    const movSnap = await db
      .collection(`businesses/${businessId}/movements`)
      .where("productId", "==", doc.id)
      .get();
    const computed = computeStockBalance(movSnap.docs.map((d) => d.data()));
    const stored = Number(doc.data().stockBalance) || 0;
    if (computed !== stored) {
      await doc.ref.update({ stockBalance: computed, updatedAt: Date.now() });
      corrections.push({ productId: doc.id, was: stored, now: computed });
    }
  }

  if (corrections.length > 0) {
    await auditLog(businessId, "STOCK_RECONCILED", uid, data.productId || "ALL", { corrections });
  }
  return { status: "success", checked: productDocs.length, corrected: corrections.length, corrections };
});
