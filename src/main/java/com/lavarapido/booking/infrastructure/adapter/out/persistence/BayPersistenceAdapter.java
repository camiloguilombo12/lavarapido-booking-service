package com.lavarapido.booking.infrastructure.adapter.out.persistence;

import com.lavarapido.booking.domain.model.BayStatus;
import com.lavarapido.booking.domain.model.ServiceBay;
import com.lavarapido.booking.domain.port.out.BayRepository;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.entity.ServiceBayJpaEntity;
import com.lavarapido.booking.infrastructure.adapter.out.persistence.repository.ServiceBayJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bahias (booking.service_bay). El codigo BAY-NN lo pone el sistema, el nombre lo escoge el admin. */
@Repository
class BayPersistenceAdapter implements BayRepository {

    private final ServiceBayJpaRepository bays;

    BayPersistenceAdapter(ServiceBayJpaRepository bays) {
        this.bays = bays;
    }

    @Override
    public List<ServiceBay> findAll() {
        return bays.findByDeletedAtIsNullOrderByIdAsc().stream().map(BayPersistenceAdapter::toBay).toList();
    }

    @Override
    public Optional<ServiceBay> findById(short bayId) {
        return bays.findByIdAndDeletedAtIsNull(bayId).map(BayPersistenceAdapter::toBay);
    }

    @Override
    public boolean existsName(String name, Short exceptBayId) {
        return bays.findByNameIgnoreCaseAndDeletedAtIsNull(name).stream()
                .anyMatch(bay -> exceptBayId == null || !bay.getId().equals(exceptBayId));
    }

    @Override
    public ServiceBay insert(String name, BayStatus status, long actor) {
        ServiceBayJpaEntity entity = new ServiceBayJpaEntity();
        entity.setCode(nextCode());
        entity.setName(name);
        entity.setStatusId(status.id());
        entity.setCreatedBy(actor);
        return toBay(bays.save(entity));
    }

    @Override
    public void update(short bayId, String name, BayStatus status, long actor) {
        ServiceBayJpaEntity entity = bays.findById(bayId).orElseThrow();
        entity.setName(name);
        if (status != null) {
            entity.setStatusId(status.id());
        }
        entity.setUpdatedBy(actor);
        bays.save(entity);
    }

    @Override
    public void softDelete(short bayId, long actor, Instant now) {
        ServiceBayJpaEntity entity = bays.findById(bayId).orElseThrow();
        entity.setDeletedAt(now);
        entity.setDeletedBy(actor);
        bays.save(entity);
    }

    /** BAY-01, BAY-02...: el siguiente numero libre contando tambien las borradas (codigo unico). */
    private String nextCode() {
        long number = bays.count() + 1;
        String code = String.format("BAY-%02d", number);
        while (bays.existsByCode(code)) {
            number++;
            code = String.format("BAY-%02d", number);
        }
        return code;
    }

    private static ServiceBay toBay(ServiceBayJpaEntity entity) {
        return new ServiceBay(entity.getId(), entity.getCode(), entity.getName(), BayStatus.ofId(entity.getStatusId()));
    }
}
