package com.lavarapido.booking.infrastructure.adapter.out.customer;

import com.lavarapido.booking.domain.exception.DependencyUnavailableException;
import com.lavarapido.booking.domain.model.VehicleSnapshot;
import com.lavarapido.booking.domain.port.out.CustomerDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Habla con customer-service por REST (ADR-004) reenviando el token de quien llama. Asi customer
 * aplica sus propias reglas: un cliente solo ve sus vehiculos y /admin/vehicles exige ADMIN.
 *
 * Si customer no responde, la operacion falla con 503 (CUSTOMER_SERVICE_UNAVAILABLE): no se
 * reserva nada sin validar que el vehiculo es del cliente (INV-BOOK-002).
 */
@Component
class CustomerServiceDirectory implements CustomerDirectory {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceDirectory.class);
    /** Tope de ids por consulta que acepta customer-service. */
    private static final int MAX_IDS = 100;

    private final RestClient client;

    CustomerServiceDirectory(@Value("${app.customer-service.base-url}") String baseUrl,
                             @Value("${app.customer-service.timeout:3s}") Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Optional<VehicleSnapshot> ownVehicle(long vehicleId) {
        try {
            VehicleDto vehicle = client.get()
                    .uri("/vehicles/{id}", vehicleId)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .retrieve()
                    .body(VehicleDto.class);
            return Optional.ofNullable(vehicle).map(dto -> dto.toSnapshot(null));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw unavailable(e);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    @Override
    public List<VehicleSnapshot> ownVehicles() {
        try {
            VehicleDto[] vehicles = client.get()
                    .uri("/vehicles")
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .retrieve()
                    .body(VehicleDto[].class);
            List<VehicleSnapshot> result = new ArrayList<>();
            if (vehicles != null) {
                for (VehicleDto vehicle : vehicles) {
                    result.add(vehicle.toSnapshot(null));
                }
            }
            return result;
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    @Override
    public Map<Long, VehicleSnapshot> vehiclesByIds(Collection<Long> vehicleIds) {
        List<Long> ids = vehicleIds.stream().distinct().toList();
        Map<Long, VehicleSnapshot> result = new HashMap<>();
        for (int start = 0; start < ids.size(); start += MAX_IDS) {
            List<Long> chunk = ids.subList(start, Math.min(start + MAX_IDS, ids.size()));
            String joined = chunk.stream().map(String::valueOf).collect(Collectors.joining(","));
            try {
                OwnedVehicleDto[] found = client.get()
                        .uri(uri -> uri.path("/admin/vehicles").queryParam("ids", joined).build())
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .retrieve()
                        .body(OwnedVehicleDto[].class);
                if (found != null) {
                    for (OwnedVehicleDto owned : found) {
                        result.put(owned.vehicle().id(), owned.vehicle().toSnapshot(owned.ownerUserId()));
                    }
                }
            } catch (RestClientException e) {
                throw unavailable(e);
            }
        }
        return result;
    }

    private static String bearer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return "Bearer " + jwt.getToken().getTokenValue();
        }
        throw new IllegalStateException("customer-service is only called inside an authenticated request");
    }

    private static DependencyUnavailableException unavailable(RestClientException e) {
        log.warn("customer-service call failed: {}", e.getMessage());
        return new DependencyUnavailableException("CUSTOMER_SERVICE_UNAVAILABLE",
                "Could not validate the vehicle with customer-service");
    }

    /** La respuesta de customer-service (VehicleResponse); solo los campos que se usan. */
    record VehicleDto(long id, String licensePlate, String licensePlateFormatted, String vehicleType,
                      short vehicleTypeId, String vehicleTypeName, String brand, String model) {

        VehicleSnapshot toSnapshot(Long ownerUserId) {
            return new VehicleSnapshot(id, licensePlate, licensePlateFormatted, vehicleType, vehicleTypeId,
                    vehicleTypeName, brand, model, ownerUserId);
        }
    }

    record OwnedVehicleDto(VehicleDto vehicle, Long ownerUserId) {
    }
}
