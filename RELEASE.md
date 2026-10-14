# Build, versões e releases

- Push em main e pull requests executam testes unitários, lint e compilação debug no GitHub Actions.
- O APK debug aparece como artefato da execução, não como release.
- Uma tag no formato `vX.Y.Z` executa pipeline e, após verificar testes, publica APK assinado em GitHub Releases.
- Segredos necessários no repositório: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
- Use a **mesma chave** em todas as versões para permitir atualização mantendo os dados. Nunca versione a chave privada.
- `versionCode` usa `GITHUB_RUN_NUMBER` (ou `APP_VERSION_CODE`) e precisa ser crescente entre builds instaláveis.
- A verificação de atualizações consulta a última release pública. Se o repositório for privado, o aplicativo precisará de uma estratégia autenticada de atualização; não inclua tokens GitHub no APK.
- Atualização é download + instalação confirmada pelo usuário; instalação silenciosa não é suportada.
