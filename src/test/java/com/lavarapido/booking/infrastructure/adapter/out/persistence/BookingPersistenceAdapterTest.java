package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.Booking;
import com.lavarapido.booking.domain.model.BookingLine;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.BookingServiceJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServicePriceJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.BookingJpaRepositories;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServiceJpaRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServicePriceJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("BookingPersistenceAdapter (puntos por línea, A-09)")
class BookingPersistenceAdapterTest {

    private final BookingJpaRepositories.Bookings bookings = mock(BookingJpaRepositories.Bookings.class);
    private final BookingJpaRepositories.Lines lines = mock(BookingJpaRepositories.Lines.class);
    private final BookingPersistenceAdapter adapter = new BookingPersistenceAdapter(bookings, lines,
            mock(BookingJpaRepositories.CancellationReasons.class), mock(ServicePriceJpaRepository.class),
            mock(ServiceJpaRepository.class), mock(EntityManager.class));

    @Test
    @DisplayName("al guardar una línea nueva se congelan los puntos que valía el servicio")
    void newLinesStoreTheirPoints() {
        BookingJpaEntity saved = mock(BookingJpaEntity.class);
        given(saved.getId()).willReturn(9L);
        given(bookings.saveAndFlush(any())).willReturn(saved);
        given(lines.findByBookingId(9L)).willReturn(List.of());
        BookingLine premium = new BookingLine(30L, 2, "PREMIUM", "Premium", new BigDecimal("40000"), (short) 75,
                (short) 1, 30, (short) 3);
        Booking booking = Booking.confirmNew(5L, List.of(premium), Instant.parse("2026-10-01T14:00:00Z"),
                Instant.parse("2026-10-01T15:15:00Z"), (short) 1, 7L, null, Instant.parse("2026-09-30T15:00:00Z"));

        adapter.save(booking, 7L);

        ArgumentCaptor<BookingServiceJpaEntity> line = ArgumentCaptor.forClass(BookingServiceJpaEntity.class);
        verify(lines).save(line.capture());
        assertEquals(30, line.getValue().getLoyaltyPoints());
    }

    @Test
    @DisplayName("al leer se usan los puntos congelados, no los que el admin puso después en el catálogo")
    void readingUsesTheFrozenPoints() {
        ServicePriceJpaEntity price = mock(ServicePriceJpaEntity.class);
        given(price.getId()).willReturn(30L);
        given(price.getServiceId()).willReturn(2);
        given(price.getPrice()).willReturn(new BigDecimal("40000"));
        given(price.getEstimatedMinutes()).willReturn((short) 75);
        ServiceJpaEntity service = mock(ServiceJpaEntity.class);
        given(service.getLoyaltyPoints()).willReturn(50);
        BookingServiceJpaEntity frozen = mock(BookingServiceJpaEntity.class);
        given(frozen.getQuantity()).willReturn((short) 1);
        given(frozen.getLoyaltyPoints()).willReturn(30);
        BookingServiceJpaEntity old = mock(BookingServiceJpaEntity.class);
        given(old.getQuantity()).willReturn((short) 1);
        given(old.getLoyaltyPoints()).willReturn(null);

        assertEquals(30, BookingPersistenceAdapter.toLine(frozen, price, Map.of(2, service)).loyaltyPoints());
        // una línea guardada antes de la columna toma el valor vigente (la migración ya la llena)
        assertEquals(50, BookingPersistenceAdapter.toLine(old, price, Map.of(2, service)).loyaltyPoints());
    }

    @Test
    @DisplayName("BIZ-06: la línea leída lleva el tipo de vehículo de su tarifa")
    void readingCarriesTheVehicleTypeOfThePrice() {
        ServicePriceJpaEntity price = mock(ServicePriceJpaEntity.class);
        given(price.getId()).willReturn(40L);
        given(price.getServiceId()).willReturn(2);
        given(price.getVehicleTypeId()).willReturn((short) 6);
        given(price.getPrice()).willReturn(new BigDecimal("28000"));
        given(price.getEstimatedMinutes()).willReturn((short) 75);
        BookingServiceJpaEntity line = mock(BookingServiceJpaEntity.class);
        given(line.getQuantity()).willReturn((short) 1);

        assertEquals((short) 6, BookingPersistenceAdapter.toLine(line, price, Map.of()).vehicleTypeId());
    }
}
