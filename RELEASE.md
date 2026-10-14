# Releases assinadas e atualizações

A cada push ou merge na `main`, o CI realiza testes unitários, Android Lint e build de validação. Depois, se **todos os quatro secrets de assinatura** existirem, gera um APK **release assinado**, verifica sua assinatura e publica automaticamente uma release estável `v0.1.<GITHUB_RUN_NUMBER>` no GitHub. Se faltarem secrets, o CI de validação permanece funcional, mas **nenhuma release nova é publicada**, evitando distribuir APKs debug incompatíveis.

## Etapa manual única: criar e guardar a assinatura

Guarde em local seguro o arquivo .jks, as duas senhas e o alias; se perder a chave, não poderá continuar atualizando os APKs existentes pela mesma identidade. Não envie chave nem senhas para o chat ou faça commit.

No terminal de sua confiança, com JDK instalado, gere a chave UMA vez:

```bash
keytool -genkeypair -v -keystore portal-release.jks -alias portal -keyalg RSA -keysize 3072 -validity 10000
```

O comando solicitará a senha do keystore e os dados da chave. Para gerar o conteúdo do secret, execute:

```bash
base64 -w 0 portal-release.jks
```

Em macOS ou sistemas sem `-w`, utilize `base64 < portal-release.jks | tr -d '\\n'`.

Abra **Settings → Secrets and variables → Actions → New repository secret** no GitHub e adicione:

- `ANDROID_KEYSTORE_BASE64` — conteúdo Base64 integral do arquivo JKS.
- `ANDROID_KEYSTORE_PASSWORD` — senha do arquivo JKS.
- `ANDROID_KEY_ALIAS` — `portal` (ou o alias escolhido).
- `ANDROID_KEY_PASSWORD` — senha da chave privada; pode ser igual à do JKS se você escolheu assim.

Não existe criação automática da chave no CI: isso trocaria a identidade digital a cada build e quebraria as atualizações.

## Primeira migração

As versões antigas foram distribuídas com certificado debug diferente. Após fazer backup das configurações necessárias, **desinstale a versão antiga uma única vez** e instale a primeira release estável gerada com a chave permanente. Nas próximas versões, preserve o mesmo arquivo JKS, alias e senhas; o `applicationId` deve continuar `br.com.portalwifi` e o `versionCode` deve aumentar. O Android continuará solicitando confirmação ao instalar a atualização.

No GitHub, após configurar os secrets, acesse **Actions → Android CI e Releases → Run workflow** (branch main). Aguarde os jobs `verify` e `publish` terminarem com sucesso, então instale o novo APK da aba Releases.

## Segurança

Este repositório é público: nunca armazene o keystore nem as senhas no código, em artefatos de CI ou em Issues. O APK gerado nas releases assinadas será público. O botão de atualização consulta também as pré-releases antigas; instale somente as releases assinadas depois da migração. Não há instalação silenciosa.
