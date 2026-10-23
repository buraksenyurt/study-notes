package com.lectures.business.design.app;

import com.lectures.business.design.sales.application.OrderMapper;
import com.lectures.business.design.sales.application.OrderService;
import com.lectures.business.design.sales.application.SalesReport;
import com.lectures.business.design.sales.domain.OrderFactory;
import com.lectures.business.design.sales.domain.SequentialOrderIdGenerator;
import com.lectures.business.design.sales.persistence.CustomerRepository;
import com.lectures.business.design.sales.persistence.InMemoryCustomerRepository;
import com.lectures.business.design.sales.persistence.InMemoryOrderRepository;
import com.lectures.business.design.sales.persistence.OrderRepository;
import java.time.Clock;
import java.util.Objects;

public final class SalesModule {

    private final CustomerRepository customers;
    private final OrderService orderService;
    private final SalesReport salesReport;

    private SalesModule(OrderRepository orders, CustomerRepository customers, Clock clock, int firstOrderId) {
        this.customers = customers;

        OrderMapper mapper = new OrderMapper();
        OrderFactory factory = new OrderFactory(new SequentialOrderIdGenerator(firstOrderId), clock);

        this.orderService = new OrderService(orders, customers, factory, mapper, clock);
        this.salesReport = new SalesReport(orders, mapper);
    }

    public static SalesModule inMemory(Clock clock, int firstOrderId) {
        Objects.requireNonNull(clock, "clock must not be null");
        return new SalesModule(new InMemoryOrderRepository(), new InMemoryCustomerRepository(),
                clock, firstOrderId);
    }

    public CustomerRepository customers() {
        return customers;
    }

    public OrderService orderService() {
        return orderService;
    }

    public SalesReport salesReport() {
        return salesReport;
    }
}
