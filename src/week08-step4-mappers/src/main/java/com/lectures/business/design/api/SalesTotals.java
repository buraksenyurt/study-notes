package com.lectures.business.design.api;

import java.math.BigDecimal;

public record SalesTotals(long orderCount, BigDecimal revenue, String currency) {

}
