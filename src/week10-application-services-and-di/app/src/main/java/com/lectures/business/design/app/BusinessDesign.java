package com.lectures.business.design.app;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.lectures.business.design.app.anemic.AnemicOrder;
import com.lectures.business.design.app.anemic.AnemicOrderLine;
import com.lectures.business.design.app.anemic.OrderCalculator;
import com.lectures.business.design.sales.api.CustomerRevenue;
import com.lectures.business.design.sales.api.OrderSummary;
import com.lectures.business.design.sales.application.OrderMapper;
import com.lectures.business.design.sales.application.SalesReport;
import com.lectures.business.design.sales.domain.Customer;
import com.lectures.business.design.sales.domain.CustomerId;
import com.lectures.business.design.sales.domain.CustomerTier;
import com.lectures.business.design.sales.domain.Order;
import com.lectures.business.design.sales.domain.OrderFactory;
import com.lectures.business.design.sales.domain.OrderId;
import com.lectures.business.design.sales.domain.OrderSpecifications;
import com.lectures.business.design.sales.domain.SequentialOrderIdGenerator;
import com.lectures.business.design.sales.domain.VolumeDiscountPolicy;
import com.lectures.business.design.sales.persistence.InMemoryOrderRepository;
import com.lectures.business.design.sales.persistence.OrderRepository;
import com.lectures.business.design.shared.kernel.Address;
import com.lectures.business.design.shared.kernel.Money;
import com.lectures.business.design.shared.kernel.Specification;

public final class BusinessDesign {

    private static final Clock CLOCK
            = Clock.fixed(Instant.parse("1996-07-04T09:00:00Z"), ZoneOffset.UTC);
    private static final BigDecimal NO_DISCOUNT = BigDecimal.ZERO;

    private static final int[] PRODUCTS = {11, 42, 72, 28, 39};
    private static final String[] PRICES = {"14.00", "9.80", "34.80", "45.60", "18.00"};
    private static final int[] QUANTITIES = {12, 10, 5, 9, 21};
    private static final String[] DISCOUNTS = {"0.05", "0.15", "0.10", "0.25", "0.05"};

    private BusinessDesign() {
    }

    public static void main(String[] args) {
        compareAnemicAndRich();
        System.out.println();
        runSalesScenario();
    }

    // CASE #1 anemic vs rich ---
    private static void compareAnemicAndRich() {
        System.out.println("=== Anemic vs rich ===");

        Order rich = new Order(OrderId.of(10248), CustomerId.of("VINET"), LocalDate.of(1996, 7, 4));
        AnemicOrder anemic = new AnemicOrder();
        anemic.setOrderId(10248);
        anemic.setCustomerId("VINET");
        anemic.setOrderDate(LocalDate.of(1996, 7, 4));

        for (int i = 0; i < PRODUCTS.length; i++) {
            rich.addLine(PRODUCTS[i], Money.tl(PRICES[i]), QUANTITIES[i], new BigDecimal(DISCOUNTS[i]));

            AnemicOrderLine line = new AnemicOrderLine();
            line.setProductId(PRODUCTS[i]);
            line.setUnitPrice(Double.parseDouble(PRICES[i]));
            line.setQuantity(QUANTITIES[i]);
            line.setDiscount(Double.parseDouble(DISCOUNTS[i]));
            anemic.getLines().add(line);
        }

        System.out.println("anemic total : " + new OrderCalculator().total(anemic));
        System.out.println("rich   total : " + rich.total());

        AnemicOrderLine broken = new AnemicOrderLine();
        broken.setQuantity(-5);
        broken.setDiscount(1.5);
        anemic.getLines().add(broken);
        anemic.setStatus("SHIPPED");
        anemic.setShippedDate(LocalDate.of(1990, 1, 1));
        System.out.println("anemic accepts: status=" + anemic.getStatus()
                + ", shipped=" + anemic.getShippedDate()
                + ", lines=" + anemic.getLines().size());

        try {
            rich.addLine(99, Money.tl("10.00"), -5, NO_DISCOUNT);
        } catch (IllegalArgumentException e) {
            System.out.println("rich rejects : " + e.getMessage());
        }
    }

    // CASE #2 sales module end to end
    private static void runSalesScenario() {
        System.out.println("=== Sales scenario ===");

        Customer vinet = new Customer(CustomerId.of("VINET"), "Vins et alcools Chevalier",
                new Address("59 rue de l'Abbaye", "Reims", "51100", "France"));
        Customer tomsp = new Customer(CustomerId.of("TOMSP"), "Toms Spezialitaten",
                new Address("Luisenstr. 48", "Munster", "44087", "Germany"));

        OrderFactory factory = new OrderFactory(new SequentialOrderIdGenerator(10248), CLOCK);
        OrderRepository repository = new InMemoryOrderRepository();

        Order shipped = factory.draftFor(vinet);
        shipped.addLine(11, Money.tl("14.00"), 10, NO_DISCOUNT);
        shipped.confirm();
        shipped.ship(LocalDate.now(CLOCK).plusDays(12));
        repository.save(shipped);

        Order confirmed = factory.draftFor(vinet);
        confirmed.addLine(11, Money.tl("10.00"), 3, NO_DISCOUNT);
        confirmed.confirm();
        repository.save(confirmed);

        Order cancelled = factory.draftFor(tomsp);
        cancelled.addLine(11, Money.tl("99.00"), 100, NO_DISCOUNT);
        cancelled.cancel();
        repository.save(cancelled);

        Order draft = factory.draftFor(tomsp);
        draft.addLine(11, Money.tl("20.00"), 51, NO_DISCOUNT);
        repository.save(draft);

        Specification<Order> bigGermanOrder = OrderSpecifications.notCancelled()
                .and(OrderSpecifications.shippedTo("Germany"))
                .and(OrderSpecifications.worthAtLeast(Money.tl("1000")));

        System.out.println("Not cancelled, shipped to Germany, worth at least 1000 TL;");
        for (Order order : repository.findAll()) {
            System.out.println("  " + order.orderId() + " " + order.status()
                    + " -> " + bigGermanOrder.isSatisfiedBy(order));
        }

        System.out.println();
        System.out.println("Discount policies for order " + draft.orderId() + " (" + draft.total() + ");");
        System.out.println("  volume : " + draft.totalWith(new VolumeDiscountPolicy()));
        System.out.println("  silver : " + draft.totalWith(CustomerTier.SILVER));

        SalesReport report = new SalesReport(repository, new OrderMapper());

        System.out.println();
        System.out.println("Order counts by status;");
        report.countByStatus().forEach((status, count)
                -> System.out.println("  " + status + ": " + count));

        System.out.println();
        System.out.println("Revenue by customer;");
        for (CustomerRevenue row : report.revenueByCustomer()) {
            System.out.println("  " + row.customerId() + ": " + row.revenue() + " " + row.currency());
        }

        System.out.println();
        System.out.println("Totals;");
        System.out.println("  " + report.totals());

        System.out.println();
        System.out.println("Orders of VINET (OrderSummary);");
        for (OrderSummary summary : report.ordersOf("VINET")) {
            System.out.println("  " + summary);
        }
    }
}
