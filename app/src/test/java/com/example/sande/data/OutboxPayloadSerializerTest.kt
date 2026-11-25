package com.example.sande.data

import com.example.sande.data.entities.BusinessProfile
import com.example.sande.data.entities.Counter
import com.example.sande.data.entities.MembershipMirror
import com.example.sande.data.entities.Product
import com.example.sande.data.entities.SaleSession
import com.example.sande.data.entities.StockMovement
import com.example.sande.data.sync.OutboxPayloadSerializer
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Tests for [OutboxPayloadSerializer].
 *
 * The backend parses `outbox_entries.payload_json` with `JSON.parse()` and
 * then reads snake_case properties off it (see `backend/src/routes/sync.ts`).
 * These tests pin that contract: the payload has to be valid JSON, and every
 * field the backend reads has to be present.
 */
class OutboxPayloadSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(payload: String): JsonObject =
        json.parseToJsonElement(payload) as JsonObject

    @Test
    fun `sale session payload is valid json`() {
        val session = SaleSession(
            id = "session1",
            businessId = "biz1",
            receiptNumber = "SAL-ABC-00001",
            deviceId = "device1",
            deviceCode = "ABC123",
            salespersonId = "owner1",
            salespersonRole = "owner",
            subtotalCents = 1000L,
            taxCents = 160L,
            totalCents = 1160L,
            status = "completed",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )

        val payload = parse(OutboxPayloadSerializer.serialize(session))

        // The fields `applySaleSession` reads in the backend.
        assertThat(payload["id"]!!.jsonPrimitive.content).isEqualTo("session1")
        assertThat(payload["receipt_number"]!!.jsonPrimitive.content).isEqualTo("SAL-ABC-00001")
        assertThat(payload["device_id"]!!.jsonPrimitive.content).isEqualTo("device1")
        assertThat(payload["device_code"]!!.jsonPrimitive.content).isEqualTo("ABC123")
        assertThat(payload["salesperson_id"]!!.jsonPrimitive.content).isEqualTo("owner1")
        assertThat(payload["salesperson_role"]!!.jsonPrimitive.content).isEqualTo("owner")
        assertThat(payload["subtotal_cents"]!!.jsonPrimitive.content).isEqualTo("1000")
        assertThat(payload["tax_cents"]!!.jsonPrimitive.content).isEqualTo("160")
        assertThat(payload["total_cents"]!!.jsonPrimitive.content).isEqualTo("1160")
        assertThat(payload["status"]!!.jsonPrimitive.content).isEqualTo("completed")
        assertThat(payload["created_at"]!!.jsonPrimitive.content).isEqualTo("1700000000000")
    }

    @Test
    fun `stock movement payload keeps the signed quantity`() {
        val movement = StockMovement(
            id = "sm1",
            productId = "prod1",
            businessId = "biz1",
            movementType = "SALE",
            quantityDelta = -3L,
            unitPriceCents = 250L,
            actorId = "owner1",
            actorRole = "owner",
            deviceCode = "ABC123",
            reason = "Sale",
            referenceId = "session1",
            reversalOf = null,
            metadataJson = null,
            createdAt = 1700000000000L
        )

        val payload = parse(OutboxPayloadSerializer.serialize(movement))

        assertThat(payload["product_id"]!!.jsonPrimitive.content).isEqualTo("prod1")
        assertThat(payload["movement_type"]!!.jsonPrimitive.content).isEqualTo("SALE")
        // A sale reduces stock, so the delta stays negative.
        assertThat(payload["quantity_delta"]!!.jsonPrimitive.content).isEqualTo("-3")
        assertThat(payload["unit_price_cents"]!!.jsonPrimitive.content).isEqualTo("250")
        assertThat(payload["reference_id"]!!.jsonPrimitive.content).isEqualTo("session1")
    }

    @Test
    fun `void movement reverses the sale with a positive delta`() {
        val movement = StockMovement(
            id = "sm2",
            productId = "prod1",
            businessId = "biz1",
            movementType = "VOID",
            quantityDelta = 3L,
            unitPriceCents = 250L,
            actorId = "owner1",
            actorRole = "owner",
            deviceCode = "ABC123",
            reason = "Void of sale",
            referenceId = "session1",
            reversalOf = null,
            metadataJson = null,
            createdAt = 1700000000000L
        )

        val payload = parse(OutboxPayloadSerializer.serialize(movement))

        assertThat(payload["movement_type"]!!.jsonPrimitive.content).isEqualTo("VOID")
        assertThat(payload["quantity_delta"]!!.jsonPrimitive.content).isEqualTo("3")
    }

    @Test
    fun `product payload carries the delete flag`() {
        val product = Product(
            id = "prod1",
            businessId = "biz1",
            name = "Hammer",
            sku = "HM-001",
            categoryId = "cat1",
            category = "Tools",
            priceCents = 150000L,
            unitOfMeasure = "piece",
            conversionRatio = 1L,
            isActive = true,
            archivedAt = 0L,
            deletedAt = 1700000000000L,
            originalPriceCents = 140000L,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )

        val payload = parse(OutboxPayloadSerializer.serialize(product))

        assertThat(payload["name"]!!.jsonPrimitive.content).isEqualTo("Hammer")
        assertThat(payload["sku"]!!.jsonPrimitive.content).isEqualTo("HM-001")
        assertThat(payload["category_id"]!!.jsonPrimitive.content).isEqualTo("cat1")
        assertThat(payload["price_cents"]!!.jsonPrimitive.content).isEqualTo("150000")
        assertThat(payload["deleted_at"]!!.jsonPrimitive.content).isEqualTo("1700000000000")
        assertThat(payload["is_active"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `business profile payload uses the backend column names`() {
        val profile = BusinessProfile(
            id = "biz1",
            name = "Sunday Hardware",
            category = "hardware",
            phone = "+254700123456",
            countryCode = "KE",
            currencyCode = "KES",
            timezone = "Africa/Nairobi",
            address = "Nairobi",
            logoUri = null,
            negativeStockPolicy = "warn",
            receiptFooter = "Thanks!",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            deletedAt = 0L,
            schemaVersion = 1
        )

        val payload = parse(OutboxPayloadSerializer.serialize(profile))

        assertThat(payload["country_code"]!!.jsonPrimitive.content).isEqualTo("KE")
        assertThat(payload["currency_code"]!!.jsonPrimitive.content).isEqualTo("KES")
        assertThat(payload["negative_stock_policy"]!!.jsonPrimitive.content).isEqualTo("warn")
        assertThat(payload["receipt_footer"]!!.jsonPrimitive.content).isEqualTo("Thanks!")
        assertThat(payload["schema_version"]!!.jsonPrimitive.content).isEqualTo("1")
    }

    @Test
    fun `membership mirror payload identifies the account`() {
        val mirror = MembershipMirror(
            id = "biz1:account1",
            businessId = "biz1",
            accountId = "account1",
            email = "staff@example.com",
            role = "staff",
            status = "active",
            invitedBy = "owner1",
            invitedAt = 1700000000000L,
            acceptedAt = 1700000001000L,
            deviceCode = "ABC123",
            lastSyncedAt = 1700000002000L,
            serverVersion = 3L,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )

        val payload = parse(OutboxPayloadSerializer.serialize(mirror))

        assertThat(payload["account_id"]!!.jsonPrimitive.content).isEqualTo("account1")
        assertThat(payload["email"]!!.jsonPrimitive.content).isEqualTo("staff@example.com")
        assertThat(payload["role"]!!.jsonPrimitive.content).isEqualTo("staff")
        assertThat(payload["last_synced_at"]!!.jsonPrimitive.content).isEqualTo("1700000002000")
        assertThat(payload["server_version"]!!.jsonPrimitive.content).isEqualTo("3")
    }

    @Test
    fun `counter payload carries device and type`() {
        val counter = Counter(
            id = "biz1:ABC123:receipt",
            businessId = "biz1",
            deviceCode = "ABC123",
            counterType = "receipt",
            currentValue = 42L,
            updatedAt = 1700000000000L
        )

        val payload = parse(OutboxPayloadSerializer.serialize(counter))

        assertThat(payload["device_code"]!!.jsonPrimitive.content).isEqualTo("ABC123")
        assertThat(payload["counter_type"]!!.jsonPrimitive.content).isEqualTo("receipt")
        assertThat(payload["current_value"]!!.jsonPrimitive.content).isEqualTo("42")
    }

    @Test
    fun `null optional fields are written as json null rather than omitted`() {
        val product = Product(
            id = "prod1",
            businessId = "biz1",
            name = "Nail",
            sku = null,
            categoryId = null,
            category = null,
            priceCents = 100L,
            unitOfMeasure = null,
            conversionRatio = null,
            isActive = true,
            archivedAt = 0L,
            deletedAt = 0L,
            originalPriceCents = 100L,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L
        )

        val payload = parse(OutboxPayloadSerializer.serialize(product))

        // The backend binds these straight into SQL columns, so they have to
        // be present even when empty.
        assertThat(payload.containsKey("sku")).isTrue()
        assertThat(payload.containsKey("category_id")).isTrue()
        assertThat(payload.containsKey("unit_of_measure")).isTrue()
        assertThat(payload.containsKey("conversion_ratio")).isTrue()
        assertThat(payload["sku"]).isEqualTo(JsonNull)
        assertThat(payload["conversion_ratio"]).isEqualTo(JsonNull)
    }

    @Test
    fun `unsupported entity is rejected`() {
        val error = runCatching {
            OutboxPayloadSerializer.serialize("not an entity")
        }.exceptionOrNull()

        assertThat(error).isNotNull()
    }
}
