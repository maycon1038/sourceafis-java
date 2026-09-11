package com.machinezoo.sourceafis.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiometricEngineTest {
    @Test
    void serializedTemplatesCanBeComparedWithManagedDependencies() throws Exception {
        var engine = new BiometricEngine();
        byte[] probe = engine.extract(image("probe.png"), 500);
        byte[] matching = engine.extract(image("matching.png"), 500);
        byte[] other = engine.extract(image("nonmatching.png"), 500);
        assertEquals("3.18.1", engine.version());
        assertTrue(engine.compare(probe, matching) > engine.compare(probe, other));
        assertTrue(engine.compare(probe, probe) > 0);
        assertThrows(IllegalArgumentException.class, () -> engine.extract(new byte[0], 0));
    }

    private byte[] image(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/com/machinezoo/sourceafis/" + name)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }
}
