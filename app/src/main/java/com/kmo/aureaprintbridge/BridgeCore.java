package com.kmo.aureaprintbridge;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BridgeCore {
    public interface Logger { void log(String message); }

    public static final String PREFS = "bridge_config";
    public static final String ACTION_START = "com.kmo.aureaprintbridge.START";
    public static final String ACTION_STOP = "com.kmo.aureaprintbridge.STOP";
    public static final String ACTION_ONCE = "com.kmo.aureaprintbridge.ONCE";
    public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final Charset PRINTER_CHARSET = Charset.forName("CP437");

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getOrCreateDeviceId(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String id = p.getString("device_id", "");
        if (id == null || id.trim().isEmpty()) {
            id = "aurea-" + UUID.randomUUID().toString();
            p.edit().putString("device_id", id).apply();
        }
        return id;
    }

    public static boolean hasBluetoothPermission(Context ctx) {
        return Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public static String normalizeArea(String area) {
        if (area == null) return "";
        String a = area.trim().toLowerCase(Locale.US)
                .replace(" ", "_")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u");
        if (a.equals("fria") || a.equals("barrafria") || a.equals("barra-fria")) return "barra_fria";
        if (a.equals("caliente") || a.equals("barracaliente") || a.equals("barra-caliente")) return "barra_caliente";
        if (a.equals("bebida")) return "bebidas";
        if (a.equals("ticket") || a.equals("cuenta") || a.equals("recibo")) return "caja";
        return a;
    }

    public static String labelForArea(String area) {
        String a = normalizeArea(area);
        if (a.equals("barra_fria")) return "BARRA FRÍA";
        if (a.equals("barra_caliente")) return "BARRA CALIENTE";
        if (a.equals("bebidas")) return "BEBIDAS";
        if (a.equals("caja")) return "CAJA";
        return a.toUpperCase(Locale.US);
    }

    public static String macForArea(SharedPreferences p, String area) {
        return p.getString(normalizeArea(area), "").trim();
    }

    public static List<String> configuredAreas(SharedPreferences p) {
        ArrayList<String> areas = new ArrayList<>();
        if (!macForArea(p, "barra_fria").isEmpty()) areas.add("barra_fria");
        if (!macForArea(p, "barra_caliente").isEmpty()) areas.add("barra_caliente");
        if (!macForArea(p, "bebidas").isEmpty()) areas.add("bebidas");
        if (!macForArea(p, "caja").isEmpty()) areas.add("caja");
        return areas;
    }

    public static int pollAndPrintOnce(Context ctx, Logger logger) throws Exception {
        SharedPreferences p = prefs(ctx);
        String base = p.getString("api_base", "").trim();
        String token = p.getString("token", "").trim();
        String branchId = p.getString("branch_id", "").trim();
        String deviceId = getOrCreateDeviceId(ctx);
        boolean claimMode = p.getBoolean("claim_mode", true);
        if (base.isEmpty() || token.isEmpty() || branchId.isEmpty()) {
            logger.log("Falta API base, token o branch_id.");
            return 0;
        }
        JSONArray jobs = claimMode ? fetchClaimedJobs(base, token, branchId, deviceId, configuredAreas(p), logger) : new JSONArray();
        if (jobs.length() == 0 && !claimMode) jobs = fetchPendingJobs(base, token, branchId);
        if (jobs.length() == 0) return 0;

        logger.log("Trabajos para imprimir: " + jobs.length());
        int printed = 0;
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject job = jobs.getJSONObject(i);
            String id = String.valueOf(job.opt("id"));
            String area = normalizeArea(job.optString("printer_area", job.optString("area", "")));
            String mac = macForArea(p, area);
            if (mac.isEmpty()) {
                String msg = "Sin impresora configurada para área: " + area;
                markJob(base, token, id, "error", msg, deviceId);
                logger.log(msg + " job=" + id);
                continue;
            }
            markJob(base, token, id, "printing", "", deviceId);
            try {
                String title = job.optString("title", "COMANDA");
                String text = job.optString("content", job.optString("text", ""));
                if (text.trim().isEmpty()) text = job.toString(2);
                byte[] bytes = buildEscPosTicket(labelForArea(area), title, text.split("\\r?\\n"));
                printBytes(ctx, mac, bytes);
                markJob(base, token, id, "printed", "", deviceId);
                logger.log("Impreso job=" + id + " área=" + area);
                printed++;
            } catch (Exception e) {
                markJob(base, token, id, "error", e.getMessage(), deviceId);
                logger.log("Error imprimiendo job=" + id + ": " + e.getMessage());
            }
        }
        return printed;
    }

    private static JSONArray fetchClaimedJobs(String base, String token, String branchId, String deviceId, List<String> areas, Logger logger) throws Exception {
        String url = trimSlash(base) + "/api/print-jobs/claim?token=" + enc(token);
        JSONObject payload = new JSONObject();
        payload.put("branch_id", branchId);
        payload.put("device_id", deviceId);
        payload.put("areas", new JSONArray(areas));
        payload.put("max_jobs", 10);
        payload.put("client_time", now());
        byte[] data = payload.toString().getBytes("UTF-8");

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        OutputStream os = conn.getOutputStream();
        os.write(data);
        os.flush();
        os.close();
        int code = conn.getResponseCode();
        String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code == 404 || code == 405) {
            logger.log("Tu backend aún no tiene /api/print-jobs/claim. Usando GET antiguo: puede duplicar si hay varios puentes.");
            return fetchPendingJobs(base, token, branchId);
        }
        if (code < 200 || code >= 300) throw new Exception("CLAIM HTTP " + code + ": " + body);
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) return new JSONArray(trimmed);
        JSONObject obj = new JSONObject(trimmed);
        JSONArray jobs = obj.optJSONArray("jobs");
        return jobs == null ? new JSONArray() : jobs;
    }

    private static JSONArray fetchPendingJobs(String base, String token, String branchId) throws Exception {
        String url = trimSlash(base) + "/api/print-jobs?branch_id=" + enc(branchId) + "&token=" + enc(token);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) throw new Exception("GET print-jobs HTTP " + code + ": " + body);
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) return new JSONArray(trimmed);
        JSONObject obj = new JSONObject(trimmed);
        JSONArray jobs = obj.optJSONArray("jobs");
        return jobs == null ? new JSONArray() : jobs;
    }

    public static void markJob(String base, String token, String id, String status, String error, String deviceId) {
        if (id == null || id.equals("null") || id.isEmpty()) return;
        try {
            String url = trimSlash(base) + "/api/print-jobs/" + enc(id) + "/status?token=" + enc(token);
            JSONObject payload = new JSONObject();
            payload.put("status", status);
            payload.put("error", error == null ? "" : error);
            payload.put("device_id", deviceId == null ? "" : deviceId);
            payload.put("device_time", now());
            byte[] data = payload.toString().getBytes("UTF-8");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            OutputStream os = conn.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }

    public static void printBytes(Context ctx, String mac, byte[] bytes) throws Exception {
        if (!hasBluetoothPermission(ctx)) throw new Exception("Falta permiso Bluetooth.");
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) throw new Exception("Este dispositivo no tiene Bluetooth.");
        if (!adapter.isEnabled()) throw new Exception("Bluetooth está apagado.");
        BluetoothDevice device = adapter.getRemoteDevice(mac);
        BluetoothSocket socket = null;
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
        } catch (Exception first) {
            closeQuiet(socket);
            try {
                Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                socket = (BluetoothSocket) m.invoke(device, 1);
                socket.connect();
            } catch (Exception second) {
                closeQuiet(socket);
                throw new Exception("No conectó con " + mac + ". " + second.getMessage());
            }
        }
        try {
            OutputStream os = socket.getOutputStream();
            os.write(bytes);
            os.flush();
            sleepQuiet(250);
        } finally {
            closeQuiet(socket);
        }
    }

    public static byte[] buildEscPosTicket(String areaLabel, String title, String[] lines) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, new byte[]{0x1B, 0x40});
        write(out, new byte[]{0x1B, 0x61, 0x01});
        write(out, new byte[]{0x1B, 0x45, 0x01});
        writeText(out, areaLabel + "\n");
        writeText(out, title + "\n");
        write(out, new byte[]{0x1B, 0x45, 0x00});
        writeText(out, "------------------------------\n");
        write(out, new byte[]{0x1B, 0x61, 0x00});
        for (String line : lines) writeText(out, sanitize(line) + "\n");
        writeText(out, "\nHora: " + now() + "\n\n\n");
        write(out, new byte[]{0x1D, 0x56, 0x42, 0x00});
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) { try { out.write(bytes); } catch (Exception ignored) {} }
    private static void writeText(ByteArrayOutputStream out, String s) { write(out, s.getBytes(PRINTER_CHARSET)); }
    public static String sanitize(String s) {
        if (s == null) return "";
        return s.replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U")
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replace("ñ", "n").replace("Ñ", "N");
    }
    public static String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }
    public static void sleepQuiet(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    private static String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
    private static String trimSlash(String s) { String out = s == null ? "" : s.trim(); while (out.endsWith("/")) out = out.substring(0, out.length() - 1); return out; }
    private static String readAll(InputStream is) throws Exception { if (is == null) return ""; ByteArrayOutputStream bos = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n; while ((n = is.read(buf)) >= 0) bos.write(buf, 0, n); return bos.toString("UTF-8"); }
    private static void closeQuiet(BluetoothSocket socket) { if (socket != null) try { socket.close(); } catch (Exception ignored) {} }
}
