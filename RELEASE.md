# Distribuição automática

A cada push/merge na branch `main`, o workflow roda testes unitários, Android Lint e build. **Somente após tudo passar** ele cria automaticamente uma prévia em GitHub Releases com APK e SHA256SUMS. Pull requests apenas validam, sem publicar.

A primeira publicação usa o próximo número de execução, com versão `v0.1.<GITHUB_RUN_NUMBER>`. Nenhuma tag precisa ser criada manualmente.

## Limitações importantes

- O APK publicado agora é **debug/prévia**, porque não há chave de assinatura estável configurada. O certificado debug da máquina CI pode mudar: instalar por cima de outra versão pode falhar. Para atualização confiável, é obrigatório assinar todas as versões com a mesma chave.
- As prévias aparecem em Releases, mas a API `/releases/latest` do GitHub não retorna pré-releases; o botão de atualização no app não as detectará.
- Para o canal estável, configurar `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, alterar o pipeline para build release assinado e publicar sem `--prerelease`.
- Nunca publique uma chave de assinatura no repositório. Instalação silenciosa não é suportada.
