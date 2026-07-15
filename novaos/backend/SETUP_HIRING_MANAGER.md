# One-time approval-role setup

The setup runner is disabled during normal NovaOS startup. From PowerShell in
`novaos/backend`, execute:

```powershell
$env:NOVAOS_SETUP_HIRING_MANAGER_ENABLED = "true"
$securePassword = Read-Host "Approval-role password" -AsSecureString
$env:NOVAOS_SETUP_HIRING_MANAGER_PASSWORD = [Net.NetworkCredential]::new("", $securePassword).Password
$env:NOVAOS_SETUP_HIRING_MANAGER_EXIT_AFTER_RUN = "true"
$env:SERVER_PORT = "0"
.\mvnw.cmd spring-boot:run
```

The process provisions the following accounts with the supplied password:
`rahul.manager@nova.com` (`HIRING_MANAGER`), `finance@nova.com` (`FINANCE`),
and `legal@nova.com` (`LEGAL`). It prints each Firebase Authentication UID,
Firestore document path, and whether each record was created, reused, or updated. With
`NOVAOS_SETUP_HIRING_MANAGER_EXIT_AFTER_RUN=true`, it closes automatically.

Clear the temporary setup variables afterward:

```powershell
Remove-Item Env:NOVAOS_SETUP_HIRING_MANAGER_ENABLED -ErrorAction SilentlyContinue
Remove-Item Env:NOVAOS_SETUP_HIRING_MANAGER_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:NOVAOS_SETUP_HIRING_MANAGER_EXIT_AFTER_RUN -ErrorAction SilentlyContinue
Remove-Item Env:SERVER_PORT -ErrorAction SilentlyContinue
```

Running the command again is safe. Each Authentication account is looked up by
email and reused, its password is reset to the password supplied for this
one-time command, and the account is re-enabled. Each Firestore document uses
the Authentication UID and only missing fields are added.
