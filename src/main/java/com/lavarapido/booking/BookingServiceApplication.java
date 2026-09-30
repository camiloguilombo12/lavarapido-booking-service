package com.lavarapido.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

// punto de entrada del booking-service
@SpringBootApplication
public class BookingServiceApplication {

    public static void main(String[] args) {
        // El JVM corre en UTC: con hibernate.jdbc.time_zone=UTC, las columnas TIME (horario) se
        // corrian 5 horas si el JVM estaba en hora de Colombia. La hora del lavadero se aplica a
        // mano con app.booking.zone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
