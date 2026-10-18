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

// DEVAM EDECEK
