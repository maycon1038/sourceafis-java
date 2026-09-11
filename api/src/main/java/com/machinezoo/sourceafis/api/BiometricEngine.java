package com.machinezoo.sourceafis.api;

import com.machinezoo.sourceafis.FingerprintCompatibility;
import com.machinezoo.sourceafis.FingerprintImage;
import com.machinezoo.sourceafis.FingerprintImageOptions;
import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;
import org.springframework.stereotype.Service;

/** Extraction and comparison run in the JVM; PostgreSQL stores serialized templates. */
@Service
public class BiometricEngine {
    public String version() {
        return FingerprintCompatibility.version();
    }

    public byte[] extract(byte[] image, int dpi) {
        if (dpi <= 0) {
            throw new IllegalArgumentException("DPI must be positive");
        }
        return new FingerprintTemplate(new FingerprintImage(image,
            new FingerprintImageOptions().dpi(dpi))).toByteArray();
    }

    public double compare(byte[] probe, byte[] candidate) {
        return new FingerprintMatcher(new FingerprintTemplate(probe))
            .match(new FingerprintTemplate(candidate));
    }
}
