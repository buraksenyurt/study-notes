package com.lectures.business.design.repository;

import com.lectures.business.design.domain.Order;
import com.lectures.business.design.domain.OrderId;
import com.lectures.business.design.domain.Specification;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> orders = new LinkedHashMap<>();

    @Override
    public void save(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        orders.put(order.orderId(), order);
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public List<Order> findAll() {
        return List.copyOf(orders.values());
    }

    @Override
    public List<Order> findAll(Specification<Order> specification) {
        Objects.requireNonNull(specification, "specification must not be null");
        return orders.values().stream()
                .filter(specification::isSatisfiedBy)
                .sorted(Comparator.comparing(Order::orderId))
                .toList();
    }

    @Override
    public long count() {
        return orders.size();
    }
}
