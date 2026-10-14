# Wi-Fi Captive Portal

Aplicativo Android para criar rede Wi-Fi local isolada e hospedar um portal HTTP, com configuração de limite de velocidade do próprio servidor e tempo de sessão.

## Escopo real do MVP

Usa `WifiManager.startLocalOnlyHotspot()` (Android 10+). **Não fornece acesso à internet aos clientes**, portanto não permite desbloquear seletivamente sites externos nem controlar a banda da internet compartilhada. O SSID e a senha são gerados pelo Android e a abertura automática de portal cativo não é garantida. O controle de velocidade e duração aplica-se apenas ao servidor web local.

## Próximos passos

Código Kotlin, testes e GitHub Actions serão adicionados separadamente. Releases assinadas exigem segredos da assinatura; nunca coloque a chave no repositório.
