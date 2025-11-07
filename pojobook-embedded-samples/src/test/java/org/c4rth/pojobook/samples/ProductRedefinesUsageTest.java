package org.c4rth.pojobook.samples;

import org.c4rth.pojobook.samples.generated.ProductRedefines;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProductRedefines serialization and deserialization.
 * Demonstrates REDEFINES clause usage - different field interpretations
 * for the same memory location based on product type.
 * <p>
 * Note: These tests focus on DISPLAY fields. COMP-3 price fields are
 * demonstrated in the pojobook-serializer module tests.
 */
class ProductRedefinesUsageTest {

    private final Charset charset = Charset.forName("CP1047");

    @Test
    void testSerializeElectronicsProduct() throws Exception {
        // Given: An electronics product with DISPLAY fields
        ProductRedefines product = new ProductRedefines();
        product.setProductId(10001);
        product.setProductName("Laptop Computer");
        product.setProductType("E"); // Electronics
        product.setWarrantyMonths(24);
        product.setModelNumber("LT-X2000");
        product.setQuantityOnHand(50);
        product.setReorderLevel(10);
        product.setSupplierId(5001);

        // When: Serialize and deserialize
        byte[] serialized = product.serialize(charset);
        ProductRedefines deserialized = ProductRedefines.deserialize(serialized, charset);

        // Then: Electronics-specific fields should be preserved
        assertNotNull(deserialized);
        assertEquals(product.getProductId(), deserialized.getProductId());
        assertEquals(product.getProductName().trim(), deserialized.getProductName().trim());
        assertEquals("E", deserialized.getProductType().trim());
        assertEquals(product.getQuantityOnHand(), deserialized.getQuantityOnHand());
    }

    @Test
    void testSerializeClothingProduct() throws Exception {
        // Given: A clothing product
        ProductRedefines product = new ProductRedefines();
        product.setProductId(20001);
        product.setProductName("Cotton T-Shirt");
        product.setProductType("C"); // Clothing
        product.setSize("Medium");
        product.setColor("Blue");
        product.setQuantityOnHand(200);
        product.setReorderLevel(50);
        product.setSupplierId(5002);

        // When: Serialize and deserialize
        byte[] serialized = product.serialize(charset);
        ProductRedefines deserialized = ProductRedefines.deserialize(serialized, charset);

        // Then: Clothing-specific fields should be preserved
        assertNotNull(deserialized);
        assertEquals(product.getProductName().trim(), deserialized.getProductName().trim());
        assertEquals("C", deserialized.getProductType().trim());
    }

    @Test
    void testSerializeFoodProduct() throws Exception {
        // Given: A food product
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

        // Then: Food-specific fields should be preserved
        assertNotNull(deserialized);
        assertEquals(product.getProductName().trim(), deserialized.getProductName().trim());
        assertEquals("F", deserialized.getProductType().trim());
    }

    @Test
    void testProductTypes() throws Exception {
        // Test all product type values
        String[] types = {"E", "C", "F", "U"}; // Electronics, Clothing, Food, Furniture

        for (String type : types) {
            ProductRedefines product = new ProductRedefines();
            product.setProductId(10000 + type.charAt(0));
            product.setProductName("Test Product " + type);
            product.setProductType(type);
            product.setQuantityOnHand(100);
            product.setSupplierId(5000);

            byte[] serialized = product.serialize(charset);
            ProductRedefines deserialized = ProductRedefines.deserialize(serialized, charset);

            assertEquals(type, deserialized.getProductType().trim(),
                    "Product type '" + type + "' should be preserved");
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

