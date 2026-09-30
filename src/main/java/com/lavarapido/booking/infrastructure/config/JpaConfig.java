package com.lavarapido.booking.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Los repositorios del horario y de las reservas estan agrupados como interfaces anidadas
 * (ScheduleJpaRepositories, BookingJpaRepositories); Spring Data solo las encuentra con
 * considerNestedRepositories.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.lavarapido.booking.infrastructure.adapter.out.persistence.repository",
        considerNestedRepositories = true)
class JpaConfig {
}
