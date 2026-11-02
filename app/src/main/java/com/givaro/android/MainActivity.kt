// 1. No teu onCreate, adicionas o WebView oculto e as configurações necessárias:
val hiddenWebView = findViewById<WebView>(R.id.hiddenWebView)
hiddenWebView.settings.javaScriptEnabled = true
hiddenWebView.settings.mediaPlaybackRequiresUserGesture = false

// HTML do motor de som oculto
val htmlContent = """
    <!DOCTYPE html>
    <html>
    <head>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
            var player;
            function onYouTubeIframeAPIReady() {
                player = new YT.Player('player', {
                    height: '1',
                    width: '1',
                    videoId: 'jfKfPfyJRdk', // ID da música a tocar
                    playerVars: { 'controls': 0, 'autoplay': 1 }
                });
            }
            function playMusic() { if(player) player.playVideo(); }
            function pauseMusic() { if(player) player.pauseVideo(); }
        </script>
    </head>
    <body style="background:black;">
        <div id="player"></div>
    </body>
    </html>
""".trimIndent()

hiddenWebView.loadDataWithBaseURL("https://www.youtube.com", htmlContent, "text/html", "UTF-8", null)

// 2. Conectas os teus botões de Play/Pause que já tens no layout para comandar esse motor:
btnPlay.setOnClickListener {
    hiddenWebView.evaluateJavascript("playMusic();", null)
}

btnPause.setOnClickListener {
    hiddenWebView.evaluateJavascript("pauseMusic();", null)
}
