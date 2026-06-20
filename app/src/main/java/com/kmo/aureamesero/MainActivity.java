package com.kmo.aureamesero;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private EditText apiBase, token, branchId, tableId, waiterName;
    private LinearLayout cartList;
    private TextView status, totalText;
    private Button sendBtn, clearBtn, configBtn, customBtn, accountBtn;
    private LinearLayout configPanel;

    private final List<OrderItem> cart = new ArrayList<>();

    static class Product {
        String name; double price; String area;
        Product(String name, double price, String area) { this.name = name; this.price = price; this.area = area; }
    }
    static class OrderItem {
        String name; int qty; double price; String area; String notes;
        OrderItem(String name, int qty, double price, String area, String notes) { this.name=name; this.qty=qty; this.price=price; this.area=area; this.notes=notes; }
    }

    private final Product[] products = new Product[]{
            new Product("Tacos de arrachera", 95, "barra_caliente"),
            new Product("Hamburguesa", 120, "barra_caliente"),
            new Product("Papas a la francesa", 65, "barra_caliente"),
            new Product("Aguachile verde", 180, "barra_fria"),
            new Product("Ceviche", 150, "barra_fria"),
            new Product("Ensalada", 95, "barra_fria"),
            new Product("Limonada mineral", 35, "bebidas"),
            new Product("Refresco", 30, "bebidas"),
            new Product("Agua natural", 25, "bebidas")
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("aurea_mesero", MODE_PRIVATE);
        buildUi();
        loadPrefs();
        renderCart();
    }

    private TextView tv(String text, int sp, int style) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setTextColor(0xFF111111);
        v.setPadding(12, 8, 12, 8);
        return v;
    }
    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setPadding(14, 8, 14, 8);
        return e;
    }
    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        scroll.addView(root);

        TextView title = tv("Áurea Mesero", 28, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = tv("Pedidos sin impresión local · Las comandas salen por el puente", 14, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        configBtn = btn("Mostrar / ocultar configuración");
        root.addView(configBtn);
        configPanel = new LinearLayout(this);
        configPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(configPanel);
        apiBase = input("API base: http://print.kmo.lat", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        token = input("Token", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        branchId = input("Sucursal", InputType.TYPE_CLASS_TEXT);
        waiterName = input("Mesero", InputType.TYPE_CLASS_TEXT);
        configPanel.addView(tv("API base", 13, Typeface.BOLD)); configPanel.addView(apiBase);
        configPanel.addView(tv("Token", 13, Typeface.BOLD)); configPanel.addView(token);
        configPanel.addView(tv("Sucursal", 13, Typeface.BOLD)); configPanel.addView(branchId);
        configPanel.addView(tv("Nombre del mesero", 13, Typeface.BOLD)); configPanel.addView(waiterName);
        Button save = btn("Guardar configuración");
        configPanel.addView(save);
        save.setOnClickListener(v -> { savePrefs(); toast("Configuración guardada"); });
        configBtn.setOnClickListener(v -> configPanel.setVisibility(configPanel.getVisibility()==View.VISIBLE ? View.GONE : View.VISIBLE));

        root.addView(tv("Mesa", 16, Typeface.BOLD));
        tableId = input("Ej. 4", InputType.TYPE_CLASS_NUMBER);
        root.addView(tableId);

        root.addView(tv("Productos rápidos", 18, Typeface.BOLD));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        root.addView(grid);
        for (Product p : products) {
            Button pb = btn(labelForProduct(p));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0; lp.height = GridLayout.LayoutParams.WRAP_CONTENT; lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            pb.setLayoutParams(lp);
            pb.setOnClickListener(v -> addProduct(p));
            grid.addView(pb);
        }

        customBtn = btn("+ Producto manual");
        root.addView(customBtn);
        customBtn.setOnClickListener(v -> showCustomDialog());

        root.addView(tv("Pedido actual", 18, Typeface.BOLD));
        cartList = new LinearLayout(this);
        cartList.setOrientation(LinearLayout.VERTICAL);
        root.addView(cartList);
        totalText = tv("Total: $0", 16, Typeface.BOLD);
        root.addView(totalText);

        sendBtn = btn("ENVIAR COMANDA");
        sendBtn.setTextSize(20);
        root.addView(sendBtn);
        sendBtn.setOnClickListener(v -> sendOrder());

        clearBtn = btn("Limpiar pedido");
        root.addView(clearBtn);
        clearBtn.setOnClickListener(v -> { cart.clear(); renderCart(); });

        accountBtn = btn("Ver cuenta de esta mesa");
        root.addView(accountBtn);
        accountBtn.setOnClickListener(v -> fetchAccount());

        status = tv("Listo", 14, Typeface.NORMAL);
        status.setTextColor(0xFF444444);
        root.addView(status);

        setContentView(scroll);
    }

    private String labelForProduct(Product p) { return p.name + "\n$" + money(p.price) + " · " + areaLabel(p.area); }
    private String areaLabel(String a) {
        if ("barra_fria".equals(a)) return "Fría";
        if ("barra_caliente".equals(a)) return "Caliente";
        if ("bebidas".equals(a)) return "Bebidas";
        return a;
    }
    private void loadPrefs() {
        apiBase.setText(prefs.getString("api", "http://print.kmo.lat"));
        token.setText(prefs.getString("token", "kmo_aurea_2026"));
        branchId.setText(prefs.getString("branch", "1"));
        waiterName.setText(prefs.getString("waiter", ""));
        configPanel.setVisibility(View.GONE);
    }
    private void savePrefs() {
        prefs.edit()
                .putString("api", apiBase.getText().toString().trim())
                .putString("token", token.getText().toString().trim())
                .putString("branch", branchId.getText().toString().trim())
                .putString("waiter", waiterName.getText().toString().trim())
                .apply();
    }
    private void addProduct(Product p) {
        for (OrderItem i : cart) {
            if (i.name.equals(p.name) && i.area.equals(p.area) && (i.notes == null || i.notes.isEmpty())) {
                i.qty += 1; renderCart(); return;
            }
        }
        cart.add(new OrderItem(p.name, 1, p.price, p.area, ""));
        renderCart();
    }
    private void renderCart() {
        cartList.removeAllViews();
        double total = 0;
        if (cart.isEmpty()) cartList.addView(tv("Sin productos todavía.", 14, Typeface.NORMAL));
        for (int idx=0; idx<cart.size(); idx++) {
            final int index = idx;
            OrderItem it = cart.get(idx);
            total += it.qty * it.price;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = tv(it.qty + "x " + it.name + " · " + areaLabel(it.area) + " · $" + money(it.price * it.qty) + (it.notes == null || it.notes.isEmpty() ? "" : "\nNota: " + it.notes), 14, Typeface.NORMAL);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Button minus = btn("−");
            Button plus = btn("+");
            Button del = btn("X");
            row.addView(minus); row.addView(plus); row.addView(del);
            minus.setOnClickListener(v -> { it.qty--; if (it.qty <= 0) cart.remove(index); renderCart(); });
            plus.setOnClickListener(v -> { it.qty++; renderCart(); });
            del.setOnClickListener(v -> { cart.remove(index); renderCart(); });
            cartList.addView(row);
        }
        totalText.setText("Total: $" + money(total));
    }

    private void showCustomDialog() {
        final Dialog d = new Dialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(26, 26, 26, 26);
        EditText name = input("Nombre producto", InputType.TYPE_CLASS_TEXT);
        EditText qty = input("Cantidad", InputType.TYPE_CLASS_NUMBER);
        EditText price = input("Precio", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText notes = input("Notas", InputType.TYPE_CLASS_TEXT);
        Spinner area = new Spinner(this);
        area.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"barra_caliente", "barra_fria", "bebidas"}));
        qty.setText("1"); price.setText("0");
        box.addView(tv("Producto manual", 20, Typeface.BOLD));
        box.addView(name); box.addView(qty); box.addView(price); box.addView(notes); box.addView(area);
        Button add = btn("Agregar"); box.addView(add);
        d.setContentView(box);
        add.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { toast("Pon nombre"); return; }
            int q = parseInt(qty.getText().toString(), 1);
            double pr = parseDouble(price.getText().toString(), 0);
            cart.add(new OrderItem(n, q, pr, String.valueOf(area.getSelectedItem()), notes.getText().toString().trim()));
            d.dismiss(); renderCart();
        });
        d.show();
    }

    private void sendOrder() {
        savePrefs();
        if (cart.isEmpty()) { toast("Agrega productos"); return; }
        String table = tableId.getText().toString().trim();
        if (table.isEmpty()) { toast("Pon mesa"); return; }
        setBusy(true, "Enviando pedido...");
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("branch_id", safe(branchId.getText().toString(), "1"));
                payload.put("table_id", table);
                payload.put("table_name", "Mesa " + table);
                payload.put("waiter_name", waiterName.getText().toString().trim());
                payload.put("idempotency_key", "mesero_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
                JSONArray items = new JSONArray();
                for (OrderItem it : cart) {
                    JSONObject o = new JSONObject();
                    o.put("name", it.name);
                    o.put("qty", it.qty);
                    o.put("price", it.price);
                    o.put("printer_area", it.area);
                    o.put("notes", it.notes == null ? "" : it.notes);
                    items.put(o);
                }
                payload.put("items", items);
                String response = postJson(endpoint("/api/orders/confirm"), payload.toString());
                JSONObject json = new JSONObject(response);
                boolean ok = json.optBoolean("ok", false);
                int jobs = json.optJSONArray("jobs") == null ? 0 : json.optJSONArray("jobs").length();
                ui.post(() -> {
                    setBusy(false, ok ? "✅ Pedido enviado. Comandas creadas: " + jobs : "❌ Error: " + response);
                    if (ok) { cart.clear(); renderCart(); toast("Pedido enviado"); }
                });
            } catch (Exception e) {
                ui.post(() -> setBusy(false, "❌ Error enviando: " + e.getMessage()));
            }
        }).start();
    }

    private void fetchAccount() {
        savePrefs();
        String table = tableId.getText().toString().trim();
        if (table.isEmpty()) { toast("Pon mesa"); return; }
        setBusy(true, "Consultando cuenta...");
        new Thread(() -> {
            try {
                String url = trimApi() + "/api/tables/" + table + "/account?branch_id=" + enc(safe(branchId.getText().toString(), "1")) + "&token=" + enc(token.getText().toString().trim());
                String res = get(url);
                JSONObject json = new JSONObject(res);
                StringBuilder sb = new StringBuilder();
                sb.append(json.optString("table_name", "Mesa " + table)).append("\n");
                JSONArray items = json.optJSONArray("items");
                double total = 0;
                if (items != null) {
                    for (int i=0;i<items.length();i++) {
                        JSONObject it = items.getJSONObject(i);
                        double line = it.optDouble("qty") * it.optDouble("unit_price");
                        total += line;
                        sb.append(it.optDouble("qty")).append("x ").append(it.optString("product_name")).append("  $").append(money(line)).append("\n");
                    }
                }
                sb.append("\nTOTAL: $").append(money(json.optDouble("total", total)));
                ui.post(() -> showMessage("Cuenta Mesa " + table, sb.toString()));
            } catch (Exception e) {
                ui.post(() -> setBusy(false, "❌ Error cuenta: " + e.getMessage()));
            }
        }).start();
    }

    private String endpoint(String path) { return trimApi() + path + "?token=" + enc(token.getText().toString().trim()); }
    private String trimApi() { String a = apiBase.getText().toString().trim(); while (a.endsWith("/")) a = a.substring(0, a.length()-1); return a; }
    private String postJson(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(20000); c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        return readResponse(c);
    }
    private String get(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(20000); c.setRequestMethod("GET");
        return readResponse(c);
    }
    private String readResponse(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code + ": " + sb.toString());
        return sb.toString();
    }
    private void setBusy(boolean busy, String msg) { sendBtn.setEnabled(!busy); status.setText(msg); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private String money(double v) { return String.format(Locale.US, "%.2f", v); }
    private int parseInt(String s, int d) { try { return Integer.parseInt(s.trim()); } catch(Exception e){ return d; } }
    private double parseDouble(String s, double d) { try { return Double.parseDouble(s.trim()); } catch(Exception e){ return d; } }
    private String safe(String s, String d) { return s == null || s.trim().isEmpty() ? d : s.trim(); }
    private String enc(String s) { try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch(Exception e){ return s; } }
    private void showMessage(String title, String message) {
        setBusy(false, "Cuenta consultada");
        new android.app.AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }
}
