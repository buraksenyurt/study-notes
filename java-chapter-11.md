<a id="top"></a>

# Bölüm 11: Domain Events ve Observer Deseni

<!-- nav:top:start -->
[← Bölüm 10](java-chapter-10.md#top) · [İçindekiler](README.md#top)
<!-- nav:top:end -->

Bir önceki bölümde yazma yönündeki kullanım senaryolarını **OrderService** sınıfına taşıdık ve tüm bağımlılıkları **SalesModule** adındaki composition root nesnesi üzerinden bağladık. İş biriminden yeni bir talep geldiğini düşünelim; *"Sipariş kargoya verildiğinde müşteriye bir bildirim gitsin."* Bunu yapmanın en hızlı ve kolay yolu `OrderService.ship` metoduna bir satır eklemek olabilir. Fakat sonrasında *"kargolanan siparişler için stok düşülsün"*, ardından *"iptal edilen siparişler muhasebeye bildirilsin"* gibi taleplerin de geldiğini düşünelim. Bu talepleri de **OrderService** sınıfında karşılarsak onu biraz daha büyütür ve servisin zamanla sistemdeki her şeyi bilen bir sınıfa haline gelmesine neden oluruz. İşte bu bölümde cevaplarını arayacağımız sorular;

- Bir aggregate'te gerçekleşen bir değişikliği, o aggregate'in söz konusu değişiklikle ilgilenenlerin kim olduğunu bilmesine gerek kalmadan nasıl duyurabiliriz?
- Bir olay *(event)* hangi bilgileri taşımalıdır, hangi bilgileri taşımaMAlıdır?
- Olayları kim, ne zaman yayınlamalıdır? *(Kayıttan önce mi, sonra mı?)*
- Senkron yayınlamanın bedeli nedir, bu bedeli hafifletmek için ne gibi stratejiler kullanılabilir?

## Observer Deseni ve Domain Event Kavramı

Observer, GoF *(Gang of Four)* desenlerinden biridir ve bir nesnenin *(subject)* durumu değiştiğinde ona bağımlı olan nesnelerin *(observers)* otomatik olarak haberdar edilmesini sağlamak için tasarlanmıştır. Buradaki kritik nokta, subject nesnesinin gözlemcilerin kim olduğunu ve ne yaptığını bilmemesidir. Sadece bir olayın gerçekleştiğini duyurur. **Domain Driven Design** yaklaşımında bu fikir **Domain Event** adıyla karşımıza çıkar. Bir domain event, iş alanında *gerçekleşmiş* ve iş birimi açısından anlamlı olan bir olgudur. Bu tanımın üç önemli sonucu vardır.

1. **Geçmiş zaman kipiyle adlandırılır:** *ShipOrder* bir komuttur *(henüz olmamış bir istek olarak düşünülmelidir)*, **OrderShipped** ise bir olaydır *(olmuş bir şeyi ifade eder)*. Komut reddedilebilir ama olay reddedilemez çünkü zaten olmuştur.
2. **Değişmezdir *(immutable)*:** Geçmiş değiştirilemez ve bu yüzden olaylar için **record** kullanmak oldukça doğaldır.
3. **İş dilini *(ubiquitous language)* kullanır:** `OrderStatusChanged(from, to)` gibi teknik bir olay yerine **OrderShipped**, **OrderCancelled** gibi iş dilinde karşılığı olan olayları tercih ederiz. Şöyle düşünelim; Depoda görevli bir personelden *"Sipariş durumu değişti"* gibi bir cümle duymayız, bunun yerine *"sipariş kargolandı"* cümlesini duyarız.

> DDD oldukça geniş bir konudur ve birçok yazılım dili prensibini, tasarım desenini ve mimari yaklaşımı içerir. Bu dönem dersimizde DDD'nin çok temel yönlerini ele almaktayız.

## Olayları *(Events)* Tanımlayalım

İşe ilk olarak `shared-kernel` modülüne bir işaretleyici *(marker)* arayüz ekleyerek başlayalım.

```java
package com.lectures.business.design.shared.kernel;

public interface DomainEvent {
}
```

**DomainEvent** arayüzü *Bir tipin hangi iş modülüne ait olduğunu soramıyorsak o tip shared-kernel'e girer.* ilkesine göre `shared-kernel` modülünde yer alır. Sonuç itibariyle **DomainEvent** kavramı herhangi bir iş modülüne ait değildir ancak **OrderShipped** tamamen **sales** modülüne aittir. Bu arayüz tanımına `occurredAt()` veya `eventId()` gibi birkaç varsayılan davranış eklemeyi düşünebiliriz. Ancak almama sebebibimiz bilinçli bir karardır. **Order** sınıfı zamanı bilmiyor *(ADR-0006, no_hidden_clock)*. Olayın ne zaman gerçekleştiği, olayın kimliği, hangi kullanıcının tetiklediği gibi bilgiler olayı taşıyan altyapının sorumluluğunda. Genellikle gerçek hayat senaryolarında bir mesaj kuyruğu kullanılıyorsa bu bilgiler mesajın zarfına *(envelope)* eklenir. Sipariş olaylarını `sales.domain` paketinde **sealed** bir arayüz altında toplayabiliriz. Dolayısıyla bu modülün sunduğu asıl olaylar *(OrderConfirmed, OrderShipped, OrderCancelled gibi)* OrderEvent arayüzünü implement eder. Aşağıdaki kodlarla devam edelim.

```java
package com.lectures.business.design.sales.domain;

public sealed interface OrderEvent extends DomainEvent
        permits OrderConfirmed, OrderShipped, OrderCancelled {

    OrderId orderId();

    CustomerId customerId();
}
```

```java
public record OrderConfirmed(OrderId orderId, CustomerId customerId, Money total) implements OrderEvent {
}

public record OrderShipped(OrderId orderId, CustomerId customerId, LocalDate shippedDate, Address destination)
        implements OrderEvent {
}

public record OrderCancelled(OrderId orderId, CustomerId customerId, OrderStatus previousStatus)
        implements OrderEvent {
}
```

Her olayın *(record türleri)* onu dinleyecek gözlemcilerin *(observer)* işini görecek kadar bilgi taşıdığına dikkat edelim. **OrderShipped** teslimat adresini, **OrderCancelled** ise iptal edilmeden önceki durumu taşıyor. Olay nesnelerin ne taşıması gerektiğine karar vermek de önemli bir mevzu. Söz gelimi neden **OrderCanceled** olayı iptal edilmeden önceki durumu taşıyor; zira onaylanmış bir siparişin iptali ile taslak bir siparişin iptali business anlamda farklı sonuçlar doğurabilir. Örneğin onaylanmış bir siparişte stok ayrılmıştır ve bu stoğun serbest bırakılması gerekebilir.

> Bir olaya ne kadar veri koymalıyız sorusunu cevaplamak kolay değildir. Literatürde iki uç yaklaşım olduğu görülüyor. **Event Notification** yaklaşımında olay sadece kimlik bilgisini taşır *(`OrderShipped(orderId)`)* ve ilgilenen taraf geri kalan bilgiyi bu değeri kullanarak kendisi okur. **Event-Carried State Transfer** yaklaşımında ise olay gözlemcinin ihtiyaç duyacağı tüm bilgiyi kendi üzerinde taşır. İlk yöntemde daha az bağlantı *(coupling)* yaratır ama gözlemciyi kaynağa sorgu atmaya zorlar. İkinci teknikse gözlemciyi bağımsız kılar ancak olayın şeması bir sözleşmeye dönüşür. Biz dersimiz boyunca orta noktada kalmaya çalışacağız.

## Aggregate Nesnesinin Görevi

// DEVAM EDECEK
