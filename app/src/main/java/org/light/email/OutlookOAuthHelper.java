package org.light.email;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class OutlookOAuthHelper {
    private static final String AUTH_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final String REDIRECT_URI = "lightemail://outlook-auth";
    private static final String SCOPES = "openid offline_access https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send";

    public static String generateCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] code = new byte[32];
        sr.nextBytes(code);
        return Base64.encodeToString(code, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String generateCodeChallenge(String verifier) throws Exception {
        byte[] bytes = verifier.getBytes(StandardCharsets.US_ASCII);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(bytes, 0, bytes.length);
        byte[] digest = md.digest();
        return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static Uri getAuthUri(String codeChallenge) {
        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.OUTLOOK_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build();
    }

    public static TokenResponse exchangeCode(String code, String codeVerifier) throws Exception {
        String body = "grant_type=authorization_code" +
            "&code=" + encode(code) +
            "&client_id=" + encode(BuildConfig.OUTLOOK_CLIENT_ID) +
            "&redirect_uri=" + encode(REDIRECT_URI) +
            "&code_verifier=" + encode(codeVerifier);

        return postTokenRequest(body);
    }

    public static TokenResponse refreshToken(String refreshToken) throws Exception {
        String body = "grant_type=refresh_token" +
            "&refresh_token=" + encode(refreshToken) +
            "&client_id=" + encode(BuildConfig.OUTLOOK_CLIENT_ID) +
            "&scope=" + encode(SCOPES);

        return postTokenRequest(body);
    }

    public static String getEmail(String idToken) {
        if (idToken == null) {
            return null;
        }
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length > 1) {
                String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(payload);
                return json.optString("preferred_username", json.optString("email", null));
            }
        } catch (Throwable ex) {
            // Log or ignore
        }
        return null;
    }

    private static String encode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static TokenResponse postTokenRequest(String body) throws Exception {
        URL url = new URL(TOKEN_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                JSONObject json = new JSONObject(response.toString());
                TokenResponse res = new TokenResponse();
                res.accessToken = json.getString("access_token");
                res.refreshToken = json.optString("refresh_token", null);
                res.idToken = json.optString("id_token", null);
                res.expiry = System.currentTimeMillis() + (json.getLong("expires_in") * 1000);
                return res;
            }
        } else {
            // Read error stream
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                throw new Exception("Token request failed: " + responseCode + " " + response.toString());
            } catch (Throwable t) {
                throw new Exception("Token request failed: " + responseCode);
            }
        }
    }

    public static class TokenResponse {
        public String accessToken;
        public String refreshToken;
        public String idToken;
        public long expiry;
    }
}
