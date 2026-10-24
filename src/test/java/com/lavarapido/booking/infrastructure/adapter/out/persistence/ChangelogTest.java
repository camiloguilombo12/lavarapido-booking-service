package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Changelog de Liquibase")
class ChangelogTest {

    private static String read(String resource) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                ChangelogTest.class.getClassLoader().getResourceAsStream(resource), resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("E-19: la semilla 113 corre después de la 109 y limpia cada dato de ejemplo de Express Car Wash")
    void seed113ClearsEveryExampleValueOfSeed109() throws IOException {
        String master = read("db/changelog/db.changelog-master.xml");
        String seed109 = read("db/changelog/seed/109-seed-establishment-schedule.sql");
        String seed113 = read("db/changelog/seed/113-clear-example-establishment.sql");

        assertTrue(master.indexOf("seed/113-clear-example-establishment.sql") > master.indexOf("seed/109-"));
        for (String example : List.of("Express Car Wash S.A.S.", "Express Car Wash", "901.482.930-1",
                "Calle 127 #19A-48, Bogotá, Colombia", "+57 312 490 8821", "contacto@expresscarwash.co")) {
            String literal = "N'" + example + "'";
            assertTrue(seed109.contains(literal), "el seed 109 ya no trae " + example);
            // en el 113 cada valor de ejemplo aparece en la precondición y en su CASE
            assertTrue(seed113.split(java.util.regex.Pattern.quote(literal), -1).length - 1 >= 2,
                    "el seed 113 no limpia " + example);
        }
    }
}
