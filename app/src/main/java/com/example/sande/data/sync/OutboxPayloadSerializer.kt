package com.example.sande.data.sync

import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Counter
import com.example.sande.data.entities.DeviceRegistration
import com.example.sande.data.entities.MembershipMirror
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.entities.StockMovement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Serializes local entities into the JSON payloads the SANDE backend expects.
 *
 * The backend parses `outbox_entries.payload_json` with `JSON.parse()` and then
 * reads **snake_case** properties out of the resulting object (see
 * `backend/src/routes/sync.ts`, functions `applyProduct`, `applySaleSession`,
 * `applyStockMovement`, …).
 *
 * Using a Kotlin data class' `toString()` instead produces
 * `SaleSession(id=…, businessId=…)`, which is neither valid JSON nor in the
 * expected shape — the backend would reject every entry with a parse error.
 *
 * The field names below therefore mirror the backend contract exactly.
 */
object OutboxPayloadSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /** Serializes any supported entity to its backend JSON representation. */
    fun serialize(entity: Any): String = when (entity) {
        is Product -> product(entity).toString()
        is SaleSession -> saleSession(entity).toString()
        is StockMovement -> stockMovement(entity).toString()
        is MembershipMirror -> membershipMirror(entity).toString()
        is DeviceRegistration -> deviceRegistration(entity).toString()
        is BusinessProfile -> businessProfile(entity).toString()
        is Counter -> counter(entity).toString()
        else -> throw IllegalArgumentException(
            "No outbox payload mapping for ${entity::class.java.simpleName}"
        )
    }

    /** Parses a payload back into a [JsonObject]; useful for tests and logging. */
    fun toJsonObject(payloadJson: String): JsonObject =
        json.parseToJsonElement(payloadJson) as JsonObject

    // ===== product =====

    private fun product(p: Product): JsonObject = buildJsonObject {
        put("id", p.id)
        put("name", p.name)
        put("sku", p.sku)
        put("category_id", p.categoryId)
        put("category", p.category)
        put("price_cents", p.priceCents)
        put("unit_of_measure", p.unitOfMeasure)
        put("conversion_ratio", p.conversionRatio)
        put("original_price_cents", p.originalPriceCents)
        put("is_active", p.isActive)
        put("archived_at", p.archivedAt)
        put("deleted_at", p.deletedAt)
        put("created_at", p.createdAt)
        put("updated_at", p.updatedAt)
    }

    // ===== sale_session =====

    private fun saleSession(s: SaleSession): JsonObject = buildJsonObject {
        put("id", s.id)
        put("receipt_number", s.receiptNumber)
        put("device_id", s.deviceId)
        put("device_code", s.deviceCode)
        put("salesperson_id", s.salespersonId)
        put("salesperson_role", s.salespersonRole)
        put("subtotal_cents", s.subtotalCents)
        put("tax_cents", s.taxCents)
        put("total_cents", s.totalCents)
        put("status", s.status)
        put("created_at", s.createdAt)
        put("updated_at", s.updatedAt)
    }

    // ===== stock_movement =====

    private fun stockMovement(m: StockMovement): JsonObject = buildJsonObject {
        put("id", m.id)
        put("product_id", m.productId)
        put("movement_type", m.movementType)
        put("quantity_delta", m.quantityDelta)
        put("unit_price_cents", m.unitPriceCents)
        put("actor_id", m.actorId)
        put("actor_role", m.actorRole)
        put("device_code", m.deviceCode)
        put("reason", m.reason)
        put("reference_id", m.referenceId)
        put("reversal_of", m.reversalOf)
        put("metadata_json", m.metadataJson)
        put("created_at", m.createdAt)
    }

    // ===== membership_mirror =====

    private fun membershipMirror(m: MembershipMirror): JsonObject = buildJsonObject {
        put("account_id", m.accountId)
        put("email", m.email)
        put("role", m.role)
        put("status", m.status)
        put("invited_by", m.invitedBy)
        put("invited_at", m.invitedAt)
        put("accepted_at", m.acceptedAt)
        put("device_code", m.deviceCode)
        put("last_synced_at", m.lastSyncedAt)
        put("server_version", m.serverVersion)
        put("created_at", m.createdAt)
        put("updated_at", m.updatedAt)
    }

    // ===== device_registration =====

    private fun deviceRegistration(d: DeviceRegistration): JsonObject = buildJsonObject {
        put("id", d.id)
        put("account_id", d.accountId)
        put("device_code", d.deviceCode)
        put("device_name", d.deviceName)
        put("device_type", d.deviceType)
        put("status", d.status)
        put("approved_by", d.approvedBy)
        put("approved_at", d.approvedAt)
        put("revoked_at", d.revokedAt)
        put("revoked_by", d.revokedBy)
        put("last_seen_at", d.lastSeenAt)
        put("last_sync_at", d.lastSyncAt)
        put("push_token", d.pushToken)
        put("created_at", d.createdAt)
        put("updated_at", d.updatedAt)
    }

    // ===== business_profile =====

    private fun businessProfile(b: BusinessProfile): JsonObject = buildJsonObject {
        put("id", b.id)
        put("name", b.name)
        put("category", b.category)
        put("phone", b.phone)
        put("country_code", b.countryCode)
        put("currency_code", b.currencyCode)
        put("timezone", b.timezone)
        put("address", b.address)
        put("logo_r2_key", b.logoUri)
        put("negative_stock_policy", b.negativeStockPolicy)
        put("receipt_footer", b.receiptFooter)
        put("created_at", b.createdAt)
        put("updated_at", b.updatedAt)
        put("schema_version", b.schemaVersion)
    }

    // ===== counter =====

    private fun counter(c: Counter): JsonObject = buildJsonObject {
        put("device_code", c.deviceCode)
        put("counter_type", c.counterType)
        put("current_value", c.currentValue)
        put("updated_at", c.updatedAt)
    }
}
