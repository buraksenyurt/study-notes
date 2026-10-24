<a id="top"></a>

# Bölüm 08: Strategy, Specification, Repository ve Mapper kavramları

<!-- nav:top:start -->
[← Bölüm 07](java-chapter-07.md#top) · [İçindekiler](README.md#top) · [Bölüm 09 →](java-chapter-09.md#top)
<!-- nav:top:end -->

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

OrderBook isimli sınıfımızda ciro hesaplaması için kullanılan iki metodumuz yer alıyor, revenueByCustomer ve totalRevenue.

```java
public Map<CustomerId, Money> revenueByCustomer() {
    return orders.stream()
            .filter(order -> order.status() != OrderStatus.CANCELLED)
            .collect(Collectors.groupingBy(
                    Order::customerId,
                    TreeMap::new,
                    Collectors.reducing(Money.tl("0"), Order::total, Money::plus)));
}

public Money totalRevenue() {
    return orders.stream()
            .filter(order -> order.status() != OrderStatus.CANCELLED)
            .map(Order::total)
            .reduce(Money.tl("0"), Money::plus);
}
```

Her iki metodun da ortak noktası, iptal edilmiş siparişleri filtreleme yaparken hesaba katmasıdır. Aslında bu bir iş kuralıdır ve kod içerisinde tekrar etmekte. Diğer yandan söz konusu iş kuralının belli bir adı yok. Yani `iptal edilen siparişler ciro hesaplamasına dahil edilmez` gibi bir iş kuralını kod içerisinde tekrar tekrar yazıyoruz ama yazdığımızın pek farkında değiliz. Buna göre aynı kuralı üçüncü bir metot içerisinde kullandığımızda da tekrar yazmak zorunda kalacağız. Bu tip bir durumda iş kuralını bir nesne haline getirip, kod içerisinde bu nesneyi kullanmak daha doğru bir yaklaşım olacaktır. Davranışsal tasarım desenlerinden biri olan Specification Deseni tam olarak bu ihtiyaca cevap verir. Öncelikle şartname için genel bir sözleşme hazırlamamız gerekiyor. Bunun kod tarafındaki karşılığı generic bir interface olacaktır.

```java
package com.lectures.business.design.domain;

import java.util.Objects;

@FunctionalInterface
public interface Specification<T> {

    boolean isSatisfiedBy(T candidate);

    default Specification<T> and(Specification<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        return candidate -> isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }

    default Specification<T> or(Specification<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        return candidate -> isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }

    default Specification<T> not() {
        return candidate -> !isSatisfiedBy(candidate);
    }
}
```

Örneğimizde generic bir interface türü söz konusu. `<T>` alias'ı ile arayüz içerisinde T görünen yerler implementasyonu yapan sınıfın belirlediği türe bürünür. Bu sayede aynı şartname farklı türlerdeki nesneler için ortak davranışları da sağlamış olur. Arayüzdeki metotların tamamı default olarak işaretlenmişlerdir. Şartname tipik olarak ve *(and)*, veya *(or)* ve değil *(not)* kombinasyonlarını sağlamaktadır. Her metot yine bir `Specification<T>` döner ve böylece zincirleme kural akışlarını oluşturabiliriz. Sanıyorum en güzeli bu şartnameyi baz alan bir sınıf tasarlamak olacak. Siparişler için söz konusu olabilecek iş kurallarını ele alalım.

```java
package com.lectures.business.design.domain;

import java.time.LocalDate;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> inStatus(OrderStatus status) {
        return order -> order.status() == status;
    }

    public static Specification<Order> cancelled() {
        return inStatus(OrderStatus.CANCELLED);
    }

    public static Specification<Order> notCancelled() {
        return cancelled().not();
    }

    public static Specification<Order> placedBy(CustomerId customerId) {
        return order -> order.customerId().equals(customerId);
    }

    public static Specification<Order> worthAtLeast(Money threshold) {
        return order -> order.total().compareTo(threshold) >= 0;
    }

    public static Specification<Order> orderedOnOrAfter(LocalDate date) {
        return order -> !order.orderDate().isBefore(date);
    }

    public static Specification<Order> shippedTo(String country) {
        return order -> order.shippingAddress()
                .filter(address -> address.isIn(country))
                .isPresent();
    }
}
```

Eğer davranışsal tasarım kalıplarından *(behavioral design patterns)* Specification kalıbının anlatımına bakarsak genellikle arayüz tabanlı türetmeler ile karşılaşabiliriz. Buradaki yaklaşımımız biraz daha farklı. OrderSpecification sınıfı tamamen statik metotlar sunuyur ve aslında her metot kendi çalışma mekaniğine sahip olmakla birlikte  geriye bir `Specification<Order>` dönüyor. Sınıfın kendisi türetme ile şartname'deki `and`, `or` ve `not` kombinasyonlarını sağlamıyor; bunun yerine her bir kuralı ayrı ayrı tanımlıyor ve gerektiğinde zincirleme olarak  `and`, `or` ve `not` kombinasyonlarına dahil edilebiliyor. Kod içerisinde şöyle bir iş kuralını kullanmak istediğimizi düşünelim; Almanya'ya giden, iptal edilmemiş ve toplam değeri en az 1000 TL olan siparişler. Bunu OrderSpecifications sınıfındaki metotları kullanarak şöyle ifade edebiliriz:

```java
package com.lectures.business.design;

import com.lectures.business.design.domain.Address;
import com.lectures.business.design.domain.Money;
import com.lectures.business.design.domain.Order;
import com.lectures.business.design.domain.OrderSpecifications;
import com.lectures.business.design.domain.Specification;

public class BusinessDesign {

    public static void main(String[] args) {

        var order1 = OrderBuilder.anOrder().withLine(11, "120.00", 30).build();
        order1.shipTo(new Address("Berlin merkez", "Berlin", "10115", "Germany"));
        var order2 = OrderBuilder.anOrder().withLine(12, "50.00", 10).build();
        order2.shipTo(new Address("Munich merkez", "Munich", "80331", "Germany"));

        Specification<Order> spec = OrderSpecifications.notCancelled()
                .and(OrderSpecifications.shippedTo("Germany"))
                .and(OrderSpecifications.worthAtLeast(Money.tl("1000")));

        System.out.println("Order1 satisfies specification: " + spec.isSatisfiedBy(order1));
        System.out.println("Order2 satisfies specification: " + spec.isSatisfiedBy(order2));

    }
}
```

Çok doğal olarak Order sınıfındaki revenueByCustomer ve totalRevenue gibi metotlar da benzer şekilde kullanılabilir ve gerektiğinde Specification kombinasyonlarına dahil edilebilir.

```java
private final Specification<Order> notCancelledOrders = OrderSpecifications.notCancelled();

public Map<CustomerId, Money> revenueByCustomer() {
    return orders.stream()
            .filter(notCancelledOrders::isSatisfiedBy)
            .collect(Collectors.groupingBy(
                    Order::customerId,
                    TreeMap::new,
                    Collectors.reducing(Money.tl("0"), Order::total, Money::plus)));
}

public Money totalRevenue() {
    return orders.stream()
            .filter(notCancelledOrders::isSatisfiedBy)
            .map(Order::total)
            .reduce(Money.tl("0"), Money::plus);
}
```

Şu anda OrderBook içerisindeki lambda ifadelerini terk edip specification tabanlı bir yaklaşıma geçtik. Tek kullanımlık ve yere bir filtre söz konusu ise lambda operatörünü kullanmaya devam etmek daha mantıklıdır. Bununla birlikte kural bir iş terimine karşılık geliyorsa *(ciro hesaplamasına dahil edilebilen siparişler gibi)*, birden fazla yerde kullanılıyorlsa ya da bileşik halde ifadelere ihtiyaç duyuluyorsa specification tabanlı yaklaşım daha uygun olacaktır. Zira görüldüğü üzere şartname yazmak da kodu uzatabilir.

## Repository Nesnesinin İnşası

Şu anda siparişleri bellek üzerindeki bir koleksiyon aracılığıyla yönetiyoru *(in-memory repository)*. Bu yaklaşım küçük ölçekli uygulamalar ve testler için uygun olmakla birlikte, gerçek dünya senaryolarında kalıcı bir veri deposuna ihtiyaç duyacağımız da aşikardır. Verinin kalıcı saklanması disk üzerinde kayıt altına alınması anlamına gelirken kurumsal çaptaki çözümler bunu veritabanları üzerinde gerçekleştirir. Bu bölümde OrderBook tasarımımızı ikiye bölüp bir arayüz implemantasyonu ile kullanmaya başlayacağız. Bunu yaparken paket yapısında da değişikliğe gideceğiz ve yavaş yavaş asıl ulaşmak istediğimiz proje iskeletini de ortaya çıkarmaya başlayacağız.

İlk olarak persistence isimli yeni bir paket açalım ve içerisine OrderRepository isimli aşağıda kod içeriği yer alan sözleşmeyi ekleyelim.

```java
package com.lectures.business.design.persistence;

import com.lectures.business.design.domain.Order;
import com.lectures.business.design.domain.OrderId;
import com.lectures.business.design.domain.OrderNotFoundException;
import com.lectures.business.design.domain.Specification;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(OrderId orderId);

    List<Order> findAll();

    List<Order> findAll(Specification<Order> specification);

    long count();

    default Order getById(OrderId orderId) {
        return findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
```

Tipik olarak bir repository veri manipülasyonu için gerekli davranışları temelde sunar. Veriyi kaydetm *(save)*, belirli bir kimlik ile arama *(findById)*, tüm veriyi listeleme *(findAll)* ya da belirli bir şartnameye göre filtreleme *(findAll(Specification))* gibi operasyonları içerir. Örneğimizde bunlar ek olarak her repository için varsayılan bir davranışı da *(getById)* sunmaktayız. Şimdi bu sözleşmenin örnek bir implementasyonuna geçelim. In-memory çalışan bir repository, veriyi bellekte tutarak bu operasyonları ele alabilir. Bunu da sözleşme ile aynı paket içerisinde konumlandırabiliriz.

```java
package com.lectures.business.design.persistence;

import com.lectures.business.design.domain.Order;
import com.lectures.business.design.domain.OrderId;
import com.lectures.business.design.domain.Specification;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> orders = new LinkedHashMap<>();

    @Override
    public void save(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        orders.put(order.orderId(), order);
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public List<Order> findAll() {
        return List.copyOf(orders.values());
    }

    @Override
    public List<Order> findAll(Specification<Order> specification) {
        Objects.requireNonNull(specification, "specification must not be null");
        return orders.values().stream()
                .filter(specification::isSatisfiedBy)
                .sorted(Comparator.comparing(Order::orderId))
                .toList();
    }

    @Override
    public long count() {
        return orders.size();
    }
}
```

Artık elimizde in-memory çalışan bir repository örneği mevcut. Bu repository, veriyi bellekte tutarak temel CRUD operasyonlarını ve şartnameye dayalı filtrelemeyi gerçekleştirme. Buna göre aslında önceden kullandığımız OrderBook nesnesine de ihtiyacımız kalmayacak.

Burada kafalarda bir soru işaret oluşabilir. Tek bir implementasyon yapacaksak neden bir sözleşmeye ihtiyaç duyuyoruz? Aslında bu, yazılım tasarımında esneklik ve genişletilebilirlik açısından önemli bir yaklaşımdır. İleride farklı veri kaynakları için yeni repository implementasyonları eklemek istediğimizde, mevcut kodu değiştirmeden sadece yeni bir sınıf ekleyerek bu ihtiyacı karşılayabiliriz. Bu hem test koşumlarında, hemde uygulama servislerindeki bağımlılıkların yönetiminde büyük kolaylık sağlar. İlerleyen bölümlerde bunun avantajını daha net görebileceğiz.

## Dış dünya CustomerId'yi bilmediğinde *(Mapper Kullanımı)*

Uygulamada kullandığımız **CustomerId** veya **OrderId** gibi veri yapılarını göz önüne alalım. Bu nesneler domain içerisinde anlamlı olan güçlendirilmiş kimlik türlerindendir. Şimdi birde dış dünyada bunların karşılıkları olduğunu düşünelim. Örneğin bir satış raporunun dış dünyaya servis olarak sunulduğu noktada api modülünde aşağıdaki gibi bir **record** türünü kullanabiliriz.

```java
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
```

Bu **record**, bir satış işlemi ile alakalı özet sipariş bilgisini dış dünyaya sunacak veriyi temsil eder. Bu sayede domain içerisindeki güçlü tipler ve kimlikler, dış dünyaya uygun basit veri tiplerine dönüştürülmüş olur. Dış dünya bileşenleri bu **api** sınırına gelen istemciler olarak düşünülebilir. Dış dünyaya ilkel türlerden oluşan veri yapıları sunmak, domain modelinin karmaşıklığını dış dünyadan izole etmenin de bir yoludur. Elbette satış raporunun sunacağı tek şey sipariş özetleri değil. Müşteri bazlı ciro satırları ve raporun genel toplamı da dış dünyaya açılabilir. Bunlar için de **api** modülüne iki **record** daha tanımlayalım.

```java
package com.lectures.business.design.api;

import java.math.BigDecimal;

public record CustomerRevenue(String customerId, BigDecimal revenue, String currency) {

}
```

```java
package com.lectures.business.design.api;

import java.math.BigDecimal;

public record SalesTotals(long orderCount, BigDecimal revenue, String currency) {

}
```

Dikkat edileceği üzere her üç **record**' da yalnızca String, BigDecimal, LocalDate, int ve long gibi ilkel *(primitive)* ya da standart türlerden oluşuyor. İçlerinde ne **CustomerId** ne **Money** ne de **OrderStatus** gibi domain türleri bulunuyor. Bir sınır tipinin tanımı tam olarak da bu. Özellikle **Money** türünün ikiye ayrıldığına dikkat edelim. Tutar **BigDecimal** olarak, para birimi ise **String** bir kur kodu olarak ele alınıyor.

Dolayısıyla karşımızda şöyle bir ihtiyaç bulunmakta. **OrderId**, **CustomerId** ve **Money** türlerinin sarmaladığı değerlerin dış dünyaya uygun ilkel türlere dönüştürülmesini nasıl sağlarız? Bu tip dönüşümler genellikle mapper olarak da ifade edilen yardımcıların işi oluyor. Aşağıdaki OrderMapper sınıfının görevi tam olarak bu.

```java
package com.lectures.business.design.application;

import com.lectures.business.design.api.CustomerRevenue;
import com.lectures.business.design.api.OrderSummary;
import com.lectures.business.design.domain.Address;
import com.lectures.business.design.domain.CustomerId;
import com.lectures.business.design.domain.Money;
import com.lectures.business.design.domain.Order;
import java.util.List;
import java.util.Objects;

public final class OrderMapper {

    public OrderSummary toSummary(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        return new OrderSummary(
                order.orderId().value(),
                order.customerId().value(),
                order.status().name(),
                order.orderDate(),
                order.total().amount(),
                order.total().currency().getCurrencyCode(),
                order.lines().size(),
                order.shippingAddress().map(Address::city).orElse(null));
    }

    public List<OrderSummary> toSummaries(List<Order> orders) {
        Objects.requireNonNull(orders, "orders must not be null");
        return orders.stream().map(this::toSummary).toList();
    }

    public CustomerRevenue toRevenue(CustomerId customerId, Money revenue) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(revenue, "revenue must not be null");

        return new CustomerRevenue(
                customerId.value(),
                revenue.amount(),
                revenue.currency().getCurrencyCode());
    }
}
```

Bu sınıfın **application** isimli yeni bir modülde olduğuna dikkat edelim. Görevi, domaindeki nesneleri dış satış raporu için kullanılacak veri yapılarına dönüştürmek. `toSummary()` metodunun aldığı ve döndürdüğü parametre bunun bir kanıtı. Tek yönlü çalışan bir nesnedir yani domain nesnelerini alır ve dış dünyaya uygun veri yapılarına dönüştürür ancak ters yönde bir dönüşüm sağlamaz. Örneğimizde hem tek bir sipariş hem de bir sipariş listesi için **OrderSummary** örneklerinin oluşturulmasını sağlıyoruz. `toRevenue()` metodu ise para tutarının dönüştürülmesini tek bir yerde topluyor ve böylece ciro bilgisi üreten her yerde aynı dönüşüm yeniden yazılmak zorunda kalmıyor.

Artık elimizde sipariş özetini ve ciro satırını ifade eden veri yapıları ile domain içeriğini bunlara çeviren bir mapper da olduğuna göre raporun kendisini ifade edecek sınıf tasarımını da yapabiliriz. Yine uygulama sınırında *(application boundary)* yer alan bir tip olması gerektiğinden onu da application modülü içerisine yerleştireceğiz.

```java
package com.lectures.business.design.application;

import com.lectures.business.design.api.CustomerRevenue;
import com.lectures.business.design.api.OrderSummary;
import com.lectures.business.design.api.SalesTotals;
import com.lectures.business.design.domain.CustomerId;
import com.lectures.business.design.domain.Money;
import com.lectures.business.design.domain.Order;
import com.lectures.business.design.domain.OrderSpecifications;
import com.lectures.business.design.persistence.OrderRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class SalesReport {

    private final OrderRepository repository;
    private final OrderMapper mapper;

    public SalesReport(OrderRepository repository, OrderMapper mapper) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    public Map<String, Long> countByStatus() {
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(
                        order -> order.status().name(),
                        TreeMap::new,
                        Collectors.counting()));
    }

    public List<CustomerRevenue> revenueByCustomer() {
        return revenueTotals().entrySet().stream()
                .map(entry -> mapper.toRevenue(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<CustomerRevenue> topCustomers(int limit) {
        return revenueTotals().entrySet().stream()
                .sorted(Map.Entry.<CustomerId, Money>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> mapper.toRevenue(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<OrderSummary> ordersOf(String customerId) {
        return mapper.toSummaries(
                repository.findAll(OrderSpecifications.placedBy(CustomerId.of(customerId))));
    }

    public SalesTotals totals() {
        List<Order> earning = earningOrders();
        Money revenue = earning.stream()
                .map(Order::total)
                .reduce(Money.tl("0"), Money::plus);

        return new SalesTotals(
                earning.size(),
                revenue.amount(),
                revenue.currency().getCurrencyCode());
    }

    private Map<CustomerId, Money> revenueTotals() {
        return earningOrders().stream()
                .collect(Collectors.groupingBy(
                        Order::customerId,
                        TreeMap::new,
                        Collectors.reducing(Money.tl("0"), Order::total, Money::plus)));
    }

    private List<Order> earningOrders() {
        return repository.findAll(OrderSpecifications.notCancelled());
    }
}
```

Satış raporunu tanımlayan sınıfımız bazı fonksiyonellikler sağlarken **OrderRepository** arayüzünü ve **OrderMapper** sınıfını kullanıyor. Bu nesnelerin constructor üzerinden içeriye alındığını fark etmişsinizdir. Özellikle **OrderRepository** açısından bakarsak tipik bir bağımlılık enjeksiyonu örneği görmektesiniz *(Dependency Injection)*. Bu sayede sınıfımız repository'nin somut bir implementasyonuna bağımlı olmadan çalışabilir ve test edilebilir. Yani istersek InMemory çalışan Order repository'sini kullanabiliriz istersek gerçek bir veritabanına bağlanan repository'yi kullanabiliriz. SalesReport sınıfına müdahale etmeden de kullanacağı repository'yi dışarıdan alabiliriz. Diğer metotlar daha çok raporun hazırlanması noktasında kullanılan public fonksiyonlardır. Örneğin statü bazlı sipariş sayıları, müşteri bazlı cirolar, en değerli müşteriler gibi gerçekten bir satış raporunda olabilecek bilgileri elde etmemizi sağlayan metotlar yer alır.

Burada asıl dikkat edilmesi gereken nokta şu; *SalesReport sınıfının bütün public metotları api modülündeki tipleri döndürür.* `countByStatus` metodu `OrderStatus` enum'ı yerine onun adını yani bir `String` verir. `revenueByCustomer` ve `topCustomers` metotları `CustomerRevenue` listesi, `totals` metodu `SalesTotals`, `ordersOf` metodu da `OrderSummary` listesi döndürmektedir. Domain tarafındaki ara sonuçlar, yani `revenueTotals` ve `earningOrders`, private olarak kalırlar ve sınıfın dışına çıkmazlar.

Bu kararın neden önemli olduğunu bir an için tersini düşünerek görebiliriz. SalesReport eğer `Map<CustomerId, Money>` döndürseydi kod pekala derlenirdi, zira application paketinin domain paketine erişmesine izin verilmekte. Üstelik testler de yeşil bayrak kaldırırdı. Ancak bu durumda raporu HTTP üzerinden yayınlayacak bir resource sınıfı `CustomerId` ve `Money` türlerini ellemek zorunda kalırdı. Buradaki kuralı şöyle ifade etmek mümkün; *sınır, dışarıya veri veren metodun kendisinde değil o metodun dönüş tipinde biter.*

`ordersOf` metodunun imzası da üzerinde durmaya değer. Parametre olarak `String` alıyor ve içeride `CustomerId.of` ile domain tipine çeviriyor. Yani içe doğru dönüşüm de mapper'ın değil, tipin kendi fabrika metodunun işi olarak ele alınmaktadır. Doğrulama orada yaşadığı için geçersiz bir müşteri kodu rapora hiç ulaşmadan reddedilmektedir.

> api modülünde neden bir arayüz *(façade)* tanımlamadığımızı merak edebilirsiniz. Böyle bir arayüzün varlık sebebi, HTTP katmanının yani rest paketinin application paketini hiç görmemesidir. Henüz rest paketine gelmediğimiz için arayüz şu an yalnızca fazladan bir dosya olacaktı. Şu anda SalesReport bir arayüz implemente etmiyor ancak sözleşmesi dönüş tiplerinde yazılı. api tiplerini döndürdüğü sürece onu ileride bir arayüzün arkasına almak sadece basit bir adım olacaktır. Önce ihtiyaçları görmeliyiz.

Bu bölümle ilgili şu notları söyleyebiliriz:

- CustomerId bilgisi uygulama sınırında String bir türe dönüşür. İlkel *(primitive)* bir tip olarak ele alınır ve bu sayede JSON, HTTP, veri tabanı gibi farklı ortamlara kolayca taşınabilir. Aynı şey Money için de geçerlidir; tutar ve para birimi iki ayrı ilkel alana iner.
- OrderStatus enum'ı dışarıya enum olarak değil adıyla *(String)* açılır. Aksi halde sınır sözleşmemiz enum sabitlerinin adlarına bağlanmış olur ve ileride bir sabiti yeniden adlandırmak istemcileri bozar.
- OrderMapper sınıfımızda `order.shippingAddress().map(Address::city).orElse(null))` şeklinde bir ifade yer alıyor. Domain içerisinde teslimat adresi opsiyonel olarak tutuluyor ve bu bizim koyduğumuz bir kural. Ancak bilgiyi dış dünyaya açarken optional'ın null olarak dönüşebilmesini sağlıyoruz. Zira dış dünya genellikle null değerleri anlayabilir ancak Optional tipini doğrudan anlayamaz *(JSON, HTTP gibi istemcileri düşünelim)*
- Tasarlanan mapper sınıfı tek yönlüdür. Yani içerisinde `toSummary()` şeklinde bir metot varken tam tersi `toOrder()` yoktur. Bu son derece doğaldır, görevi gereğidir. Dışarıdan gelen primitive verilerden bir aggregate oluşturmak mapper'ların değil OrderFactory gibi fabrika sınıflarının sorumluluğundadır. Dikkat edilmesi gereken nokta; DTO *(Data Transfer Object)* nesnelerinden yararlanan bir mapper yazılıp aggregate kurallarının etrafından dolaşılabileceğidir. Buna dikkat etmemiz gerekiyor.
- `SalesReportTest` içerisindeki `noDomainTypeLeaksFromTheReport` testi, SalesReport sınıfının public metotlarını dolaşıp dönüş ve parametre tiplerinde domain paketinin geçmediğini doğrular. Bu denetimi ilerleyen bölümlerde bir ArchUnit testine devredebiliriz.

## Paketlerin Düzeni

Geliştirdiğimiz proje modüler olarak iyileşmeye devam ediyor. Şu ana kadar ele aldığımız nihai sınıfları olmaları gereken paketlere yerleştiriyoruz. Aşağıdaki tabloda bu paket yapısının bir özetini görebilirsiniz.

| **Class/record** | **Package** |
| --- | --- |
| Order, Customer, Money, Address, OrderLine | domain |
| OrderStatus, Specification, OrderSpecifications, DiscountPolicy | domain |
| OrderFactory, OrderIdGenerator | domain |
| OrderRepository, InMemoryOrderRepository | persistence |
| OrderSummary, CustomerRevenue, SalesTotals | api |
| OrderMapper, SalesReport | application |

Buna göre ilerleyen zamanlarda modüller arası bağımlılıkların yönünü ifade edeceğimiz ADR *(Architecture Decision Record)* dosyasına atıfta bulunabiliriz. Hatta bu kararları bir ArchUnit testi ile doğrulamayı tercih edeceğiz. Henüz resource'ları ekleyeceğimiz HTTP katmanına gelmediğimiz için bu kısmı şimdilik göz ardı edelim ancak var olan paketler arası erişim izinlerini aşağıdaki tablo ile özetleyebiliriz.

| Erişen *(aşağı doğru okuyun)* / Erişilen *(sağa doğru okuyun)* | api | rest | application | domain | persistence |
| --- | --- | --- | --- | --- | --- |
| api | - | X | X | X | X |
| rest | Evet | - | X | X | X |
| application | Evet | X | - | Evet | Evet |
| domain | X | X | X | - | X |
| persistence | X | X | X | Evet | - |

Bunu şöyle okumak gerekir; örneğin application paketi, api, domain ve persistence paketlerine erişebilir ancak rest paketine erişemez ya da domain paketi hiçbir pakete erişmez. Ya da şu şekilde ifade edebiliriz; api paketinde yer alan `OrderSummary` sınıfı diğer paketlerden erişilebilir ancak api paketinden başka bir pakete erişemez. *(OrderMapper ve SalesReport tarafından kullanıldığını hatırlayalım; tersi, yani api paketinden application paketine doğru bir erişim mümkün değildir)*

> Paketlerle ilgili bilgileri eğitim amaçlı olarak `package-info.java` dosyalarına ekledik. Lütfen kontrol ediniz. Ayrıca bu dersteki örneklerle ilgili test sınıfları da ilgili paketlerde yer almaktadır. Onları da incelemeyi unutmayalım.

---

## Sorular

1. BusinessDesign örneğindeki `order.totalWith(p -> new BigDecimal(0.8))` satırını inceleyelim. `new BigDecimal(0.8)` hangi değeri üretir ve neden `new BigDecimal("0.8")` tercih edilmelidir? Ayrıca bu lambda %80 indirim mi yoksa %20 indirim mi uygular? Strateji nesnelerinin ürettiği oranlar için bir doğrulama nerede yapılmalıdır?
2. OrderLine üzerinde zaten bir discount alanı varken totalWith metodu sipariş toplamına ikinci bir indirim uyguluyor. Bir müşteri hem GOLD seviyesinde hem de hacim indirimine hak kazanıyorsa hangi oran uygulanmalıdır? En yüksek olan mı, toplamları mı, yoksa ardışık olarak mı? Birden fazla DiscountPolicy nesnesini birleştiren bir *Composite* strateji nasıl tasarlanır?
3. CustomerTier bir enum olarak DiscountPolicy arayüzünü uyguluyor. Bu yaklaşımın sınıf tabanlı bir stratejiye göre artıları ve eksileri nelerdir? İndirim oranlarının veritabanından veya bir konfigürasyon dosyasından okunması gerekseydi enum hâlâ doğru bir seçim olur muydu?
4. OrderSpecifications içindeki kurallar lambda ifadeleri olarak yazıldı ve InMemoryOrderRepository bunları bellekteki koleksiyon üzerinde çalıştırıyor. Siparişler bir veritabanına taşındığında `findAll(Specification<Order>)` metodu milyonlarca kaydı belleğe çekmeden nasıl çalışabilir? *(Spring Data JPA'daki Specification ve JPA Criteria API yaklaşımlarını araştıralım)*
5. `worthAtLeast(Money.tl("1000"))` şartnamesi farklı bir döviz cinsinden bir siparişle karşılaştığında ne olur? Bir şartnamenin false dönmek yerine exception fırlatması `and/or` zincirlerini nasıl etkiler?
6. InMemoryOrderRepository sınıfında kullanılan `findAll()` metodu, siparişleri ekleme sırasına göre, `findAll(Specification)` ise orderId sırasına göre döndürmekte. Bu tutarsızlık ne gibi sorunlara yol açabilir? Repository sözleşmesi sıralamalar ile ilgili bir garanti vermeli midir?
7. Repository'den alınan bir Order nesnesi üzerinde `confirm()` çağrıldığında, save metodu tekrar çağrılmadan da değişiklik repository'ye yansıyor mu? **Vaughn Vernon**'un *collection-oriented* ve *persistence-oriented* repository ayrımını araştıralım. Bu iki yaklaşımdan hangisi gerçek bir veritabanı implementasyonuna geçildiğinde daha az sürpriz yaratır bulmaya çalışalım.
8. Tek bir implementasyonu olan OrderRepository arayüzü için "YAGNI *(You Aren't Gonna Need It)*" eleştirisi yapılabilir mi? Arayüzün testlerde ve bağımlılık yönünün tersine çevrilmesinde *(Dependency Inversion)* sağladığı fayda nedir? Bu arayüz persistence paketi yerine domain paketinde dursaydı ne değişirdi?
9. OrderMapper dönüşümleri elle yazıyor. MapStruct veya ModelMapper gibi kütüphaneler bu işi otomatikleştirebilir. Derleme zamanında kod üreten bir mapper ile reflection tabanlı bir mapper arasındaki farklar nelerdir araştıralım. Domain tiplerinin sarmaladığı değerleri *(`CustomerId.value()`, `Money.amount()`)* açmak gibi kurallar otomatik bir mapper'da nasıl ifade edilir ve bu yaklaşım hangi riskleri taşır inceyelim.

## Bu bölümün kodu

- [`src/week08-step1-strategy`](src/week08-step1-strategy)
- [`src/week08-step2-specification`](src/week08-step2-specification)
- [`src/week08-step3-repository`](src/week08-step3-repository)
- [`src/week08-step4-mappers`](src/week08-step4-mappers)

```powershell
cd src
mvn -pl week08-step1-strategy -am test
mvn -pl week08-step2-specification -am test
mvn -pl week08-step3-repository -am test
mvn -pl week08-step4-mappers -am test
```

<!-- nav:bottom:start -->
---

| ← Önceki Bölüm | İçindekiler | Sonraki Bölüm → |
| :--- | :---: | ---: |
| [Bölüm 07: Typed Identity, Static Factory, Factory ve Builder ile Nesne Üretimi](java-chapter-07.md#top) | [Ders Notları](README.md#top) | [Bölüm 09: Maven Modülleri ve ArchUnit Kullanımı](java-chapter-09.md#top) |
<!-- nav:bottom:end -->
