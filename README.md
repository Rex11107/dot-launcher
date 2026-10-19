# Dot Launcher

Launcher Android minimale in stile dot-matrix (a puntini), scritto da zero.

*English below.*

## Funzioni

- **Due stili**: Classico (puntini e caratteri a matrice) e Moderno (numeri sottili e vetro), tema scuro, grigio, chiaro o automatico.
- **Oltre 30 widget a puntini**: orologi, Riepilogo (data, meteo e prossimo evento), meteo (Open-Meteo, senza account), calendario, batteria, musica, contapassi con la distanza percorsa, bussola, fasi lunari, note, contatori, tempo di utilizzo, focus e altri. Si possono impilare e scorrere in verticale.
- **Widget di sistema** delle app installate, ridimensionabili.
- **Dot Sveglia** (facoltativa): sveglia con alba simulata sullo schermo, luce e suono che crescono insieme, suoni ambientali generati dall'app (mare, pioggia, campanelle) o file audio, posticipo e prova breve. Il widget Sveglia può usare questa, l'Orologio di sistema o un'altra app.
- **Cassetto a sezioni**: le app vengono divise per tipo, con sezioni rinominabili, contenitori, cartelle sulla home con copertina che può aprire direttamente un'app, ordine libero, alfabetico o per app più usate, uso a una mano e sezione Lavoro per il profilo di lavoro.
- **Ricerca universale**: sveglie e timer ("sveglia 7:30", "timer 10 min"), app, anche quelle nascoste se lo scegli, scorciatoie, impostazioni del telefono, contatti, eventi, calcolatrice, convertitore di unità e motori web rispettosi della privacy (DuckDuckGo predefinito).
- **Pagina a sinistra**: chat con un'IA (Claude, ChatGPT, Gemini, OpenRouter o un servizio compatibile), notizie da feed RSS, calendario, attività e note.
- **Personalizzazione**: colore d'accento, forma e dimensione delle icone, pacchetti di icone, nomi e icone delle app, righe della home, dock, carattere, animazioni di pagine e menu.
- **Impostazioni a categorie** con ricerca, e trascinamento laterale per tornare indietro.
- **Sfondi animati a puntini**: buco nero, meteo dal vivo (pioggia, neve, sole o stelle in base al meteo) e onda; si fermano quando non si vedono, per la batteria.
- **Orologio in carica**: orologio a puntini a tutto schermo quando colleghi il caricatore (facoltativo).
- **Gesti configurabili** (anche a due dita e pizzico; scorrendo in su su un'icona si può aprire un'azione o un widget dell'app), pallini di notifica personalizzabili (punto o numero, dimensione, angolo, colore) e anteprima delle notifiche.
- **Benessere digitale**: tempo di utilizzo, limiti giornalieri, pausa prima di aprire, modalità focus.
- **Privacy**: app nascoste e singole app bloccate con impronta, volto o PIN; le chiavi API restano solo sul telefono e sono escluse dai backup; nessun server, nessuna statistica.
- **Condivisione del layout**: un file con solo aspetto e disposizione, senza dati personali, da mandare ad altri; chi lo riceve può tornare al proprio layout.
- **Trasparenza sull'IA**: una pagina mostra a chi va ogni domanda e cosa viene inviato.
- Italiano e inglese, configurazione guidata al primo avvio, backup della configurazione su file, registro locale degli errori.

## Permessi

Ogni permesso serve a una funzione precisa e quasi tutti si chiedono solo quando la usi.

- **Internet**: meteo, notizie RSS, assistente IA (solo se lo configuri), controllo degli aggiornamenti.
- **Posizione approssimativa**: il meteo della tua zona.
- **Calendario e contatti**: widget Calendario e Riepilogo, ricerca di eventi e contatti.
- **Attività fisica**: contapassi.
- **Accesso ai dati di utilizzo** e **accesso alle notifiche** (da concedere a mano): benessere digitale, pallini, anteprima delle notifiche e casella dei messaggi.
- **Foto, video e audio** (solo se attivi "File nella ricerca"): trovare i file per nome nella ricerca.
- **Impronta o PIN**: app nascoste e app bloccate.
- **Installazione e rimozione di app**: aggiornamenti dell'app e voce Disinstalla.
- **Solo per Dot Sveglia**: sveglie esatte, notifiche e schermo intero sopra il blocco, servizio in primo piano mentre suona, vibrazione, riattivazione dopo il riavvio ed esclusione dall'ottimizzazione della batteria. Se usi la sveglia di sistema non servono.

Nessun dato lascia il telefono, tranne le richieste al servizio meteo, ai feed che scegli, al servizio IA che configuri e a GitHub per controllare gli aggiornamenti.

## Installazione

1. Scarica l'ultima versione: https://github.com/Rex11107/dot-launcher/releases/latest/download/DotLauncher.apk
2. Installala (Android 8 o successivo) e scegli Dot Launcher come app Home quando la configurazione guidata lo propone.
3. Su Honor e Huawei: Impostazioni › Batteria › Avvio app › Dot Launcher › Gestisci manualmente, con tutti gli interruttori attivi. Se il trascinamento laterale nelle impostazioni non funziona bene, spegni il dock in Impostazioni › Funzioni di accessibilità › Multi-finestra.

Gli aggiornamenti si installano sopra la versione precedente: home e impostazioni restano.

## Segnalare un problema

Impostazioni › Informazioni › Segnala un problema: copia il registro degli errori (salvato solo sul telefono) e incollalo in una [segnalazione](https://github.com/Rex11107/dot-launcher/issues).

## Compilazione

Java Android senza librerie esterne (minSdk 26, targetSdk 34). Ogni push su `main` compila l'APK con GitHub Actions e pubblica una release. Le pull request verso `main` producono una pre-release di prova, che non diventa l'ultima versione. Per firmare con una chiave propria servono i segreti `KEYSTORE_B64` e `KEYSTORE_PASSWORD`; senza, la build usa una chiave temporanea.

## Licenza

Dot Launcher è software libero, distribuito con licenza [GNU GPL versione 3](LICENSE): puoi usarlo, studiarlo, modificarlo e ridistribuirlo, a patto che anche le versioni modificate restino libere con la stessa licenza.

La logica di Dot Sveglia riprende [Fossify Clock](https://github.com/FossifyOrg/Clock) (programmazione e servizio della sveglia) e FakeDawn (l'idea dell'alba sullo schermo), entrambi liberi con licenza GPL-3.0. I suoni ambientali sono generati dal codice, senza file di terzi.

I caratteri Geist, Space Mono e Doto sono distribuiti con licenza SIL Open Font License 1.1 (vedi `app/src/main/assets/fonts`). Meteo: [Open-Meteo](https://open-meteo.com/).

---

## English

Minimal dot-matrix Android launcher, written from scratch. Classic and Modern styles, 30+ dotted widgets with stacks (including a Summary widget and a step counter with distance), an optional sunrise alarm (Dot Sveglia), a sectioned app drawer with a Work section, home folders whose cover can open an app, universal search, a left page with AI chat, RSS news, calendar, tasks and notes, configurable gestures, customizable notification badges, digital wellbeing tools, hidden and locked apps protected by fingerprint or PIN, and no servers or analytics. Every permission is tied to a feature and most are only requested when you use it; the alarm permissions are only needed for Dot Sveglia. Italian and English. The alarm builds on Fossify Clock and FakeDawn (GPL-3.0).

Download: https://github.com/Rex11107/dot-launcher/releases/latest/download/DotLauncher.apk · License: GPL-3.0.
