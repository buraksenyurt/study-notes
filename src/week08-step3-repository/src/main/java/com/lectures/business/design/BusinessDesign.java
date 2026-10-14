package com.lectures.business.design;

import com.lectures.business.design.domain.Address;
import com.lectures.business.design.domain.Money;
import com.lectures.business.design.domain.Order;
import com.lectures.business.design.domain.OrderBook;
import com.lectures.business.design.domain.OrderId;
import com.lectures.business.design.domain.OrderSpecifications;
import com.lectures.business.design.domain.Specification;

public class BusinessDesign {

    public static void main(String[] args) {

        OrderBook book = new OrderBook();
        book.add(OrderBuilder.anOrder()
                .withId(10248).forCustomer("VINET")
                .withLine(11, "14.00", 10)
                .shippingTo(new Address("Paris merkez", "Paris", "10245", "France"))
                .shipped().build());
        book.add(OrderBuilder.anOrder()
                .withId(10249).forCustomer("VINET")
                .withLine(11, "10.00", 3)
                .shippingTo(new Address("Paris merkez", "Paris", "10245", "France"))
                .confirmed().build());
        book.add(OrderBuilder.anOrder()
                .withId(10250).forCustomer("TOMSP")
                .withLine(11, "99.00", 100)
                .shippingTo(new Address("Berlin merkez", "Berlin", "10115", "Germany"))
                .cancelled().build());
        book.add(OrderBuilder.anOrder()
                .withId(10251).forCustomer("TOMSP")
                .withLine(11, "20.00", 51)
                .shippingTo(new Address("Berlin merkez", "Berlin", "10115", "Germany"))
                .build());

        Specification<Order> spec = OrderSpecifications.notCancelled()
                .and(OrderSpecifications.shippedTo("Germany"))
                .and(OrderSpecifications.worthAtLeast(Money.tl("1000")));

        System.out.println("Order1 satisfies specification: " + spec.isSatisfiedBy(book.getById(OrderId.of(10248))));
        System.out.println("Order2 satisfies specification: " + spec.isSatisfiedBy(book.getById(OrderId.of(10249))));
        System.out.println("Order3 satisfies specification: " + spec.isSatisfiedBy(book.getById(OrderId.of(10250))));
        System.out.println("Order4 satisfies specification: " + spec.isSatisfiedBy(book.getById(OrderId.of(10251))));

        System.out.println("Total revenue: " + book.totalRevenue());
        System.out.println("Revenue by customers: " + book.revenueByCustomer().entrySet());

    }
}
