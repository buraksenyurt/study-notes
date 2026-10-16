package com.lectures.business.design;

import com.lectures.business.design.api.CustomerRevenue;
import com.lectures.business.design.api.OrderSummary;
import com.lectures.business.design.application.OrderMapper;
import com.lectures.business.design.application.SalesReport;
import com.lectures.business.design.domain.Address;
import com.lectures.business.design.domain.Money;
import com.lectures.business.design.domain.Order;
import com.lectures.business.design.domain.OrderId;
import com.lectures.business.design.domain.OrderSpecifications;
import com.lectures.business.design.domain.Specification;
import com.lectures.business.design.persistence.InMemoryOrderRepository;
import com.lectures.business.design.persistence.OrderRepository;

public class BusinessDesign {

    public static void main(String[] args) {

        OrderRepository repository = new InMemoryOrderRepository();
        repository.save(OrderBuilder.anOrder()
                .withId(10248).forCustomer("VINET")
                .withLine(11, "14.00", 10)
                .shippingTo(new Address("Paris merkez", "Paris", "10245", "France"))
                .shipped().build());
        repository.save(OrderBuilder.anOrder()
                .withId(10249).forCustomer("VINET")
                .withLine(11, "10.00", 3)
                .shippingTo(new Address("Paris merkez", "Paris", "10245", "France"))
                .confirmed().build());
        repository.save(OrderBuilder.anOrder()
                .withId(10250).forCustomer("TOMSP")
                .withLine(11, "99.00", 100)
                .shippingTo(new Address("Berlin merkez", "Berlin", "10115", "Germany"))
                .cancelled().build());
        repository.save(OrderBuilder.anOrder()
                .withId(10251).forCustomer("TOMSP")
                .withLine(11, "20.00", 51)
                .shippingTo(new Address("Berlin merkez", "Berlin", "10115", "Germany"))
                .build());

        Specification<Order> spec = OrderSpecifications.notCancelled()
                .and(OrderSpecifications.shippedTo("Germany"))
                .and(OrderSpecifications.worthAtLeast(Money.tl("1000")));

        System.out.println("Order1 satisfies specification: " + spec.isSatisfiedBy(repository.getById(OrderId.of(10248))));
        System.out.println("Order2 satisfies specification: " + spec.isSatisfiedBy(repository.getById(OrderId.of(10249))));
        System.out.println("Order3 satisfies specification: " + spec.isSatisfiedBy(repository.getById(OrderId.of(10250))));
        System.out.println("Order4 satisfies specification: " + spec.isSatisfiedBy(repository.getById(OrderId.of(10251))));

        SalesReport report = new SalesReport(repository, new OrderMapper());

        System.out.println();
        System.out.println("Status based customer counts;");
        report.countByStatus().forEach((status, count)
                -> System.out.println(status + ": " + count));

        System.out.println();
        System.out.println("Revenue (by customer);");
        for (CustomerRevenue row : report.revenueByCustomer()) {
            System.out.println(row.customerId() + ": " + row.revenue() + " " + row.currency());
        }

        System.out.println();
        System.out.println("Total;");
        System.out.println(report.totals());

        System.out.println();
        System.out.println("Orders of VINET (OrderSummary);");
        for (OrderSummary summary : report.ordersOf("VINET")) {
            System.out.println(summary);
        }
    }
}
