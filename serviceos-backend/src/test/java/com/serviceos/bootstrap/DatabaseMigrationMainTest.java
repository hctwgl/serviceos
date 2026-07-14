package com.serviceos.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationMainTest {

    @Test
    void deploymentMigrationLocationsMatchApplicationFlywayLocations() throws IOException {
        assertThat(DatabaseMigrationMain.locations()).containsExactlyElementsOf(applicationLocations());
    }

    private static List<String> applicationLocations() throws IOException {
        InputStream input = DatabaseMigrationMainTest.class.getResourceAsStream("/application.yml");
        assertThat(input).as("application.yml must exist on the test classpath").isNotNull();

        List<String> locations = new ArrayList<>();
        boolean readingLocations = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                String trimmed = line.trim();
                if ("locations:".equals(trimmed)) {
                    readingLocations = true;
                    continue;
                }
                if (readingLocations && trimmed.startsWith("- ")) {
                    locations.add(trimmed.substring(2));
                } else if (readingLocations && !trimmed.isBlank() && !trimmed.startsWith("#")) {
                    break;
                }
            }
        }
        return locations;
    }
}
