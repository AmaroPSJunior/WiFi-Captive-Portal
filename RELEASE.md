# Releases assinadas e atualizações

O GitHub Actions valida os testes unitários, Android Lint e a compilação após cada push ou merge na `main`. Se os **dois Secrets** de assinatura estiverem configurados, também produz um APK release assinado com a chave permanente, verifica a assinatura e publica a versão automaticamente. Se faltarem, os testes continuam, mas a publicação é omitida para não distribuir um APK incompatível.

## Configuração manual única (dois Secrets)

Crie **uma única chave de assinatura** em um local seguro, com JDK instalado. O alias deve ser `portal` e a senha da chave privada deve ser **a mesma senha do keystore**:

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore portal-release.jks -alias portal -keyalg RSA -keysize 3072 -validity 10000
```

Quando solicitado, escolha e guarde a senha. No formato PKCS12, use a mesma senha para o arquivo e a chave. Guarde o arquivo `portal-release.jks` em backup seguro: perdê-lo impossibilita assinar futuras atualizações com a mesma identidade.

Converta o arquivo para Base64 **sem quebras de linha**:

- Linux: `base64 -w 0 portal-release.jks`
- macOS: `base64 < portal-release.jks | tr -d '\\n'`

Abra [Secrets do repositório](https://github.com/AmaroPSJunior/WiFi-Captive-Portal/settings/secrets/actions) e cadastre somente:

1. `WIFI_PORTAL_KEYSTORE` — texto Base64 completo do arquivo `.jks`.
2. `WIFI_PORTAL_PASSWORD` — senha escolhida para o keystore e a chave privada.

O alias `portal` está fixado no Gradle; a senha da chave privada é reutilizada a partir de `WIFI_PORTAL_PASSWORD`. **Não publique o keystore ou senhas no GitHub, Issues, logs ou chat.** É possível manter os dois Secrets antigos de alias e senha da chave cadastrados sem uso, mas eles não são mais necessários.

## Primeira instalação e atualizações

Após cadastrar os Secrets, vá em **Actions → Android CI e Releases → Run workflow**, selecione `main` e execute. Confirme que os jobs `verify` e `publish` terminaram com sucesso e baixe o APK na aba Releases.

As versões anteriores podem usar certificados de teste diferentes. Nesse caso, faça backup das configurações e **desinstale apenas uma vez** a versão antiga antes de instalar a primeira release com a chave permanente. As próximas atualizações devem manter o mesmo `applicationId` (`br.com.portalwifi`), o mesmo keystore e um `versionCode` crescente. O Android ainda exigirá confirmação de instalação; não existe atualização silenciosa em aplicativos comuns.

Se já existir uma chave de produção usada para este mesmo aplicativo, **reutilize-a em vez de gerar outra**, desde que seu alias seja `portal` e ela permita a mesma senha de armazenamento e da chave. Trocar a chave exige reinstalação e pode causar perda de dados locais.
