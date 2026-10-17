# Bölüm 9 - Maven Modülleri ve ArchUnit Kullanımı

Bu bölüme kadarki kod örneklerimizi tek bir Maven modülü altında gerçekleştirdik. Proje türünden olan root pom altında bölümlerin her birini ayrı jar paketleri halinde tasarladık. Bu daha çok ders konularını ayrı paketler halinde organize etmek istediğimiz için uyguladığımı bir pratikti. Pek tabii bir önceki bölümde iş nesnelerini, servisleri ve diğer bazı kavramları kendi paketlerine almaya başladık. Gerçek hayat çözümlerinde paket ve modüller arasındaki ilişkileri doğru tesis etmek oldukça önemlidir. Paketlerin aynı modül içerisinde yer almasının avantajları olduğu kadar dezavantajları da olabilir. Bu biraz da seçilen mimari yaklaşıma ve alınan kararlara *(çoğunlukla ADR-Architecture Decision Record maddelerinde açıkça belirtilir)* bağlıdır. Bu bölümde özellikle modül sınırlarını anlamlandırmaya da çalışacağız. Şu soruların cevaplarını bulmaya çalışacağız:

- Paket sınıfı ve modül sınırları neyi ifade eder? Derleyici hangisini kontrol edebilir?
- Modülleri katmanlara göre mi ayırmak gerekir yoksa bir özelliğe *(feature)* göre mi?
- Ortak paylaşılan iş birimleri için ayrı bir modül mü oluşturmak gerekir yoksa mevcut modüller içerisinde mi yer almalıdır?
- Mimari bir kararın kural olarak sayılması için ne gerekir ? *(Grep vs ArchUnit)*

## Modül Haritasının Hazırlanması

Son dersimize göre `week08-step4-mappers` modülünde dört paket bulunuyor; api, application, domain ve persistence. Soru şu; bunları modüller halinde bölmek istesek kaç modüle ihtiyacımız olur? İlk etapta buradaki her bir katmanın bir modüle olarak ele alınabileceğini düşünebiliriz. Bu yanlış olmamakla birlikte her zaman en doğru çözüm değildir. Kobay Northwind senaryomuzu düşündüğümüzde aslında aşağıdaki gibi bir durumdan bahsedebiliriz.

```text
shared-kernel -> catalog, inventory, crm, sales, purchasing, shipping -> norwhint-web(war)

                            -> quality/architecture-tests
```

Bu yaklaşıma göre hiçbir modül aslında bir katman *(layer)* değildir. Altı iş modülü vardır ve katmanlar her modülün içinde birer paket olarak bulunur. Bu stratekide paketleme özelliğe göredir *(package-by-feature)*, katmana göre değil. Dolayısıyla `com.northwind.inventory.reservation` gibi bir paket geçerliyken, `com.northwind.service` şeklinde bir paket geçerli değildir. Bunu aşağıdaki üç gerekçe ile de açıklayabiliriz.

- Katmanlara göre bölümleme yaptığımızda her iş değişikliğinin bütün modülleri dolaşma riski oluşur. Söz gelimi *siparişe indirim ekle" işi domain, application ve api modüllerini birlikte değiştirmeyi gerektirebilir. Üç modülü aynı **Pull Request** içinde değiştiriyorsak bu üç modülün aslında tek bir modül olduğunu varsayabiliriz. Yani modül sınırlarında bir sorun vardır.
- sales ve inventory modülleri arasındaki sınır oldukça anlamlıdır. İki ayrı iş alanı, iki ayrı takım ve iki farklı değişim hızı söz konusudur. domain ve application şeklinde katmanların olduğu bir kurguda ise bu sınırlar daha belirsiz hale gelir.
- Modüller arası bağımlılıkların yönetimi daha kolaydır. Özellikle ArchUnit gibi araçlarla modüller arası bağımlılık kurallarını test edebiliriz. Bu sayede istenmeyen bağımlılıkların da önüne geçebiliriz.

Asıl lab ortamında geliştireceğimiz projede yukarıdaki stratejiyi uygulayacağımızdan bu bölümde minyatür bir replikasını inşa etmeye çalışacağız. Aşağıdakine benzer bir iskeleti hedefliyoruz.

```text
project-root/
├── pom.xml
├── shared-kernel/      (Money, Address, Specification, DomainException vs burada yer alır)
├── sales/              (api, application, domain, persistence paket olarak burada yer alır)
├── app/                (Main ve öğrenme amaçlı kullandığımız anemik örnekler burada durur)
└── quality/
    └── architecture-tests/  (ArchUnit testleri de burada yer alır)
```

Dört modülden oluşan bu kurguda hiçbir modül bir katman *(layer)* değildir. İş yapan tek modülümüz sales modülüdür. *(Lab çalışmamızda ele alacağımız örnekte sales gibi beş modül daha olacak)* Ayrıca;

- `shared-kernel` modülü sadece genel *(generic)* tipleri barındırır. Money, Address, PostalAddress, Specification, DomainException vs. burada yer alır. Bir tipin bu modüle girip girmediğini belirlemek için, hangi iş modüle ait olduğunu soramıyor olmamız gerekir. Söz gelimi OrderSpecitication, shared-kernel'e girmez zira Order'a özgü bir kavramdır. Buna karşın `Specification<T>` tam olarak generic bir kavramdır ve shared-kernel'de yer alır.
- Benzer şekilde IncompleteOrderException, InvalidStateTransitionException, OrderNotModifiableException, OrderNotFoundException gibi istisnalar da shared-kernel'e girmez. Zira bunlar da `sales` domain'ine özgüdür. Tabii `DomainException` genel bir türdür ve `shared-kernel`'de yer alır.
- Burada tartışmaya neden olabilecek iki tür de vardır; OrderId ve CustomerId. Tüm modüller için ortak görünebilirler ama sipariş müşterisi sipariş modülünün bir parçasıdır ve dolayısıyla `OrderId` ve `CustomerId` genellikle `sales` modülünde yer alır *(Bu DDD içerisinde de zor kararlar arasında yer alır. Tartışmaya açık.)*

> `week09-maven-modules-and-archunit` klasörünü dikkatlice inceleyelim. Root pom.xml'deki package özelliği ile alt klasörlerdeki package özelliklerinin ne olduğuna bakalım ve pom ile jar olmaları arasındaki farkları araştıralım.

## Sales Modülü

Satış modülünün amacı, siparişlerin yönetilmesi ve işlenmesidir. Bu modül, `api`, `application`, `domain` ve `persistence` paketlerini içerir ve iş mantığını kapsar. Aşağıdaki yapıya sahiptir.

```text
├── api/            OrderSummary, CustomerRevenue, SalesTotals, package-info
├── application/    OrderMapper, SalesReport, package-info
├── domain/         Order, OrderLine, OrderStatus, Customer, CustomerId, OrderId,
│                   OrderFactory, OrderIdGenerator, SequentialOrderIdGenerator,
│                   OrderSpecifications, DiscountPolicy, VolumeDiscountPolicy,
│                   CustomerTier, IncompleteOrderException,
│                   InvalidStateTransitionException, OrderNotModifiableException,
│                   OrderNotFoundException, package-info
└── persistence/    OrderRepository, InMemoryOrderRepository, package-info
```

Benzer kurgu test modülü için de geçerlidir.

```text
├── domain/         OrderTest, OrderStatusTest, CustomerTest, OrderQueryTest,
│                   CustomerIdTest, OrderIdTest, OrderFactoryTest, OrderBuilderTest,
│                   DiscountPolicyTest, OrderSpecificationsTest, OrderBuilder
├── persistence/    InMemoryOrderRepositoryTest
└── application/    SalesReportTest, OrderMapperTest
```

Sales modülü, `shared-kernel` modülüne bağımlıdır ama tam tersi geçerli değildir. Üstelik bu bağımlılığı pom.xml dosyasında da açıkça görebiliriz.

```xml
 <dependencies>
    <dependency>
        <groupId>com.lectures</groupId>
        <artifactId>week09-shared-kernel</artifactId>
    </dependency>
</dependencies>
```

Ufak bir deneme; Her şey week9 projesine taşındıktan sonra örneğin shared modülündeki Money sınıfına aşağıdaki satırı ekleyip derlemeyi deneyelim.

```java
import com.lectures.business.design.sales.domain.Order;
```

Aşağıdaki gibi bir derleme hatası alırız.

![week09 compilation error](./images/week_09_00.png)

`shared-kernel`'in pom dosyasında sales modülüne bir bağımlılık eklenmediği için bu derleme hatasının alınması normaldir ve zaten istediğim bir şeydir. Aynı modül içinde sadece paketlerden oluşan bir tasarımda kalsaydık bu tür bir bağımlılık sorunuyla karşılaşmazdık. Tabii tam tersi durumda, yani sales modülündeki Order içerisinden shared-kernel modülündeki sınıflara erişmeye çalıştığımızda derleme hatası almayız çünkü sales modülü shared-kernel modülüne bağımlıdır. Buna göre;

| Hatalı Bağımlılık Yönü | Kim durdurur? |
| --- | --- |
| `shared-kernel` -> `sales` | Maven derleme zamanı |
| `sales.domain` -> `sales.persistence` | Maven durdurmaz eğer ikisi de aynı jar içerisindeyse |

## Uçtan Uca Çalışan Bir Smoke Test Yazmak

Bir uygulamada yeni sürüm çıktığımızda veya build değişikliği yaptığımızda sistemin en temel ve kritik fonksiyonelliklerinin çalışıp çalışmadığını hızlıca doğrulamak için uygulanan ön seviye testleri **smoke test** olarak adlandırılır *(Bu terim aslında donanım dünyasından geliyor. Yeni bir elektronik devreye ilk kez elektrik verildiğinde duman çıkıp çıkmadığına yani bir şeyin yanıp yanmadığına bakılmasından...)* Bu testlerde amaç yazılımın tüm detaylarını test etmekten ziyade ana işlevlerin çalışıp çalışmadığını görerek daha kapsamlı *(QA - Quality Assurance)* testlere geçmeden önce hızlı bir kontrol sağlamaktır. Yani sistemin ana işlevleri bile çalışmıyorsa diğer detayları test etmek için vakit kabetmeye gerek yoktur. Artık projemiz biraz daha olgunlaştığına göre bu tip bir testi de ele almak doğru olacaktır. Örneğin repository'yi kuralım, satış raporunu alalım, rapordaki sayıların doğrulunu kontrol edelim. Bunun için daha önceden kullandığımız **OrderBuilder** sınıfından da yararlanabiliriz *(Builder pattern konusuna bakarken değinmiştik)* Ancak bu sınıfımız şu anda sales modülündeki test paketinde yer alıyor. Bu yüzden sales modülünden bir `test-jar` yayınlayıp *(publish)* diğer modüllerde kullanmayı deneyeceğiz. Aslında, OrderBuilder'ı main paketine taşımak da bir çözüm olarak görülebilir ama bu durumda Test Fixture nesnesini üretime bağımlı hale getirmiş oluruz *(Gereksiz bir şekilde `sales.jar` ile birlikte her yere dağılır)*

> Test Fixture; Bir yazılım kodunu test etmeden önce sistemi belirli ve kontrollü bir başlangıç durumuna getiren hazırlık kodları ve verilerdir. Amaç,testlerin her defasında aynı koşullarda çalışmasını sağlamaktır. Örneğin bir teste başlamadan önce veritabanına önceden tanımlanmış örnek kullanıcıların atılması veya sahte ürün verilerinin yüklenmesi gibi. Derslerimizde ele aldığımız OrderBuilder tam olarak bu amaca hizmet etmektedir.

// DEVAM EDECEK

---

## Sorular

// EKLENECEK

## Bu bölümün kodu

// EKLENECEK

```powershell
cd src
```
