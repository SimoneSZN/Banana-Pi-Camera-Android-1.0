# Banana Pi Camera Control Android V3.16 (progetto sorgente)

Questa versione riprende la logica del programma Windows V3.16: connessione SSH alla Banana Pi su 192.168.4.1, stato del ciclo e dell’avvio automatico, START/STOP, enable/disable boot, LIVE PREVIEW MJPEG 640x480 solo a ciclo fermo, conteggio file/spazio, download su Android con indicatore di avanzamento, cancellazione foto/video/entrambi, riavvio e spegnimento.

## Compilazione APK con GitHub Actions
1. Crea un repository GitHub **privato**.
2. Carica il CONTENUTO della cartella BananaPiCameraAndroid, inclusa .github/workflows/build-apk.yml.
3. Vai in Actions > Build Android APK > Run workflow (oppure attendi il push).
4. A build verde scarica l'artifact BananaPiCamera-debug-apk, estrai app-debug.apk, e installalo sul telefono Android.

## Utilizzo
- Collega il telefono dalle impostazioni Wi-Fi Android alla rete BANANAPI_CAMERA. Se Android avverte che la rete non ha Internet, scegli di rimanere connesso.
- Inserisci IP, utente e password SSH della Banana Pi.
- Premi STATO SERVIZIO E AGGIORNA prima di usare gli altri comandi.
- Il controllo dell'avvio automatico modifica systemd, non avvia né ferma immediatamente il ciclo.
- La preview è consentita solo quando camera-cycle.service è inactive/failed.
- I comandi sudo richiedono che l'utente possa eseguire systemctl con la password SSH.
- I download vengono salvati sotto Download/BananaPiCamera/{photos,video}; la barra misura i byte trasferiti nella selezione. I file già scaricati vengono saltati quando coincide la dimensione.
- La cancellazione è irreversibile, richiede conferma e viene impedita quando il ciclo non è fermo.
- Spegnimento e riavvio interrompono il Wi-Fi; lo spegnimento non toglie fisicamente alimentazione.

## Stato del progetto
Codice sorgente predisposto; build e test con hardware reale ancora da effettuare. Non è un APK precompilato. Per maggiore sicurezza, conferma la chiave SSH al primo accesso.
