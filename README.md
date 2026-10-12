# Dot Launcher

Launcher Android minimale in stile dot-matrix, ispirato all'estetica Nothing ma scritto da zero: nessun codice, carattere, sfondo o marchio di Nothing.

*English below.*

## Funzioni

- **Due stili**: Classico (puntini e caratteri a matrice) e Moderno (numeri sottili e vetro), tema scuro, grigio, chiaro o automatico.
- **Oltre 30 widget a puntini**: orologi, meteo (Open-Meteo, senza account), calendario, batteria, musica, contapassi, bussola, fasi lunari, note, contatori, tempo di utilizzo, focus e altri. Si possono impilare e scorrere in verticale.
- **Widget di sistema** delle app installate, ridimensionabili.
- **Cassetto a sezioni**: le app vengono divise per tipo, con sezioni rinominabili, contenitori, ordine libero o alfabetico e sezione Lavoro per il profilo di lavoro.
- **Ricerca universale**: app, scorciatoie, impostazioni del telefono, contatti, eventi, calcolatrice, convertitore di unità e motori web rispettosi della privacy (DuckDuckGo predefinito).
- **Pagina a sinistra**: chat con un'IA (Claude, ChatGPT, Gemini, OpenRouter o un servizio compatibile), notizie da feed RSS, calendario, attività e note.
- **Personalizzazione**: colore d'accento, forma e dimensione delle icone, pacchetti di icone, nomi e icone delle app, righe della home, dock, carattere, animazioni di pagine e menu.
- **Gesti configurabili** (anche a due dita e pizzico), pallini di notifica e anteprima delle notifiche.
- **Benessere digitale**: tempo di utilizzo, limiti giornalieri, pausa prima di aprire, modalità focus.
- **Privacy**: app nascoste protette da impronta o PIN; le chiavi API restano solo sul telefono e sono escluse dai backup; nessun server, nessuna statistica.
- Italiano e inglese, configurazione guidata al primo avvio, backup della configurazione su file, registro locale degli errori.

## Installazione

1. Scarica l'ultima versione: https://github.com/Rex11107/dot-launcher/releases/latest/download/DotLauncher.apk
2. Installala (Android 8 o successivo) e scegli Dot Launcher come app Home quando la configurazione guidata lo propone.
3. Su Honor e Huawei: Impostazioni › Batteria › Avvio app › Dot Launcher › Gestisci manualmente, con tutti gli interruttori attivi.

Gli aggiornamenti si installano sopra la versione precedente: home e impostazioni restano.

## Segnalare un problema

Impostazioni › Informazioni › Segnala un problema: copia il registro degli errori (salvato solo sul telefono) e incollalo in una [segnalazione](https://github.com/Rex11107/dot-launcher/issues).

## Compilazione

Java Android senza librerie esterne (minSdk 26, targetSdk 34). Ogni push su `main` compila l'APK con GitHub Actions e pubblica una release. Per firmare con una chiave propria servono i segreti `KEYSTORE_B64` e `KEYSTORE_PASSWORD`; senza, la build usa una chiave temporanea.

## Licenza

Dot Launcher è software libero, distribuito con licenza [GNU GPL versione 3](LICENSE): puoi usarlo, studiarlo, modificarlo e ridistribuirlo, a patto che anche le versioni modificate restino libere con la stessa licenza.

I caratteri Geist, Space Mono e Doto sono distribuiti con licenza SIL Open Font License 1.1 (vedi `app/src/main/assets/fonts`). Meteo: [Open-Meteo](https://open-meteo.com/).

---

## English

Minimal dot-matrix Android launcher inspired by the Nothing look, written from scratch (no Nothing code, fonts, wallpapers or trademarks). Classic and Modern styles, 30+ dotted widgets with stacks, a sectioned app drawer with a Work section, universal search, a left page with AI chat, RSS news, calendar, tasks and notes, configurable gestures, digital wellbeing tools, protected hidden apps and no servers or analytics. Italian and English.

Download: https://github.com/Rex11107/dot-launcher/releases/latest/download/DotLauncher.apk · License: GPL-3.0.
