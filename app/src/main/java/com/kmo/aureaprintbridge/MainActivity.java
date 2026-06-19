package com.kmo.aureaprintbridge;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 1801;

    private EditText apiBaseEt;
    private EditText tokenEt;
    private EditText branchEt;
    private EditText coldEt;
    private EditText hotEt;
    private EditText drinksEt;
    private EditText cashierEt;
    private CheckBox autoStartCb;
    private CheckBox claimModeCb;
    private CheckBox keepScreenCb;
    private TextView deviceIdTv;
    private TextView statusTv;
    private TextView devicesTv;
    private TextView logTv;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = BridgeCore.prefs(this);
        BridgeCore.getOrCreateDeviceId(this);
        buildUi();
        loadConfig();
        ensurePermissions();
        refreshServiceStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServiceStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Áurea POS Bridge");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView help = new TextView(this);
        help.setText("Puente persistente para comandas. Puede correr fijo en una iMin/tablet/celular o como mini-puente en varias tablets. Para evitar duplicados entre varios dispositivos, el backend debe usar el endpoint /api/print-jobs/claim.");
        help.setPadding(0, 0, 0, dp(12));
        root.addView(help);

        deviceIdTv = new TextView(this);
        deviceIdTv.setTextIsSelectable(true);
        deviceIdTv.setPadding(0, 0, 0, dp(8));
        root.addView(deviceIdTv);

        apiBaseEt = addField(root, "API base URL", "https://tudominio.com");
        tokenEt = addField(root, "Token de sucursal", "token-secreto");
        branchEt = addField(root, "Branch/Sucursal ID", "1");
        coldEt = addField(root, "MAC Barra fría", "00:11:22:AA:BB:01");
        hotEt = addField(root, "MAC Barra caliente", "00:11:22:AA:BB:02");
        drinksEt = addField(root, "MAC Bebidas", "00:11:22:AA:BB:03");
        cashierEt = addField(root, "MAC Caja/Ticket/iMin integrada si aparece como Bluetooth", "");

        autoStartCb = new CheckBox(this);
        autoStartCb.setText("Iniciar puente automáticamente al prender/reiniciar");
        root.addView(autoStartCb);

        claimModeCb = new CheckBox(this);
        claimModeCb.setText("Modo sin duplicados / varios mini-puentes (requiere /claim en backend)");
        claimModeCb.setChecked(true);
        root.addView(claimModeCb);

        keepScreenCb = new CheckBox(this);
        keepScreenCb.setText("Mantener pantalla encendida dentro de la app");
        root.addView(keepScreenCb);

        LinearLayout row1 = row(root);
        Button save = button("Guardar");
        save.setOnClickListener(v -> saveConfig());
        row1.addView(save);
        Button list = button("Ver Bluetooth emparejadas");
        list.setOnClickListener(v -> listPairedDevices());
        row1.addView(list);

        LinearLayout row2 = row(root);
        Button testCold = button("Probar fría");
        testCold.setOnClickListener(v -> testPrint("barra_fria"));
        row2.addView(testCold);
        Button testHot = button("Probar caliente");
        testHot.setOnClickListener(v -> testPrint("barra_caliente"));
        row2.addView(testHot);

        LinearLayout row3 = row(root);
        Button testDrinks = button("Probar bebidas");
        testDrinks.setOnClickListener(v -> testPrint("bebidas"));
        row3.addView(testDrinks);
        Button testCashier = button("Probar caja");
        testCashier.setOnClickListener(v -> testPrint("caja"));
        row3.addView(testCashier);

        LinearLayout row4 = row(root);
        Button once = button("Buscar/imprimir una vez");
        once.setOnClickListener(v -> runOnceService());
        row4.addView(once);
        Button start = button("Iniciar servicio persistente");
        start.setOnClickListener(v -> startBridgeService());
        row4.addView(start);

        LinearLayout row5 = row(root);
        Button stop = button("Detener servicio");
        stop.setOnClickListener(v -> stopBridgeService());
        row5.addView(stop);
        Button refresh = button("Actualizar estado");
        refresh.setOnClickListener(v -> refreshServiceStatus());
        row5.addView(refresh);

        LinearLayout row6 = row(root);
        Button pin = button("Modo kiosko visual");
        pin.setOnClickListener(v -> startKioskVisual());
        row6.addView(pin);
        Button unpin = button("Salir kiosko");
        unpin.setOnClickListener(v -> stopKioskVisual());
        row6.addView(unpin);

        LinearLayout row7 = row(root);
        Button battery = button("Abrir batería");
        battery.setOnClickListener(v -> openBatterySettings());
        row7.addView(battery);

        statusTv = new TextView(this);
        statusTv.setText("Estado: detenido");
        statusTv.setPadding(0, dp(10), 0, dp(6));
        root.addView(statusTv);

        devicesTv = new TextView(this);
        devicesTv.setText("Impresoras emparejadas aparecerán aquí.");
        devicesTv.setTextIsSelectable(true);
        devicesTv.setPadding(0, dp(10), 0, dp(10));
        root.addView(devicesTv);

        logTv = new TextView(this);
        logTv.setText("Logs:\n");
        logTv.setTextIsSelectable(true);
        root.addView(logTv);

        setContentView(scroll);
    }

    private EditText addField(LinearLayout root, String label, String hint) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setPadding(0, dp(8), 0, 0);
        root.addView(tv);
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        root.addView(et);
        return et;
    }

    private LinearLayout row(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        root.addView(row);
        return row;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void loadConfig() {
        apiBaseEt.setText(prefs.getString("api_base", ""));
        tokenEt.setText(prefs.getString("token", ""));
        branchEt.setText(prefs.getString("branch_id", ""));
        coldEt.setText(prefs.getString("barra_fria", ""));
        hotEt.setText(prefs.getString("barra_caliente", ""));
        drinksEt.setText(prefs.getString("bebidas", ""));
        cashierEt.setText(prefs.getString("caja", ""));
        autoStartCb.setChecked(prefs.getBoolean("auto_start", false));
        claimModeCb.setChecked(prefs.getBoolean("claim_mode", true));
        keepScreenCb.setChecked(prefs.getBoolean("keep_screen", true));
        applyKeepScreen();
        deviceIdTv.setText("Device ID: " + BridgeCore.getOrCreateDeviceId(this));
    }

    private void saveConfig() {
        prefs.edit()
                .putString("api_base", clean(apiBaseEt.getText().toString()))
                .putString("token", clean(tokenEt.getText().toString()))
                .putString("branch_id", clean(branchEt.getText().toString()))
                .putString("barra_fria", clean(coldEt.getText().toString()))
                .putString("barra_caliente", clean(hotEt.getText().toString()))
                .putString("bebidas", clean(drinksEt.getText().toString()))
                .putString("caja", clean(cashierEt.getText().toString()))
                .putBoolean("auto_start", autoStartCb.isChecked())
                .putBoolean("claim_mode", claimModeCb.isChecked())
                .putBoolean("keep_screen", keepScreenCb.isChecked())
                .apply();
        applyKeepScreen();
        log("Configuración guardada.");
    }

    private String clean(String s) { return s == null ? "" : s.trim(); }

    private void applyKeepScreen() {
        if (keepScreenCb.isChecked()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private boolean ensurePermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            boolean bt = Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            boolean notif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (!bt || !notif) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMS);
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_PERMS);
            return false;
        }
        return true;
    }

    private void listPairedDevices() {
        if (!ensurePermissions()) return;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { devicesTv.setText("Este dispositivo no tiene Bluetooth."); return; }
        if (!adapter.isEnabled()) { devicesTv.setText("Bluetooth está apagado. Préndelo en Ajustes."); return; }
        Set<BluetoothDevice> devices = adapter.getBondedDevices();
        if (devices == null || devices.isEmpty()) {
            devicesTv.setText("No hay dispositivos Bluetooth emparejados. Empareja las impresoras desde Ajustes > Bluetooth.");
            return;
        }
        StringBuilder sb = new StringBuilder("Dispositivos emparejados:\n\n");
        for (BluetoothDevice d : devices) {
            String name = safeName(d);
            sb.append(name).append("\nMAC: ").append(d.getAddress()).append("\n\n");
        }
        devicesTv.setText(sb.toString());
    }

    private String safeName(BluetoothDevice d) {
        try {
            String name = d.getName();
            return name == null || name.trim().isEmpty() ? "Sin nombre" : name;
        } catch (SecurityException se) { return "Sin permiso Bluetooth"; }
    }

    private void testPrint(String area) {
        saveConfig();
        String mac = BridgeCore.macForArea(prefs, area);
        if (mac.isEmpty()) { log("No hay MAC configurada para " + area); return; }
        String alias = BridgeCore.labelForArea(area);
        byte[] ticket = BridgeCore.buildEscPosTicket(alias, "PRUEBA DE IMPRESIÓN", new String[]{
                "Aurea POS Bridge",
                "Area: " + BridgeCore.sanitize(alias),
                "Device: " + BridgeCore.getOrCreateDeviceId(this),
                "Modo: persistente/kiosko listo",
                "Si salio este ticket, esta area quedo lista."
        });
        new Thread(() -> {
            try { BridgeCore.printBytes(this, mac, ticket); log("Prueba OK: " + alias); }
            catch (Exception e) { log("Error prueba " + alias + ": " + e.getMessage()); }
        }).start();
    }

    private void startBridgeService() {
        saveConfig();
        if (!ensurePermissions()) return;
        Intent svc = new Intent(this, BridgeService.class);
        svc.setAction(BridgeCore.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        log("Servicio persistente solicitado.");
        refreshServiceStatus();
    }

    private void stopBridgeService() {
        Intent svc = new Intent(this, BridgeService.class);
        svc.setAction(BridgeCore.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        log("Deteniendo servicio.");
        refreshServiceStatus();
    }

    private void runOnceService() {
        saveConfig();
        if (!ensurePermissions()) return;
        Intent svc = new Intent(this, BridgeService.class);
        svc.setAction(BridgeCore.ACTION_ONCE);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        log("Búsqueda manual solicitada.");
    }

    private void startKioskVisual() {
        saveConfig();
        try {
            if (Build.VERSION.SDK_INT >= 21) startLockTask();
            log("Modo kiosko visual iniciado. En algunos Android pide confirmar fijar pantalla.");
        } catch (Exception e) {
            log("No se pudo iniciar kiosko visual: " + e.getMessage());
        }
    }

    private void stopKioskVisual() {
        try {
            if (Build.VERSION.SDK_INT >= 21) stopLockTask();
            log("Saliendo de kiosko visual.");
        } catch (Exception e) {
            log("No se pudo salir de kiosko: " + e.getMessage());
        }
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
            log("En esta pantalla, busca Batería y pon Sin restricciones/No optimizar para Áurea POS Bridge.");
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    private void refreshServiceStatus() {
        boolean running = prefs.getBoolean("service_running", false);
        String last = prefs.getString("last_status", running ? "Puente activo" : "Puente detenido");
        statusTv.setText((running ? "Estado: ACTIVO - " : "Estado: detenido - ") + last);
        logTv.setText(prefs.getString("service_log", "Logs servicio:\n"));
    }

    private void log(String message) {
        String line = BridgeCore.now() + " - " + message + "\n";
        runOnUiThread(() -> {
            String current = logTv.getText().toString();
            String next = current + line;
            if (next.length() > 12000) next = "Logs:\n" + next.substring(next.length() - 10000);
            logTv.setText(next);
            statusTv.setText(message);
            prefs.edit().putString("service_log", next).putString("last_status", message).apply();
        });
    }
}
