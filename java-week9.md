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

// DEVAM EDECEK

---

## Sorular

// EKLENECEK

## Bu bölümün kodu

// EKLENECEK

```powershell
cd src
```
