# Bölüm 10 - Uygulama Servisleri ve Manuel Dependency Injection

Bir önceki bölümde kodumuzu Maven modüllerine ayırıp paketler arasındaki bağımlılık yönlerini ArchUnit testleriyle koruma altına aldık. Lakin sales modülünün dış dünyaya sunduğu yetenekler hala sınırlı. SalesReport aslında sadece okuma yapıyor. Sipariş oluşturmak, onaylamak veya kargoya vermek isteyen bir istemcinin Order nesnesini oluşturup ilgili metotları çağırması ve sonucu repository'ye kaydetmesi gerekiyor. BusinessDesign sınıfındaki main metodunda yaptığımız şey tam olarak buydu. Genellikle kurumsal çözümlerde bu süreçler bir HTTP isteği *(REST API çağrısı, WEB formu vb.)*, bir mesaj kuyruğu tüketicisi *(RabbitMQ Consumer)* veya zamanlanmış bir görev *(Task Scheduler)* tarafından tetiklenir. Bu tetikleyicilerin her biri domain nesnelerini doğrudan kullanmaya başlarsa bir önceki bölümde çizdiğimiz sınırların da bir anlamı kalmaz. Dolayısıyla bu bölümde aşağıdaki sorulara cevap aramaya çalışacağız.

- Bir kullanım senaryosu *(use case)* hangi sınıfta yaşamalıdır? *(Domain nesnesinde mi yoksa onun bir üst katmanında mı?)*
- Bir servis ihtiyaç duyduğu bileşenleri nereden almalıdır? *(new çağrısının yeri neresidir?)*
- Zaman olarak bugün bilgisi koda nasıl girmelidir? *(Bölüm 3'te ele aldığımız `ship(LocalDate date)` sorusunu hatırlayalım)*
- Bir Dependency Injection Container sistemi *(CDI, Spring)* tam olarak neyi otomatikleştirir?

## Use Case Nedir?

Bir use case'in SalesReport'tan farkı nedir sorusunuz düşünerek başlayabiliriz. Şu ana kadar application paketine eklediğimiz iki sınıf var. OrderMapper ve SalesReport. SalesReport okuma yönünde çalışan bir sınıf. Repository üzerinden siparişleri alır ve gruplayarak raporlar üretir. Sistemin durumunu değiştiren hiçbir işlem yapmaz. Yazma yönündeki işlemler içinse genellikle aşağıdaki gibi bir akış söz konusu olur.

```text
1. İşlem komutunu çevir      PlaceOrderCommand -> CustomerId, Money, ...
2. Aggregate'ı yükle         customers.getById(), orders.getById()
3. Kararı ona bırak          order.confirm(), order.ship(date),...
4. Kaydet                    orders.save(order)
5. Çıktıyı çevir             mapper.toSummary(order) -> OrderSummary
```

Bu akışa dikkat edersek 3ncü adım dışında hiçbirinin bir iş kuralı içermediğini görürüz. Siparişin hangi durumdan hangi duruma geçebileceği, kargo tarihinin sipariş tarihinden önce olamayacağı ya da adres bilgisi olmayan bir siparişin onaylanamayacağı gibi kurallar zaten **Order** sınıfının içinde yer almakta. Uygulama servisi *(application service)* bu kuralları bilmez *(ki bilmek zorunda da değildir)* sadece akışı koordine eder. Bu ayrım önemlidir çünkü iş kuralları *(business rules)* servis sınıfına sızmaya başladığında *(örneğin `if (order.status() == DRAFT && ...)` gibi kontroller eklendiğinde)* domain modelimiz yeniden anemik hale gelmeye başlar. Aslında kaçmaya çalıştığımız şey budur.

> Martin Fowler bu katmanı **Service Layer** olarak adlandırır ve *"uygulamanın sınırını tanımlar, mevcut işlemleri belirler ve her işlemin yanıtını koordine eder"* şeklinde tarifler. DDD *(Domain Driven Design)* literatüründe ise **Application Service** terimi kullanılır. Kural yine aynıdır; ince bir koordinasyon katmanı.

### Tek Sınıf mı, Senaryo Başına Bir Sınıf mı?

Kullanım senaryolarını *(Use Case)* organize etmenin iki yaygın yolu vardır.

| **Yaklaşım** | **Örnek** | **Pros** | **Cons** |
| --- | --- | --- | --- |
| **Aggregate başına bir servis** | `OrderService.placeOrder / confirm / ship / cancel` | Az sayıda sınıf yer alır. İlgili senaryolar bir arada durur. **Jakarta EE** ve **Spring** dünyasında en sık görülen yapılardandır. | Senaryo sayısı arttıkça sınıf büyür ve her metot tüm bağımlılıkları kullanmasa bile hepsini taşır. |
| **Senaryo başına bir sınıf** | PlaceOrderHandler, ShipOrderHandler gibi | Her sınıfın tek bir sorumluluğu ve kendi bağımlılık listesi olur *(SRP - Single Responsibility Principle)*. CQRS'e *(Command Query Responsibility Segregation)* geçiş kolaylaşır. | Sınıf sayısı hızla artar. Küçük projelerde abartılı bir seremoniyi *(ceremony)* hissettirir :D |

Şimdilik ilk senaryo ile devam edelim. Kalanını tartışmaya açarız.

## Komutlar *(Commands)* ve `api` Paketine İki Yeni Tip

Servisin girdisi bir domain nesnesi **olmamalı**. Aksi halde istemcinin **Order**, **Money** veya **CustomerId** gibi tipleri tanıması gerekir. Ayrıca, ADR-0003'te belirlediğimiz api sözleşmesi de delinmiş olur. Bu nedenle api paketine sadece JDK tiplerinden oluşan iki record ekleyeceğiz.

```java
package com.lectures.business.design.sales.api;

import java.math.BigDecimal;

public record OrderLineRequest(int productId, BigDecimal unitPrice, int quantity, BigDecimal discount) {

    public static OrderLineRequest of(int productId, String unitPrice, int quantity) {
        return new OrderLineRequest(productId, new BigDecimal(unitPrice), quantity, BigDecimal.ZERO);
    }
}
```

ve

```java
package com.lectures.business.design.sales.api;

import java.util.List;
import java.util.Objects;

public record PlaceOrderCommand(String customerId, List<OrderLineRequest> lines) {

    public PlaceOrderCommand {
        Objects.requireNonNull(lines, "lines must not be null");
        lines = List.copyOf(lines);
    }
}
```

PlaceOrderCommand sınıfındaki `List.copyOf` çağrısına dikkat edelim. Record'lar yüzeysel *(shallow)* olarak değişmezdir *(immutable)*. Eğer gelen listeyi olduğu gibi saklasaydık, komutu oluşturan kod listeye sonradan eleman ekleyerek komutun içeriğini değiştirebilirdi *(Bölüm 2'deki record tartışmamızı hatırlayalım)*.

Bu record'larda iş kuralı doğrulaması yapmadığımıza da dikkat edelim. Örneğin quantity değerinin pozitif olup olmadığını kontrol etmiyoruz. Bu kontrol zaten OrderLine record'unun compact constructor'ında yapılıyor. Aynı kuralı iki yere yazmak, bir gün ikisinin birbirinden farklılaşması demektir. Girdinin *biçimsel* doğrulaması *(alan boş mu, sayı mı vs.)* ise HTTP sınırlarında karşılayacağımız bir durum.

## Müşteriyi Bulalım

Pek tabii sipariş verebilmek bir müşteri bilgisi gerekiyor. Müşterinin var olup olmadığını kontrol etmek ve hatta teslimat adresini müşteriden almak lazım. Bunu üstlenen bir sözleşmeyi aşağıdaki gibi bir interface olarak yazabiliriz.

```java
package com.lectures.business.design.sales.persistence;

import com.lectures.business.design.sales.domain.Customer;
import com.lectures.business.design.sales.domain.CustomerId;
import com.lectures.business.design.sales.domain.CustomerNotFoundException;
import java.util.Optional;

public interface CustomerRepository {

    void save(Customer customer);

    Optional<Customer> findById(CustomerId customerId);

    default Customer getById(CustomerId customerId) {
        return findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
```

Tipik bir repository tasarımı söz konusu. getById metodumuz varsayılan bir implementasyon sunuyor ve eğer müşteri bulunamazsa özel bir istisna fırlatıyor *(CustomerNotFoundException)*. Bu sayede repository'yi kullanan kod, her seferinde null kontrolü yapmak zorunda kalmıyor. CustomerNotFoundException sınıfı tahmin edileceği üzere `sales.domain` paketinde yer almalı. **OrderNotFoundException** ile neredeyse aynı sınıf olduğunu söyleyebiliriz.

```java
package com.lectures.business.design.sales.domain;

import com.lectures.business.design.shared.kernel.DomainException;

public class CustomerNotFoundException extends DomainException {

    private final CustomerId customerId;

    public CustomerNotFoundException(CustomerId customerId) {
        super("no customer with id " + customerId);
        this.customerId = customerId;
    }

    public CustomerId customerId() {
        return customerId;
    }
}
```

## Sipariş Servisinin Tasarımı

Artık sipariş vermek için gerekli sınıfı uygulama katmanına ekleyebiliriz. Temel olarak sipariş verilmesi, onaylanması, gönderimi ya da değişiklik yapılması gibi işlemleri üstlenecek bir servis tasarlayacağız. Bu servis, domain katmanındaki iş kurallarını ve repository'leri kullanarak sipariş yönetimini sağlayacak. **OrderService** isimli sınıfı `sales.application` paketinde aşağıdaki gibi geliştirebiliriz.

```java
package com.lectures.business.design.sales.application;

import com.lectures.business.design.sales.api.OrderLineRequest;
import com.lectures.business.design.sales.api.OrderSummary;
import com.lectures.business.design.sales.api.PlaceOrderCommand;
import com.lectures.business.design.sales.domain.Customer;
import com.lectures.business.design.sales.domain.CustomerId;
import com.lectures.business.design.sales.domain.Order;
import com.lectures.business.design.sales.domain.OrderFactory;
import com.lectures.business.design.sales.domain.OrderId;
import com.lectures.business.design.sales.persistence.CustomerRepository;
import com.lectures.business.design.sales.persistence.OrderRepository;
import com.lectures.business.design.shared.kernel.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.function.Consumer;

public final class OrderService {

    private static final Currency TRY = Currency.getInstance("TRY");

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final OrderFactory factory;
    private final OrderMapper mapper;
    private final Clock clock;

    public OrderService(OrderRepository orders, CustomerRepository customers,
            OrderFactory factory, OrderMapper mapper, Clock clock) {
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
        this.customers = Objects.requireNonNull(customers, "customers must not be null");
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public OrderSummary placeOrder(PlaceOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Customer customer = customers.getById(CustomerId.of(command.customerId()));

        Order order = factory.draftFor(customer);
        for (OrderLineRequest line : command.lines()) {
            order.addLine(line.productId(), new Money(line.unitPrice(), TRY),
                    line.quantity(), line.discount());
        }
        orders.save(order);
        return mapper.toSummary(order);
    }

    public OrderSummary confirm(int orderId) {
        return change(orderId, Order::confirm);
    }

    public OrderSummary ship(int orderId) {
        return change(orderId, order -> order.ship(LocalDate.now(clock)));
    }

    public OrderSummary cancel(int orderId) {
        return change(orderId, Order::cancel);
    }

    public OrderSummary getOrder(int orderId) {
        return mapper.toSummary(orders.getById(OrderId.of(orderId)));
    }

    private OrderSummary change(int orderId, Consumer<Order> action) {
        Order order = orders.getById(OrderId.of(orderId));
        action.accept(order);
        orders.save(order);
        return mapper.toSummary(order);
    }
}
```

Sınıf tasarımıyla ilgili olarak üzerinde durulması gereken önemli noktalar var;

1. Dikkat edileceği üzere fonksiyonellikler hiçbir **if** veya **switch** bloğu kullanmlıyor. Servis, bir siparişin kargolanabilir olup olmadığı ile ilgilenmek yerine doğrudan `order.ship()` metodunu çağırıyor. Eğer bir ihlal varsa `Order` sınıfından bir domain exception nesnesi fırlatılıyor. Bu exception nesnesinin servisten geçip çağıran tarafa ulaştığına dikkat edelim. *(Buradaki yaklaşımla ilgili olarak [Tell, don't ask](https://martinfowler.com/bliki/TellDontAsk.html) prensibini araştıralım)*
2. `Consumer<Order>` türünden parametre alan **change** metodu kendi için bir iş akışına sahip. Siparişin bulunması, kabul edilmesi, kaydedilmesi ve özetinin oluşturulması gibi adımları kapsıyor. Aslında Load -> Apply -> Save -> Transform gibi bir iş akışı söz konusu. Bu sayede tekrar eden kodları merkezi bir noktada topluyoruz ve her değişiklik operasyonu için aynı adımların uygulanmasını garanti altına alıyoruz.
3. Servisimizin **public** yüzeyinden hiçbir domain tipinin olmadığına dikkat edelim. Tüm operasyonlar domain nesneleri yerine DTO veya primitive tipler üzerinden çalışıyor. Bu sayede servis, domain modelinin detaylarını dış dünyaya sızdırmıyor ve bağımsız bir API sunuyor. Bir başka deyişle HTTP katmanı Order sınıfını tanımak zorunda kalmıyor.
4. Şu an için para birimini sabit tutuyoruz ancak bu ideal bir yol değil. Gerçek bir çözümde para birimi bilgisi çoğunlukla müşteriden, bir fiyat listesinden ya da komutun kendisinden gelir. *(Bunu nasıl uygulayabileceğimizi tartışalım)*
5. **Today** bilgisi `LocalDate.now()` yerine enjekte edilen `Clock` üzerinden alınıyor. Özellikle testlerde sabit bir tarih kullanmak gerektiğinde bu yaklaşım oldukça kullanışlı olacaktır.
6. OrderService nesnesi, yapıcı metodu *(constructor)* üzerinden gerekli bağımlılıkları *(dependency)* alıyor. CustomerRepository, OrderRepository ve Clock gibi bağımlılıklar dışarıdan enjekte ediliyor *(Dependency Inversion prensibine bakalım)*

### Constructor Injection Hakkında

**OrderService** hiçbir şekilde bağımlılıklarını kendisi oluşturmuyor. Hepsini constructor metot üzerinden alıyor. Bu tekniğe **Constructor Injection** adı verilmekte. Neden önemli olduğunu görmek için aynı servisin bağımlılıklarını kendisinin oluşturduğu aşağıdaki versiyonunu ele alalım.

```java
public final class OrderService {

    private final OrderRepository orders = new InMemoryOrderRepository();
    private final OrderFactory factory = new OrderFactory(
            new SequentialOrderIdGenerator(10248), Clock.systemDefaultZone());
    ...
}
```

Burada bir dizi sorun var.

1. **Test edilemez kod:** Testte saati sabitlemek ya da repository'yi değiştirmek imkansız hale geliyor. Nitekim servis kendi seçtiği implementasyonlara kalıcı olarak bağlı hale geliyor. Buraya dokunmadan OrderService sınıfını farklı bileşenlerle kullanmak mümkün değil.
2. **Paylaşılamayan durum:** **SalesReport** da kendi `new InMemoryOrderRepository()` örneğini oluşturursa, servisin kaydettiği siparişleri rapor hiçbir zaman göremeyecektir. İşin kötü yanı kod sorunsuz derlenir ama sistem hatalı çalışır.
3. **Gizli bağımlılık:** Servisin neye ihtiyaç duyduğunu sınıfın imzasından anlamak mümkün değildir. Gövdesine bakmak gerekir ki object user bu bilgiye ulaşamayadabilir *(kapalı bir paketten okunuyor olabilir)*. Bir sınıfın gerçek bağımlılıklarını öğrenmek için tüm kodunu okumamamız gerekir.
4. **Bağımlılığın tersine çevrilmesinin *(Dependency Inversion)* bozulması:** application paketi artık sadece sözleşmeyi *(`OrderRepository`)* değil gerçek bir implementasyonu *(`InMemoryOrderRepository`)* bilir. İlerleyen zamanlarda **JDBC** tabanlı bir repository'ye geçmek istediğimizde servis kodunu değiştirmek zorunda kalırız, kalmamalıyız.

Constructor injection ile bu sorunların hepsi ortadan kalkar. Üstelik bağımlılıkları `final` alanlarda tutabildiğimiz için nesne oluşturulduğu anda tam ve tutarlı bir halde olur. Diğer yandan yapıcı metodun 5'ten fazla parametre almaya başlaması ise sınıfın fazla sorumluluk üstlendiğine dair erken bir uyarı olarak da değerlendirilmelidir.

> Genellikle üç tür injection'dan bahsedilir: **constructor**, **setter** ve **field** *(alan)*. Field injection *(`@Inject private OrderRepository orders;`)* kısa göründüğü için oldukça cazip gelir. Nitekim alan `final` olamaz, nesne container olmadan oluşturulduğunda bağımlılıklar `null` kalır ve testlerde reflection kullanmak gerekebilir. Örneğin, C# tarafında ASP.NET Core'un dahili container'ının yalnızca constructor injection'ı desteklemesi bir tesadüf değildir.

### Zaman Bağımlılığı

*"Bugün" bilgisini `LocalDate.now()` ile Order nesnesinin içinden almak yerine dışarıdan vermenin test edilebilirlik açısından faydası ne olabilir?* Zira `Order.ship(LocalDate date)` metodu zaten parametre olarak tarih bilgisini alıyordu. Aslında bu tarihi kimin üreteceği önemlidir. Yeni tasarımımıza göre OrderServis tarihi enjekte edilmiş `Clock` nesnesinden okuyor. **ship** metodunda nasıl kullandığımıza bakalım.

```java
public OrderSummary ship(int orderId) {
    return change(orderId, order -> order.ship(LocalDate.now(clock)));
}
```

Servis içinde `LocalDate.now()` şeklinde *(parametresiz)* bir çağrı yapsaydık, kargo tarihini doğrulayan bir test yazmak imkansız hale gelirdi. Test bugün geçer ama yarın kırılırdı. `Clock.fixed(...)` ile sabit bir zaman verilebilir ama siparişin bugün verilip on iki gün sonra kargolandığı bir senaryoyu test etmek için zamanın da ilerlemesi beklenir. Bunun için test paketine aşağıdaki gibi bir test dublörü *(test double)* ekleyebiliriz.

```java
package com.lectures.business.design.sales.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A test double for time: stands still until the test moves it.
 *
 * <p>
 * {@code Clock.fixed} cannot move and the system clock cannot be controlled.
 * Use cases that span days (order today, ship next week) need something in
 * between.
 */
public final class SteppingClock extends Clock {

    private Instant now;

    public SteppingClock(String isoInstant) {
        this.now = Instant.parse(isoInstant);
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("SteppingClock is UTC only");
    }
}
```

**Clock** soyut bir sınıf *(abstract class)* olduğu için kendi saatimizi yazmak oldukça kolay. Test sırasında saat biz ilerletene kadar durur.

## Composition Root

Sipariş servisi tasarımına göre yapıcı metot üzerinden alınan bağımlılıklar dışarıdan gelmeli. Peki ya bu nesneleri kim oluşturacak?

// DEVAM EDECEK
