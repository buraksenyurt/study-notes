package com.lectures.business.design.app;

import com.lectures.business.design.sales.api.CustomerRevenue;
import com.lectures.business.design.sales.application.OrderMapper;
import com.lectures.business.design.sales.application.SalesReport;
import com.lectures.business.design.sales.domain.OrderBuilder;
import com.lectures.business.design.sales.persistence.InMemoryOrderRepository;
import com.lectures.business.design.sales.persistence.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class AppSmokeTest {

    @Test
    @DisplayName("the whole stack answers one reporting question")
    void theStackWorksEndToEnd() {
        OrderRepository repository = new InMemoryOrderRepository();
        repository.save(OrderBuilder.anOrder()
                .withId(10248).forCustomer("VINET").withLine(11, "14.00", 10).shipped().build());
        repository.save(OrderBuilder.anOrder()
                .withId(10250).forCustomer("TOMSP").withLine(11, "99.00", 5).cancelled().build());
        SalesReport report = new SalesReport(repository, new OrderMapper());
        assertThat(report.revenueByCustomer())
                .containsExactly(new CustomerRevenue("VINET", new BigDecimal("140.00"), "TRY"));
        assertThat(report.totals().orderCount()).isEqualTo(1);
        assertThat(report.ordersOf("VINET")).hasSize(1);
    }
}
