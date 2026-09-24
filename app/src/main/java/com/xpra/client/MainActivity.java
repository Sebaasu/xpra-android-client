package com.xpra.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private FloatingActionButton fabKeyboard;
    private SharedPreferences prefs;
    private static final String PREF_SERVER_URL = "server_url";
    private static final String DEFAULT_URL = "http://100.94.216.124:9876";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("XpraPrefs", MODE_PRIVATE);
        webView = findViewById(R.id.webView);
        fabKeyboard = findViewById(R.id.fabKeyboard);

        setupWebView();
        setupControls();

        String url = prefs.getString(PREF_SERVER_URL, null);
        if (url == null) {
            promptServerUrl();
        } else {
            loadXpra(url);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Desactivar comportamientos de arrastre y rebote del navegador
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectMobileOptimizations();
            }
        });
    }

    private void injectMobileOptimizations() {
        // Inyecta CSS y JS para:
        // 1. Eliminar barras de scroll y desbordes del navegador
        // 2. Prevenir el zoom por doble toque accidental
        // 3. Forzar el modo pantalla completa del canvas
        String js = "javascript:(function() {" +
                "  var meta = document.querySelector('meta[name=viewport]');" +
                "  if (!meta) {" +
                "    meta = document.createElement('meta');" +
                "    meta.name = 'viewport';" +
                "    document.getElementsByTagName('head')[0].appendChild(meta);" +
                "  }" +
                "  meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';" +
                "  document.body.style.overflow = 'hidden';" +
                "  document.body.style.touchAction = 'none';" +
                "})()";
        webView.loadUrl(js);
    }

    private void setupControls() {
        // Toggle teclado virtual de Android
        fabKeyboard.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        });

        // Mantener presionado el botón flotante para cambiar la IP/URL
        fabKeyboard.setOnLongClickListener(v -> {
            promptServerUrl();
            return true;
        });
    }

    private void promptServerUrl() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Servidor Xpra");
        builder.setMessage("Ingresa la URL de Tailscale (ej: http://100.94.216.124:9876)");

        final EditText input = new EditText(this);
        String currentUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_URL);
        input.setText(currentUrl);
        builder.setView(input);

        builder.setPositiveButton("Conectar", (dialog, which) -> {
            String newUrl = input.getText().toString().trim();
            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                newUrl = "http://" + newUrl;
            }
            prefs.edit().putString(PREF_SERVER_URL, newUrl).apply();
            loadXpra(newUrl);
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void loadXpra(String url) {
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
