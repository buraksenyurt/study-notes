package com.lectures.business.design;

import com.lectures.business.design.domain.CustomerTier;
import com.lectures.business.design.domain.DiscountPolicy;
import com.lectures.business.design.domain.VolumeDiscountPolicy;
import java.math.BigDecimal;

public class BusinessDesign {

    public static void main(String[] args) {
        var order = OrderBuilder.anOrder().withLine(11, "10.00", 30).build();

        System.out.println("Order total " + order.total());
        System.out.println("Discount policy None " + order.totalWith(DiscountPolicy.NONE));
        System.out.println("Discount by volume " + order.totalWith(new VolumeDiscountPolicy()));
        System.out.println("Discount for gold customers " + order.totalWith(CustomerTier.GOLD));
        System.out.println("Discount by 0.8" + order.totalWith(p -> new BigDecimal(0.8)));
    }
}
