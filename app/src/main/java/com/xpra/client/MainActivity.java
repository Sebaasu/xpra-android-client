package com.xpra.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ToggleButton;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private View touchpadOverlay;
    private View specialKeyBar;
    private ImageButton btnToggleKeys;
    private ImageButton btnKeyboard;
    private ToggleButton toggleCtrl;
    private ToggleButton toggleAlt;

    private SharedPreferences prefs;
    private static final String PREF_SERVER_URL = "server_url";
    private static final String DEFAULT_URL = "http://100.94.216.124:9876";

    // Variables de cursor virtual
    private float virtualCursorX = 500;
    private float virtualCursorY = 500;
    private float lastTouchX = 0;
    private float lastTouchY = 0;
    private GestureDetector gestureDetector;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("XpraPrefs", MODE_PRIVATE);
        webView = findViewById(R.id.webView);
        touchpadOverlay = findViewById(R.id.touchpadOverlay);
        specialKeyBar = findViewById(R.id.specialKeyBar);
        btnToggleKeys = findViewById(R.id.btnToggleKeys);
        btnKeyboard = findViewById(R.id.btnKeyboard);
        toggleCtrl = findViewById(R.id.toggleCtrl);
        toggleAlt = findViewById(R.id.toggleAlt);

        setupWebView();
        setupTouchpad();
        setupSpecialKeys();
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

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectMobileScript();
            }
        });
    }

    private void injectMobileScript() {
        // Script inyectado que maneja cursor virtual nativo, clics y teclado
        String js = "javascript:(function() {" +
                "  document.body.style.overflow = 'hidden';" +
                "  document.body.style.touchAction = 'none';" +
                "  window.__sendXpraKey = function(keyName, ctrl, alt) {" +
                "    var client = window.client || (window.Xpra && window.Xpra.client);" +
                "    if (client && client.send_key_action) {" +
                "      var modifiers = [];" +
                "      if (ctrl) modifiers.push('control');" +
                "      if (alt) modifiers.push('mod1');" +
                "      client.send_key_action(keyName, true, modifiers);" +
                "      setTimeout(function(){ client.send_key_action(keyName, false, modifiers); }, 50);" +
                "    } else {" +
                "      var ev = new KeyboardEvent('keydown', {key: keyName, bubbles: true, ctrlKey: ctrl, altKey: alt});" +
                "      document.dispatchEvent(ev);" +
                "      setTimeout(function(){ document.dispatchEvent(new KeyboardEvent('keyup', {key: keyName, bubbles: true, ctrlKey: ctrl, altKey: alt})); }, 50);" +
                "    }" +
                "  };" +
                "  window.__sendMouseAction = function(x, y, btn, isDown) {" +
                "    var client = window.client || (window.Xpra && window.Xpra.client);" +
                "    if (client && client.send_mouse_action) {" +
                "      client.send_mouse_action(x, y, btn, isDown);" +
                "    } else {" +
                "      var target = document.elementFromPoint(x, y) || document.body;" +
                "      var type = isDown ? 'mousedown' : 'mouseup';" +
                "      var ev = new MouseEvent(type, {clientX: x, clientY: y, button: btn, bubbles: true});" +
                "      target.dispatchEvent(ev);" +
                "      if (!isDown) target.dispatchEvent(new MouseEvent('click', {clientX: x, clientY: y, button: btn, bubbles: true}));" +
                "    }" +
                "  };" +
                "  window.__sendWheelAction = function(deltaY) {" +
                "    var ev = new WheelEvent('wheel', {deltaY: deltaY, bubbles: true});" +
                "    document.dispatchEvent(ev);" +
                "  };" +
                "})()";
        webView.loadUrl(js);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchpad() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Toque 1 dedo = Clic izquierdo
                simulateClick(1);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // Toque sostenido = Clic derecho
                simulateClick(3);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Doble toque = Clic izquierdo rápido
                simulateClick(1);
                return true;
            }
        });

        touchpadOverlay.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);

            int pointerCount = event.getPointerCount();
            int action = event.getActionMasked();

            if (pointerCount == 1) {
                // Movimiento relativo de 1 dedo = Mover cursor del mouse
                float x = event.getX();
                float y = event.getY();

                if (action == MotionEvent.ACTION_DOWN) {
                    lastTouchX = x;
                    lastTouchY = y;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float dx = (x - lastTouchX) * 1.5f;
                    float dy = (y - lastTouchY) * 1.5f;

                    virtualCursorX = Math.max(0, Math.min(webView.getWidth(), virtualCursorX + dx));
                    virtualCursorY = Math.max(0, Math.min(webView.getHeight(), virtualCursorY + dy));

                    lastTouchX = x;
                    lastTouchY = y;

                    sendMouseMove((int) virtualCursorX, (int) virtualCursorY);
                }
            } else if (pointerCount == 2) {
                // 2 dedos = Scroll vertical / rueda del ratón
                if (action == MotionEvent.ACTION_MOVE) {
                    float y = event.getY(0);
                    float dy = y - lastTouchY;
                    lastTouchY = y;
                    if (Math.abs(dy) > 10) {
                        sendMouseWheel(dy > 0 ? -120 : 120);
                    }
                } else if (action == MotionEvent.ACTION_POINTER_UP) {
                    // Si se levantan 2 dedos rápidamente = Clic derecho
                    simulateClick(3);
                }
            }
            return true;
        });
    }

    private void simulateClick(int button) {
        int x = (int) virtualCursorX;
        int y = (int) virtualCursorY;
        String jsDown = String.format("javascript:window.__sendMouseAction(%d, %d, %d, true);", x, y, button);
        String jsUp = String.format("javascript:window.__sendMouseAction(%d, %d, %d, false);", x, y, button);
        webView.loadUrl(jsDown);
        webView.postDelayed(() -> webView.loadUrl(jsUp), 50);
    }

    private void sendMouseMove(int x, int y) {
        String js = String.format("javascript:if(window.client&&window.client.send_mouse_position){window.client.send_mouse_position(%d,%d);}", x, y);
        webView.loadUrl(js);
    }

    private void sendMouseWheel(int delta) {
        String js = String.format("javascript:window.__sendWheelAction(%d);", delta);
        webView.loadUrl(js);
    }

    private void setupSpecialKeys() {
        // Enlazar teclas especiales
        bindKey(findViewById(R.id.btnEsc), "Escape");
        bindKey(findViewById(R.id.btnTab), "Tab");
        bindKey(findViewById(R.id.btnSuper), "Super_L");
        bindKey(findViewById(R.id.btnUp), "Up");
        bindKey(findViewById(R.id.btnDown), "Down");
        bindKey(findViewById(R.id.btnLeft), "Left");
        bindKey(findViewById(R.id.btnRight), "Right");
        bindKey(findViewById(R.id.btnEnter), "Return");
    }

    private void bindKey(Button btn, String keyName) {
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            boolean ctrl = toggleCtrl.isChecked();
            boolean alt = toggleAlt.isChecked();
            String js = String.format("javascript:window.__sendXpraKey('%s', %b, %b);", keyName, ctrl, alt);
            webView.loadUrl(js);

            // Desactivar modificadores de un solo uso
            if (ctrl) toggleCtrl.setChecked(false);
            if (alt) toggleAlt.setChecked(false);
        });
    }

    private void setupControls() {
        // Mostrar / Ocultar barra superior de teclas especiales
        btnToggleKeys.setOnClickListener(v -> {
            if (specialKeyBar.getVisibility() == View.VISIBLE) {
                specialKeyBar.setVisibility(View.GONE);
            } else {
                specialKeyBar.setVisibility(View.VISIBLE);
            }
        });

        // Alternar teclado nativo de Android
        btnKeyboard.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        });

        // Mantener presionado para cambiar URL
        btnKeyboard.setOnLongClickListener(v -> {
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
        if (specialKeyBar.getVisibility() == View.VISIBLE) {
            specialKeyBar.setVisibility(View.GONE);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
