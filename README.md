# Banana Pi Camera — app Android (versione iniziale)

App **nativa Java Android**, progettata a partire dal programma desktop `bananapi_camera_control_FINALE_313_LIVE_PREVIEW.py`.

## Funzioni
- Collegamento SSH a `192.168.4.1`, utente `simone` (password richiesta, non salvata).
- Verifica stato `camera-cycle.service`.
- START/STOP del ciclo tramite `sudo systemctl`.
- Conteggio foto e video e spazio libero sulla microSD.
- Live preview MJPEG 640x480 / 10 fps; **solo se il servizio è inattivo**.
- Scaricamento foto e video su `Download/BananaPiCamera/photos` e `Download/BananaPiCamera/video` tramite SFTP, evitando file con stesso nome e dimensione.
- Memorizzazione della chiave pubblica del server SSH dopo approvazione iniziale.

## Come generare APK, senza Android Studio
1. Crea un repository GitHub **privato** e carica tutti i file del progetto, mantenendo le cartelle.
2. Apri `Actions` -> `Build Android APK` -> `Run workflow`, oppure esegui push su `main`.
3. Al termine, apri l'esecuzione e scarica `BananaPiCamera-debug-apk` sotto `Artifacts`.
4. Estrai `app-debug.apk`, trasferiscilo su Android e installalo consentendo l'installazione dall'origine scelta.

La build richiede che GitHub Actions possa scaricare Android SDK, Gradle, plugin Android e JSch.

## Uso
1. Connetti manualmente il telefono alla rete Wi-Fi `BANANAPI_CAMERA`. Android può chiedere di restare connesso a una rete **senza Internet**: conferma.
2. Apri l'app, inserisci la password SSH e premi `VERIFICA CONNESSIONE`.
3. Al primo collegamento, verifica che il fingerprint SSH mostrato sia quello della tua Banana Pi prima di accettarlo. Se la chiave cambia in futuro, la connessione verrà rifiutata.
4. Usa STOP per interrompere il ciclo; solo allora premi `LIVE PREVIEW`.
5. Chiudi la preview prima di premere START.

## Prerequisiti e limiti
- Il servizio deve chiamarsi `camera-cycle.service`.
- La camera deve essere `/dev/video1` e supportare MJPEG 640x480 10 fps.
- `ffmpeg` deve essere presente sulla Banana Pi.
- Il comando `sudo systemctl` richiede che l'utente SSH abbia i permessi sudo; si usa la password SSH anche per sudo. Se le credenziali sono diverse, serve adattare questo comando.
- La preview usa un canale SSH video dedicato e viene chiusa prima di riavviare il ciclo.
- Un solo dispositivo dovrebbe controllare la camera per volta; non vi è arbitraggio con il programma Windows.
- Il download di file molto grandi attraverso Wi-Fi 2.4 GHz può richiedere tempo. Il download incrementale considera file con stesso nome e dimensione.
- Primo prototipo: non ancora testato su un telefono e sulla Banana Pi fisica.
