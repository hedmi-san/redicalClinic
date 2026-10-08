# Guide Opérationnel — Déploiement Réseau Local Sans Internet (LAN Offline)
**Cabinet Nour El Islam — Système de Gestion Clinique (ERP)**

Ce guide détaille la mise en service, la configuration du routeur WiFi du cabinet et le dépannage de la liaison sans fil entre le poste du médecin et le poste de la réception.

---

## 1. Vue d'Ensemble de l'Architecture

- **Poste Principal (Médecin / Serveur)** :
  - Ordinateur portable du médecin.
  - Héberge la base de données centrale chiffrée (H2 AES).
  - Démarre automatiquement le serveur interne (port TCP `9092`) et le phare de détection (port UDP `8888`).
  - Effectue automatiquement des sauvegardes compressées dans `~/clinic/backups/`.
  
- **Poste Secondaire (Réception / Client)** :
  - Ordinateur de la réceptionniste (compte employé).
  - Détecte automatiquement l'adresse IP du médecin au démarrage sans saisie manuelle.
  - Ne crée aucune base locale pour éviter toute désynchronisation ou divergence de données.

---

## 2. Configuration du Routeur WiFi du Cabinet

Le système fonctionne en réseau local autonome sans connexion Internet. Toutefois, certains routeurs activent par défaut une sécurité appelée **Isolation des clients (AP Isolation / Station Isolation)** qui empêche les ordinateurs connectés au même WiFi de communiquer entre eux.

### Comment vérifier et désactiver l'Isolation AP :
1. Connectez-vous à la page d'administration du routeur WiFi (généralement `192.168.1.1` ou `192.168.0.1` depuis un navigateur).
2. Allez dans la section **Sans fil / Wi-Fi** -> **Paramètres Avancés** (Wireless Advanced Settings).
3. Recherchez l'option :
   - `AP Isolation` / `Client Isolation` / `Isolation de point d'accès`
   - `Station Isolation` / `Guest Network Isolation`
4. Assurez-vous que cette option est **DÉSACTIVÉE (Disabled)**.
5. Vérifiez que les deux ordinateurs sont connectés au **réseau Wi-Fi principal** du cabinet, et non à un réseau « Invité » (Guest Network).

---

## 3. Configuration du Réseau Windows (Réseau Privé)

Windows Defender applique des restrictions strictes si la connexion est considérée comme un « Réseau public ».

### Définir le réseau comme Réseau Privé :
Sur les **deux ordinateurs** :
1. Ouvrez les **Paramètres Windows** (Touche Windows + `I`).
2. Allez dans **Réseau et Internet** -> **Wi-Fi**.
3. Cliquez sur le nom de votre réseau Wi-Fi de la clinique.
4. Sous **Type de profil réseau**, cochez impérativement **Réseau privé** (Private Network).

---

## 4. Autorisation du Pare-feu Windows Defender

Les ports utilisés par RedicalClinic sont :
- **TCP 9092** : Connexions à la base de données centrale H2.
- **UDP 8888** : Découverte automatique instantanée du poste principal.

### Configuration automatique en 1 clic :
Dans le dossier d'installation de RedicalClinic (ou le dossier d'installation généré) :
1. Faites un clic droit sur le fichier `configure-firewall.bat`.
2. Choisissez **Exécuter en tant qu'administrateur**.
3. Le script configure automatiquement les règles d'autorisation entrantes pour le profil Réseau Privé.

### Configuration manuelle via PowerShell (si nécessaire) :
Ouvrez PowerShell en mode administrateur et exécutez :
```powershell
netsh advfirewall firewall add rule name="RedicalClinic-H2" dir=in action=allow protocol=TCP localport=9092 enable=yes profile=private
netsh advfirewall firewall add rule name="RedicalClinic-Discovery" dir=in action=allow protocol=UDP localport=8888 enable=yes profile=private
```

---

## 5. Gestion de l'Alimentation du PC Médecin (Éviter la Mise en Veille)

Le PC du médecin étant le serveur central, si l'ordinateur portable se met en veille ou si l'écran se ferme, la réception perdra temporairement l'accès à la base de données.

### Configuration recommandée sur le PC Médecin :
1. Ouvrez le **Panneau de configuration** -> **Options d'alimentation**.
2. Cliquez sur **Choisir l'action qui suit la fermeture du capot**.
3. Réglez **Lorsque je referme le capot** sur : **Ne rien faire** (sur secteur).
4. Réglez le délai de **Mise en veille** sur secteur à une durée prolongée (ex: 2 heures ou Jamais).

---

## 6. Sauvegardes Automatiques et Sécurité des Données

- **Sauvegardes automatiques** : À chaque fermeture de l'application sur le PC médecin, une archive compressée `.zip` de la base centrale est générée dans :
  `C:\Users\<NomUtilisateur>\clinic\backups\clinic_backup_YYYYMMDD_HHMMSS.zip`
- **Sauvegarde externe recommandée** : Il est recommandé de copier périodiquement ce dossier `backups` sur une clé USB dédiée pour se prémunir contre toute panne matérielle du PC portable.
- **Fichier SQLite historique** : L'ancien fichier `clinic.db` reste conservé intact dans le dossier `clinic` à titre de copie de sécurité historique.

---

## 7. Dépannage Rapide (Checklist)

| Symptôme | Cause probable | Solution |
| :--- | :--- | :--- |
| **"En attente du poste du médecin..." reste affiché sur le PC réception** | Le PC du médecin n'est pas allumé ou l'application n'est pas lancée. | Allumez le PC du médecin, vérifiez qu'il est connecté au Wi-Fi et lancez RedicalClinic. |
| **Les deux PC sont connectés mais ne se trouvent pas** | Le Wi-Fi est configuré en "Réseau public" ou l'isolation AP est activée. | Basculez le Wi-Fi en "Réseau privé" dans Windows et vérifiez les options du routeur Wi-Fi (désactiver AP Isolation). |
| **Bannière rouge "Connexion avec le poste principal perdue" en cours de session** | Le PC médecin s'est mis en veille ou le signal Wi-Fi a faibli. | Rallumez le PC médecin. La reconnexion s'effectue automatiquement sans perte de données saisies. |
