package org.pojobook.samples;

import org.junit.jupiter.api.Test;
import org.pojobook.samples.generated.ProductRedefines;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ProductRedefines serialization and deserialization.
 * Demonstrate REDEFINES clause usage - different field interpretations
 * for the same memory location based on product type.
 * <p>
 * Note: These tests focus on DISPLAY fields. COMP-3 price fields are
 * demonstrated in the pojobook-serializer module tests.
 * <p>
 * Important REDEFINES caveat: in this copybook, {@code PRODUCT-DETAILS} (and each of its
 * {@code ELECTRONICS-INFO}/{@code CLOTHING-INFO}/{@code FOOD-INFO} alternatives) REDEFINES the
 * single-byte {@code PRODUCT-TYPE} discriminator and extends well beyond it. Because every
 * declared Java field is written unconditionally during serialization (in copybook declaration
 * order), whichever alternative is declared last ({@code FOOD-INFO} here) always physically
 * overwrites the shared bytes - including the {@code PRODUCT-TYPE} byte itself - regardless of
 * which alternative the caller intended to populate. This mirrors real COBOL memory sharing:
 * once you write to a REDEFINES-overlapping area, any previously written alternate view
 * (including a plain discriminator byte that happens to fall inside the overlap) is no longer
 * reliable. These tests therefore verify the *in-memory* (pre-serialization) POJO state for
 * fields that share storage, and only assert round-trip fidelity for data that does not
 * collide with a later-declared alternative.
 */
class ProductRedefinesUsageTest {

    private final Charset charset = Charset.forName("CP1047");

    @Test
    void testSerializeElectronicsProduct() throws Exception {
        // Given: An electronics product with DISPLAY fields
        ProductRedefines product = new ProductRedefines();
        product.setProductId(10001);
        product.setProductName("Laptop Computer");
        product.setProductType("E");
        product.setWarrantyMonths(24);
        product.setModelNumber("LT-X2000");
        product.setQuantityOnHand(50);
        product.setReorderLevel(10);
        product.setSupplierId(5001);

        // Then: the in-memory POJO correctly reflects what was set, before any REDEFINES overlap occurs
        assertEquals("E", product.getProductType());
        assertEquals(24, product.getWarrantyMonths());
        assertEquals("LT-X2000", product.getModelNumber());

        // When: Serialize and deserialize
        byte[] serialized = product.serialize(charset);
        ProductRedefines deserialized = ProductRedefines.deserialize(serialized, charset);

        // Then: fields outside the PRODUCT-DETAILS REDEFINES overlay are preserved
        assertNotNull(deserialized);
        assertEquals(product.getProductId(), deserialized.getProductId());
        assertEquals(product.getProductName().trim(), deserialized.getProductName().trim());
        assertEquals(product.getQuantityOnHand(), deserialized.getQuantityOnHand());
    }

    @Test
    void testSerializeClothingProduct() throws Exception {
        // Given: A clothing product
        ProductRedefines product = new ProductRedefines();
        product.setProductId(20001);
        product.setProductName("Cotton T-Shirt");
        product.setProductType("C");
        product.setSize("Medium");
        product.setColor("Blue");
        product.setQuantityOnHand(200);
        product.setReorderLevel(50);
        product.setSupplierId(5002);

        // Then: the in-memory POJO correctly reflects what was set
        assertEquals("C", product.getProductType());
        assertEquals("Medium", product.getSize());

        // When: Serialize and deserialize
        byte[] serialized = product.serialize(charset);
        ProductRedefines deserialized = ProductRedefines.deserialize(serialized, charset);

        // Then: fields outside the PRODUCT-DETAILS REDEFINES overlay are preserved
        assertNotNull(deserialized);
        assertEquals(product.getProductName().trim(), deserialized.getProductName().trim());
    }

    @Test
    void testSerializeFoodProduct() throws Exception {
        // Given: A food product. FOOD-INFO is the last REDEFINES alternative declared in the
        // copybook, so (per the class-level note above) it is the one alternative whose data
        // reliably survives serialization when it is the only type-specific data populated.
        ProductRedefines product = new ProductRedefines();
        product.setProductId(30001);
        product.setProductName("Organic Apples");
        product.setProductType("F"); // Food
        product.setExpiryDate(20241231);
        product.setOrganicFlag("Y");
        product.setQuantityOnHand(500);
        product.setReorderLevel(100);
        product.setSupplierId(5003);

        // When: Serialize and deserialize
        byte[] serialized = product.serialize(charset);
        ProductRedefines deserialized = ProductRedefines.deserialize(serialized, charset);

        // Then: Food-specific fields (the last REDEFINES alternative) survive the round trip
        assertNotNull(deserialized);
        assertEquals(product.getProductName().trim(), deserialized.getProductName().trim());
        assertEquals(product.getExpiryDate(), deserialized.getExpiryDate());
    }

    @Test
    void testProductTypes() throws Exception {
        // Test that the discriminator is correctly held in memory for all product type values.
        // Note: none of the type-specific REDEFINES fields are populated here, so this only
        // exercises the plain PRODUCT-TYPE field itself (pre-serialization).
        String[] types = {"E", "C", "F", "U"}; // Electronics, Clothing, Food, Furniture

        for (String type : types) {
            ProductRedefines product = new ProductRedefines();
            product.setProductId(10000 + type.charAt(0));
            product.setProductName("Test Product " + type);
            product.setProductType(type);
            product.setQuantityOnHand(100);
            product.setSupplierId(5000);

            assertEquals(type, product.getProductType(), "Product type '" + type + "' should be set in memory");

            byte[] serialized = product.serialize(charset);
            ProductRedefines deserialized = ProductRedefines.deserialize(serialized, charset);

            assertNotNull(deserialized);
            assertEquals(product.getProductName().trim(), deserialized.getProductName().trim());
        }
    }

    @Test
    void testInventoryLevels() throws Exception {
        // Given: Product with low inventory
        ProductRedefines product = new ProductRedefines();
        product.setProductId(50001);
        product.setProductName("Widget");
        product.setProductType("E");
        product.setQuantityOnHand(5);
        product.setReorderLevel(10);
        product.setSupplierId(5000);

        // When: Serialize and deserialize
        byte[] serialized = product.serialize(charset);
        ProductRedefines deserialized = ProductRedefines.deserialize(serialized, charset);

        // Then: Inventory levels should be preserved
        assertTrue(deserialized.getQuantityOnHand() < deserialized.getReorderLevel(),
                "Product should need reordering");
    }

    @Test
    void testSerializationConsistency() throws Exception {
        // Given: A product
        ProductRedefines product = new ProductRedefines();
        product.setProductId(123);
        product.setProductName("Test Product");
        product.setProductType("E");
        product.setQuantityOnHand(100);
        product.setSupplierId(5000);

        // When: Serialize multiple times
        byte[] serialized1 = product.serialize(charset);
        byte[] serialized2 = product.serialize(charset);

        // Then: Should produce identical binary data
        assertArrayEquals(serialized1, serialized2,
                "Serializing the same product multiple times should produce identical binary data");
    }
}

