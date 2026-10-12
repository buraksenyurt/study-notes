# Bölüm 8 - Strategy, Specification, Repository ve Mapper kavramları

Bu bölümde domain modelimiz farklı desenler ve yaklaşımlarla geliştirmeye devam ediyoruz. Birçok sorunun ortak çözümü olan tasarım desenleri burada da devreye girecek.

## Strategy Deseni

Sipariş kalemlerinde bir indirim oranı yer alıyor. Nortwhind veritabanındaki order_details tablosunda bununla ilgili discount isimli bir alan var. Şu anki tasarımımızda Discount alanını OrderLine record'unda tutuyoruz. Burada dikkat edilmesi gereken önemli bir husus var; indirim politikaları zamanla müşteriye göre ya da uygulanan kampanyalara bağlı olara değişebilir. O zaman sipariş kalemlerini içerisine alan bir sipariş nesnesi indirim oranını bilmeli midir? Bu soruyu iki açıdan değerlendirelim.

- İndirim politikası değiştiğinde Order sınıfı da değişir, bir başka deyişle aggregate nesnesi kendisi ile alakalı olmayan bir sebeple değişmeye başlar.
- İkinci olarak aynı siparişin iki farklı indirim politikası ile hesaplanması mümkün olmaz.

Dolayısıyla sorunun cevabı hayırdır. Sipariş nesnesi indirim oranını bilmemeli, bunun yerine indirim politikalarını uygulayan ayrı bir strateji nesnesi kullanılmalıdır. Ayrı bir strateji nesnesini Order sınıfı ile nasıl ilişkilendirebileceğimize bakalım. Öncelikle indirim politikalarına ait davranışları *(behavior)* tanımlayan bir arayüz oluşturalım.

```java
package com.lectures.business.design.domain;

import java.math.BigDecimal;

@FunctionalInterface
public interface DiscountPolicy {
    DiscountPolicy NONE = order -> BigDecimal.ZERO;
    BigDecimal rateFor(Order order);
}
```

DiscountPolicy arayüzü sipariş nesnesine uygulanacak indirim politikasını tanımlar. `rateFor` metodu, verilen sipariş için geçerli indirim oranını döner. `NONE` sabiti ise hiçbir indirim uygulanmadığını temsil eder. Dolayısıyla ne kadar farklı indirim politikası varsa bu arayüzden türetilmiş sınıflarda toplanabilir ve gerektiğinde sipariş nesnesine uygulanabilir. Object user hangi tür politikanın nasıl uygulandığını bilmek zorunda değildir, onun için asıl gereken soyut arayüz *(DiscountPolicy)* üzerinden indirim politikasını kullanmaktır. Buna göre sistem aşağıdaki gibi iki farklı indirim politikası ekleyebiliriz.

VolumeDiscountPolicy örneğini ekleyelim.

```java
package com.lectures.business.design.domain;

import java.math.BigDecimal;

public final class VolumeDiscountPolicy implements DiscountPolicy {

    private static final BigDecimal LARGE = new BigDecimal("0.15");
    private static final BigDecimal MEDIUM = new BigDecimal("0.10");
    private static final BigDecimal SMALL = new BigDecimal("0.05");

    @Override
    public BigDecimal rateFor(Order order) {
        int quantity = order.totalQuantity();
        if (quantity >= 100) {
            return LARGE;
        }

        if (quantity >= 50) {
            return MEDIUM;
        }

        if (quantity >= 20) {
            return SMALL;
        }
        return BigDecimal.ZERO;
    }
}
```

VolumeDiscountPolicy sınıfı sipariş miktarına göre bir indirim uygulamaktadır. Büyük siparişler için daha yüksek indirim oranları, orta ve küçük siparişler için ise daha düşük indirim oranları söz konusudur. Sınıfın DiscountPolicy arayüzünü uyguladığına dikkat edelim. Şimdi de ikinci bir indirim politikası ekleyelim. Bu sefer müşterinin sahip olduğu pakete göre bir indirim uygulanmakta.

```java
package com.lectures.business.design.domain;

import java.math.BigDecimal;

public enum CustomerTier implements DiscountPolicy {
    STANDARD(BigDecimal.ZERO),
    SILVER(new BigDecimal("0.05")),
    GOLD(new BigDecimal("0.10"));

    private final BigDecimal rate;

    CustomerTier(BigDecimal rate) {
        this.rate = rate;
    }

    @Override
    public BigDecimal rateFor(Order order) {
        return rate;
    }
}
```

Bu sefer bir enum türü kullanarak indirim politikasını tanımladık. `CustomerTier` enum'ı müşterinin sahip olduğu pakete göre farklı indirim oranları sunuyor. Her bir enum sabiti, ilgili indirim oranını temsil eden `BigDecimal` değerine sahiptir ve `rateFor` metodu her zaman bu değeri döner.

Buna göre Order sınıfına aşağıdaki gibi bir metot eklememiz yeterli olacaktır.

```java
public Money totalWith(DiscountPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return total().discountedBy(policy.rateFor(this));
}
```

Eklediğimiz bu basit metot oldukça önemli. Parametre olarak bir arayüz *(DiscountPolicy)* alıyor ve siparişin toplam tutarını bu indirim politikasına göre hesaplıyor. Hangi politikanın uygulandığı ile veya nasıl uygulandığı ile ilgilenmiyor. Dolayısıyla sisteme yeni indirim politikaları eklenmesi Order sınıfında bir değişiklik gerektirmeyecektir ve hatta bu indirim politikasını ele alan kısmın farklı modüllerde yer alacağı düşünülürse domain modelimize dokunmadan yeni politikalar kullanılabilir hale gelecektir. Özetle bir davranışın stratejisini soyutlamış oluyoruz.

Bunun dışında rateFor metodunun this keyword'ü ile çağırıldığına dikkat edelim. Yani ilgili metot asıl strateji uygulayıcısına *(concrete class)* çalışma zamanındaki Order nesne örneğini gönderir. Bu bilinçli bir tercih zira ilgili politikanın kaç adet kalem olduğu, hangi ürünlerin yer aldığı, ne zaman sipariş edildiği gibi bilgilere de ulaşmasını isteriz ki farklı türden indirim politikaları uygulayabilsin.

Daha önce değindiğimiz üzere arayüz sözleşmemiz `@FunctionalInterface` anotasyonu ile işaretlenmiştir. Bu, DiscountPolicy arayüzünün yalnızca tek bir soyut metot içerdiğini ve dolayısıyla lambda ifadeleri ile de kullanılabileceğini garanti eder.  Aşağıdaki örnekte bunun da bir kullanımı gösterilmektedir.

```java
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
```

Peki strateji tasarım desenini ne zaman kullanmalıyız?

- Aynı iş için birde fazla kural varsa ve hangisinin kullanılacağına çağıran taraf karar veriyorsa,
- İndirim politikası, kampanya uygulaması, konfigurasyon yönetimi gibi akışları düşündüğümüzde kurallar nesnenin kendi işi değilse,
- Söz konusu kuralların bağımsız olarak test edilmesi gerekiyorsa,

strateji desenini kullanabiliriz. Lakin, bunların hiçbiri söz konusu değilse sisteme gereksiz bir arayüz katmanının eklenmesine de sebep olabiliriz. Belki de domain'in sunduğu tek şey siparişlerin toplamıdır ve bu durumda basit bir metot ile indirim hesaplamalarını yapmak yeterli olabilir.

## Kuralı veri haline getirmek için Specification Deseni

// DEVAM EDECEK

## Bu bölümün kodu

[`src/week08-step1-strategy`](src/week08-step1-strategy)

```powershell
cd src
mvn -pl week08-strategy -am test
```
