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
import android.widget.ImageView;
import android.widget.ToggleButton;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private View touchpadOverlay;
    private ImageView virtualCursorIcon;
    private View specialKeyBar;
    private ImageButton btnToggleKeys;
    private ImageButton btnKeyboard;
    private ToggleButton toggleCtrl;
    private ToggleButton toggleAlt;

    private SharedPreferences prefs;
    private static final String PREF_SERVER_URL = "server_url";
    private static final String DEFAULT_URL = "http://100.94.216.124:9876";

    // Posición del cursor en pantalla
    private float cursorX = 300;
    private float cursorY = 300;
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
        virtualCursorIcon = findViewById(R.id.virtualCursorIcon);
        specialKeyBar = findViewById(R.id.specialKeyBar);
        btnToggleKeys = findViewById(R.id.btnToggleKeys);
        btnKeyboard = findViewById(R.id.btnKeyboard);
        toggleCtrl = findViewById(R.id.toggleCtrl);
        toggleAlt = findViewById(R.id.toggleAlt);

        setupWebView();
        setupTouchpad();
        setupSpecialKeys();
        setupControls();

        // Posicionar cursor inicial
        updateCursorVisualPosition();

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
        // Enlazar eventos directamente con el cliente Xpra interno (client.do_window_mouse_move)
        String js = "javascript:(function() {" +
                "  document.body.style.overflow = 'hidden';" +
                "  document.body.style.touchAction = 'none';" +
                "  window.__sendXpraKey = function(keyName, ctrl, alt) {" +
                "    var c = window.client;" +
                "    var mods = [];" +
                "    if (ctrl) mods.push('control');" +
                "    if (alt) mods.push('mod1');" +
                "    if (c && c.send) {" +
                "      c.send(['key-action', 0, keyName, true, mods, 0, keyName, 0]);" +
                "      setTimeout(function(){ c.send(['key-action', 0, keyName, false, mods, 0, keyName, 0]); }, 40);" +
                "    }" +
                "  };" +
                "  window.__dispatchMouse = function(x, y, btn, isDown) {" +
                "    var c = window.client;" +
                "    var mods = [];" +
                "    if (c && c.send) {" +
                "      var btns = isDown ? [btn] : [];" +
                "      c.send(['button-action', 0, btn, isDown, [x, y], mods]);" +
                "    } else {" +
                "      var el = document.elementFromPoint(x, y) || document.body;" +
                "      var type = isDown ? 'mousedown' : 'mouseup';" +
                "      el.dispatchEvent(new MouseEvent(type, {clientX: x, clientY: y, button: btn-1, bubbles: true}));" +
                "      if (!isDown) el.dispatchEvent(new MouseEvent('click', {clientX: x, clientY: y, button: btn-1, bubbles: true}));" +
                "    }" +
                "  };" +
                "  window.__dispatchMove = function(x, y) {" +
                "    var c = window.client;" +
                "    if (c && c.send) {" +
                "      c.send(['pointer-position', 0, [x, y], []]);" +
                "    }" +
                "  };" +
                "  window.__dispatchWheel = function(x, y, up) {" +
                "    var c = window.client;" +
                "    var btn = up ? 4 : 5;" +
                "    if (c && c.send) {" +
                "      c.send(['button-action', 0, btn, true, [x, y], []]);" +
                "      c.send(['button-action', 0, btn, false, [x, y], []]);" +
                "    }" +
                "  };" +
                "})()";
        webView.loadUrl(js);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchpad() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Tap 1 dedo = Clic izquierdo en la posición del cursor
                triggerClick(1);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // Long press = Clic derecho
                triggerClick(3);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                triggerClick(1);
                return true;
            }
        });

        touchpadOverlay.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);

            int pointerCount = event.getPointerCount();
            int action = event.getActionMasked();

            if (pointerCount == 1) {
                float x = event.getX();
                float y = event.getY();

                if (action == MotionEvent.ACTION_DOWN) {
                    lastTouchX = x;
                    lastTouchY = y;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float dx = (x - lastTouchX) * 1.6f;
                    float dy = (y - lastTouchY) * 1.6f;

                    cursorX = Math.max(0, Math.min(touchpadOverlay.getWidth(), cursorX + dx));
                    cursorY = Math.max(0, Math.min(touchpadOverlay.getHeight(), cursorY + dy));

                    lastTouchX = x;
                    lastTouchY = y;

                    updateCursorVisualPosition();
                    sendMoveToXpra((int) cursorX, (int) cursorY);
                }
            } else if (pointerCount == 2) {
                // Dos dedos = scroll de rueda
                if (action == MotionEvent.ACTION_MOVE) {
                    float y = event.getY(0);
                    float dy = y - lastTouchY;
                    lastTouchY = y;
                    if (Math.abs(dy) > 12) {
                        sendWheelToXpra((int) cursorX, (int) cursorY, dy > 0);
                    }
                } else if (action == MotionEvent.ACTION_POINTER_UP) {
                    // Tap con 2 dedos = Clic derecho
                    triggerClick(3);
                }
            }
            return true;
        });
    }

    private void updateCursorVisualPosition() {
        virtualCursorIcon.setX(cursorX);
        virtualCursorIcon.setY(cursorY);
    }

    private void triggerClick(int button) {
        int x = (int) cursorX;
        int y = (int) cursorY;
        String jsDown = String.format("javascript:window.__dispatchMouse(%d, %d, %d, true);", x, y, button);
        String jsUp = String.format("javascript:window.__dispatchMouse(%d, %d, %d, false);", x, y, button);
        webView.loadUrl(jsDown);
        webView.postDelayed(() -> webView.loadUrl(jsUp), 40);
    }

    private void sendMoveToXpra(int x, int y) {
        String js = String.format("javascript:window.__dispatchMove(%d, %d);", x, y);
        webView.loadUrl(js);
    }

    private void sendWheelToXpra(int x, int y, boolean up) {
        String js = String.format("javascript:window.__dispatchWheel(%d, %d, %b);", x, y, up);
        webView.loadUrl(js);
    }

    private void setupSpecialKeys() {
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

            if (ctrl) toggleCtrl.setChecked(false);
            if (alt) toggleAlt.setChecked(false);
        });
    }

    private void setupControls() {
        btnToggleKeys.setOnClickListener(v -> {
            if (specialKeyBar.getVisibility() == View.VISIBLE) {
                specialKeyBar.setVisibility(View.GONE);
            } else {
                specialKeyBar.setVisibility(View.VISIBLE);
            }
        });

        btnKeyboard.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        });

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
