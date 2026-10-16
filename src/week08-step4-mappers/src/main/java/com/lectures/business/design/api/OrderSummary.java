package com.lectures.business.design.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderSummary(
        int orderId,
        String customerId,
        String status,
        LocalDate orderDate,
        BigDecimal total,
        String currency,
        int lineCount,
        String shippingCity) {

}
