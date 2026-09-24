package ph.philcord.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final int VPN_REQUEST_CODE = 100;
    private static final int MEDIA_PERMISSION_CODE = 101;
    private static final String DISCORD_URL = "https://discord.com/app";
    private static final String MOBILE_VERSION = "0.1.1";
    private static final Pattern CHROME_VERSION = Pattern.compile("(?:Chrome|Chromium)/([0-9.]+)");

    private WebView webView;
    private TextView statusText;
    private Button toggleButton;
    private ProgressBar progressBar;
    private boolean discordLoaded;
    private boolean receiverRegistered;
    private boolean vpnPromptShown;
    private PermissionRequest pendingMediaRequest;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!SecureDnsVpnService.ACTION_STATUS.equals(intent.getAction())) return;
            updateStatus(intent.getBooleanExtra(SecureDnsVpnService.EXTRA_CONNECTED, false));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        toggleButton = findViewById(R.id.toggleButton);
        progressBar = findViewById(R.id.progressBar);
        webView = findViewById(R.id.webView);

        configureWebView();
        toggleButton.setOnClickListener(view -> {
            if (SecureDnsVpnService.isRunning()) {
                stopSecureDns();
            } else {
                requestAndStartSecureDns();
            }
        });

        updateStatus(SecureDnsVpnService.isRunning());
        if (!SecureDnsVpnService.isRunning()) {
            webView.post(this::requestAndStartSecureDns);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(SecureDnsVpnService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        receiverRegistered = true;
        updateStatus(SecureDnsVpnService.isRunning());
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void configureWebView() {
        webView.setBackgroundColor(Color.rgb(49, 51, 56));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(makeDesktopChromeUserAgent(settings.getUserAgentString()));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        if (Build.VERSION.SDK_INT >= 26) WebView.startSafeBrowsing(this, null);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                view.evaluateJavascript(
                        "Object.defineProperty(navigator,'platform',{get:()=> 'Win32'});"
                                + "Object.defineProperty(navigator,'maxTouchPoints',{get:()=> 0});",
                        null
                );
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setVisibility(newProgress == 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }
        });
    }

    private static String makeDesktopChromeUserAgent(String webViewUserAgent) {
        Matcher matcher = CHROME_VERSION.matcher(webViewUserAgent);
        String chromeVersion = matcher.find() ? matcher.group(1) : "124.0.0.0";
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + chromeVersion
                + " Safari/537.36 PhilCordMobile/"
                + MOBILE_VERSION;
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        List<String> androidPermissions = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                androidPermissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                androidPermissions.add(Manifest.permission.CAMERA);
            }
        }

        if (androidPermissions.isEmpty()) {
            grantApprovedWebResources(request);
            return;
        }

        pendingMediaRequest = request;
        requestPermissions(androidPermissions.toArray(new String[0]), MEDIA_PERMISSION_CODE);
    }

    private void grantApprovedWebResources(PermissionRequest request) {
        List<String> approved = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                approved.add(resource);
            }
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                approved.add(resource);
            }
        }
        if (approved.isEmpty()) request.deny();
        else request.grant(approved.toArray(new String[0]));
    }

    private void requestAndStartSecureDns() {
        if (vpnPromptShown) return;
        Intent permissionIntent = VpnService.prepare(this);
        if (permissionIntent != null) {
            vpnPromptShown = true;
            startActivityForResult(permissionIntent, VPN_REQUEST_CODE);
        } else {
            startSecureDns();
        }
    }

    private void startSecureDns() {
        Intent intent = new Intent(this, SecureDnsVpnService.class).setAction(SecureDnsVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
        statusText.setText("Starting encrypted DNS…");
    }

    private void stopSecureDns() {
        startService(new Intent(this, SecureDnsVpnService.class).setAction(SecureDnsVpnService.ACTION_STOP));
    }

    private void updateStatus(boolean connected) {
        if (connected) {
            statusText.setText("Secure DNS connected");
            toggleButton.setText("Disconnect");
            if (!discordLoaded) {
                discordLoaded = true;
                progressBar.setVisibility(View.VISIBLE);
                webView.loadUrl(DISCORD_URL);
            }
        } else {
            statusText.setText("Secure DNS disconnected");
            toggleButton.setText("Connect");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VPN_REQUEST_CODE) return;
        vpnPromptShown = false;
        if (resultCode == RESULT_OK) startSecureDns();
        else updateStatus(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSION_CODE && pendingMediaRequest != null) {
            PermissionRequest request = pendingMediaRequest;
            pendingMediaRequest = null;
            grantApprovedWebResources(request);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (pendingMediaRequest != null) pendingMediaRequest.deny();
        webView.destroy();
        super.onDestroy();
    }
}
