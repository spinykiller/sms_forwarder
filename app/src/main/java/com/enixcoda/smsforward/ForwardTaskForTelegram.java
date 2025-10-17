package com.enixcoda.smsforward;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class ForwardTaskForTelegram extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "ForwardTaskForTelegram";
    String senderNumber;
    String message;
    String chatId;
    String token;

    public ForwardTaskForTelegram(String senderNumber, String message, String chatId, String token) {
        this.senderNumber = senderNumber;
        this.message = message;
        this.chatId = chatId;
        this.token = token;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try {
            String fullMessage = String.format("Message from %s:\n%s", senderNumber, message);
            sendViaTelegram(chatId, fullMessage, token);
        } catch (Exception e) {
            Log.e(TAG, "Error sending Telegram message: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Send message via Telegram using GET request with URL parameters
     * Supports both numeric chat_id and @username format for groups/channels
     * 
     * @param chatId The chat ID (numeric) or @username for groups/channels
     * @param message The message to send
     * @param token The bot token
     */
    private void sendViaTelegram(String chatId, String message, String token) throws IOException {
        HttpURLConnection connection = null;
        try {
            // Build URL with encoded parameters (similar to Python's urllib.parse.quote_plus)
            String encodedMessage = URLEncoder.encode(message, "UTF-8");
            String encodedChatId = URLEncoder.encode(chatId, "UTF-8");
            
            // Construct the Telegram API URL
            String urlString = String.format(
                "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s",
                token,
                encodedChatId,
                encodedMessage
            );
            
            Log.d(TAG, "Sending to Telegram chat: " + chatId);
            
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read response
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
                );
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                Log.d(TAG, "Telegram message sent successfully. Response: " + response.toString());
            } else {
                // Read error response
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream())
                );
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorResponse.append(line);
                }
                reader.close();
                
                Log.e(TAG, "Telegram API error. Status: " + responseCode + ", Response: " + errorResponse.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while sending Telegram message: " + e.getMessage(), e);
            throw new IOException("Failed to send Telegram message", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}