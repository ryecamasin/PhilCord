package ph.philcord.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

public final class SecureDnsVpnService extends VpnService {
    static final String ACTION_START = "ph.philcord.mobile.START_SECURE_DNS";
    static final String ACTION_STOP = "ph.philcord.mobile.STOP_SECURE_DNS";
    static final String ACTION_STATUS = "ph.philcord.mobile.SECURE_DNS_STATUS";
    static final String EXTRA_CONNECTED = "connected";

    private static final String TAG = "PhilCordDns";
    private static final String CHANNEL_ID = "philcord_secure_dns";
    private static final int NOTIFICATION_ID = 21;
    private static final String VPN_ADDRESS = "10.111.0.2";
    private static final String DNS_ADDRESS = "10.111.0.1";
    private static final String[] DOH_ENDPOINTS = {
            "https://1.1.1.1/dns-query",
            "https://8.8.8.8/dns-query"
    };

    private static volatile boolean running;
    private volatile boolean stopping;
    private ParcelFileDescriptor tunnel;
    private Thread worker;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTunnel();
            return START_NOT_STICKY;
        }
        if (!running) startTunnel();
        return START_STICKY;
    }

    private void startTunnel() {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        Builder builder = new Builder()
                .setSession("PhilCord Secure DNS")
                .setMtu(1500)
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .addRoute(DNS_ADDRESS, 32)
                .setBlocking(true);
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false);

        try {
            builder.addAllowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException impossible) {
            stopSelf();
            return;
        }

        tunnel = builder.establish();
        if (tunnel == null) {
            stopSelf();
            return;
        }

        stopping = false;
        running = true;
        broadcastStatus(true);
        worker = new Thread(this::runPacketLoop, "PhilCord-DNS");
        worker.start();
    }

    private void runPacketLoop() {
        byte[] buffer = new byte[32767];
        try (FileInputStream input = new FileInputStream(tunnel.getFileDescriptor());
             FileOutputStream output = new FileOutputStream(tunnel.getFileDescriptor())) {
            while (!stopping) {
                int length = input.read(buffer);
                if (length <= 0) continue;
                DnsPacket packet = DnsPacket.parse(buffer, length);
                if (packet == null) continue;

                byte[] response;
                try {
                    response = packet.buildResponse(resolveOverHttps(packet.dnsMessage()));
                } catch (Exception error) {
                    Log.w(TAG, "Encrypted DNS request failed", error);
                    response = packet.buildServerFailure();
                }
                output.write(response);
            }
        } catch (Exception error) {
            if (!stopping) Log.e(TAG, "Secure DNS tunnel stopped unexpectedly", error);
        } finally {
            stopTunnel();
        }
    }

    private byte[] resolveOverHttps(byte[] query) throws Exception {
        Exception lastError = null;
        for (String endpoint : DOH_ENDPOINTS) {
            HttpsURLConnection connection = null;
            try {
                connection = (HttpsURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Accept", "application/dns-message");
                connection.setRequestProperty("Content-Type", "application/dns-message");
                connection.setFixedLengthStreamingMode(query.length);
                try (OutputStream requestBody = connection.getOutputStream()) {
                    requestBody.write(query);
                }

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("DoH server returned " + connection.getResponseCode());
                }
                String contentType = connection.getContentType();
                if (contentType == null || !contentType.toLowerCase().startsWith("application/dns-message")) {
                    throw new IllegalStateException("Unexpected DoH content type");
                }
                return readFully(connection.getInputStream());
            } catch (Exception error) {
                lastError = error;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw lastError == null ? new IllegalStateException("No DoH endpoint configured") : lastError;
    }

    private static byte[] readFully(InputStream input) throws Exception {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[2048];
            int count;
            while ((count = input.read(chunk)) != -1) {
                output.write(chunk, 0, count);
                if (output.size() > 65535) throw new IllegalStateException("DNS response is too large");
            }
            return output.toByteArray();
        }
    }

    private Notification buildNotification() {
        PendingIntent openApp = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent disconnect = PendingIntent.getService(
                this,
                1,
                new Intent(this, SecureDnsVpnService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.philcord_icon)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.secure_dns_active))
                .setContentIntent(openApp)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "Disconnect", disconnect).build())
                .build();
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.secure_dns_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Shows when PhilCord encrypted DNS is active");
        manager.createNotificationChannel(channel);
    }

    private synchronized void stopTunnel() {
        if (stopping) return;
        stopping = true;
        running = false;
        broadcastStatus(false);
        if (tunnel != null) {
            try {
                tunnel.close();
            } catch (Exception ignored) {
            }
            tunnel = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void broadcastStatus(boolean connected) {
        Intent status = new Intent(ACTION_STATUS)
                .setPackage(getPackageName())
                .putExtra(EXTRA_CONNECTED, connected);
        sendBroadcast(status);
    }

    @Override
    public void onRevoke() {
        stopTunnel();
        super.onRevoke();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopTunnel();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }
}
