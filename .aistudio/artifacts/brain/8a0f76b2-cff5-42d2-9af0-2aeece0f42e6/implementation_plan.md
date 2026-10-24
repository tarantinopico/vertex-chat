# Vertex Chat

**Vertex Chat** je osobní, produkčně připravená nativní Android aplikace (Kotlin + Jetpack Compose) pro pokročilou konverzaci a práci s kódem nad modely Google Cloud Vertex AI a Gemini API. Kombinuje nekompromisní AMOLED Material 3 rozhraní, hardwarově šifrované přihlašování přes Service Account (AES/GCM v Android Keystore), izolovaný Virtuální souborový systém (VFS) s obousměrným AI Function Callingem a spolehlivé provádění dlouhých generativních úloh na pozadí pomocí WorkManageru.

---

## Přehled uživatelských rozhodnutí (User Review & Critical Decisions)

> [!IMPORTANT]
> Níže jsou shrnuta klíčová produktová a architektonická rozhodnutí potvrzená v úvodní fázi návrhu. Po schválení tohoto plánu tlačítkem **Proceed** vygeneruji kompletní, kompilovatelný zdrojový kód, testy, CI/CD workflow i dokumentaci.

- **Potvrzené rozhodnutí 1 – Vizuální identita a navigace**: Čistý AMOLED černý režim (`#000000`) s akcentní barvou **Electric Blue (`#3B82F6`)**, jemným skleněným efektem (glassmorphism) na horní liště a vstupním poli a bočním **NavigationDrawerem** pro rychlou správu chatů, přístup k VFS a nastavení.
- **Potvrzené rozhodnutí 2 – Duální režim autentizace (Vertex AI OAuth2 + Gemini API klíč)**: Primární podpora Google Cloud Service Account JSON (šifrovaného přes Android Keystore + AES/GCM + uživatelské heslo/PIN pro odemčení relace a generování OAuth2 Bearer tokenů s automatickou obnovou) doplněná o přepínač na přímý Gemini API klíč (`BuildConfig.GEMINI_API_KEY` nebo vlastní klíč) pro okamžité testování v emulátoru bez nutnosti nahrávat JSON.
- **Potvrzené rozhodnutí 3 – Plnohodnotný interaktivní VFS**: Obrazovka a detail Virtuálního souborového systému nabídne nejen čtení s čísly řádků a export přes SAF / sdílení, ale také vyhledávání v obsahu, ruční úpravy souborů a přímé vytváření souborů i složek uživatelem v UI (vedle automatických AI nástrojů).

---

## 1. Přehled a hlavní koncept (Overview & Core Concept)

- **Co aplikace dělá**: Slouží jako osobní vývojářské a analytické AI pracovní prostředí na Androidu. Každá konverzace má vlastní kontext, modelovou konfiguraci (včetně zobrazení *Thought Summary* u přemýšlejících modelů jako `gemini-3.1-pro-preview` či `gemini-2.5-pro`), izolovaný virtuální souborový systém (VFS) a běží odolně na pozadí i při minimalizaci aplikace nebo při vícekrokovém generování rozsáhlých souborů (`MAX_TOKENS` auto-continue).
- **Cílový uživatel**: Náročný uživatel a vývojář, který vyžaduje plnou kontrolu nad svým Google Cloud Vertex AI projektem, bezpečné lokální uložení klíčů bez prostředníků a schopnost nechat AI číst, vytvářet a upravovat soubory přímo v pískovišti konverzace.
- **Hlavní přidaná hodnota**: Spojení přímého OAuth2 přístupu k Vertex AI, streamovaného SSE parsování v reálném čase, lokální exekuce VFS nástrojů (`createFolder`, `writeFile`, `readFile`, `deleteFile`, `listFiles`, `moveFile`, `renameFile`) a bezvýpadkového běhu na pozadí s možností okamžitého exportu vygenerovaných artefaktů do systému Android.

---

## 2. Uživatelská zkušenost a vizuální design (User Experience & Visual Design)

### Klíčové uživatelské průchody (User Flows)

1. **První spuštění, šifrování a odemčení (Onboarding & Vault Unlock)**:
   - Uživatel si zvolí režim připojení: **Vertex AI Service Account** (vloží JSON textem nebo vybere soubor přes systémový picker, nastaví šifrovací heslo, Google Cloud Project ID a region např. `us-central1`) nebo **Rychlý Gemini API režim**.
   - Service Account JSON je okamžitě zašifrován kombinací klíče v Android Keystore (`AES/GCM/NoPadding`) a odvozeného klíče z hesla (PBKDF2WithHmacSHA256) a uložen. Při dalším spuštění uživatel pouze odemkne trezor heslem a token se drží pouze v RAM s automatickou obnovou před expirací.
2. **Konverzace, výběr modelu a Thinking & Tool-Use zpětná vazba**:
   - V horní liště s poloprůhledným rozostřením uživatel vybírá z načtených modelů (`gemini-3.1-pro-preview`, `gemini-3.5-flash`, `gemini-2.5-pro`, `gemini-2.5-flash` atd.), které jsou cachované v databázi Room.
   - Po odeslání dotazu se zobrazí pulzující indikátor *„Přemýšlím…“*. Jakmile začnou přicházet SSE události, myšlenkové pochody modelu (`thought = true`) se skládají do rozbalovacího panelu **Myšlenkový postup (Thought Summary)** nad samotnou odpovědí.
   - Pokud model zavolá VFS nástroj (`functionCall`), v proudu zprávy se vykreslí interaktivní **Tool-Use karta** s ikonou operace, názvem souboru, indikátorem průběhu a po dokončení přímými tlačítky **Zobrazit v editoru** a **Exportovat / Stáhnout**.
   - Odpověď se vykresluje s podporou bohatého Markdownu (zvýrazněné bloky kódu s tlačítkem kopírování, nadpisy, tučné písmo, kurzíva, seznamy, tabulky) a dlouhý stisk bubliny zkopíruje zprávu do schránky.
3. **Správa Virtuálního souborového systému (VFS Explorer & Editor)**:
   - Z bočního šuplíku nebo horní lišty chatu uživatel přejde do obrazovky **Soubory (VFS)** daného chatu.
   - Stromová struktura v `LazyColumn` umožňuje rozbalovat složky, filtrovat soubory, zakládat nové soubory/složky, přejmenovávat a mazat položky.
   - Klepnutí na soubor otevře modální panel s virtualizovaným prohlížečem řádků (`LazyColumn` s čísly řádků v monospace fontu *JetBrains Mono* / *Roboto Mono*), vyhledáváním v souboru, přepínačem do režimu ruční úpravy a exportem přes Storage Access Framework (`ACTION_CREATE_DOCUMENT`) nebo systémové sdílení (`FileProvider`).
4. **Dlouhé úlohy na pozadí a automatické navazování (`MAX_TOKENS`)**:
   - Každý požadavek spouští foreground `CoroutineWorker`, který zobrazuje systémovou notifikaci s aktuální fází (*„Generuji odpověď…“*, *„Zapisuji soubor…“*, *„Automaticky pokračuji v dlouhé odpovědi (krok 2)…“*) a akčním tlačítkem **Zrušit**.
   - Pokud model ukončí odpověď s `finishReason == "MAX_TOKENS"`, worker plynule odešle navazující požadavek a připojuje další bloky přímo do rozpracované zprávy v Room databázi.

### Vizuální identita a téma (AMOLED Material 3)

- **Estetický směr**: *Precision Obsidian Studio* — vysoce kontrastní, technicky čisté prostředí optimalizované pro OLED displeje s nulovým prosvítáním pozadí a ostrými akcenty.
- **Barevná paleta**:
  - Pozadí (`background`, `surface`): Čistá AMOLED černá `#000000`.
  - Vyvýšené povrchy a karty (`surfaceContainer`, bubliny AI, Tool karty): Odstupňované uhlové tóny `#0E0E0E`, `#141414` a `#1A1A1A` s jemným 1dp ohraničením `#262626`.
  - Primární akcent (`primary`): **Electric Blue `#3B82F6`** (uživatelské bubliny v tónu `#172554` s lemem `#2563EB`, aktivní prvky `#3B82F6`).
  - Sekundární akcent (`tertiary` pro AI nástroje a myšlenkové bloky): Jemná fialová `#8B5CF6` / `#1E1B4B`.
  - Text: Primární `#E5E5E5`, sekundární `#8A8A8A`, stavové barvy `#10B981` (úspěch VFS operace) a `#EF4444` (chyba).
- **Typografie a hierarchie**:
  - Kombinace čitelného bezpatkového písma pro konverzaci a lokálně přibaleného monospace písma (**JetBrains Mono** / **Roboto Mono** přes `res/font/`) pro bloky kódu, cesty ve VFS a číslování řádků.
- **Glass efekt a animace**:
  - Horní lišta (`TopAppBar`) a spodní vstupní dok využívají poloprůhlednou vrstvu (`#CC0A0A0A`) s jemným rozostřením a horní/spodní 1dp spekulární linkou (`#1F3B82F6`).
  - Plynulé přechody pomocí `AnimatedContent`, `AnimatedVisibility` a `animateContentSize()` při rozbalování panelu myšlenek a stromu VFS.

---

## 3. Klíčová architektonická rozhodnutí a řešení rizik (Key Product Decisions & Trade-Offs)

### Fázování projektu a pořadí implementace

1. **Fáze 1 – Konfigurace buildu, závislosti a bezpečnostní trezor (Security & Crypto Layer)**:
   - *Odůvodnění*: Bezpečná správa Service Account JSON a OAuth2 tokenů je základním kamenem pro jakoukoliv komunikaci s Vertex AI.
2. **Fáze 2 – Datová vrstva (Room databáze, DataStore a streamovaný VFS engine)**:
   - *Odůvodnění*: Chat, AI nástroje i WorkManager vyžadují perzistentní úložiště zpráv, modelů a souborového stromu ještě před připojením síťového klienta.
3. **Fáze 3 – Vertex AI / Gemini síťová vrstva, SSE parser a orchestrátor VFS nástrojů**:
   - *Odůvodnění*: Zajišťuje autentizované volání `:streamGenerateContent?alt=sse`, parsování myšlenkových bloků (`thought`), detekci `functionCall` a lokální vykonání VFS operací s odesláním `functionResponse`.
4. **Fáze 4 – WorkManager Foreground Worker a `MAX_TOKENS` orchestrace**:
   - *Odůvodnění*: Obaluje AI volání do odolného běhu na pozadí s notifikacemi a automatickým navazováním dlouhých výstupů.
5. **Fáze 5 – UI vrstva (AMOLED Theme, NavigationDrawer, Chat, Markdown, VFS Explorer & Editor)**:
   - *Odůvodnění*: Napojuje reaktivní `Flow` z Room a stav WorkManageru na kompletní obrazovky v Jetpack Compose.
6. **Fáze 6 – Unit testy (VFS + SSE parser), ProGuard/R8, README a GitHub Actions CI/CD**:
   - *Odůvodnění*: Ověření správnosti kritických komponent a automatizovaný produkční build pipeline.

### Klíčové závislosti a verze

- **Jádro a UI**: Kotlin `2.2.10`, Jetpack Compose BOM `2024.09.00`, Material 3, Material Icons Extended, Activity Compose `1.10.1`, Navigation Compose `2.8.9`, Lifecycle Runtime & ViewModel Compose `2.8.7`.
- **Perzistence a práce na pozadí**: Room (`room-runtime`, `room-ktx`, `room-compiler` přes KSP) `2.7.0`, DataStore Preferences `1.1.7`, WorkManager (`androidx.work:work-runtime-ktx:2.10.0`).
- **Síť, serializace a autentizace**: OkHttp `4.12.0` / `4.10.0`, Retrofit `2.12.0`, Kotlinx Serialization JSON `1.8.0` (pro čisté a rychlé zpracování SSE JSON chunků a JWTS/OAuth2 struktur), `androidx.security:security-crypto:1.1.0-alpha06` + nativní Android Keystore (`AES/GCM/NoPadding`).
- **Testování**: JUnit `4.13.2`, Kotlinx Coroutines Test `1.10.2`, Robolectric `4.16.1`.

### Identifikace rizikových míst a jejich architektonické řešení

- **Riziko 1: Krypto-kompatibilita a závislost `google-auth-library-oauth2-http` na Androidu**:
  - *Problém*: Těžké javovské knihovny `google-cloud-aiplatform` a `google-auth` často způsobují konflikty duplicitních tříd (`META-INF/DEPENDENCIES`, `grpc-netty`, Apache HttpClient) při R8 minifikaci v Androidu.
  - *Řešení*: Implementujeme čistý, standardizovaný **Service Account OAuth2 JWT klient v Kotlinu** (parsování `client_email`, `private_key` PKCS#8 a `token_uri` ze Service Account JSONu, podepsání RS256 JWT pomocí standardního `java.security.Signature` s `SHA256withRSA` a směna za Bearer token přes `https://oauth2.googleapis.com/token` s cachingem v paměti a automatickou obnovou 5 minut před expirací). Tím získáme 100% kompatibilitu s Google Cloud Vertex AI, nulové konflikty v R8 a bleskový build.
- **Riziko 2: Limity Foreground Service na Androidu 14+ (API 34/35)**:
  - *Problém*: Android 14+ vyžaduje striktní deklaraci `foregroundServiceType` v manifestu i při volání `setForeground()`, jinak aplikace spadne na `MissingForegroundServiceTypeException`.
  - *Řešení*: V `AndroidManifest.xml` deklarujeme `FOREGROUND_SERVICE` a `FOREGROUND_SERVICE_DATA_SYNC` a službu `androidx.work.impl.foreground.SystemForegroundService` s `android:foregroundServiceType="dataSync"`. V `CoroutineWorker.getForegroundInfo()` předáme `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` na API 29+.
- **Riziko 3: OOM (Out-Of-Memory) při velkých souborech ve VFS**:
  - *Problém*: Načtení nebo zápis souboru s tisíci řádky najednou do `String` v paměti může shodit UI nebo zahltit kontext.
  - *Řešení*: Zápis (`writeFile`) probíhá streamovaně přes `BufferedWriter` / `FileOutputStream` (s podporou `append = true`). Čtení v UI editoru/prohlížeči probíhá po stránkách/řádcích přes indexovaný `BufferedReader` a v `LazyColumn` se vykreslují pouze viditelné řádky. AI nástroj `readFile` podporuje volitelné parametry `startLine` a `endLine` pro bezpečné čtení výřezů velkých souborů.
- **Riziko 4: Přerušení SSE streamu nebo rozdělené víceřádkové JSON události**:
  - *Problém*: Server-Sent Events (`:streamGenerateContent?alt=sse`) mohou doručit současně `thought` části, běžný text, `functionCall` i `finishReason = "MAX_TOKENS"`.
  - *Řešení*: Dedikovaný `VertexSseParser` čte `ResponseBody.byteStream().bufferedReader()`, bezpečně skládá `data:` payloady, rozlišuje části s příznakem `thought == true` od finálního textu, zachytává volání funkcí a vrací typované události (`StreamEvent.ThoughtDelta`, `StreamEvent.TextDelta`, `StreamEvent.ToolCall`, `StreamEvent.Finish`).

---

## 4. Technická architektura a datová strategie (Technical Architecture & Data Strategy)

### Architektonické schéma systému

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         UI VRSTVA (Jetpack Compose)                          │
│  ┌────────────────────┐  ┌─────────────────────────┐  ┌───────────────────┐  │
│  │ NavigationDrawer   │  │ ChatScreen + TopBar     │  │ VfsExplorerScreen │  │
│  │ (Seznam chatů,     │  │ - Model Dropdown        │  │ - Strom složek    │  │
│  │  Nový chat,        │  │ - Thought Summary Panel │  │ - Line Viewer     │  │
│  │  Přepínání sekcí)  │  │ - Tool-Use Cards        │  │ - Editor & Hledání│  │
│  └─────────┬──────────┘  │ - Markdown Bubbles      │  │ - SAF / Share     │  │
│            │             └────────────┬────────────┘  └─────────┬─────────┘  │
└────────────┼──────────────────────────┼─────────────────────────┼────────────┘
             │                          ▼                         │
┌────────────┴────────────────────────────────────────────────────┴────────────┐
│                     VIEWMODEL & STAVOVÁ VRSTVA (MVVM)                        │
│     ChatViewModel  ◄───►  VfsViewModel  ◄───►  AuthSettingsViewModel         │
└────────────┬──────────────────────────┬─────────────────────────┬────────────┘
             │                          │                         │
             ▼                          ▼                         ▼
┌─────────────────────────┐ ┌────────────────────────┐ ┌───────────────────────┐
│  WORKMANAGER VRSTVA     │ │   DATOVÁ & VFS VRSTVA  │ │ BEZPEČNOST & AUTH     │
│ ┌─────────────────────┐ │ │ ┌────────────────────┐ │ │ ┌───────────────────┐ │
│ │ AiGenerationWorker  │ │ │ │ Room Database      │ │ │ │ KeystoreVault     │ │
│ │ - Foreground Notif. │◄┼─┼─┤ - ChatEntity       │ │ │ │ - AES/GCM/NoPad   │ │
│ │ - Cancel Action     │ │ │ │ - MessageEntity    │ │ │ │ - EncryptedPrefs  │ │
│ │ - Tool Call Loop    │ │ │ │ - VfsNodeEntity    │ │ │ └─────────┬─────────┘ │
│ │ - Auto MAX_TOKENS   │ │ │ │ - ModelEntity      │ │ │           ▼           │
│ └──────────┬──────────┘ │ │ └─────────┬──────────┘ │ │ ┌───────────────────┐ │
└────────────┼────────────┘ │           │            │ │ │ VertexOAuthClient │ │
             │              │ ┌─────────▼──────────┐ │ │ │ - RS256 JWT Sign  │ │
             │              │ │ StreamedVfsStorage │ │ │ │ - Token RAM Cache │ │
             │              │ │ (filesDir/vfs/...) │ │ │ └─────────┬─────────┘ │
             │              │ └────────────────────┘ │ └───────────┼───────────┘
             ▼              └───────────▲────────────┘             │
┌───────────────────────────────────────┴──────────────────────────▼───────────┐
│                   AI & SÍŤOVÁ VRSTVA (OkHttp + SSE + Tools)                  │
│  - VertexAiClient (:streamGenerateContent?alt=sse & ListModels API)          │
│  - VertexSseParser (Thought deltas, Text deltas, FunctionCalls, FinishReason)│
│  - VfsToolExecutor (createFolder, writeFile, readFile, list, move, delete)   │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Datový model a stav (Room + VFS + DataStore)

1. **`ChatEntity` (Room)**: `id` (UUID), `title`, `selectedModelId`, `systemPrompt`, `createdAt`, `updatedAt`.
2. **`MessageEntity` (Room)**: `id` (UUID), `chatId` (FK s kaskádovým mazáním), `role` (`USER`, `MODEL`, `TOOL`), `content` (průběžně aktualizovaný Markdown text), `thoughtSummary` (volitelný text myšlenkového postupu), `toolCallsJson` (seznam vykonaných VFS operací a jejich výsledků pro vykreslení Tool-Use karet), `status` (`STREAMING`, `COMPLETED`, `CANCELLED`, `ERROR`), `continuationCount` (počet automatických navázání při `MAX_TOKENS`), `timestamp`.
3. **`VfsNodeEntity` (Room)**: `id` (UUID), `chatId` (izolace VFS per chat), `parentId` (nullable pro kořen), `name`, `virtualPath` (např. `/src/main/App.kt`), `physicalRelativePath` (cesta v `filesDir/vfs/{chatId}/...`), `isDirectory`, `sizeBytes`, `mimeType`, `lineCount`, `updatedAt`.
4. **`AiModelEntity` (Room)**: `modelId` (např. `gemini-3.1-pro-preview`, `gemini-2.5-pro`, `gemini-2.5-flash`, `gemini-3.5-flash`), `displayName`, `description`, `supportsThinking`, `maxOutputTokens`, `isAvailable`.
5. **`Vault & Preferences` (EncryptedSharedPreferences + DataStore)**:
   - V šifrovaném úložišti: zašifrovaný Service Account JSON payload, sůl (salt), IV vektor a kontrolní součet pro ověření hesla při odemčení.
   - V DataStore: `projectId`, `region` (výchozí `us-central1`), `authMode` (`VERTEX_SERVICE_ACCOUNT` nebo `GEMINI_API_KEY`), `defaultModelId`, `enableThinking` (`true`), `temperature`.

### Mapování interaktivních komponent a stavových přechodů

- **Trezor a autentizace (`AuthSettingsScreen`)**:
  - Vložení JSONu nebo načtení souboru přes `ActivityResultContracts.OpenDocument` -> validace povinných polí (`project_id`, `private_key`, `client_email`) -> zašifrování heslem a uložení -> okamžité otestování generování tokenu a načtení seznamu modelů.
- **Konverzační obrazovka (`ChatScreen`)**:
  - Odeslání zprávy -> zápis uživatelské zprávy a prázdné `STREAMING` zprávy asistenta do Room -> spuštění `AiGenerationWorker` s unikátním tagem chatu -> živé pozorování `Flow<List<MessageEntity>>` v UI.
  - Během čekání na první token se zobrazuje animovaný indikátor *„Přemýšlím…“*. Při příchodu `ThoughtDelta` se plynule plní rozbalovací karta myšlenek.
  - Při zavolání nástroje (`functionCall`) worker okamžitě provede operaci nad `StreamedVfsStorage`, zapíše stav do `toolCallsJson` v `MessageEntity` (v UI se objeví **Tool-Use karta** se stavem a tlačítky **Zobrazit** / **Stáhnout**) a odešle `functionResponse` zpět modelu v rámci téhož běhu.
  - Tlačítko **Zastavit** v chatu i v systémové notifikaci zruší běžící `WorkRequest` podle ID a uloží dosavadní vygenerovaný text jako `CANCELLED`.
- **Virtuální souborový systém (`VfsExplorerScreen` & `VfsFileSheet`)**:
  - Strom složek udržuje množinu rozbalených uzlů (`expandedNodeIds`).
  - Otevření souboru streamuje řádky přes `Flow` / stránkovaný buffer do virtualizovaného `LazyColumn`, umožňuje filtrovat řádky podle hledaného výrazu, upravit obsah v editoru a exportovat soubor přes `ActivityResultContracts.CreateDocument` nebo sdílet přes `FileProvider`.
