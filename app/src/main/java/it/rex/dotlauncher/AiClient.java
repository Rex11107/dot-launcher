package it.rex.dotlauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Domande brevi all'IA. Senza chiave la domanda passa all'app (o al sito) del servizio scelto;
 * con una chiave API facoltativa la risposta arriva direttamente nel pannello.
 */
final class AiClient {
    static final String[] PROVIDERS = {"claude", "chatgpt", "gemini", "openrouter", "custom"};

    static final String SYSTEM = T.t("Rispondi in italiano nel modo più breve possibile: al massimo due frasi, ")
            + T.t("senza introduzioni né conclusioni. Se la domanda è un calcolo o un problema di matematica, ")
            + T.t("scrivi solo i passaggi matematici essenziali e il risultato, senza parole.");

    /** Premessa aggiunta alla domanda quando la si passa all'app (lì non si può impostare un prompt di sistema). */
    static final String PREFIX = T.t("(Rispondi in poche parole. Se è un calcolo mostra solo i passaggi e il risultato, ")
            + T.t("senza parole.) ");

    static String label(String p) {
        switch (p) {
            case "claude": return "Claude";
            case "chatgpt": return "ChatGPT";
            case "gemini": return "Gemini";
            case "openrouter": return "OpenRouter";
            default: return T.t("Personalizzato");
        }
    }

    static String appPackage(String p) {
        switch (p) {
            case "claude": return "com.anthropic.claude";
            case "chatgpt": return "com.openai.chatgpt";
            case "gemini": return "com.google.android.apps.bard";
            default: return null;
        }
    }

    /** Sito con la domanda già scritta, se il servizio lo permette (altrimenti la pagina principale). */
    static String webUrl(String p, String q) {
        String e;
        try {
            e = URLEncoder.encode(q, "UTF-8");
        } catch (Exception ex) {
            e = "";
        }
        switch (p) {
            case "claude": return "https://claude.ai/new?q=" + e;
            case "chatgpt": return "https://chatgpt.com/?q=" + e;
            case "gemini": return "https://gemini.google.com/app";
            case "openrouter": return "https://openrouter.ai/chat";
            default: return null;
        }
    }

    static boolean webPrefills(String p) {
        return "claude".equals(p) || "chatgpt".equals(p);
    }

    static String defaultModel(String p) {
        switch (p) {
            case "claude": return "claude-haiku-5-5";
            case "chatgpt": return "gpt-4o-mini";
            case "gemini": return "gemini-2.5-flash";
            case "openrouter": return "openrouter/auto";
            default: return "";
        }
    }

    static String defaultBase(String p) {
        switch (p) {
            case "chatgpt": return "https://api.openai.com/v1";
            case "openrouter": return "https://openrouter.ai/api/v1";
            default: return "";
        }
    }

    /** history: coppie {ruolo, testo} con ruolo "user" o "assistant"; l'ultima è la domanda. */
    static String ask(String p, String key, String model, String base, List<String[]> history) throws Exception {
        if (model == null || model.trim().isEmpty()) model = defaultModel(p);
        model = model.trim();
        if ("claude".equals(p)) return anthropic(key, model, history);
        if ("gemini".equals(p)) return gemini(key, model, history);
        String b = base == null || base.trim().isEmpty() ? defaultBase(p) : base.trim();
        if (b.isEmpty()) throw new Exception(T.t("Manca l'indirizzo del servizio"));
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return openAiCompatible(b + "/chat/completions", key, model, history, !"chatgpt".equals(p));
    }

    private static String anthropic(String key, String model, List<String[]> history) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", 600);
        body.put("system", SYSTEM);
        JSONArray msgs = new JSONArray();
        for (String[] m : history) msgs.put(new JSONObject().put("role", m[0]).put("content", m[1]));
        body.put("messages", msgs);
        JSONObject r = post("https://api.anthropic.com/v1/messages", body,
                new String[]{"x-api-key", key, "anthropic-version", "2023-06-01"});
        JSONArray content = r.optJSONArray("content");
        StringBuilder sb = new StringBuilder();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject c = content.getJSONObject(i);
                if ("text".equals(c.optString("type"))) sb.append(c.optString("text"));
            }
        }
        return sb.toString().trim();
    }

    private static String gemini(String key, String model, List<String[]> history) throws Exception {
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", SYSTEM))));
        JSONArray contents = new JSONArray();
        for (String[] m : history) {
            contents.put(new JSONObject().put("role", "assistant".equals(m[0]) ? "model" : "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", m[1]))));
        }
        body.put("contents", contents);
        JSONObject r = post("https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(model, "UTF-8") + ":generateContent", body, new String[]{"x-goog-api-key", key});
        JSONArray cands = r.optJSONArray("candidates");
        StringBuilder sb = new StringBuilder();
        if (cands != null && cands.length() > 0) {
            JSONObject content = cands.getJSONObject(0).optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts != null) for (int i = 0; i < parts.length(); i++) sb.append(parts.getJSONObject(i).optString("text"));
        }
        return sb.toString().trim();
    }

    private static String openAiCompatible(String url, String key, String model, List<String[]> history,
                                           boolean maxTokens) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        if (maxTokens) body.put("max_tokens", 600);
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", SYSTEM));
        for (String[] m : history) msgs.put(new JSONObject().put("role", m[0]).put("content", m[1]));
        body.put("messages", msgs);
        JSONObject r = post(url, body, new String[]{"Authorization", "Bearer " + key,
                "X-Title", "Dot Launcher"});
        JSONArray ch = r.optJSONArray("choices");
        if (ch == null || ch.length() == 0) return "";
        JSONObject msg = ch.getJSONObject(0).optJSONObject("message");
        return msg == null ? "" : msg.optString("content").trim();
    }

    private static JSONObject post(String url, JSONObject body, String[] headers) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        for (int i = 0; i + 1 < headers.length; i += 2) c.setRequestProperty(headers[i], headers[i + 1]);
        try (OutputStream o = c.getOutputStream()) {
            o.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
        }
        c.disconnect();
        if (code >= 400) {
            String msg = T.t("Errore ") + code;
            try {
                JSONObject e = new JSONObject(sb.toString());
                Object err = e.opt("error");
                if (err instanceof JSONObject) msg += ": " + ((JSONObject) err).optString("message", "");
                else if (err != null) msg += ": " + err;
            } catch (Exception ignored) {
            }
            if (code == 401 || code == 403) msg = T.t("Chiave API non valida o senza permessi (") + code + ")";
            throw new Exception(msg);
        }
        return new JSONObject(sb.toString());
    }

    private AiClient() {}
}
