package com.lectures.business.design.domain;

import java.math.BigDecimal;

@FunctionalInterface
public interface DiscountPolicy {
    DiscountPolicy NONE = order -> BigDecimal.ZERO;
    BigDecimal rateFor(Order order);
}

