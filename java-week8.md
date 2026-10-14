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
package com.lectures.business.design.repository;

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
package com.lectures.business.design.repository;

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

// DEVAM EDECEK

## Bu bölümün kodu

- [`src/week08-step1-strategy`](src/week08-step1-strategy)
- [`src/week08-step2-specification`](src/week08-step2-specification)
- [`src/week08-step3-repository`](src/week08-step3-repository)

```powershell
cd src
mvn -pl week08-step1-strategy -am test
mvn -pl week08-step2-specification -am test
mvn -pl week08-step3-repository -am test
```
