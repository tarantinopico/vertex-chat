# Vertex Chat (Android • Kotlin • Jetpack Compose • Vertex AI & VFS)

**Vertex Chat** je osobní nativní Android aplikace postavená na **100% Kotlinu**, **Jetpack Compose (Material 3 AMOLED)**, **Room**, **DataStore**, **WorkManageru** a přímém napojení na **Google Cloud Vertex AI** (přes šifrovaný Service Account JSON a OAuth2) i **Gemini API**.

---

## Klíčové vlastnosti

1. **Dvouvrstvý šifrovaný trezor Service Accountu (`KeystoreServiceAccountVault`)**:
   - Vložený nebo vybraný soubor `service-account.json` je zašifrován pomocí hesla uživatele (`PBKDF2WithHmacSHA256` + `AES/GCM/NoPadding`) a následně obalen hardwarovým klíčem z **Android Keystore** a uložen do `EncryptedSharedPreferences`.
   - OAuth2 Bearer tokeny se podepisují lokálně pomocí `SHA256withRSA` (`RS256` JWT assertion), cachují výhradně v RAM paměti a automaticky obnovují 5 minut před expirací.
   - Aplikace rovněž podporuje okamžité přepnutí na **Gemini API klíč** (`BuildConfig.GEMINI_API_KEY` ze Secrets panelu v AI Studiu nebo vlastní klíč) pro rychlé testování.
2. **Izolovaný Virtuální souborový systém (VFS) pro každý chat**:
   - Každá konverzace má vlastní pískoviště v `filesDir/vfs/{chatId}` a strom metadat v Room databázi (`VfsNodeEntity`).
   - Veškerý zápis a čtení velkých souborů probíhá streamovaně (`FileOutputStream` / `BufferedReader`).
   - Podporuje AI Function Calling nástroje (`createFolder`, `writeFile`, `readFile`, `listFiles`, `deleteFile`, `moveFile`, `renameFile`) s interaktivními **Tool-Use kartami** přímo v konverzaci.
   - Samostatná obrazovka **Virtuální soubory (VFS)** s rozbalovacím stromem složek, modálním prohlížečem řádků s čísly řádků, vyhledáváním v obsahu, ručním editorem a exportem přes **SAF (`ACTION_CREATE_DOCUMENT`)** nebo **FileProvider (`ACTION_SEND`)**.
3. **Práce na pozadí a automatické pokračování (`AiGenerationWorker`)**:
   - Každý požadavek běží jako `CoroutineWorker` přes WorkManager s Foreground notifikací (`FOREGROUND_SERVICE_TYPE_DATA_SYNC` pro Android 14+) a tlačítkem **Zrušit**.
   - Při `finishReason == "MAX_TOKENS"` worker automaticky navazuje dalším dotazem a plynule připojuje text do Room databáze.
4. **AMOLED Design & Thinking Mode**:
   - Čistě černé pozadí (`#000000`), karty v odstínech `#0E0E0E`–`#1A1A1A`, akcent **Electric Blue (`#3B82F6`)** a **Soft Violet (`#8B5CF6`)**.
   - Podpora `ThinkingConfig` (`includeThoughts = true`) s animovaným indikátorem *„Přemýšlím…“* a rozbalovacím panelem **Myšlenkový postup modelu** nad odpovědí.

---

## Nastavení Google Cloud Service Accountu pro Vertex AI

1. Otevřete [Google Cloud Console](https://console.cloud.google.com/) a vyberte svůj projekt.
2. Povolte **Vertex AI API** (`aiplatform.googleapis.com`).
3. Přejděte do **IAM & Admin → Service Accounts** a klikněte na **Create Service Account**.
4. Přiřaďte roli **Vertex AI User** (`roles/aiplatform.user`).
5. V záložce **Keys** zvolte **Add Key → Create new key → JSON** a uložte soubor `.json` do telefonu.
6. V aplikaci **Vertex Chat** otevřete **Autentizace & Trezor**, vyberte soubor `.json` (nebo vložte jeho text), zvolte heslo pro zašifrování a klikněte na **Zašifrovat do Keystore a aktivovat OAuth2**.

---

## Jak aplikaci sestavit (Build)

### 1. Lokální build (příkazová řádka)

```bash
# Spuštění unit a Robolectric testů (VFS, SSE parser, Keystore Vault)
gradle :app:testDebugUnitTest

# Sestavení Debug APK
gradle :app:assembleDebug

# Výstupní APK najdete v:
# app/build/outputs/apk/debug/app-debug.apk
```

### 2. GitHub Actions CI/CD (`.github/workflows/build-apk.yml`)

Pro automatické podepisování Release APK a publikování GitHub Releases nastavte v repozitáři (**Settings → Secrets and variables → Actions**) tyto 4 secrets:

- `KEYSTORE_BASE64`: Výstup příkazu `base64 -w 0 release-keystore.jks`
- `KEYSTORE_PASSWORD`: Heslo k JKS úložišti
- `KEY_ALIAS`: Alias klíče v JKS
- `KEY_PASSWORD`: Heslo ke klíči
